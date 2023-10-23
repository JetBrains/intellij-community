// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.documentation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Implement this interface and register as {@code com.intellij.platform.backend.documentation.psiTargetProvider} extension
 * to provide a {@link DocumentationTarget} implementation by a {@link PsiElement}.
 *
 * @see DocumentationTargetProvider
 * @see SymbolDocumentationTargetProvider
 */
@OverrideOnly
public interface PsiDocumentationTargetProvider {

  @Internal
  ExtensionPointName<PsiDocumentationTargetProvider> EP_NAME = ExtensionPointName.create(
    "com.intellij.platform.backend.documentation.psiTargetProvider"
  );

  /**
   * @return target to handle documentation actions which are invoked on the given {@code element},
   * or {@code null} if this provider is not aware of the given element
   * @see com.intellij.lang.documentation.DocumentationProvider#generateDoc
   */
  @ApiStatus.OverrideOnly
  @RequiresReadLock
  @RequiresBackgroundThread(generateAssertion = false)
  default @Nullable DocumentationTarget documentationTarget(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
    throw new IllegalStateException("Override this or documentationTargets(PsiElement, PsiElement)");
  }

  @RequiresReadLock
  @RequiresBackgroundThread(generateAssertion = false)
  default @NotNull List<@NotNull DocumentationTarget> documentationTargets(@NotNull PsiElement element,
                                                                           @Nullable PsiElement originalElement) {
    DocumentationTarget target = documentationTarget(element, originalElement);
    return target == null ? List.of() : List.of(target);
  }
}
