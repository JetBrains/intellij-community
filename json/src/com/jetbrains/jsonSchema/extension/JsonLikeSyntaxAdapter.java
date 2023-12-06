// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JsonLikeSyntaxAdapter {
  @Nullable PsiElement getPropertyValue(PsiElement property);
  default @NotNull PsiElement adjustValue(@NotNull PsiElement value) { return value; }
  @Nullable String getPropertyName(PsiElement property);
  @NotNull PsiElement createProperty(final @NotNull String name, final @NotNull String value, PsiElement element);
  boolean ensureComma(PsiElement self, PsiElement newElement);
  void removeIfComma(PsiElement forward);
  boolean fixWhitespaceBefore(PsiElement initialElement, PsiElement element);
  @NotNull String getDefaultValueFromType(@Nullable JsonSchemaType type);
  PsiElement adjustNewProperty(PsiElement element);
  PsiElement adjustPropertyAnchor(LeafPsiElement element);
}
