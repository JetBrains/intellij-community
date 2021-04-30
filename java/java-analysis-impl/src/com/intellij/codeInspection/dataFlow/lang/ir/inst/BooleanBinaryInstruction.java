/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaCondition;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.*;
import static com.intellij.psi.JavaTokenType.*;

public class BooleanBinaryInstruction extends ExpressionPushingInstruction implements BranchingInstruction {
  // AND and OR for boolean arguments only
  private static final TokenSet ourSignificantOperations = TokenSet.create(EQ, EQEQ, NE, LT, GT, LE, GE, INSTANCEOF_KEYWORD, AND, OR);

  /**
   * A special operation to express string comparison by content (like equals() method does).
   * Used to desugar switch statements
   */
  public static final IElementType STRING_EQUALITY_BY_CONTENT = EQ;

  private final @Nullable IElementType myOpSign;

  /**
   * @param opSign sign of the operation
   * @param anchor to bind instruction to
   */
  public BooleanBinaryInstruction(IElementType opSign, @Nullable DfaAnchor anchor) {
    super(anchor);
    myOpSign = opSign == XOR ? NE : ourSignificantOperations.contains(opSign) ? opSign : null;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowRunner runner, @NotNull DfaMemoryState stateBefore) {
    DfaValue dfaRight = stateBefore.pop();
    DfaValue dfaLeft = stateBefore.pop();

    if (myOpSign == AND || myOpSign == OR) {
      return handleAndOrBinop(runner, stateBefore, dfaRight, dfaLeft);
    }
    return handleRelationBinop(runner, stateBefore, dfaRight, dfaLeft);
  }

  private static RelationType @NotNull [] splitRelation(RelationType relationType) {
    switch (relationType) {
      case LT:
      case LE:
      case GT:
      case GE:
        return new RelationType[]{RelationType.LT, RelationType.GT, RelationType.EQ};
      default:
        return new RelationType[]{relationType, relationType.getNegated()};
    }
  }

  private DfaInstructionState @NotNull [] handleRelationBinop(@NotNull DataFlowRunner runner,
                                                              @NotNull DfaMemoryState memState,
                                                              @NotNull DfaValue dfaRight,
                                                              @NotNull DfaValue dfaLeft) {
    if((myOpSign == EQEQ || myOpSign == NE) && memState.shouldCompareByEquals(dfaLeft, dfaRight)) {
      ArrayList<DfaInstructionState> states = new ArrayList<>(2);
      DfaMemoryState equality = memState.createCopy();
      DfaCondition condition = dfaLeft.eq(dfaRight);
      if (equality.applyCondition(condition)) {
        pushResult(runner, equality, BOOLEAN);
        states.add(nextState(runner, equality));
      }
      if (memState.applyCondition(condition.negate())) {
        pushResult(runner, memState, booleanValue(myOpSign == NE));
        states.add(nextState(runner, memState));
      }
      return states.toArray(DfaInstructionState.EMPTY_ARRAY);
    }
    RelationType relationType = myOpSign == STRING_EQUALITY_BY_CONTENT ? RelationType.EQ :
                                DfaPsiUtil.getRelationByToken(myOpSign);
    if (relationType == null) {
      pushResult(runner, memState, BOOLEAN);
      return nextStates(runner, memState);
    }
    RelationType[] relations = splitRelation(relationType);

    ArrayList<DfaInstructionState> states = new ArrayList<>(relations.length);

    for (int i = 0; i < relations.length; i++) {
      RelationType relation = relations[i];
      DfaCondition condition = dfaLeft.cond(relation, dfaRight);
      if (condition == DfaCondition.getFalse()) continue;
      if (condition == DfaCondition.getTrue()) {
        pushResult(runner, memState, booleanValue(relationType.isSubRelation(relation)));
        return nextStates(runner, memState);
      }
      final DfaMemoryState copy = i == relations.length - 1 && !states.isEmpty() ? memState : memState.createCopy();
      if (copy.applyCondition(condition)) {
        pushResult(runner, copy, booleanValue(relationType.isSubRelation(relation)));
        states.add(nextState(runner, copy));
      }
    }
    if (states.isEmpty()) {
      // Neither of relations could be applied: likely comparison with NaN; do not split the state in this case, just push false
      pushResult(runner, memState, FALSE);
      return nextStates(runner, memState);
    }

    return states.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  private DfaInstructionState @NotNull [] handleAndOrBinop(@NotNull DataFlowRunner runner,
                                                           @NotNull DfaMemoryState memState,
                                                           @NotNull DfaValue dfaRight, 
                                                           @NotNull DfaValue dfaLeft) {
    List<DfaInstructionState> result = new ArrayList<>(2);
    boolean or = myOpSign == OR;
    DfaMemoryState copy = memState.createCopy();
    DfaCondition cond = dfaRight.eq(booleanValue(or));
    if (copy.applyCondition(cond)) {
      pushResult(runner, copy, booleanValue(or));
      result.add(nextState(runner, copy));
    }
    if (memState.applyCondition(cond.negate())) {
      pushResult(runner, memState, dfaLeft);
      result.add(nextState(runner, memState));
    }
    return result.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  public String toString() {
    return "BOOLEAN_OP " + myOpSign;
  }
}
