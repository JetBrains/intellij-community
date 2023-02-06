// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ui.ideScaleIndicator.IdeScaleIndicatorManager
import com.intellij.ide.ui.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.impl.zoomIndicator.ZoomIndicatorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.layout.ValidationInfoBuilder
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.math.abs

@Service(Service.Level.APP)
class IdeScaleTransformer : UISettingsListener, Disposable {
  private var lastSetScale: Float = UISettingsUtils.currentIdeScale

  init {
    Disposer.register(ApplicationManager.getApplication(), this)
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(UISettingsListener.TOPIC, this)
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    if (lastSetScale.percentValue != UISettingsUtils.currentIdeScale.percentValue) {
      scale()
    }
  }

  private fun scale() {
    lastSetScale = UISettingsUtils.currentIdeScale
    tweakEditorFont()
    notifyAllAndUpdateUI()
  }

  private fun tweakEditorFont() {
    for (editor in EditorFactory.getInstance().allEditors) {
      if (editor is EditorEx) {
        editor.putUserData(ZoomIndicatorManager.SUPPRESS_ZOOM_INDICATOR_ONCE, true)
        editor.setFontSize(UISettingsUtils.scaledEditorFontSize)
      }
    }
  }

  private fun notifyAllAndUpdateUI() {
    LafManager.getInstance().updateUI()
    EditorUtil.reinitSettings()
  }

  @Internal
  class Settings {
    companion object {
      private const val SCALING_STEP = 0.1f
      private const val PRESENTATION_MODE_MIN_SCALE = 0.4f
      private const val PRESENTATION_MODE_MAX_SCALE = 4f
      val regularScaleOptions = listOf(1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)
      val regularScaleComboboxModel = CollectionComboBoxModel(regularScaleOptions.map { it.percentStringValue },
                                                              UISettings.getInstance().ideScale.percentStringValue)
      val presentationModeScaleComboboxModel = CollectionComboBoxModel(regularScaleOptions.map { it.percentStringValue },
                                                                       UISettings.getInstance().presentationModeIdeScale.percentStringValue)

      fun validatePercentScaleInput(builder: ValidationInfoBuilder, comboBox: ComboBox<String>): ValidationInfo? {
        val scale = scaleFromPercentStringValue(comboBox.item)
                    ?: return builder.error(IdeBundle.message("presentation.mode.ide.scale.wrong.number.message"))

        if (scale.percentValue < PRESENTATION_MODE_MIN_SCALE.percentValue
            || scale.percentValue > PRESENTATION_MODE_MAX_SCALE.percentValue) {
          return builder.error(IdeBundle.message("presentation.mode.ide.scale.out.of.range.number.message.format",
                                                 PRESENTATION_MODE_MIN_SCALE.percentValue,
                                                 PRESENTATION_MODE_MAX_SCALE.percentValue))
        }

        return null
      }

      fun scaleFromPercentStringValue(stringValue: String?): Float? {
        var string = stringValue ?: return null
        regularScaleOptions.firstOrNull { string == it.percentStringValue }?.let { return it }

        if (string.last() == '%') string = string.dropLast(1)
        val value = string.toFloatOrNull() ?: return null
        if (value.toInt().toFloat() != value) return null
        if (value <= 0f) return null
        return value / 100
      }

      fun increasedScale() = scaleWithIndexShift(1,
                                                 UISettingsUtils.currentIdeScale,
                                                 UISettings.getInstance().presentationMode)

      fun decreasedScale() = scaleWithIndexShift(-1,
                                                 UISettingsUtils.currentIdeScale,
                                                 UISettings.getInstance().presentationMode)

      private fun scaleWithIndexShift(indexShift: Int, scale: Float, isPresentation: Boolean): Float? {
        return if (isPresentation){
          (scale + indexShift * SCALING_STEP).takeIf { it > 0 }
        }
        else {
          (getNearestOptionIndex(scale) + indexShift).let { nextIndex ->
            if (nextIndex < 0 || nextIndex >= regularScaleOptions.size) null
            else regularScaleOptions[nextIndex]
          }
        }
      }

      private fun getNearestOptionIndex(scale: Float): Int {
        var candidateIndex = 0
        var diff = abs(regularScaleOptions[candidateIndex] - scale)

        regularScaleOptions.forEachIndexed { index, v ->
          val newDiff = abs(v - scale)
          if (newDiff < diff) {
            diff = newDiff
            candidateIndex = index
          }
        }

        return candidateIndex
      }
    }
  }

  companion object {
    @JvmStatic
    val instance: IdeScaleTransformer get() = service<IdeScaleTransformer>()

    @JvmStatic
    fun setup() {
      instance
    }
  }

  override fun dispose() {}
}

class IdeScalePostStartupActivity : ProjectPostStartupActivity {
  override suspend fun execute(project: Project) {
    IdeScaleTransformer.setup()
    IdeScaleIndicatorManager.setup(project)
  }
}
