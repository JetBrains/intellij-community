// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi;

import com.intellij.model.SymbolDeclaration;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Symbol declaration in PSI tree,
 * which is a way to convey host element and range in host element to the platform, e.g., for highlighting and navigation.
 * <p/>
 * <h4>Lifecycle</h4>
 * The PsiSymbolDeclaration instance is expected to stay valid within a single read action.
 * <p/>
 * <h4>Equality</h4>
 * There are no restrictions on whether implementations must provide {@link #equals}/{@link #hashCode}.
 *
 * @see PsiSymbolDeclarationProvider
 */
public interface PsiSymbolDeclaration extends SymbolDeclaration {

  /**
   * @return underlying (declaring) element
   */
  @NotNull PsiElement getDeclaringElement();

  /**
   * @return range relative to {@link #getDeclaringElement() element} range,
   * which is considered a declaration, e.g. range of identifier in Java class
   */
  @NotNull TextRange getRangeInDeclaringElement();

  /**
   * @return range in the {@link PsiElement#getContainingFile containing file} of the {@link #getDeclaringElement() element}
   * which is considered a declaration
   * @see #getRangeInDeclaringElement()
   */
  @NotNull
  default TextRange getAbsoluteRange() {
    return getRangeInDeclaringElement().shiftRight(getDeclaringElement().getTextRange().getStartOffset());
  }
}
