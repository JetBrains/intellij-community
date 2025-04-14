// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.documentation;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implement this interface and register as {@code com.intellij.platform.backend.documentation.psiTargetProvider} extension
 * to provide a {@link DocumentationTarget} implementation by a {@link LookupElement}.
 */
@ApiStatus.Experimental
@OverrideOnly
public interface LookupElementDocumentationTargetProvider {

  @Internal
  ExtensionPointName<LookupElementDocumentationTargetProvider> EP_NAME = ExtensionPointName.create(
    "com.intellij.platform.backend.documentation.lookupElementTargetProvider"
  );

  /**
   * @return target to handle documentation actions which are invoked on the given {@code element},
   * or {@code null} if this provider is not aware of the given element
   */
  @OverrideOnly
  @RequiresReadLock
  @RequiresBackgroundThread(generateAssertion = false)
  @Nullable DocumentationTarget documentationTarget(@NotNull PsiFile psiFile, @NotNull LookupElement element, int offset);
}
