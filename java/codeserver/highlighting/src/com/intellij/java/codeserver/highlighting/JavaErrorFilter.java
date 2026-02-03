// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * A filter for Java compilation errors that allows suppressing specific errors in specific contexts 
 * (e.g., in the presence of an annotation processor)
 */
public interface JavaErrorFilter {
  ExtensionPointName<JavaErrorFilter> EP_NAME = ExtensionPointName.create("com.intellij.lang.java.javaErrorFilter");

  /**
   * @param file current Java file
   * @param error compilation error to check
   * @return true if this error should not be reported
   */
  boolean shouldSuppressError(@NotNull PsiFile file, @NotNull JavaCompilationError<?, ?> error);
}
