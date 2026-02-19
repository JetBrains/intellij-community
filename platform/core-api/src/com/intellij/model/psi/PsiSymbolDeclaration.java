// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.psi;

import com.intellij.model.Symbol;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Symbol declaration in a PSI tree,
 * which is a way to convey host element and range in the host element to the platform, e.g., for highlighting and navigation.
 * <p/>
 * A symbol might be declared in several places, i.e., several declarations may declare the same symbol:
 * <pre>
 * SymbolDeclaration           d1   d2  dN
 *                               ↘  ↓  ↙
 * Symbol                           s
 * </pre>
 * <h4>Lifecycle</h4>
 * The PsiSymbolDeclaration instance is expected to stay valid within a single read action.
 * <p/>
 * <h4>Equality</h4>
 * There are no restrictions on whether implementations must provide {@link #equals}/{@link #hashCode}.
 *
 * @see PsiSymbolDeclarationProvider
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/declarations-and-references.html">Declarations and Reference (IntelliJ Platform Docs)</a>
 */
public interface PsiSymbolDeclaration {

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
  default @NotNull TextRange getAbsoluteRange() {
    return getRangeInDeclaringElement().shiftRight(getDeclaringElement().getTextRange().getStartOffset());
  }

  @NotNull
  Symbol getSymbol();
}
