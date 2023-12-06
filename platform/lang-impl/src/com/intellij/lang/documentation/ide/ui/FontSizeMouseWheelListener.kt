// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.ui

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.options.FontSize
import com.intellij.ui.FontSizeModel
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import kotlin.math.abs

internal class FontSizeMouseWheelListener(
  private val model: FontSizeModel<FontSize>,
) : MouseWheelListener {

  override fun mouseWheelMoved(e: MouseWheelEvent) {
    if (!EditorSettingsExternalizable.getInstance().isWheelFontChangeEnabled || !EditorUtil.isChangeFontSize(e)) {
      // TODO this check must be done outside this listener: listener should not even be registered if this case
      return
    }
    val rotation = e.wheelRotation
    if (rotation == 0) {
      return
    }
    var newFontSize = model.value
    val increase = rotation <= 0
    repeat(abs(rotation)) {
      newFontSize = if (increase) {
        newFontSize.larger()
      }
      else {
        newFontSize.smaller()
      }
    }
    model.value = newFontSize
  }
}
