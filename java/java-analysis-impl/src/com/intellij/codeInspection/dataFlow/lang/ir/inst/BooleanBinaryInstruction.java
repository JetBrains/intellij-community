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

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.JavaTokenType.*;

public class BooleanBinaryInstruction extends ExpressionPushingInstruction<PsiExpression> implements BranchingInstruction {
  // AND and OR for boolean arguments only
  private static final TokenSet ourSignificantOperations = TokenSet.create(EQ, EQEQ, NE, LT, GT, LE, GE, INSTANCEOF_KEYWORD, AND, OR);

  /**
   * A special operation to express string comparison by content (like equals() method does).
   * Used to desugar switch statements
   */
  public static final IElementType STRING_EQUALITY_BY_CONTENT = EQ;

  private final IElementType myOperationSign;
  private final int myLastOperand;

  public BooleanBinaryInstruction(IElementType opSign, @Nullable PsiExpression expression) {
    this(opSign, expression, -1);
  }

  /**
   * @param opSign sign of the operation
   * @param expression PSI element to bind the instruction to
   * @param lastOperand number of last operand if anchor is a {@link PsiPolyadicExpression} and this instruction is the result of
   *                    part of that expression; -1 if not applicable
   */
  public BooleanBinaryInstruction(IElementType opSign, @Nullable PsiExpression expression, int lastOperand) {
    super(expression);
    assert lastOperand == -1 || expression instanceof PsiPolyadicExpression;
    myOperationSign = opSign == XOR ? NE : ourSignificantOperations.contains(opSign) ? opSign : null;
    myLastOperand = lastOperand;
  }

  /**
   * @return range inside the anchor which evaluates this instruction, or null if the whole anchor evaluates this instruction
   */
  @Override
  @Nullable
  public TextRange getExpressionRange() {
    return DfaPsiUtil.getRange(getExpression(), myLastOperand);
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitBinop(this, runner, stateBefore);
  }

  public String toString() {
    return "BOOLEAN_OP " + myOperationSign;
  }

  public IElementType getOperationSign() {
    return myOperationSign;
  }
}
