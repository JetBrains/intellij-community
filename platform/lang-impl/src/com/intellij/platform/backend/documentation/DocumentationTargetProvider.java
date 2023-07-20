// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.documentation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Implement this interface and register as {@code com.intellij.lang.documentation} extension
 * to provide a {@link DocumentationTarget} implementation by an offset in a {@link PsiFile}.
 *
 * @see SymbolDocumentationTargetProvider
 * @see PsiDocumentationTargetProvider
 */
@OverrideOnly
public interface DocumentationTargetProvider {

  @Internal
  ExtensionPointName<DocumentationTargetProvider> EP_NAME = ExtensionPointName.create(
    "com.intellij.platform.backend.documentation.targetProvider"
  );

  @RequiresReadLock
  @RequiresBackgroundThread
  @NotNull List<? extends @NotNull DocumentationTarget> documentationTargets(@NotNull PsiFile file, int offset);
}
