// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import org.jetbrains.annotations.ApiStatus.Internal

class IslandsState {
  companion object {
    @Volatile
    private var enabled = false

    @Volatile
    private var custom = false

    @Internal
    fun setEnabled(enabled: Boolean, custom: Boolean) {
      this.enabled = enabled
      this.custom = custom
    }

    /**
     * @return true if island rendering mode is enabled.
     */
    fun isEnabled(): Boolean {
      return enabled
    }

    /**
     * @return true if island rendering mode is enabled for the custom UI theme.
     *
     * see Settings | Advanced Settings | Enable Islands UI for custom themes
     */
    fun isCustomEnabled(): Boolean {
      return custom
    }
  }
}