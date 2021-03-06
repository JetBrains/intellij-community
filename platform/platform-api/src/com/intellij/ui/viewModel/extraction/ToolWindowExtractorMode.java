// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.extraction;

public enum ToolWindowExtractorMode {
  /**
   * Do not allow clients to access toolwindow.
   */
  DISABLE,
  /**
   * Use single host toolwindow, allow clients to access host toolwindow but DO NOT share its UI with clients.
   */
  FALLBACK,
  /**
   * Use single host toolwindow and share its UI between multiple clients.
   */
  MIRROR,
  /**
   * Create a separate ToolWindow instance for each client.
   */
  PER_CLIENT
}
