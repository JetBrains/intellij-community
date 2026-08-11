// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.rendering

import org.jetbrains.annotations.ApiStatus

interface ThemeContext {
  fun digest(): String
  /**
   * Temporary method to get image modifiers from theme, should be replaced with functions that will
   * apply path transformations, color patching etc.
   */
  @ApiStatus.Internal
  fun imageModifiers(): ImageModifiers? = null

  companion object {
    val None: ThemeContext = object : ThemeContext {
      override fun digest(): String = "NONE"
    }
  }
}