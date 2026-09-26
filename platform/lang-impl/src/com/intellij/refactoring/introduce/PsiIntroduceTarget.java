// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduce;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiIntroduceTarget<T extends PsiElement> implements IntroduceTarget {
  protected final @NotNull SmartPsiElementPointer<T> myPointer;

  public PsiIntroduceTarget(@NotNull T psi) {
    myPointer = SmartPointerManager.getInstance(psi.getProject()).createSmartPsiElementPointer(psi);
  }

  @Override
  public @NotNull TextRange getTextRange() {
    return getPlace().getTextRange();
  }

  @Override
  public @Nullable T getPlace() {
    return myPointer.getElement();
  }

  @Override
  public @NotNull String render() {
    return getPlace().getText();
  }

  @Override
  public boolean isValid() {
    return myPointer.getElement() != null;
  }
}
