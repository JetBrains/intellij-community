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

import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

/**
 * An instruction that pushes given value to the stack
 */
public class PushInstruction extends EvalInstruction {
  private final @NotNull DfaValue myValue;
  private final boolean myReferenceWrite;

  public PushInstruction(@NotNull DfaValue value, PsiExpression place) {
    this(value, place, false);
  }

  public PushInstruction(@NotNull DfaValue value, PsiExpression place, final boolean isReferenceWrite) {
    super(place, 0);
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
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory, @NotNull DfaMemoryState state, @NotNull DfaValue @NotNull ... arguments) {
    return myValue;
  }

  public String toString() {
    return "PUSH " + myValue;
  }
}
