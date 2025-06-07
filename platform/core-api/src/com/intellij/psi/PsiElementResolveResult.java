// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Trivial implementation of {@link ResolveResult}.
 */
public class PsiElementResolveResult implements ResolveResult{
  private final @NotNull PsiElement myElement;
  private final boolean myValidResult;

  /**
   * Creates a resolve result with the specified resolve target.
   *
   * @param element the resolve target element.
   */
  public PsiElementResolveResult(@NotNull PsiElement element) {
    this(element, true);
  }

  public PsiElementResolveResult(final @NotNull PsiElement element, final boolean validResult) {
    myElement = element;
    myValidResult = validResult;
  }

  @Override
  public @NotNull PsiElement getElement() {
    return myElement;
  }

  @Override
  public boolean isValidResult() {
    return myValidResult;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final PsiElementResolveResult that = (PsiElementResolveResult)o;

    if (!myElement.equals(that.myElement)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myElement.hashCode();
  }

  @Override
  public @NonNls String toString() {
    return "PsiElementResolveResult with " + myElement.getClass() + ": " +
           (myElement instanceof PsiNamedElement ? ((PsiNamedElement)myElement).getName() : myElement.getText());
  }

  public static ResolveResult @NotNull [] createResults(@Nullable Collection<? extends PsiElement> elements) {
    if (elements == null || elements.isEmpty()) return EMPTY_ARRAY;

    final ResolveResult[] results = new ResolveResult[elements.size()];
    int i = 0;
    for (PsiElement element : elements) {
      results[i++] = new PsiElementResolveResult(element);
    }
    return results;
  }

  public static ResolveResult @NotNull [] createResults(PsiElement @Nullable ... elements) {
    if (elements == null || elements.length == 0) return EMPTY_ARRAY;

    final ResolveResult[] results = new ResolveResult[elements.length];
    for (int i = 0; i < elements.length; i++) {
      results[i] = new PsiElementResolveResult(elements[i]);
    }
    return results;
  }
}
