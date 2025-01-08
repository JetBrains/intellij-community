// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting.errors;

import com.intellij.java.codeserver.highlighting.JavaCompilationErrorBundle;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

non-sealed class JavaParameterizedErrorKind<Psi extends PsiElement, Context> implements JavaErrorKind<Psi, Context> {
  private final @NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String myKey;

  JavaParameterizedErrorKind(@NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String key) {
    myKey = key;
  }

  @Override
  public @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) @NotNull String key() {
    return myKey;
  }

  @Override
  public String toString() {
    return "JavaErrorKind[" + myKey + "]";
  }

  @Override
  public @NotNull HtmlChunk description(@NotNull Psi element, Context context) {
    return HtmlChunk.raw(JavaCompilationErrorBundle.message(myKey));
  }
}
