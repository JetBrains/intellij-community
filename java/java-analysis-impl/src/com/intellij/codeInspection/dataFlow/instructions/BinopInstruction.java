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

public class BinopInstruction extends BranchingInstruction implements ExpressionPushingInstruction {
  private static final TokenSet ourSignificantOperations =
    TokenSet.create(EQEQ, NE, LT, GT, LE, GE, INSTANCEOF_KEYWORD, PLUS, MINUS, AND, PERC, DIV, GTGT, GTGTGT);
  private final IElementType myOperationSign;
  private final @Nullable PsiType myResultType;
  private final int myLastOperand;

  public BinopInstruction(IElementType opSign, @Nullable PsiExpression psiAnchor, @Nullable PsiType resultType) {
    this(opSign, psiAnchor, resultType, -1);
  }

  public BinopInstruction(IElementType opSign, @Nullable PsiExpression psiAnchor, @Nullable PsiType resultType, int lastOperand) {
    super(psiAnchor);
    myResultType = resultType;
    myOperationSign = ourSignificantOperations.contains(opSign) ? opSign : null;
    myLastOperand = lastOperand;
  }

  /**
   * @return range inside the anchor which evaluates this instruction, or null if the whole anchor evaluates this instruction
   */
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
}
