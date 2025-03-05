// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.update;

import org.jetbrains.annotations.ApiStatus.Obsolete;

/**
 * Use {@link com.intellij.util.ui.UiScopeKt#launchOnShow}
 */
@Obsolete
public interface Activatable {
  default void showNotify() {
  }

  default void hideNotify() {
  }

  /**
   * @deprecated Use {@link Activatable} directly.
   */
  @Deprecated
  class Adapter implements Activatable {
  }
}
