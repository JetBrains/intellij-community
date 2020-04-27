// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.controlflow.impl;

import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InstructionImpl extends InstructionBaseImpl {

  private final int myNumber;

  public InstructionImpl(@NotNull final ControlFlowBuilder builder, @Nullable final PsiElement element) {
    super(element);
    myNumber = builder.instructionCount++;
  }

  @Override
  public final int num() {
    return myNumber;
  }
}