// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import kotlin.math.round


class UISettingsUtils {
  companion object {
    private val settings get() = UISettings.getInstance()

    val currentIdeScale get() = if (settings.presentationMode) settings.presentationModeIdeScale else settings.ideScale

    fun setCurrentIdeScale(scale: Float) {
      if (scale.percentValue == currentIdeScale.percentValue) return
      if (settings.presentationMode) settings.presentationModeIdeScale = scale
      else settings.ideScale = scale
    }

    @JvmStatic
    var presentationModeFontSize: Float
      get() = scaleFontSize(globalSchemeEditorFontSize, settings.presentationModeIdeScale)
      set(value) {
        settings.presentationModeIdeScale = presentationModeIdeScaleFromFontSize(value)
      }

    @JvmStatic
    internal fun presentationModeIdeScaleFromFontSize(fontSize: Float): Float =
      (fontSize / globalSchemeEditorFontSize).let {
        if (it.percentValue == 100) 1f
        else it
      }

    @JvmStatic
    val scaledConsoleFontSize: Float
      get() = scaleFontSize(EditorColorsManager.getInstance().globalScheme.consoleFontSize2D, currentIdeScale)

    @JvmStatic
    val scaledEditorFontSize: Float
      get() = scaleFontSize(globalSchemeEditorFontSize, currentIdeScale)

    private val globalSchemeEditorFontSize: Float get() = EditorColorsManager.getInstance().globalScheme.editorFontSize2D

    @JvmStatic
    fun scaleFontSize(fontSize: Float): Float = scaleFontSize(fontSize, currentIdeScale)

    @JvmStatic
    fun scaleFontSize(fontSize: Float, scale: Float): Float =
      if (scale == 1f) fontSize
      else round(fontSize * scale)
  }
}

val Float.percentValue get() = (this * 100 + 0.5).toInt()
val Float.percentStringValue get() = "$percentValue%"