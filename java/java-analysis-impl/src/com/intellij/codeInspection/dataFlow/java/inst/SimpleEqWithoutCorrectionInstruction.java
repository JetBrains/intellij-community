// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.FALSE;
import static com.intellij.codeInspection.dataFlow.types.DfTypes.TRUE;

/**
 * WARNING: use {@link BooleanBinaryInstruction} if it is possible, this class doesn't perform "corrections"
 * <p>
 * Evaluates {@code left == right} as <em>exact</em> equality of the two top stack values and pushes the boolean
 * result, narrowing the operands into the matching and non-matching states directly via {@link DfType#meet(DfType)}
 * and {@link DfType#tryNegate()}.
 * <p>
 * Unlike {@link BooleanBinaryInstruction} with {@link com.intellij.codeInspection.dataFlow.value.RelationType#EQ},
 * this instruction applies no relation "correction". Equality therefore follows the
 * representation equivalence already modeled by the operands' {@link DfType}: {@code +0.0} and {@code -0.0} are
 * distinct, while all {@code NaN} bit patterns are equal.
 * <p>
 * One of the examples for using it is matching floating-point {@code switch} case constants, which the JLS defines via representation
 * equivalence (JEP 532, "Primitive Types in Patterns, instanceof, and switch").
 */
public class SimpleEqWithoutCorrectionInstruction extends ExpressionPushingInstruction {
  public SimpleEqWithoutCorrectionInstruction(@Nullable DfaAnchor anchor) {
    super(anchor);
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    DfaValue dfaConstant = stateBefore.pop();
    DfaValue dfaSelector = stateBefore.pop();
    DfType constantType = stateBefore.getDfType(dfaConstant);

    ArrayList<DfaInstructionState> states = new ArrayList<>(2);
    DfaMemoryState taken = stateBefore.createCopy();
    if (taken.meetDfType(dfaSelector, constantType)) {
      pushResult(interpreter, taken, TRUE);
      states.add(nextState(interpreter, taken));
    }

    DfType tryNegate = constantType.tryNegate();
    //something strange, skip
    if (tryNegate == null) {
      pushResult(interpreter, stateBefore, FALSE);
      return nextStates(interpreter, stateBefore);
    }

    if (stateBefore.meetDfType(dfaSelector, tryNegate)) {
      pushResult(interpreter, stateBefore, FALSE);
      states.add(nextState(interpreter, stateBefore));
    }
    return states.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  @Override
  public String toString() {
    return "SIMPLE_EQ_WITHOUT_CORRECTION";
  }
}
