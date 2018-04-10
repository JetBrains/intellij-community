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

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

/**
 * This instruction pops a top-of-stack value dereferencing it (so nullability warning might be issued if top-of-stack is nullable value).
 */
public class DereferenceInstruction extends Instruction {
  private final @NotNull PsiExpression myExpression;

  public DereferenceInstruction(@NotNull PsiExpression expression) {
    myExpression = expression;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitFieldReference(this, runner, stateBefore);
  }

  public String toString() {
    return "DEREFERENCE: " + myExpression.getText();
  }

  @NotNull
  public PsiExpression getExpression() {
    return myExpression;
  }
}
