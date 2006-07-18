package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.value.DfaRelationValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jul 16, 2003
 * Time: 10:25:44 PM
 * To change this template use Options | File Templates.
 */
public interface DfaMemoryState {
  DfaMemoryState createCopy();

  DfaValue pop();
  void push(DfaValue value);

  int popOffset();
  void pushOffset(int offset);

  void emptyStack();

  void setVarValue(DfaVariableValue var, DfaValue value);

  boolean applyInstanceofOrNull(DfaRelationValue dfaCond);

  boolean applyCondition(DfaValue dfaCond);

  boolean applyNotNull(DfaValue value);

  void flushFields(DataFlowRunner runner);

  void flushVariable(DfaVariableValue variable);

  boolean isNull(DfaValue dfaVar);

  boolean checkNotNullable(DfaValue value);

  boolean canBeNaN(DfaValue dfaValue);
}
