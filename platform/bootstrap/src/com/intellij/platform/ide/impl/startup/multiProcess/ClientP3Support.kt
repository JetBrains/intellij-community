// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.startup.multiProcess

import com.intellij.openapi.project.impl.P3Support
import java.nio.file.Path

internal class ClientP3Support : P3Support {
  override fun isEnabled(): Boolean = true

  override fun canBeOpenedInThisProcess(projectStoreBaseDir: Path): Boolean = true

  override suspend fun openInChildProcess(projectStoreBaseDir: Path) {
    throw UnsupportedOperationException()
  }
}