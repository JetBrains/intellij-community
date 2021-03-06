// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.controlflow.impl;

import com.intellij.codeInsight.controlflow.ConditionalInstruction;
import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConditionalInstructionImpl extends InstructionImpl implements ConditionalInstruction {
  @Nullable
  private final PsiElement myCondition;
  private final boolean myResult;

  public ConditionalInstructionImpl(@NotNull final ControlFlowBuilder builder,
                                    @Nullable final PsiElement element,
                                    @Nullable final PsiElement condition,
                                    final boolean result) {
    super(builder, element);
    myCondition = condition;
    myResult = result;
  }


  @Nullable
  @Override
  public PsiElement getCondition() {
    return myCondition;
  }

  @Override
  public boolean getResult() {
    return myResult;
  }

  @NotNull
  @Override
  public String toString() {
    return super.toString() + ". Condition: " + (myCondition != null ? myCondition.getText() : null) + ":" + myResult;
  }
}
