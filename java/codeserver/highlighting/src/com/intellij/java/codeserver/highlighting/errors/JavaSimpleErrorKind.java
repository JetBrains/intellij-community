// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting.errors;

import com.intellij.java.codeserver.highlighting.JavaCompilationErrorBundle;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Function;

/**
 * Java compilation error without additional context
 */
public non-sealed class JavaSimpleErrorKind<Psi extends PsiElement> implements JavaErrorKind<Psi, Void> {
  private final @NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String myKey;

  JavaSimpleErrorKind(@NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String key) {
    myKey = key;
  }

  @Override
  public @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) @NotNull String key() {
    return myKey;
  }

  @Override
  public @NotNull HtmlChunk description(@NotNull Psi psi, Void unused) {
    return HtmlChunk.raw(JavaCompilationErrorBundle.message(myKey));
  }

  public @NotNull JavaCompilationError<Psi, Void> create(@NotNull Psi psi) {
    return JavaErrorKind.super.create(psi, null);
  }
  
  JavaSimpleErrorKind<Psi> withAnchor(@NotNull Function<? super @NotNull Psi, ? extends @NotNull PsiElement> anchorExtractor) {
    return new JavaSimpleErrorKind<>(myKey) {
      @Override
      public @NotNull PsiElement anchor(@NotNull Psi psi, Void unused) {
        return anchorExtractor.apply(psi);
      }
    };
  }

  @Override
  public String toString() {
    return "JavaErrorKind[" + myKey + "]";
  }
}
