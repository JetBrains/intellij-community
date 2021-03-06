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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.JavaTokenType.*;

public class BinopInstruction extends ExpressionPushingInstruction<PsiExpression> implements BranchingInstruction {
  private static final TokenSet ourSignificantOperations =
    TokenSet.create(EQ, EQEQ, NE, LT, GT, LE, GE, INSTANCEOF_KEYWORD, PLUS, MINUS, AND, OR, XOR, PERC, DIV, ASTERISK, GTGT, GTGTGT, LTLT);

  /**
   * A special operation to express string comparison by content (like equals() method does).
   * Used to desugar switch statements
   */
  public static final IElementType STRING_EQUALITY_BY_CONTENT = EQ;

  private final IElementType myOperationSign;
  private final @Nullable PsiType myResultType;
  private final int myLastOperand;

  public BinopInstruction(IElementType opSign, @Nullable PsiExpression expression, @Nullable PsiType resultType) {
    this(opSign, expression, resultType, -1);
  }

  /**
   * @param opSign sign of the operation
   * @param expression PSI element to bind the instruction to
   * @param resultType result of the operation
   * @param lastOperand number of last operand if anchor is a {@link PsiPolyadicExpression} and this instruction is the result of
   *                    part of that expression; -1 if not applicable
   * @param unrolledLoop true means that this instruction is executed inside an unrolled loop; in this case it will never be widened
   */
  public BinopInstruction(IElementType opSign,
                          @Nullable PsiExpression expression,
                          @Nullable PsiType resultType,
                          int lastOperand) {
    super(expression);
    assert lastOperand == -1 || expression instanceof PsiPolyadicExpression;
    myResultType = resultType;
    myOperationSign =
      opSign == XOR && PsiType.BOOLEAN.equals(resultType) ? NE : // XOR for boolean is equivalent to NE 
      ourSignificantOperations.contains(opSign) ? opSign : null;
    myLastOperand = lastOperand;
  }

  /**
   * @return range inside the anchor which evaluates this instruction, or null if the whole anchor evaluates this instruction
   */
  @Override
  @Nullable
  public TextRange getExpressionRange() {
    if (myLastOperand != -1 && getExpression() instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression anchor = (PsiPolyadicExpression)getExpression();
      PsiExpression[] operands = anchor.getOperands();
      if (operands.length > myLastOperand + 1) {
        return new TextRange(0, operands[myLastOperand].getStartOffsetInParent()+operands[myLastOperand].getTextLength());
      }
    }
    return null;
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
}
