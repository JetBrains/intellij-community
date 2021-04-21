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

package com.intellij.codeInspection.dataFlow.lang.ir.inst;

import com.intellij.codeInspection.dataFlow.jvm.ExceptionTransfer;
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReturnInstruction extends ControlTransferInstruction {
  private final PsiElement myAnchor;

  public ReturnInstruction(@NotNull DfaControlTransferValue transfer, @Nullable PsiElement anchor) {
    this(transfer, anchor, true);
  }

  private ReturnInstruction(@NotNull DfaControlTransferValue transfer, @Nullable PsiElement anchor, boolean linkTraps) {
    super(transfer, linkTraps);
    myAnchor = anchor;
  }

  @NotNull
  @Override
  public Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    var instruction = new ReturnInstruction(getTransfer().bindToFactory(factory), myAnchor, false);
    instruction.setIndex(getIndex());
    return instruction;
  }

  @Nullable
  public PsiElement getAnchor() {
    return myAnchor;
  }

  public boolean isViaException() {
    DfaControlTransferValue transfer = getTransfer();
    return transfer.getTarget() instanceof ExceptionTransfer;
  }

}
