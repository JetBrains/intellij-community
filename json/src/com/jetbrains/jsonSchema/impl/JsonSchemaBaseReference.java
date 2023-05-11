// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class JsonSchemaBaseReference<T extends PsiElement> extends PsiReferenceBase<T> {
  public JsonSchemaBaseReference(T element, TextRange textRange) {
    super(element, textRange, true);
  }

  @Override
  public @Nullable PsiElement resolve() {
    return ResolveCache.getInstance(getElement().getProject()).resolveWithCaching(this, MyResolver.INSTANCE, false, false);
  }

  public abstract @Nullable PsiElement resolveInner();


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    JsonSchemaBaseReference that = (JsonSchemaBaseReference)o;

    return isIdenticalTo(that);
  }

  protected boolean isIdenticalTo(JsonSchemaBaseReference that) {
    return myElement.equals(that.myElement);
  }

  @Override
  public int hashCode() {
    return myElement.hashCode();
  }

  private static final class MyResolver implements ResolveCache.Resolver {
    private static final MyResolver INSTANCE = new MyResolver();

    @Override
    public @Nullable PsiElement resolve(@NotNull PsiReference ref, boolean incompleteCode) {
      return ((JsonSchemaBaseReference<?>)ref).resolveInner();
    }
  }
}
