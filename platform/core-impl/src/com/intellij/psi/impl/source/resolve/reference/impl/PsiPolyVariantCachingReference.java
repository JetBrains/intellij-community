/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class PsiPolyVariantCachingReference implements PsiPolyVariantReference {
  @Override
  @NotNull
  public final ResolveResult[] multiResolve(boolean incompleteCode) {
    PsiElement element = getElement();
    PsiFile file = element.getContainingFile();
    return ResolveCache.getInstance(file.getProject()).resolveWithCaching(this, MyResolver.INSTANCE, true, incompleteCode,file);
  }

  @Override
  public PsiElement resolve() {
    ResolveResult[] results = multiResolve(false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  @NotNull
  protected abstract ResolveResult[] resolveInner(boolean incompleteCode, @NotNull PsiFile containingFile);

  @Override
  public boolean isReferenceTo(final PsiElement element) {
    return getElement().getManager().areElementsEquivalent(resolve(), element);
  }

  @Override
  public boolean isSoft(){
    return false;
  }

  @Nullable
  public static <T extends PsiElement> ElementManipulator<T> getManipulator(T currentElement){
    return ElementManipulators.getManipulator(currentElement);
  }

  private static class MyResolver implements ResolveCache.PolyVariantContextResolver<PsiPolyVariantReference> {
    private static final MyResolver INSTANCE = new MyResolver();

    @NotNull
    @Override
    public ResolveResult[] resolve(@NotNull PsiPolyVariantReference ref, @NotNull PsiFile containingFile, boolean incompleteCode) {
      return ((PsiPolyVariantCachingReference)ref).resolveInner(incompleteCode, containingFile);
    }
  }
}
