// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.controlflow.impl;

import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.codeInsight.controlflow.TransparentInstruction;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TransparentInstructionImpl extends InstructionBaseImpl implements TransparentInstruction {

  private final @NotNull String myMarkerName;
  private final int myNum;

  public TransparentInstructionImpl(final @NotNull ControlFlowBuilder builder,
                                    final @Nullable PsiElement element,
                                    @NotNull String markerName) {
    super(element);
    myMarkerName = markerName;
    myNum = builder.transparentInstructionCount++;
  }

  @Override
  public @NotNull String getElementPresentation() {
    return super.getElementPresentation() + "(" + myMarkerName + ")";
  }

  @Override
  protected @NotNull String id() {
    return "t" + num();
  }

  @Override
  public int num() {
    return myNum;
  }
}
