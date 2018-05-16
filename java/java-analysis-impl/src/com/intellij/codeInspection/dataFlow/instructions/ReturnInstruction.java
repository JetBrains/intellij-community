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

import com.intellij.codeInspection.dataFlow.ControlTransferInstruction;
import com.intellij.codeInspection.dataFlow.DfaControlTransferValue;
import com.intellij.codeInspection.dataFlow.ExceptionTransfer;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReturnInstruction extends ControlTransferInstruction {
  private final PsiElement myAnchor;

  public ReturnInstruction(@NotNull DfaControlTransferValue transfer, @Nullable PsiElement anchor) {
    super(transfer);
    myAnchor = anchor;
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
