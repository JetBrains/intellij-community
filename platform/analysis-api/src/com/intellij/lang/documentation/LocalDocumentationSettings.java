// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
@ApiStatus.Internal
public class LocalDocumentationSettings implements ClientDocumentationSettings {

  @Override
  public boolean isHighlightingOfQuickDocSignaturesEnabled() {
    return ApplicationManager.getApplication().isUnitTestMode()
           || Registry.is("documentation.component.enable.highlighting.of.quick.doc.signatures");
  }

  @Override
  public boolean isHighlightingOfCodeBlocksEnabled() {
    return ApplicationManager.getApplication().isUnitTestMode()
           || AdvancedSettings.getBoolean("documentation.components.enable.code.blocks.highlighting");
  }

  @Override
  public boolean isSemanticHighlightingOfLinksEnabled() {
    return ApplicationManager.getApplication().isUnitTestMode()
           || AdvancedSettings.getBoolean("documentation.components.enable.highlighting.of.links");
  }

  @Override
  public @NotNull DocumentationSettings.InlineCodeHighlightingMode getInlineCodeHighlightingMode() {
    return (ApplicationManager.getApplication().isUnitTestMode()
            || AdvancedSettings.getBoolean("documentation.components.enable.inline.code.highlighting"))
           ? DocumentationSettings.InlineCodeHighlightingMode.SEMANTIC_HIGHLIGHTING
           : DocumentationSettings.InlineCodeHighlightingMode.NO_HIGHLIGHTING;
  }
}
