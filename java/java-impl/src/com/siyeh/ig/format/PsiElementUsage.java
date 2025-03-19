// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.format;

import com.intellij.find.usages.api.PsiUsage;
import com.intellij.model.Pointer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * A class, which represents a PsiUsage, based on concrete PsiElement.
 * @see PsiUsage
 */
public class PsiElementUsage implements PsiUsage {
  private final @NotNull PsiElement myArg;

  public PsiElementUsage(@NotNull PsiElement arg) {
    myArg = arg;
  }

  @Override
  public @NotNull Pointer<? extends PsiUsage> createPointer() {
    return Pointer.hardPointer(this);
  }

  @Override
  public @NotNull PsiFile getFile() {
    return myArg.getContainingFile();
  }

  @Override
  public @NotNull TextRange getRange() {
    return myArg.getTextRange();
  }

  @Override
  public boolean getDeclaration() {
    return true;
  }
}
