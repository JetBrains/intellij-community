// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.controlflow.impl;

import com.intellij.codeInsight.controlflow.ConditionalInstruction;
import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConditionalInstructionImpl extends InstructionImpl implements ConditionalInstruction {
  private final @Nullable PsiElement myCondition;
  private final boolean myResult;

  public ConditionalInstructionImpl(final @NotNull ControlFlowBuilder builder,
                                    final @Nullable PsiElement element,
                                    final @Nullable PsiElement condition,
                                    final boolean result) {
    super(builder, element);
    myCondition = condition;
    myResult = result;
  }


  @Override
  public @Nullable PsiElement getCondition() {
    return myCondition;
  }

  @Override
  public boolean getResult() {
    return myResult;
  }

  @Override
  public @NotNull String toString() {
    return super.toString() + ". Condition: " + (myCondition != null ? myCondition.getText() : null) + ":" + myResult;
  }
}
