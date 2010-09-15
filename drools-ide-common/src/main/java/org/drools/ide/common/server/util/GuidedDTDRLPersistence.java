/**
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.ide.common.server.util;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.drools.ide.common.client.modeldriven.brl.ActionFieldValue;
import org.drools.ide.common.client.modeldriven.brl.ActionInsertFact;
import org.drools.ide.common.client.modeldriven.brl.ActionRetractFact;
import org.drools.ide.common.client.modeldriven.brl.ActionSetField;
import org.drools.ide.common.client.modeldriven.brl.ActionUpdateField;
import org.drools.ide.common.client.modeldriven.brl.FactPattern;
import org.drools.ide.common.client.modeldriven.brl.IAction;
import org.drools.ide.common.client.modeldriven.brl.IPattern;
import org.drools.ide.common.client.modeldriven.brl.BaseSingleFieldConstraint;
import org.drools.ide.common.client.modeldriven.brl.RuleAttribute;
import org.drools.ide.common.client.modeldriven.brl.RuleMetadata;
import org.drools.ide.common.client.modeldriven.brl.RuleModel;
import org.drools.ide.common.client.modeldriven.brl.SingleFieldConstraint;
import org.drools.ide.common.client.modeldriven.dt.ActionCol;
import org.drools.ide.common.client.modeldriven.dt.ActionInsertFactCol;
import org.drools.ide.common.client.modeldriven.dt.ActionRetractFactCol;
import org.drools.ide.common.client.modeldriven.dt.ActionSetFieldCol;
import org.drools.ide.common.client.modeldriven.dt.AttributeCol;
import org.drools.ide.common.client.modeldriven.dt.ConditionCol;
import org.drools.ide.common.client.modeldriven.dt.GuidedDecisionTable;
import org.drools.ide.common.client.modeldriven.dt.MetadataCol;

/**
 * This takes care of converting GuidedDT object to DRL (via the RuleModel).
 * @author Michael Neale
 *
 */
public class GuidedDTDRLPersistence {

	public static GuidedDTDRLPersistence getInstance() {
		return new GuidedDTDRLPersistence();
	}

	public String marshal(GuidedDecisionTable dt) {

		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < dt.data.length; i++) {
			String[] row = dt.data[i];
			String num = row[0];
			String desc = row[1];

			RuleModel rm = new RuleModel();
			rm.name = getName(dt.tableName, num);

			doMetadata(dt.getMetadataCols(), row, rm);
			doAttribs(dt.getMetadataCols().size(), dt.attributeCols, row, rm);
			doConditions(dt.getMetadataCols().size() + dt.attributeCols.size(), dt.conditionCols, row, rm);
			doActions(dt.getMetadataCols().size() + dt.attributeCols.size() + dt.conditionCols.size(), dt.actionCols, row, rm);

			if(dt.parentName != null){
				rm.parentName = dt.parentName;
			}

			sb.append("#from row number: " + (i + 1) + "\n");
			String rule = BRDRLPersistence.getInstance().marshal(rm);
			sb.append(rule);
			sb.append("\n");
		}


