// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting.errors;

import com.intellij.java.codeserver.highlighting.JavaCompilationErrorBundle;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * A kind of Java compilation error
 * 
 * @param <Psi> type of context PSI element required for this error
 * @param <Context> additional context required for a particular kind, if any
 */
public sealed interface JavaErrorKind<Psi extends PsiElement, Context> permits Parameterized, JavaSimpleErrorKind {
  /**
   * @return a key, which uniquely identifies the error kind
   */
  @NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String key();

  /**
   * @param psi PSI element associated with an error
   * @param context a context in which the error should be rendered
   * @return rendered localized error description
   */
  @NotNull HtmlChunk description(@NotNull Psi psi, Context context);

  /**
   * @param psi PSI element associated with an error
   * @param context a context in which the error should be rendered
   * @return error message anchor (must be within the psi)
   */
  default @NotNull PsiElement anchor(@NotNull Psi psi, Context context) {
    return psi;
  }

  /**
   * @param psi PSI element associated with an error
   * @param context a context in which the error should be rendered
   * @throws IllegalArgumentException if the context or PSI element are not applicable to this error kind
   */
  default void validate(@NotNull Psi psi, Context context) throws IllegalArgumentException {
  }

  /**
   * @param context context to bind an instance to
   * @return create an instance of this error
   */
  default @NotNull JavaCompilationError<Psi, Context> create(@NotNull Psi psi, Context context) {
    return new JavaCompilationError<>(this, psi, context);
  }
}
