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
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

public class PushInstruction extends ExpressionPushingInstruction<PsiExpression> {
  private final @NotNull DfaValue myValue;
  private final boolean myReferenceWrite;

  public PushInstruction(@NotNull DfaValue value, PsiExpression place) {
    this(value, place, false);
  }

  public PushInstruction(@NotNull DfaValue value, PsiExpression place, final boolean isReferenceWrite) {
    super(place);
    myValue = value;
    myReferenceWrite = isReferenceWrite;
  }

  public boolean isReferenceWrite() {
    return myReferenceWrite;
  }

  @NotNull
  public DfaValue getValue() {
    return myValue;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitPush(this, runner, stateBefore, myValue);
  }

  public String toString() {
    return "PUSH " + myValue;
  }
}
