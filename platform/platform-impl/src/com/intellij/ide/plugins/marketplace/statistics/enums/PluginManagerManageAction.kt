// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.enums

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class PluginManagerManageAction {
  MANAGE_REPOSITORIES,
  HTTP_PROXY,
  CERTIFICATES,
  INSTALL_FROM_DISK,
  ENABLE_ALL,
  DISABLE_ALL,
  TOGGLE_AUTO_UPDATE,
  RESET
}
