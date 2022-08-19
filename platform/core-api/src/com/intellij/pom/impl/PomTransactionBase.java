// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public PomModelEvent getAccumulatedEvent() {
    return myAccumulatedEvent;
  }

  @Override
  public void run() throws IncorrectOperationException {
    myAccumulatedEvent.merge(runInner());
  }

  @NotNull
  public abstract PomModelEvent runInner() throws IncorrectOperationException;

  @NotNull
  @Override
  public PsiElement getChangeScope() {
    return myScope;
  }

}
