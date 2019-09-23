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

package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.JavaTokenType.*;

public class BinopInstruction extends BranchingInstruction implements ExpressionPushingInstruction {
  private static final TokenSet ourSignificantOperations =
    TokenSet.create(EQ, EQEQ, NE, LT, GT, LE, GE, INSTANCEOF_KEYWORD, PLUS, MINUS, AND, OR, XOR, PERC, DIV, ASTERISK, GTGT, GTGTGT, LTLT);

  /**
   * A placeholder operation to model string concatenation inside loop:
   * currently we cannot properly widen states and precise handling of concatenation
   * inside loop (including length tracking) may lead to state divergence,
   * so we use special operation for this case.
   */
  public static final IElementType STRING_CONCAT_IN_LOOP = ASTERISK;
  /**
   * A special operation to express string comparison by content (like equals() method does).
   * Used to desugar switch statements
   */
  public static final IElementType STRING_EQUALITY_BY_CONTENT = EQ;

  private final IElementType myOperationSign;
  private final @Nullable PsiType myResultType;
  private final int myLastOperand;
  private final boolean myUnrolledLoop;
  private boolean myWidened;

  public BinopInstruction(IElementType opSign, @Nullable PsiExpression psiAnchor, @Nullable PsiType resultType) {
    this(opSign, psiAnchor, resultType, -1);
  }

  public BinopInstruction(IElementType opSign, @Nullable PsiExpression psiAnchor, @Nullable PsiType resultType, int lastOperand) {
    this(opSign, psiAnchor, resultType, lastOperand, false);
  }

  /**
   * @param opSign sign of the operation
   * @param psiAnchor PSI element to bind the instruction to
   * @param resultType result of the operation
   * @param lastOperand number of last operand if anchor is a {@link PsiPolyadicExpression} and this instruction is the result of
   *                    part of that expression; -1 if not applicable
   * @param unrolledLoop true means that this instruction is executed inside an unrolled loop; in this case it will never be widened
   */
  public BinopInstruction(IElementType opSign,
                          @Nullable PsiExpression psiAnchor,
                          @Nullable PsiType resultType,
                          int lastOperand,
                          boolean unrolledLoop) {
    super(psiAnchor);
    myResultType = resultType;
    myOperationSign = ourSignificantOperations.contains(opSign) ? opSign : null;
    myLastOperand = lastOperand;
    myUnrolledLoop = unrolledLoop;
  }

  /**
   * Make operation wide (less precise) if necessary (called for the operations inside loops only)
   */
  public void widenOperationInLoop() {
    // these operations usually produce non-converging states
    if (!myUnrolledLoop && !myWidened && (myOperationSign == PLUS || myOperationSign == MINUS || myOperationSign == ASTERISK) &&
        mayProduceDivergedState()) {
      myWidened = true;
    }
  }

  private boolean mayProduceDivergedState() {
    PsiElement anchor = getExpression();
    if (anchor instanceof PsiUnaryExpression) {
      return PsiUtil.isIncrementDecrementOperation(anchor);
    }
    while (anchor != null && !(anchor instanceof PsiAssignmentExpression) && !(anchor instanceof PsiVariable)) {
      if (anchor instanceof PsiStatement ||
          anchor instanceof PsiExpressionList && anchor.getParent() instanceof PsiCallExpression ||
          anchor instanceof PsiArrayInitializerExpression || anchor instanceof PsiArrayAccessExpression ||
          anchor instanceof PsiBinaryExpression &&
          DfaRelationValue.RelationType.fromElementType(((PsiBinaryExpression)anchor).getOperationTokenType()) != null) {
        return false;
      }
      anchor = anchor.getParent();
    }
    return true;
  }

  /**
   * @return range inside the anchor which evaluates this instruction, or null if the whole anchor evaluates this instruction
   */
  @Override
  @Nullable
  public TextRange getExpressionRange() {
    if (myLastOperand != -1 && getPsiAnchor() instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression anchor = (PsiPolyadicExpression)getPsiAnchor();
      PsiExpression[] operands = anchor.getOperands();
      if (operands.length > myLastOperand + 1) {
        return new TextRange(0, operands[myLastOperand].getStartOffsetInParent()+operands[myLastOperand].getTextLength());
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PsiExpression getExpression() {
    return (PsiExpression)getPsiAnchor();
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitBinop(this, runner, stateBefore);
  }

  @Nullable
  public PsiType getResultType() {
    return myResultType;
  }

  public String toString() {
    return "BINOP " + myOperationSign;
  }

  public IElementType getOperationSign() {
    return myOperationSign;
  }

  /**
   * @return true if the operation must be executed with widening, otherwise it may produce a diverged state.
   */
  public boolean isWidened() {
    return myWidened;
  }
}
