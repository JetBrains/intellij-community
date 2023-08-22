// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom;

import com.intellij.pom.event.PomModelEvent;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public interface PomTransaction {
  @NotNull
  PomModelEvent getAccumulatedEvent();

  void run() throws IncorrectOperationException;

  @NotNull
  PsiElement getChangeScope();

}
