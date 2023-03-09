// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.impl.zoomIndicator.ZoomIndicatorManager
import com.intellij.ui.scale.JBUIScale
import kotlin.math.round


object IdeScaleTransformer {
  const val DEFAULT_SCALE = 1.0f
  private const val SCALING_STEP = 0.25f

  @Volatile
  private var current: ScalingParameters? = null
  private var savedOriginalConsoleFontSize: Float? = null

  val currentScale: Float
    get() = current?.scale ?: DEFAULT_SCALE

  val currentEditorFontSize: Float
    get() = current?.editorFont ?: globalEditorFontSize

  val isEditorFontSizeForced: Boolean
    get() = current is ScalingParameters.FontOriented

  private val globalEditorFontSize: Float
    get() = EditorColorsManager.getInstance().globalScheme.editorFontSize2D

  fun scaledEditorFontSize(fontSize: Float): Float =
    current?.scaledEditorFontSize(fontSize) ?: fontSize

  fun zoomIn() {
    scale(currentScale + SCALING_STEP)
  }

  fun zoomOut() {
    scale(currentScale - SCALING_STEP)
  }

  fun reset() {
    scale(DEFAULT_SCALE)
  }

  private fun prepareTweaking() {
    if (currentScale == DEFAULT_SCALE) {
      savedOriginalConsoleFontSize = EditorColorsManager.getInstance().globalScheme.consoleFontSize2D
    }
  }

  @JvmStatic
  fun scaleToEditorFontSize(fontSize: Float, performBeforeTweaking: () -> Unit) {
    if (fontSize <= globalEditorFontSize) {
      scale(DEFAULT_SCALE, performBeforeTweaking)
      return
    }

    prepareTweaking()
    performBeforeTweaking()
    val newParameters = ScalingParameters.FontOriented(fontSize)
    performTweaking(newParameters)
  }

  private fun scale(newScaleFactor: Float, performBeforeTweaking: (() -> Unit)? = null) {
    prepareTweaking()
    performBeforeTweaking?.invoke()
    val savedOriginalConsoleFontSize = savedOriginalConsoleFontSize ?: return
    val newParameters = ScalingParameters.ScaleOriented(newScaleFactor, savedOriginalConsoleFontSize)
    performTweaking(newParameters)
  }

  private fun performTweaking(parameters: ScalingParameters) {
    current = parameters
    tweakEditorFont(parameters)
    notifyAllAndUpdateUI()
  }

  private fun tweakEditorFont(parameters: ScalingParameters) {
    EditorColorsManager.getInstance().globalScheme.setConsoleFontSize(parameters.consoleFontSize)

    for (editor in EditorFactory.getInstance().allEditors) {
      if (editor is EditorEx) {
        editor.putUserData(ZoomIndicatorManager.SUPPRESS_ZOOM_INDICATOR_ONCE, true)
        editor.setFontSize(parameters.editorFont)
      }
    }
  }

  private fun notifyAllAndUpdateUI() {
    UISettings.getInstance().fireUISettingsChanged()
    LafManager.getInstance().updateUI()
    EditorUtil.reinitSettings()
  }
}

private abstract class ScalingParameters {
  abstract val scale: Float
  abstract val consoleFontSize: Float
  open val editorFont: Float
    get() = scaledEditorFontSize(EditorColorsManager.getInstance().globalScheme.editorFontSize2D)

  abstract fun scaledEditorFontSize(fontSize: Float): Float

  class ScaleOriented(override val scale: Float,
                      private val originalConsoleFontSize: Float) : ScalingParameters() {
    override val consoleFontSize: Float get() = round(originalConsoleFontSize * scale)
    override fun scaledEditorFontSize(fontSize: Float): Float =
      round(fontSize * scale)
  }

  class FontOriented(override val editorFont: Float) : ScalingParameters() {
    override val scale: Float get() = JBUIScale.getFontScale(editorFont)
    override val consoleFontSize: Float get() = editorFont
    override fun scaledEditorFontSize(fontSize: Float): Float = editorFont
  }
}
