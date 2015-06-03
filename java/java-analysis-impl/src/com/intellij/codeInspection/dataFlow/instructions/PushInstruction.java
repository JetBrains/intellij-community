/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 7, 2002
 * Time: 1:25:41 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PushInstruction extends Instruction {
  private final DfaValue myValue;
  private final PsiElement myPlace;
  private final boolean myReferenceWrite;

  public PushInstruction(@Nullable DfaValue value, PsiElement place) {
    this(value, place, false);
  }

  public PushInstruction(@Nullable DfaValue value, PsiElement place, final boolean isReferenceWrite) {
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

  public PsiElement getPlace() {
    return myPlace;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DfaMemoryState stateBefore, @NotNull InstructionVisitor visitor) {
    return visitor.visitPush(this, stateBefore);
  }

  @Override
  public String toString() {
    return "PUSH " + myValue;
  }
}
