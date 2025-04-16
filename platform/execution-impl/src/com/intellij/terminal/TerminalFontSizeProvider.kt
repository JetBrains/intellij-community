// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TerminalFontSizeProvider {
  companion object {
    @JvmField val KEY: Key<TerminalFontSizeProvider> = Key.create("TerminalFontSizeProvider")
  }

  fun getFontSize(): Float

  /**
   * Sets temporary font size without changing the size in the settings.
   * Useful for temporary Zoom feature.
   */
  fun setFontSize(newSize: Float)

  /**
   * Resets the temporary font size set using [setFontSize] to the default from the settings.
   */
  fun resetFontSize()

  fun addListener(parentDisposable: Disposable, listener: Listener)

  interface Listener {
    fun fontChanged()
  }
}