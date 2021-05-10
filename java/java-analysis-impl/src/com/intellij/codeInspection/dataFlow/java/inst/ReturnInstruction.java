// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.jvm.ExceptionTransfer;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
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
