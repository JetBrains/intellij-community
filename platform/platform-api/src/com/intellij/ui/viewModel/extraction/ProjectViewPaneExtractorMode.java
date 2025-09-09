// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.viewModel.extraction;

import org.jetbrains.annotations.ApiStatus.Internal;

@Internal
public enum ProjectViewPaneExtractorMode {
  /**
   * Convert using be controls (default)
   */
  BE_CONTROL,

  /**
   * Convert using Lux
   */
  DIRECT_TRANSFER
}
