// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.documentation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Extension point to provide inline documentation for {@link DocumentationTarget} based documentation
 */
public interface InlineDocumentationProvider {
  @ApiStatus.Internal
  ExtensionPointName<InlineDocumentationProvider> EP_NAME = ExtensionPointName.create(
    "com.intellij.platform.backend.documentation.inlineDocumentationProvider"
  );

  /**
   * This defines {@link InlineDocumentation} in file, which can be rendered in place.
   * HTML content to be displayed will be obtained using {@link InlineDocumentation#renderText()} method.
   */  @NotNull
  Collection<InlineDocumentation> inlineDocumentationItems(PsiFile file);

  /**
   * Returns {@link InlineDocumentation} corresponding to the provided text range in a file.
   *
   * @see #inlineDocumentationItems(PsiFile)
   */
  @Nullable
  InlineDocumentation findInlineDocumentation(PsiFile file, TextRange textRange);
}