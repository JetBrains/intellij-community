// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.listeners;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;


public abstract class RefactoringElementAdapter implements RefactoringElementListener, UndoRefactoringElementListener {
  @Override
  public final void elementMoved(@NotNull PsiElement newElement) {
    elementRenamedOrMoved(newElement);
  }

  protected abstract void elementRenamedOrMoved(@NotNull PsiElement newElement);

  @Override
  public final void elementRenamed(@NotNull PsiElement newElement) {
    elementRenamedOrMoved(newElement);
  }
}
