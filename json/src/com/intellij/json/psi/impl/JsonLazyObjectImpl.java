// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.psi.impl;

import com.intellij.extapi.psi.ASTDelegatePsiElement;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.JsonElementVisitor;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JsonLazyObjectImpl extends LazyParseablePsiElement implements JsonObject {
  public JsonLazyObjectImpl(@Nullable CharSequence buffer) {
    super(JsonElementTypes.OBJECT, buffer);
  }

  public void accept(@NotNull JsonElementVisitor visitor) {
    visitor.visitObject(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonElementVisitor) {
      accept((JsonElementVisitor)visitor);
    }
    else {
      super.accept(visitor);
    }
  }

  @Override
  public @Nullable ItemPresentation getPresentation() {
    return JsonPsiImplUtils.getPresentation(this);
  }

  @Override
  public String toString() {
    return "JsonObject";
  }

  @Override
  public @NotNull List<JsonProperty> getPropertyList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, JsonProperty.class);
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return ASTDelegatePsiElement.getChildrenSkippingLeaves(this);
  }

  @Override
  public @Nullable JsonProperty findProperty(@NotNull String name) {
    return JsonObjectMixin.findProperty(this, name);
  }
}