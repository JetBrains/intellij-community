// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JsonLikeSyntaxAdapter {
  @Nullable PsiElement getPropertyValue(PsiElement property);
  @NotNull default PsiElement adjustValue(@NotNull PsiElement value) { return value; }
  @Nullable String getPropertyName(PsiElement property);
  @NotNull PsiElement createProperty(@NotNull final String name, @NotNull final String value);
  boolean ensureComma(PsiElement backward, PsiElement self, PsiElement newElement);
  void removeIfComma(PsiElement forward);
  boolean fixWhitespaceBefore(PsiElement initialElement, PsiElement element);
}
