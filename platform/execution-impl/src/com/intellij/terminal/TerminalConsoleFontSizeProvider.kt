// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.openapi.Disposable

internal class TerminalConsoleFontSizeProvider : TerminalFontSizeProvider {
  private val uiSettingsManager = TerminalUiSettingsManager.getInstance()

  override fun getFontSize(): Int {
    return uiSettingsManager.getFontSize()
  }

  override fun getFontSize2D(): Float {
    return uiSettingsManager.getFontSize2D()
  }

  override fun setFontSize(newSize: Float) {
    return uiSettingsManager.setFontSize(newSize)
  }

  override fun resetFontSize() {
    return uiSettingsManager.resetFontSize()
  }

  override fun addListener(parentDisposable: Disposable, listener: TerminalFontSizeProvider.Listener) {
    uiSettingsManager.addListener(parentDisposable, object : TerminalUiSettingsListener {
      override fun fontChanged() {
        listener.fontChanged()
      }
    })
  }
}