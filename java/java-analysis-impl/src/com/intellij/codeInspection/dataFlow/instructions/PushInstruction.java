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
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PushInstruction extends Instruction {
  private final DfaValue myValue;
  private final PsiExpression myPlace;
  private final boolean myReferenceWrite;

  public PushInstruction(@Nullable DfaValue value, PsiExpression place) {
    this(value, place, false);
  }

  public PushInstruction(@Nullable DfaValue value, PsiExpression place, final boolean isReferenceWrite) {
    myValue = value != null ? value : DfaUnknownValue.getInstance();
    myPlace = place;
    myReferenceWrite = isReferenceWrite;
  }

  public boolean isReferenceWrite() {
    return myReferenceWrite;
  }

  @NotNull
  public DfaValue getValue() {
    return myValue;
  }

  public PsiExpression getPlace() {
    return myPlace;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitPush(this, runner, stateBefore);
  }

  public String toString() {
    return "PUSH " + myValue;
  }
}
