// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.psi.impl;

import com.intellij.json.psi.JsonElementGenerator;
import com.intellij.json.psi.JsonProperty;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class JsonPropertyMixin extends JsonElementImpl implements JsonProperty {
  JsonPropertyMixin(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    final JsonElementGenerator generator = new JsonElementGenerator(getProject());
    // Strip only both quotes in case user wants some exotic name like key'
    getNameElement().replace(generator.createStringLiteral(StringUtil.unquoteString(name)));
    return this;
  }

  @Override
  public PsiReference getReference() {
    return new JsonPropertyNameReference(this);
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    final PsiReference[] fromProviders = ReferenceProvidersRegistry.getReferencesFromProviders(this);
    return ArrayUtil.prepend(new JsonPropertyNameReference(this), fromProviders);
  }
}
