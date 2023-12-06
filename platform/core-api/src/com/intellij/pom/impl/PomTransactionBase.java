// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.impl;

import com.intellij.pom.PomManager;
import com.intellij.pom.PomTransaction;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public abstract class PomTransactionBase implements PomTransaction{
  private final PsiElement myScope;
  private final PomModelEvent myAccumulatedEvent;
  public PomTransactionBase(@NotNull PsiElement scope) {
    myScope = scope;
    myAccumulatedEvent = new PomModelEvent(PomManager.getModel(scope.getProject()), null);
  }

  @Override
  public @NotNull PomModelEvent getAccumulatedEvent() {
    return myAccumulatedEvent;
  }

  @Override
  public void run() throws IncorrectOperationException {
    myAccumulatedEvent.merge(runInner());
  }

  public abstract @NotNull PomModelEvent runInner() throws IncorrectOperationException;

  @Override
  public @NotNull PsiElement getChangeScope() {
    return myScope;
  }

}