		return sb.toString();

	}

	void doActions(int condAndAttrs, List<ActionCol> actionCols, String[] row, RuleModel rm) {
		List<LabelledAction> actions = new ArrayList<LabelledAction>();
		for (int i = 0; i < actionCols.size(); i++) {
			ActionCol c = actionCols.get(i);
			String cell = row[condAndAttrs + i + GuidedDecisionTable.INTERNAL_ELEMENTS];
            if (!validCell(cell)) {
                cell = c.defaultValue;
            }
			if (validCell(cell)) {
				if (c instanceof ActionInsertFactCol) {
					ActionInsertFactCol ac = (ActionInsertFactCol)c;
					LabelledAction a = findByLabelledAction(actions, ac.boundName);
					if (a == null) {
						a = new LabelledAction();
						a.boundName  = ac.boundName;
						ActionInsertFact ins = new ActionInsertFact(ac.factType);
						ins.setBoundName( ac.boundName );
						a.action = ins;
						actions.add(a);
					}
					ActionInsertFact ins = (ActionInsertFact) a.action;
					ActionFieldValue val = new ActionFieldValue(ac.factField, cell, ac.type);
					ins.addFieldValue(val);
				} else if (c instanceof ActionRetractFactCol) {
					ActionRetractFactCol rf = (ActionRetractFactCol)c;
					LabelledAction a = findByLabelledAction(actions, rf.boundName);
					if (a == null) {
						a = new LabelledAction();
						a.action = new ActionRetractFact(rf.boundName);
						a.boundName = rf.boundName;
						actions.add(a);
					}
				} else if (c instanceof ActionSetFieldCol) {
					ActionSetFieldCol sf = (ActionSetFieldCol)c;
					LabelledAction a = findByLabelledAction(actions, sf.boundName);
					if (a == null) {
						a = new LabelledAction();
						a.boundName = sf.boundName;
						if (!sf.update) {
							a.action = new ActionSetField(sf.boundName);
						} else {
							a.action = new ActionUpdateField(sf.boundName);
						}
						actions.add(a);
					} else if (sf.update && !(a.action instanceof ActionUpdateField)) {
						//lets swap it out for an update as the user has asked for it.
						ActionSetField old = (ActionSetField) a.action;
						ActionUpdateField update = new ActionUpdateField(sf.boundName);
						update.fieldValues = old.fieldValues;
						a.action = update;
					}
					ActionSetField asf = (ActionSetField) a.action;
					ActionFieldValue val = new ActionFieldValue(sf.factField, cell, sf.type);
					asf.addFieldValue(val);
				}
			}
		}

		rm.rhs = new IAction[actions.size()];
		for (int i = 0; i < rm.rhs.length; i++) {
			rm.rhs[i] = actions.get(i).action;
		}
	}

	private LabelledAction findByLabelledAction(List<LabelledAction> actions, String boundName) {
		for (LabelledAction labelledAction : actions) {
			if (labelledAction.boundName.equals(boundName)) {
				return labelledAction;
			}
		}
		return null;
	}

	void doConditions(int numOfAttributesAndMeta, List<ConditionCol> conditionCols, String[] row, RuleModel rm) {

		List<FactPattern> patterns = new ArrayList<FactPattern>();

		for (int i = 0; i < conditionCols.size(); i++) {
			ConditionCol c = (ConditionCol) conditionCols.get(i);
			String cell = row[i + GuidedDecisionTable.INTERNAL_ELEMENTS + numOfAttributesAndMeta];

            if (!validCell(cell)) {
                //try default value
                cell = c.defaultValue;
            }
            
			if (validCell(cell)) {

				//get or create the pattern it belongs too
				FactPattern fp = findByFactPattern(patterns, c.boundName);
				if (fp == null) {
					fp = new FactPattern(c.factType);
					fp.boundName = c.boundName;
					patterns.add(fp);
				}



				//now add the constraint from this cell
				switch (c.constraintValueType) {
					case BaseSingleFieldConstraint.TYPE_LITERAL:
					case BaseSingleFieldConstraint.TYPE_RET_VALUE:
						SingleFieldConstraint sfc = new SingleFieldConstraint(c.factField);
						if (no(c.operator)) {

							String[] a = cell.split("\\s");
							if (a.length > 1) {
								sfc.setOperator(a[0]);
								sfc.setValue(a[1]);
							} else {
								sfc.setValue(cell);
							}
						} else {
							sfc.setOperator(c.operator);
                            if (c.operator.equals("in")) {
                                sfc.setValue(makeInList(cell));
                            } else {
                                sfc.setValue(cell);
                            }

						}
						sfc.setConstraintValueType(c.constraintValueType);
						fp.addConstraint(sfc);
						break;
					case BaseSingleFieldConstraint.TYPE_PREDICATE:
						SingleFieldConstraint pred = new SingleFieldConstraint();
						pred.setConstraintValueType(c.constraintValueType);
                        if (c.factField != null && c.factField.indexOf("$param") > -1) {
                            //handle interpolation
                            pred.setValue(c.factField.replace("$param", cell));  
                        } else {
						    pred.setValue(cell);
                        }
						fp.addConstraint(pred);
						break;
				default:
					throw new IllegalArgumentException("Unknown constraintValueType: " + c.constraintValueType);
				}
			}
		}
		rm.lhs = patterns.toArray(new IPattern[patterns.size()]);
	}

    /**
     * take a CSV list and turn it into DRL syntax
     */
    String makeInList(String cell) {
        if (cell.startsWith("(")) return cell;
        String res = "";
        StringTokenizer st = new StringTokenizer(cell, ",");
        while (st.hasMoreTokens()) {
            String t = st.nextToken().trim();
            if (t.startsWith("\"")) {
                res += t;
            } else {
                res += "\"" + t + "\"";
            }
            if (st.hasMoreTokens()) res += ", ";
        }
        return "(" + res + ")";
    }


    private boolean no(String operator) {
		return operator == null || "".equals(operator);
	}

	private FactPattern findByFactPattern(List<FactPattern> patterns, String boundName) {
		for (FactPattern factPattern : patterns) {
			if (factPattern.boundName.equals(boundName)) {
				return factPattern;
			}
		}
		return null;
	}

	void doAttribs(int numOfMeta, List<AttributeCol> attributeCols, String[] row, RuleModel rm) {
		List<RuleAttribute> attribs = new ArrayList<RuleAttribute>();
		for (int j = 0; j < attributeCols.size(); j++) {
			AttributeCol at = attributeCols.get(j);
			String cell = row[j + GuidedDecisionTable.INTERNAL_ELEMENTS + numOfMeta];
			if (validCell(cell)) {
				attribs.add(new RuleAttribute(at.attr, cell));
			} else if (at.defaultValue != null) {
                attribs.add(new RuleAttribute(at.attr, at.defaultValue));                
            }
		}
		if (attribs.size() > 0) {
			rm.attributes = attribs.toArray(new RuleAttribute[attribs.size()]);
		}
	}

	void doMetadata(List<MetadataCol> metadataCols, String[] row, RuleModel rm) {

		// setup temp list
		List<RuleMetadata> metadataList = new ArrayList<RuleMetadata>();

		for (int j = 0; j < metadataCols.size(); j++) {
			MetadataCol meta = metadataCols.get(j);
			String cell = row[j + GuidedDecisionTable.INTERNAL_ELEMENTS];
			if (validCell(cell)) {
				metadataList.add(new RuleMetadata(meta.attr, cell));
			}
		}
		if (metadataList.size() > 0) {
			rm.metadataList = metadataList.toArray(new RuleMetadata[metadataList.size()]);
		}
	}

	String getName(String tableName, String num) {
		return "Row " + num + " " + tableName;
	}

	boolean validCell(String c) {
		return (c != null) && (!c.trim().equals(""));
	}

	private class LabelledAction {
		String boundName;
		IAction action;
	}

}