// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi;

import com.intellij.model.Symbol;
import com.intellij.model.SymbolReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Reference from a {@link PsiElement} to a {@link Symbol} or possibly several Symbols.
 *
 * @see PsiCompletableReference
 */
public interface PsiSymbolReference extends SymbolReference {

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
  @NotNull
  default TextRange getAbsoluteRange() {
    return getRangeInElement().shiftRight(getElement().getTextRange().getStartOffset());
  }

  /**
   * @return text covered by the reference
   */
  static @NotNull String getReferenceText(@NotNull PsiSymbolReference reference) {
    return reference.getRangeInElement().substring(reference.getElement().getText());
  }
}
