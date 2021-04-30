/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.dataFlow.lang.ir.inst;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaCondition;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Objects;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.*;

/**
 * @author peter
 */
public class InstanceofInstruction extends ExpressionPushingInstruction implements BranchingInstruction {
  @Nullable private final PsiExpression myLeft;
  @Nullable private final PsiType myCastType;

  public InstanceofInstruction(@NotNull DfaAnchor anchor, @Nullable PsiExpression left,
                               @NotNull PsiType castType) {
    super(anchor);
    myLeft = left;
    myCastType = castType;
  }

  /**
   * Construct a class object instanceof check (e.g. from Class.isInstance call); castType is not known
   * @param psiAnchor anchor call
   */
  public InstanceofInstruction(@NotNull PsiMethodCallExpression psiAnchor) {
    super(new JavaExpressionAnchor(psiAnchor));
    myLeft = null;
    myCastType = null;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowRunner runner, @NotNull DfaMemoryState stateBefore) {
    DfaValue dfaRight = stateBefore.pop();
    DfaValue dfaLeft = stateBefore.pop();
    DfaValueFactory factory = runner.getFactory();
    boolean unknownTargetType = false;
    DfaCondition condition = null;
    if (isClassObjectCheck()) {
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
      pushResult(runner, stateBefore, BOOLEAN, dfaLeft, dfaRight);
      states.add(nextState(runner, stateBefore));
    }
    else {
      final DfaMemoryState trueState = stateBefore.createCopy();
      if (trueState.applyCondition(condition)) {
        pushResult(runner, trueState, unknownTargetType ? BOOLEAN : TRUE, dfaLeft, dfaRight);
        states.add(nextState(runner, trueState));
      }
      DfaCondition negated = condition.negate();
      if (unknownTargetType ? stateBefore.applyContractCondition(negated) : stateBefore.applyCondition(negated)) {
        pushResult(runner, stateBefore, FALSE, dfaLeft, dfaRight);
        states.add(nextState(runner, stateBefore));
        if (stateBefore.isNull(dfaLeft) && DfaNullability.fromDfType(leftType) == DfaNullability.UNKNOWN) {
          // Not-instanceof check leaves only "null" possible value in some state: likely the state is ephemeral
          stateBefore.markEphemeral();
        }
      }
    }
    return states.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  /**
   * @return instanceof operand or null if it's not applicable
   * (e.g. instruction is emitted when inlining Xyz.class::isInstance method reference)
   */
  @Nullable
  public PsiExpression getLeft() {
    return myLeft;
  }

  public PsiExpression getExpression() {
    return ((JavaExpressionAnchor)Objects.requireNonNull(getDfaAnchor())).getExpression();
  }
  
  @Nullable
  public PsiType getCastType() {
    return myCastType;
  }

  /**
   * @return true if this instanceof instruction checks against Class object (e.g. Class.isInstance() call). In this case
   * class object is located on the stack and cast type is not known
   */
  public boolean isClassObjectCheck() {
    return myCastType == null;
  }

  @Override
  public String toString() {
    return "INSTANCE_OF" + (myCastType == null ? "" : " " + myCastType.getCanonicalText()); 
  }
}
