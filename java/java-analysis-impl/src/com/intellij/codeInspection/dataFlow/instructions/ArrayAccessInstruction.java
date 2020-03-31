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
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.PsiArrayAccessExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ArrayAccessInstruction extends ExpressionPushingInstruction<PsiArrayAccessExpression> {
  private final @NotNull DfaValue myValue;

  public ArrayAccessInstruction(@NotNull DfaValue value, @NotNull PsiArrayAccessExpression expression) {
    super(expression);
    myValue = value;
  }

  @NotNull
  public DfaValue getValue() {
    return myValue;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitArrayAccess(this, runner, stateBefore);
  }

  @Override
  public String toString() {
    return "ARRAY_ACCESS " + Objects.requireNonNull(getExpression()).getText();
  }
}
