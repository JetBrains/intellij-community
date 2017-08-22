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

package com.intellij.codeInspection.dataFlow.instructions;


import com.intellij.codeInspection.dataFlow.*;
import com.intellij.psi.PsiElement;

public class ConditionalGotoInstruction extends BranchingInstruction implements JumpInstruction {
  private ControlFlow.ControlFlowOffset myOffset;
  private final boolean myIsNegated;

  public ConditionalGotoInstruction(ControlFlow.ControlFlowOffset myOffset, boolean isNegated, PsiElement psiAnchor) {
    super(psiAnchor);
    this.myOffset = myOffset;
    myIsNegated = isNegated;
  }

  public boolean isNegated() {
    return myIsNegated;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitConditionalGoto(this, runner, stateBefore);
  }

  public String toString() {
    return (isNegated() ? "!":"") + "cond?_goto " + getOffset();
  }

  @Override
  public int getOffset() {
    return myOffset.getInstructionOffset();
  }

  @Override
  public void setOffset(final int offset) {
    myOffset = new ControlFlow.ControlFlowOffset() {
      @Override
      public int getInstructionOffset() {
        return offset;
      }
    };
  }
}
