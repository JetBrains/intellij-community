// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.extraction;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

public interface ToolWindowViewModelExtractor {
  ExtensionPointName<ToolWindowViewModelExtractor> EP_NAME = ExtensionPointName.create("com.intellij.toolWindowExtractor");

  boolean isApplicable(@NotNull String toolWindowId);

  @NotNull
  ToolWindowExtractorMode getMode();
}