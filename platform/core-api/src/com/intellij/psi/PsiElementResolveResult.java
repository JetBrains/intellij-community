/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Trivial implementation of {@link ResolveResult}.
 */
public class PsiElementResolveResult implements ResolveResult{
  @NotNull private final PsiElement myElement;
  private final boolean myValidResult;

  /**
   * Creates a resolve result with the specified resolve target.
   *
   * @param element the resolve target element.
   */
  public PsiElementResolveResult(@NotNull PsiElement element) {
    this(element, true);
  }

  public PsiElementResolveResult(@NotNull final PsiElement element, final boolean validResult) {
    myElement = element;
    myValidResult = validResult;
  }

  @Override
  @NotNull public PsiElement getElement() {
    return myElement;
  }

  @Override
  public boolean isValidResult() {
    return myValidResult;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final PsiElementResolveResult that = (PsiElementResolveResult)o;

    if (!myElement.equals(that.myElement)) return false;

    return true;
  }

  public int hashCode() {
    return myElement.hashCode();
  }

  @NonNls
  public String toString() {
    return "PsiElementResolveResult with " + myElement.getClass() + ": " +
           (myElement instanceof PsiNamedElement ? ((PsiNamedElement)myElement).getName() : myElement.getText());
  }

  @NotNull
  public static ResolveResult[] createResults(@Nullable Collection<? extends PsiElement> elements) {
    if (elements == null || elements.isEmpty()) return EMPTY_ARRAY;

    final ResolveResult[] results = new ResolveResult[elements.size()];
    int i = 0;
    for (PsiElement element : elements) {
      results[i++] = new PsiElementResolveResult(element);
    }
    return results;
  }

  @NotNull
  public static ResolveResult[] createResults(@Nullable PsiElement... elements) {
    if (elements == null || elements.length == 0) return EMPTY_ARRAY;

    final ResolveResult[] results = new ResolveResult[elements.length];
    for (int i = 0; i < elements.length; i++) {
      results[i] = new PsiElementResolveResult(elements[i]);
    }
    return results;
  }
}
