// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.psi.impl;

import com.intellij.extapi.psi.ASTDelegatePsiElement;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonElementVisitor;
import com.intellij.json.psi.JsonValue;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JsonLazyArrayImpl extends LazyParseablePsiElement implements JsonArray {
  public JsonLazyArrayImpl(@Nullable CharSequence buffer) {
    super(JsonElementTypes.ARRAY, buffer);
  }

  public void accept(@NotNull JsonElementVisitor visitor) {
    visitor.visitArray(this);
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
    return "JsonArray";
  }

  @Override
  public @NotNull List<JsonValue> getValueList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, JsonValue.class);
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return ASTDelegatePsiElement.getChildrenSkippingLeaves(this);
  }
}
