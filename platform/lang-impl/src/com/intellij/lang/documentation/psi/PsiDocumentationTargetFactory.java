// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.psi;

import com.intellij.lang.documentation.DocumentationTarget;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implement this interface and register as {@code com.intellij.lang.psiDocumentation} extension
 * to provide a {@link DocumentationTarget} implementation by a {@link PsiElement}.
 *
 * @see com.intellij.lang.documentation.symbol.SymbolDocumentationTargetFactory
 */
@Experimental
public interface PsiDocumentationTargetFactory {

  @Internal
  ExtensionPointName<PsiDocumentationTargetFactory> EP_NAME = ExtensionPointName.create("com.intellij.lang.psiDocumentation");

  /**
   * @return target to handle documentation actions which are invoked on the given {@code element},
   * or {@code null} if this factory is not aware of the given element
   * @see com.intellij.lang.documentation.DocumentationProvider#generateDoc
   */
  @Nullable DocumentationTarget documentationTarget(@NotNull PsiElement element, @Nullable PsiElement originalElement);
}
