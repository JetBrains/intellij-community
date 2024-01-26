// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.documentation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Extension point to provide inline documentation for {@link DocumentationTarget} based documentation
 */
@OverrideOnly
public interface InlineDocumentationProvider {

  @Internal
  ExtensionPointName<InlineDocumentationProvider> EP_NAME = ExtensionPointName.create(
    "com.intellij.platform.backend.documentation.inlineDocumentationProvider"
  );

  /**
   * This defines {@link InlineDocumentation} in file, which can be rendered in place.
   * HTML content to be displayed will be obtained using {@link InlineDocumentation#renderText()} method.
   */
  @NotNull Collection<@NotNull InlineDocumentation> inlineDocumentationItems(PsiFile file);

  /**
   * Returns {@link InlineDocumentation} corresponding to the provided text range in a file.
   *
   * @see #inlineDocumentationItems(PsiFile)
   */
  @Nullable InlineDocumentation findInlineDocumentation(@NotNull PsiFile file, @NotNull TextRange textRange);
}
