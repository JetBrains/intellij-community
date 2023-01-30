// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.impl.zoomIndicator.ZoomIndicatorManager
import com.intellij.ui.scale.JBUIScale
import kotlin.math.round

@State(name = "IdeScaleTransformer", storages = [Storage("ideScaleTransformer.xml")])
class IdeScaleTransformer : PersistentStateComponent<IdeScaleTransformer.PersistedScale> {

  @Volatile
  private var current: ScalingParameters? = null
    set(value) {
      field = value
      if (value?.shouldPersist == true) parametersToPersist = value
    }

  @Volatile
  private var parametersToPersist: ScalingParameters? = null
  private var savedOriginalConsoleFontSize: Float = -1f

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

  fun resetToLastPersistedScale(performBeforeTweaking: () -> Unit) {
    scale(parametersToPersist?.scale ?: DEFAULT_SCALE, performBeforeTweaking)
  }

  private fun prepareTweaking() {
    if (currentScale == DEFAULT_SCALE) {
      savedOriginalConsoleFontSize = EditorColorsManager.getInstance().globalScheme.consoleFontSize2D
    }
  }

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

  fun scale(newScaleFactor: Float) {
    scale(newScaleFactor, null)
  }

  private fun scale(newScaleFactor: Float, performBeforeTweaking: (() -> Unit)?) {
    prepareTweaking()
    performBeforeTweaking?.invoke()
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

  class PersistedScale {
    var scaleFactor: Float = DEFAULT_SCALE
    var savedOriginalConsoleFontSize: Float = -1f
  }

  override fun getState(): PersistedScale =
    PersistedScale().also {
      it.scaleFactor = parametersToPersist?.scale ?: DEFAULT_SCALE
      it.savedOriginalConsoleFontSize = savedOriginalConsoleFontSize
    }

  override fun loadState(state: PersistedScale) {
    savedOriginalConsoleFontSize = state.savedOriginalConsoleFontSize
    current = ScalingParameters.ScaleOriented(state.scaleFactor, state.savedOriginalConsoleFontSize)
  }

  companion object {
    const val DEFAULT_SCALE = 1.0f
    private const val SCALING_STEP = 0.25f

    @JvmStatic
    val instance: IdeScaleTransformer
      get() = service<IdeScaleTransformer>()
  }
}

private abstract class ScalingParameters {
  abstract val scale: Float
  abstract val consoleFontSize: Float
  open val shouldPersist = true
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
    override val shouldPersist = false
    override fun scaledEditorFontSize(fontSize: Float): Float = editorFont
  }
}
