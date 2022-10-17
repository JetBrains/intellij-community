// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.codeInspection.dataFlow.value.DfaCondition;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaWrappedValue;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.*;

/**
 * Evaluate comparison like a < b
 */
public class BooleanBinaryInstruction extends ExpressionPushingInstruction {
  private final @NotNull RelationType myRelation;
  private final boolean myForceEqualityByContent;

  /**
   * @param relation               operation relation
   * @param forceEqualityByContent if true, equality by content will be used instead of reference equality where supported
   *                               (e.g., for strings)
   * @param anchor                 to bind instruction to
   */
  public BooleanBinaryInstruction(@NotNull RelationType relation, boolean forceEqualityByContent, @Nullable DfaAnchor anchor) {
    super(anchor);
    myRelation = relation;
    myForceEqualityByContent = forceEqualityByContent;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    DfaValue dfaRight = stateBefore.pop();
    DfaValue dfaLeft = stateBefore.pop();

    if ((myRelation == RelationType.EQ || myRelation == RelationType.NE) &&
        !myForceEqualityByContent && shouldCompareByEquals(stateBefore, dfaLeft, dfaRight)) {
      ArrayList<DfaInstructionState> states = new ArrayList<>(2);
      DfaMemoryState equality = stateBefore.createCopy();
      DfaCondition condition = dfaLeft.eq(dfaRight);
      if (equality.applyCondition(condition)) {
        pushResult(interpreter, equality, BOOLEAN);
        states.add(nextState(interpreter, equality));
      }
      if (stateBefore.applyCondition(condition.negate())) {
        pushResult(interpreter, stateBefore, booleanValue(myRelation == RelationType.NE));
        states.add(nextState(interpreter, stateBefore));
      }
      return states.toArray(DfaInstructionState.EMPTY_ARRAY);
    }
    RelationType[] relations = splitRelation(myRelation);

    ArrayList<DfaInstructionState> states = new ArrayList<>(relations.length);

    for (int i = 0; i < relations.length; i++) {
      RelationType relation = relations[i];
      DfaCondition condition = dfaLeft.cond(relation, dfaRight);
      if (condition == DfaCondition.getFalse()) continue;
      boolean result = myRelation.isSubRelation(relation);
      if (condition == DfaCondition.getTrue()) {
        pushResult(interpreter, stateBefore, booleanValue(result));
        return nextStates(interpreter, stateBefore);
      }
      final DfaMemoryState copy = i == relations.length - 1 && !states.isEmpty() ? stateBefore : stateBefore.createCopy();
      if (copy.applyCondition(condition) &&
          copy.meetDfType(dfaLeft, copy.getDfType(dfaLeft).correctForRelationResult(myRelation, result)) &&
          copy.meetDfType(dfaRight, copy.getDfType(dfaRight).correctForRelationResult(myRelation, result))) {
        pushResult(interpreter, copy, booleanValue(result));
        states.add(nextState(interpreter, copy));
      }
    }
    if (states.isEmpty()) {
      // Neither of relations could be applied: likely comparison with NaN; do not split the state in this case, just push false
      pushResult(interpreter, stateBefore, FALSE);
      return nextStates(interpreter, stateBefore);
    }

    return states.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  private static RelationType @NotNull [] splitRelation(RelationType relationType) {
    return switch (relationType) {
      case LT, LE, GT, GE -> new RelationType[]{RelationType.LT, RelationType.GT, RelationType.EQ};
      default -> new RelationType[]{relationType, relationType.getNegated()};
    };
  }

  /**
   * Returns true if two given values should be compared by content, rather than by reference.
   * @param memState memory state
   * @param dfaLeft left value
   * @param dfaRight right value
   * @return true if two given values should be compared by content, rather than by reference.
   */
  private static boolean shouldCompareByEquals(@NotNull DfaMemoryState memState, @NotNull DfaValue dfaLeft, @NotNull DfaValue dfaRight) {
    if (dfaLeft == dfaRight && !(dfaLeft instanceof DfaWrappedValue) && !(dfaLeft.getDfType() instanceof DfConstantType)) {
      return false;
    }
    return TypeConstraint.fromDfType(memState.getDfType(dfaLeft)).isComparedByEquals() &&
           TypeConstraint.fromDfType(memState.getDfType(dfaRight)).isComparedByEquals();

  }

  public String toString() {
    return "BOOLEAN_OP " + myRelation;
  }
}
