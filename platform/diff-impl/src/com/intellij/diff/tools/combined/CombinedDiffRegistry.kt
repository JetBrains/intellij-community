// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.idea.AppMode
import com.intellij.openapi.util.registry.Registry

object CombinedDiffRegistry {
  fun isEnabled(): Boolean = Registry.`is`("enable.combined.diff") && !AppMode.isRemoteDevHost()

  fun getPreloadedBlocksCount(): Int = Registry.intValue("combined.diff.visible.viewport.delta", 3, 1, 100)

  fun getMaxBlockCountInMemory(): Int = Registry.intValue("combined.diff.loaded.content.limit")

  fun getFilesLimit(): Int = Registry.intValue("combined.diff.files.limit")
}