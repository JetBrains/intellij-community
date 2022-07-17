// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * An instruction to exit the interpretation (but possibly process traps like 'finally' blocks).
 */
public class ReturnInstruction extends ControlTransferInstruction {
  private final PsiElement myAnchor;

  public ReturnInstruction(@NotNull DfaValueFactory factory, @NotNull FList<DfaControlTransferValue.Trap> traps, @Nullable PsiElement anchor) {
    this(factory.controlTransfer(DfaControlTransferValue.RETURN_TRANSFER, traps), anchor, true);
  }

  private ReturnInstruction(@NotNull DfaControlTransferValue transfer, @Nullable PsiElement anchor, boolean linkTraps) {
    super(transfer, linkTraps);
    myAnchor = anchor;
  }

  @NotNull
  @Override
  public Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    return new ReturnInstruction(getTransfer().bindToFactory(factory), myAnchor, false);
  }

  @Nullable
  public PsiElement getAnchor() {
    return myAnchor;
  }

  @Override
  public String toString() {
    int[] indexes = getSuccessorIndexes();
    return "RETURN" + (indexes.length == 0 ? "" : " [traps: " + Arrays.toString(indexes) + "]");
  }
}
