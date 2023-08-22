// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.jvm.transfer.ExceptionTransfer;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlTransferInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Throw Java exception
 */
public class ThrowInstruction extends ControlTransferInstruction {
  private final @Nullable PsiElement myAnchor;

  public ThrowInstruction(@NotNull DfaControlTransferValue transfer, @Nullable PsiElement anchor) {
    this(transfer, anchor, true);
  }

  private ThrowInstruction(@NotNull DfaControlTransferValue transfer, @Nullable PsiElement anchor, boolean linkTraps) {
    super(transfer, linkTraps);
    assert transfer.getTarget() instanceof ExceptionTransfer;
    myAnchor = anchor;
  }

  @NotNull
  @Override
  public Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    return new ThrowInstruction(getTransfer().bindToFactory(factory), myAnchor, false);
  }

  @Nullable
  public PsiElement getAnchor() {
    return myAnchor;
  }

  @Override
  public String toString() {
    int[] indexes = getSuccessorIndexes();
    return "THROW " +
           ((ExceptionTransfer)getTransfer().getTarget()).getThrowable() + 
           (indexes.length == 0 ? "" : " [targets: " + Arrays.toString(indexes) + "]");
  }
}
