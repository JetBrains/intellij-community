// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.util.ui.JBFont

class UISettingsUtils(private val settings: UISettings) {
  val currentIdeScale: Float
    get() = settings.currentIdeScale

  fun setCurrentIdeScale(scale: Float) {
    settings.currentIdeScale = scale
  }

  var presentationModeFontSize: Float
    get() = scaleFontSize(globalSchemeEditorFontSize, settings.presentationModeIdeScale)
    set(value) {
      settings.presentationModeIdeScale = presentationModeIdeScaleFromFontSize(value)
    }

  val scaledConsoleFontSize: Float
    get() = scaleFontSize(EditorColorsManager.getInstance().globalScheme.consoleFontSize2D, currentIdeScale)

  val scaledEditorFontSize: Float
    get() = scaleFontSize(globalSchemeEditorFontSize, currentIdeScale)

  fun scaleFontSize(fontSize: Float): Float = scaleFontSize(fontSize, currentIdeScale)

  val currentDefaultScale: Float
    get() = defaultScale(UISettings.getInstance().presentationMode)

  companion object {
    @JvmStatic
    fun getInstance(): UISettingsUtils = UISettingsUtils(UISettings.getInstance())

    @JvmStatic
    fun with(settings: UISettings): UISettingsUtils = UISettingsUtils(settings)

    private val globalSchemeEditorFontSize: Float
      get() = EditorColorsManager.getInstance().globalScheme.editorFontSize2D

    internal fun presentationModeIdeScaleFromFontSize(fontSize: Float): Float {
      return (fontSize / globalSchemeEditorFontSize).let {
        if (it.percentValue == 100) 1f else it
      }
    }

    @JvmStatic
    fun scaleFontSize(fontSize: Float, scale: Float): Float = JBFont.scaleFontSize(fontSize, scale)

    @JvmStatic
    fun percentValue(value: Float): Int = value.percentValue

    fun defaultScale(isPresentation: Boolean): Float = if (isPresentation) 1.75f else 1f
  }
}

val Float.percentValue: Int
  get() = (this * 100 + 0.5).toInt()

val Float.percentStringValue: String
  get() = "$percentValue%"