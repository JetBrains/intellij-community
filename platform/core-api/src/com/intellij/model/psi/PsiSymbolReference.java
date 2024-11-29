// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.psi;

import com.intellij.model.Symbol;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Reference from a {@link PsiElement} to a {@link Symbol} or possibly several Symbols.
 *
 * @see PsiCompletableReference
 * @see PsiSymbolReferenceProvider
 */
public interface PsiSymbolReference {

  /**
   * @return the underlying (referencing) element of the reference
   */
  @NotNull
  PsiElement getElement();

  /**
   * @return range in {@link #getElement() element} which is considered a reference,
   * e.g. range of `bar` in `foo.bar` qualified reference expression
   */
  @NotNull
  TextRange getRangeInElement();

  /**
   * @return range in the {@link PsiElement#getContainingFile containing file} of the {@link #getElement element}
   * which is considered a reference
   * @see #getRangeInElement
   */
  default @NotNull TextRange getAbsoluteRange() {
    return getRangeInElement().shiftRight(getElement().getTextRange().getStartOffset());
  }

  /**
   * @return collection of referenced symbols with additional data, or empty collection if there are no targets
   */
  @NotNull
  @Unmodifiable
  Collection<? extends Symbol> resolveReference();

  /**
   * Default implementation checks results from {@link #resolveReference()}.
   * Override this method to skip actual resolution if this reference cannot ever resolve to this target.
   *
   * @return whether this reference resolves to a target
   */
  default boolean resolvesTo(@NotNull Symbol target) {
    return ContainerUtil.or(resolveReference(), it -> it.equals(target));
  }

  /**
   * @return text covered by the reference
   */
  static @NotNull String getReferenceText(@NotNull PsiSymbolReference reference) {
    return reference.getRangeInElement().substring(reference.getElement().getText());
  }
}
