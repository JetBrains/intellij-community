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
  PER_CLIENT,
  /**
   * Create a separate ToolWindow instance in a separate window that will be sent via projector to client
   */
  PROJECTOR_INSTANCING,
  /**
   * Steal content from host's toolwindow to share it using Projector
   */
  PROJECTOR_STEALING;

  public boolean isPerClientLike() {
    return this == PER_CLIENT || this == PROJECTOR_INSTANCING;
  }

  public boolean isMirrorLike() {
    return this == MIRROR || this == PROJECTOR_STEALING;
  }

  public boolean isProjected() {
    return this == PROJECTOR_INSTANCING || this == PROJECTOR_STEALING;
  }
}
