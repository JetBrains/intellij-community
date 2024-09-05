// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.search

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

/**
 * Used to detect when the IDE is started in the "build searchable options" mode,
 * which is used in the build process to index all configurables for faster searching
 */
@Service
class TraverseUIMode {
  @Volatile
  private var isActive = false

  internal fun setActive(value: Boolean) {
    isActive = value
  }

  /**
   * @return `true` iff the IDE was started in "build searchable options" mode
   */
  fun isActive(): Boolean = isActive

  companion object {
    @JvmStatic
    fun getInstance(): TraverseUIMode = service()
  }
}
