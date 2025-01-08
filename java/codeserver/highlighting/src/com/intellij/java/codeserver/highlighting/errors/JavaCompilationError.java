// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting.errors;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A concrete instance of a Java compilation error. Note that the instance is bound to a PSI element, so it should not
 * outlive the read-action. Otherwise it may become invalid.
 * 
 * @param kind error kind
 * @param psi PSI element where the error occurred
 * @param context additional context necessary to properly render the error and the corresponding quick-fixes
 * @param <Context> type of the context
 */
public record JavaCompilationError<Psi extends PsiElement, Context>(@NotNull JavaErrorKind<Psi, Context> kind, 
                                                                    @NotNull Psi psi, 
                                                                    Context context) {
  public JavaCompilationError {
    kind.validate(psi, context);
  }

  /**
   * @return a desired anchor to put the error message at
   */
  public @NotNull PsiElement anchor() {
    return kind.anchor(psi, context);
  }

  /**
   * @return range within anchor to highlight; or null if the whole anchor should be highlighted
   */
  public @Nullable TextRange range() {
    return kind.range(psi, context);
  }

  /**
   * @return a desired highlighting type to display the error
   */
  public @NotNull JavaErrorHighlightType highlightType() {
    return kind.highlightType(psi, context);
  }

  /**
   * @return a user-readable localized error description
   */
  public @NotNull HtmlChunk description() {
    return kind.description(psi, context);
  }
}
