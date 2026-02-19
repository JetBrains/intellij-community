// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class PluginUpdateActivity {
  /**
   * Does not count as a plugin update check, used in various system activities: import of settings, plugin synchronization, etc.
   */
  AVAILABLE_VERSIONS,

  /**
   * Registers an update check attempt for installed plugins in statistics.
   */
  INSTALLED_VERSIONS;
}