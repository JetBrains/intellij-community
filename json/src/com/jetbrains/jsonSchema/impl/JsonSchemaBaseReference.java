/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.impl;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Irina.Chernushina on 3/31/2016.
 */
public abstract class JsonSchemaBaseReference<T extends PsiElement> extends PsiReferenceBase<T> {
  public JsonSchemaBaseReference(T element, TextRange textRange) {
    super(element, textRange, true);
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    return ResolveCache.getInstance(getElement().getProject()).resolveWithCaching(this, MyResolver.INSTANCE, false, false);
  }

  @Nullable
  public abstract PsiElement resolveInner();


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    JsonSchemaBaseReference that = (JsonSchemaBaseReference)o;

    if (!myElement.equals(that.myElement)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myElement.hashCode();
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private static class MyResolver implements ResolveCache.Resolver {
    private static final MyResolver INSTANCE = new MyResolver();

    @Nullable
    public PsiElement resolve(@NotNull PsiReference ref, boolean incompleteCode) {
      return ((JsonSchemaBaseReference)ref).resolveInner();
    }
  }
}
