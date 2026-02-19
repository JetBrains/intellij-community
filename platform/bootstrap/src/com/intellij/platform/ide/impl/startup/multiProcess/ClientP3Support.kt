// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.startup.multiProcess

import com.intellij.openapi.project.impl.P3Support
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path

@VisibleForTesting
@ApiStatus.Internal
class ClientP3Support : P3Support {
  override fun isEnabled(): Boolean = true

  override fun canBeOpenedInThisProcess(projectStoreBaseDir: Path): Boolean = true

  override suspend fun openInChildProcess(projectStoreBaseDir: Path) {
    throw UnsupportedOperationException()
  }
  
  override val disabledPluginsFileName: String
    get() = "disabled_plugins_frontend.txt"
}