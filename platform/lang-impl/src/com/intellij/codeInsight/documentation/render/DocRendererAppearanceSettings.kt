// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DocRendererAppearanceSettings")
@file:ApiStatus.Internal

package com.intellij.codeInsight.documentation.render

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.documentation.getDefaultFontSize
import com.intellij.codeInsight.documentation.getDocumentationFontSize
import com.intellij.codeInsight.documentation.render.DocRenderItemUpdater.updateRenderers
import com.intellij.codeInsight.documentation.setDocumentationFontSize
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.options.FontSize
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.MouseInfo
import javax.swing.JSlider

internal const val MIN_WIDTH: Int = 350
private const val DEFAULT_MAX_WIDTH: Int = 680
private const val MAX_WIDTH_KEY: String = "doc.renderer.max.width"

internal class DocRendererAppearanceSettingsAction : DumbAwareAction(CodeInsightBundle.messagePointer("javadoc.adjust.appearance")) {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return

    val fontRange = FontSize.entries
    val currentFont = getDocumentationFontSize()

    var slider: JSlider? = null
    var maxWidthField: JBTextField? = null

    val panel = panel {
      panel {
        row(CodeInsightBundle.message("javadoc.settings.font.size")) {
          slider = slider(0, fontRange.size - 1, 1, 0)
            .onChanged {
              setDocumentationFontSize(fontRange[it.value])
              updateRenderers(editor, true)
            }
            .applyToComponent { value = fontRange.indexOf(currentFont).coerceAtLeast(0) }
            .component
        }
        row(CodeInsightBundle.message("javadoc.settings.max.width")) {
          maxWidthField = intTextField(IntRange(MIN_WIDTH, Int.MAX_VALUE))
            .text(getMaxWidth().toString())
            .onChanged {
              setMaxWidth(it.text.toIntOrNull() ?: 0)
              updateRenderers(editor, true)
            }
            .gap(RightGap.SMALL)
            .component
          @NlsSafe val pixelUnit = "px"
          label(pixelUnit)
        }
        row {
          link(CodeInsightBundle.message("javadoc.settings.reset")) {
            slider?.value = fontRange.indexOf(getDefaultFontSize()).coerceAtLeast(0)
            maxWidthField?.text = DEFAULT_MAX_WIDTH.toString()
          }
        }
      }
    }
      .withBorder(JBUI.Borders.empty(UIUtil.PANEL_REGULAR_INSETS))

    val anchor = MouseInfo.getPointerInfo().location.apply {
      translate(-panel.preferredSize.width / 2, -panel.preferredSize.height / 2)
    }

    JBPopupFactory.getInstance()
      .createComponentPopupBuilder(panel, slider)
      .setBelongsToGlobalPopupStack(true)
      .setFocusable(true)
      .setRequestFocus(true)
      .createPopup()
      .show(RelativePoint(anchor).getPointOn(editor.contentComponent))
  }
}

internal fun getMaxWidth(): Int {
  val stored = PropertiesComponent.getInstance().getInt(MAX_WIDTH_KEY, DEFAULT_MAX_WIDTH)
  return stored.coerceAtLeast(MIN_WIDTH)
}

internal fun setMaxWidth(value: Int) {
  val v = value.coerceAtLeast(MIN_WIDTH)
  PropertiesComponent.getInstance().setValue(MAX_WIDTH_KEY, v, DEFAULT_MAX_WIDTH)
}
