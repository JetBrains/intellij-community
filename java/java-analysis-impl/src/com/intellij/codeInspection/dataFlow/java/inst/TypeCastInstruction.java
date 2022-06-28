// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.TypeConstraints;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.jvm.problems.ClassCastProblem;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.NULL;
import static com.intellij.codeInspection.dataFlow.types.DfTypes.typedObject;

public class TypeCastInstruction extends ExpressionPushingInstruction {
  private final PsiExpression myCasted;
  private final PsiType myCastTo;
  private final @Nullable DfaControlTransferValue myTransferValue;

  public TypeCastInstruction(PsiTypeCastExpression castExpression,
                             PsiExpression casted,
                             PsiType castTo,
                             @Nullable DfaControlTransferValue value) {
    this(new JavaExpressionAnchor(castExpression), casted, castTo, value);
  }
  
  private TypeCastInstruction(DfaAnchor castExpression,
                             PsiExpression casted,
                             PsiType castTo,
                             @Nullable DfaControlTransferValue value) {
    super(castExpression);
    assert !(castTo instanceof PsiPrimitiveType);
    myCasted = casted;
    myCastTo = TypeConversionUtil.erasure(castTo);
    myTransferValue = value;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    if (myTransferValue == null) return this;
    return new TypeCastInstruction(getDfaAnchor(), myCasted, myCastTo, myTransferValue.bindToFactory(factory));
  }

  public PsiExpression getCasted() {
    return myCasted;
  }

  public @Nullable UnsatisfiedConditionProblem getConditionProblem() {
    JavaExpressionAnchor anchor = (JavaExpressionAnchor)getDfaAnchor();
    return anchor == null ? null : new ClassCastProblem(((PsiTypeCastExpression)anchor.getExpression()));
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    final DfaValueFactory factory = interpreter.getFactory();
    PsiType fromType = getCasted().getType();
    TypeConstraint constraint = TypeConstraints.instanceOf(myCastTo);
    boolean castPossible = true;
    List<DfaInstructionState> result = new ArrayList<>();
    if (myTransferValue != null) {
      DfaMemoryState castFail = stateBefore.createCopy();
      if (fromType != null && myCastTo.isConvertibleFrom(fromType)) {
        if (!castTopOfStack(factory, stateBefore, constraint)) {
          castPossible = false;
        } else {
          result.add(nextState(interpreter, stateBefore));
          pushResult(interpreter, stateBefore, stateBefore.pop());
        }
      }
      DfaValue value = castFail.peek();
      DfaCondition notNullCondition = value.cond(RelationType.NE, NULL);
      DfaCondition notTypeCondition = value.cond(RelationType.IS_NOT, typedObject(myCastTo, Nullability.NOT_NULL));
      if (castFail.applyCondition(notNullCondition) && castFail.applyCondition(notTypeCondition)) {
        List<DfaInstructionState> states = myTransferValue.dispatch(castFail, interpreter);
        for (DfaInstructionState cceState : states) {
          cceState.getMemoryState().markEphemeral();
        }
        result.addAll(states);
      }
    } else {
      if (fromType != null && myCastTo.isConvertibleFrom(fromType)) {
        if (!castTopOfStack(factory, stateBefore, constraint)) {
          castPossible = false;
        }
      }

      result.add(nextState(interpreter, stateBefore));
      pushResult(interpreter, stateBefore, stateBefore.pop());
    }
    UnsatisfiedConditionProblem problem = getConditionProblem();
    if (problem != null) {
      interpreter.getListener().onCondition(problem, stateBefore.peek(), castPossible ? ThreeState.UNSURE : ThreeState.YES, stateBefore);
    }
    return result.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  private static boolean castTopOfStack(@NotNull DfaValueFactory factory,
                                        @NotNull DfaMemoryState state,
                                        @NotNull TypeConstraint type) {
    DfaValue value = state.peek();
    DfType dfType = state.getDfType(value);
    DfType result = dfType.meet(type.asDfType());
    if (!result.equals(dfType)) {
      if (result == NULL || !state.meetDfType(value, result)) return false;
      if (!(value instanceof DfaVariableValue)) {
        state.pop();
        state.push(factory.fromDfType(result));
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return "CAST_TO "+myCastTo.getCanonicalText();
  }
}
