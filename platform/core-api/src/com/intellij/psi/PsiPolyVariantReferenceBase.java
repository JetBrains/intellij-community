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

package com.intellij.psi;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class PsiPolyVariantReferenceBase<T extends PsiElement> extends PsiReferenceBase<T> implements PsiPolyVariantReference {

  public PsiPolyVariantReferenceBase(@NotNull final T psiElement) {
    super(psiElement);
  }

  public PsiPolyVariantReferenceBase(@NotNull T element, TextRange range) {
    super(element, range);
  }

  public PsiPolyVariantReferenceBase(@NotNull final T psiElement, final boolean soft) {
    super(psiElement, soft);
  }

  public PsiPolyVariantReferenceBase(@NotNull final T element, final TextRange range, final boolean soft) {
    super(element, range, soft);
  }

  @Override
  @Nullable
  public PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    final ResolveResult[] results = multiResolve(false);
    for (ResolveResult result : results) {
      if (getElement().getManager().areElementsEquivalent(result.getElement(), element)) {
        return true;
      }
    }
    return false;
  }
}
