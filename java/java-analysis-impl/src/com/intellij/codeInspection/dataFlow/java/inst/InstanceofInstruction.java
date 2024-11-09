// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.GetterDescriptor;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTypesUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.*;

public class InstanceofInstruction extends ExpressionPushingInstruction {
  private final boolean myClassObjectCheck;

  /**
   * @param anchor anchor to use
   * @param classObjectCheck if true then the top-of-stack value is assumed to be a Class object (PsiType constant or not)
   *                         instead of target DfType
   */
  public InstanceofInstruction(@Nullable DfaAnchor anchor, boolean classObjectCheck) {
    super(anchor);
    myClassObjectCheck = classObjectCheck;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    DfaValue dfaRight = stateBefore.pop();
    DfaValue dfaLeft = stateBefore.pop();
    DfaValueFactory factory = interpreter.getFactory();
    boolean unknownTargetType = false;
    DfaCondition condition = null;
    if (myClassObjectCheck) {
      PsiType type = stateBefore.getDfType(dfaRight).getConstantOfType(PsiType.class);
      if (type == null || type instanceof PsiPrimitiveType) {
        // Unknown/primitive class: just execute contract "null -> false"
        condition = dfaLeft.cond(RelationType.NE, NULL);
        unknownTargetType = true;
      } else {
        dfaRight = factory.fromDfType(typedObject(type, Nullability.NOT_NULL));
      }
    }
    if (condition == null) {
      condition = dfaLeft.cond(RelationType.IS, dfaRight);
    }

    ArrayList<DfaInstructionState> states = new ArrayList<>(2);
    DfType leftType = stateBefore.getDfType(dfaLeft);
    if (condition.isUnknown()) {
      pushResult(interpreter, stateBefore, BOOLEAN, dfaLeft, dfaRight);
      states.add(nextState(interpreter, stateBefore));
    }
    else {
      final DfaMemoryState trueState = stateBefore.createCopy();
      if (trueState.applyCondition(condition)) {
        pushResult(interpreter, trueState, unknownTargetType ? BOOLEAN : TRUE, dfaLeft, dfaRight);
        states.add(nextState(interpreter, trueState));
      }
      DfaCondition negated = condition.negate();
      if (unknownTargetType ? stateBefore.applyContractCondition(negated) : stateBefore.applyCondition(negated)) {
        pushResult(interpreter, stateBefore, FALSE, dfaLeft, dfaRight);
        states.add(nextState(interpreter, stateBefore));
        DfaNullability oldNullability = DfaNullability.fromDfType(leftType);
        DfaNullability newNullability = DfaNullability.fromDfType(stateBefore.getDfType(dfaLeft));
        if (newNullability == DfaNullability.NULL && oldNullability == DfaNullability.UNKNOWN) {
          // Not-instanceof check leaves only "null" possible value in some state: likely the state is ephemeral
          stateBefore.markEphemeral();
        }
      }
    }
    return states.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  @Override
  public List<VariableDescriptor> getRequiredDescriptors(@NotNull DfaValueFactory factory) {
    return StreamEx.of(factory.getValues())
      .select(DfaVariableValue.class)
      .map(DfaVariableValue::getDescriptor)
      .filter(desc -> desc instanceof GetterDescriptor getterDescriptor &&
                      PsiTypesUtil.isGetClass(getterDescriptor.getPsiElement()))
      .toList();
  }

  @Override
  public String toString() {
    return "INSTANCE_OF " + (myClassObjectCheck ? "(CLASS)" : "");
  }
}
