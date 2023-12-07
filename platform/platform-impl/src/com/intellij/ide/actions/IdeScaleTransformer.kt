// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ui.ideScaleIndicator.IdeScaleIndicatorManager
import com.intellij.ide.ui.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.impl.zoomIndicator.ZoomIndicatorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.layout.ValidationInfoBuilder
import org.jetbrains.annotations.Nls

@Service(Service.Level.APP)
internal class IdeScaleTransformer {
  private val settingsUtils: UISettingsUtils
    get() = UISettingsUtils.getInstance()

  private var lastSetScale: Float? = null

  internal fun uiSettingsChanged() {
    if (lastSetScale?.percentValue != settingsUtils.currentIdeScale.percentValue) {
      scale()
    }
  }

  internal fun setupLastSetScale() {
    if (lastSetScale == null) {
      lastSetScale = settingsUtils.currentIdeScale
    }
  }

  private fun scale() {
    lastSetScale = settingsUtils.currentIdeScale
    tweakEditorFont()
    notifyAllAndUpdateUI()
  }

  private fun tweakEditorFont() {
    for (editor in EditorFactory.getInstance().allEditors) {
      if (editor is EditorEx) {
        if (editor.isDisposed) {
          continue
        }

        editor.putUserData(ZoomIndicatorManager.SUPPRESS_ZOOM_INDICATOR_ONCE, true)
        editor.setFontSize(if (editor.editorKind == EditorKind.CONSOLE) settingsUtils.scaledConsoleFontSize
                           else settingsUtils.scaledEditorFontSize)
      }
    }
  }

  private fun notifyAllAndUpdateUI() {
    serviceIfCreated<LafManager>()?.updateUI()
    EditorUtil.reinitSettings()
  }

  class Settings {
    companion object {
      private const val SCALING_STEP = 0.1f
      private const val PRESENTATION_MODE_MIN_SCALE = 0.5f
      private const val PRESENTATION_MODE_MAX_SCALE = 4f

      val allowAnyZoomValuesInSettings: Boolean get() = Registry.`is`("ide.scale.editable.combobox", false)

      private val ideScaleOptions =
        (if (allowAnyZoomValuesInSettings) listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f) else emptyList()) +
        listOf(1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)

      private val presentationModeScaleOptions = listOf(1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f, 2.25f, 2.5f, 2.75f, 3f)
      val currentScaleOptions: List<Float> get() = scaleOptions(UISettings.getInstance().presentationMode)

      private fun scaleOptions(isPresentation: Boolean) = if (isPresentation) presentationModeScaleOptions else ideScaleOptions

      fun createIdeScaleComboboxModel(): CollectionComboBoxModel<String> {
        return CollectionComboBoxModel(scaleOptions(false).map { it.percentStringValue },
                                       UISettings.getInstance().ideScale.percentStringValue)
      }

      fun createPresentationModeScaleComboboxModel(): CollectionComboBoxModel<String> {
        return CollectionComboBoxModel(scaleOptions(true).map { it.percentStringValue },
                                       UISettings.getInstance().presentationModeIdeScale.percentStringValue)
      }

      fun validatePresentationModePercentScaleInput(builder: ValidationInfoBuilder, comboBox: ComboBox<String>): ValidationInfo? {
        val message = validatePresentationModePercentScaleInput(comboBox.item) ?: return null
        return builder.error(message)
      }

      @Nls
      fun validatePresentationModePercentScaleInput(string: String): String? {
        val scale = scaleFromPercentStringValue(string, true)
                    ?: return IdeBundle.message("presentation.mode.ide.scale.wrong.number.message")

        return validatePresentationModePercentScale(scale)
      }

      @Nls
      private fun validatePresentationModePercentScale(scale: Float): String? {
        if (scale.percentValue < PRESENTATION_MODE_MIN_SCALE.percentValue ||
            scale.percentValue > PRESENTATION_MODE_MAX_SCALE.percentValue) {
          return IdeBundle.message("presentation.mode.ide.scale.out.of.range.number.message.format",
                                   PRESENTATION_MODE_MIN_SCALE.percentValue,
                                   PRESENTATION_MODE_MAX_SCALE.percentValue)
        }

        return null
      }

      fun scaleFromPercentStringValue(stringValue: String?, isPresentation: Boolean?): Float? {
        var string = stringValue ?: return null
        val scaleOption = isPresentation?.let { scaleOptions(it) } ?: currentScaleOptions
        scaleOption.firstOrNull { string == it.percentStringValue }?.let { return it }

        if (string.last() == '%') {
          string = string.dropLast(1)
        }

        val value = string.toFloatOrNull() ?: return null
        return if ((value.toInt().toFloat() != value) || value <= 0f) null else value / 100
      }

      fun increasedScale(): Float? {
        return scaleWithIndexShift(isNext = true,
                                   scale = UISettingsUtils.getInstance().currentIdeScale,
                                   isPresentation = UISettings.getInstance().presentationMode)
      }

      fun decreasedScale(): Float? {
        return scaleWithIndexShift(isNext = false,
                                   scale = UISettingsUtils.getInstance().currentIdeScale,
                                   isPresentation = UISettings.getInstance().presentationMode)
      }

      private fun scaleWithIndexShift(isNext: Boolean, scale: Float, isPresentation: Boolean): Float? {
        val scaleOptions = scaleOptions(isPresentation)
        val lessOrEqualScaleIndex = getNearestLessOrEqualOptionIndex(scale, scaleOptions)
        val shift = if (isNext) 1 else -1
        val lessOrEqualScale = scaleOptions.getOrNull(lessOrEqualScaleIndex)

        val result: Float? = when {
          isPresentation && (scale <= scaleOptions.first() - SCALING_STEP || scale >= scaleOptions.last() + SCALING_STEP) -> {
            scale + SCALING_STEP * shift
          }
          lessOrEqualScale != null -> {
            if (lessOrEqualScale.percentValue == scale.percentValue || shift > 0) {
              val nextScale = scaleOptions.getOrNull(lessOrEqualScaleIndex + shift)
              if (isPresentation && nextScale == null) scale + SCALING_STEP * shift else nextScale
            }
            else lessOrEqualScale
          }
          lessOrEqualScaleIndex < 0 && shift > 0 -> scaleOptions.first()
          lessOrEqualScaleIndex >= scaleOptions.size && shift < 0 -> scaleOptions.last()
          else -> null
        }

        return result?.takeIf { !isPresentation || validatePresentationModePercentScale(it) == null }
      }

      private fun getNearestLessOrEqualOptionIndex(scale: Float, scaleOptions: List<Float>): Int {
        scaleOptions.forEachIndexed { index, v ->
          if (scale.percentValue == v.percentValue) return index
          else if (scale.percentValue < v.percentValue) return index - 1
        }

        return scaleOptions.size
      }
    }
  }

  companion object {
    fun getInstance(): IdeScaleTransformer = service<IdeScaleTransformer>()
  }
}

private class IdeScalePostStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    IdeScaleTransformer.getInstance().setupLastSetScale()
    IdeScaleIndicatorManager.getInstance(project)
  }
}

private class IdeScaleSettingsListener : UISettingsListener {
  override fun uiSettingsChanged(uiSettings: UISettings) {
    IdeScaleTransformer.getInstance().uiSettingsChanged()
  }
}
