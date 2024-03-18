// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
@ApiStatus.Experimental
public interface ClientDocumentationSettings {
  static ClientDocumentationSettings getCurrentInstance() {
    return ApplicationManager.getApplication().getService(ClientDocumentationSettings.class);
  }

  boolean isHighlightingOfQuickDocSignaturesEnabled();

  boolean isHighlightingOfCodeBlocksEnabled();

  boolean isSemanticHighlightingOfLinksEnabled();

  boolean isCodeBackgroundEnabled();

  @NotNull
  DocumentationSettings.InlineCodeHighlightingMode getInlineCodeHighlightingMode();
}
