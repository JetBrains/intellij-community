// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.popup;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public interface JBPopupListener {
  default void beforeShown(@NotNull LightweightWindowEvent event) {
  }

  default void onClosed(@NotNull LightweightWindowEvent event) {
  }

  /**
   * @deprecated Use {@link JBPopupListener} directly.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  class Adapter implements JBPopupListener {
  }
}
