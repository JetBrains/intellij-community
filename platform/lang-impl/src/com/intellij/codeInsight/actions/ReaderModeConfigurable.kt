// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions

import com.intellij.application.options.colors.ReaderModeStatsCollector
import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.ide.DataManager
import com.intellij.lang.LangBundle
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.*
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.UIUtil
import javax.swing.event.HyperlinkListener

internal class ReaderModeConfigurable(private val project: Project) : BoundSearchableConfigurable(LangBundle.message("configurable.reader.mode"), "READER_MODE_HELP") {
  private val settings get() = ReaderModeSettings.instance(project)

  private val cdInlays get() = CheckboxDescriptor(LangBundle.message("checkbox.inlays"), settings::showInlaysHints)
  private val cdRenderedDocs get() = CheckboxDescriptor(LangBundle.message("checkbox.rendered.docs"), settings::showRenderedDocs)
  private val cdLigatures get() = CheckboxDescriptor(LangBundle.message("checkbox.ligatures"), settings::showLigatures)
  private val cdLineSpacing get() = CheckboxDescriptor(LangBundle.message("checkbox.line.spacing"), settings::increaseLineSpacing)
  private val cdWarnings get() = CheckboxDescriptor(LangBundle.message("checkbox.hide.warnings"), settings::showWarnings)
  private val cdEnabled get() = CheckboxDescriptor(LangBundle.message("checkbox.reader.mode.toggle"), settings::enabled)

  override fun createPanel(): DialogPanel {
    return panel {
      lateinit var enabled: CellBuilder<JBCheckBox>
      row {
        enabled = checkBox(cdEnabled).comment("")
          .comment(LangBundle.message("checkbox.reader.mode.toggle.comment"))

        row(LangBundle.message("titled.border.reader.mode.settings")) {
          row {
            val renderedDocs = EditorSettingsExternalizable.getInstance().isDocCommentRenderingEnabled
            checkBox(cdRenderedDocs).enableIf(enabled.selected).enabled(!renderedDocs)

            if (renderedDocs) {
              component(createGoToGlobalSettings("editor.preferences.appearance")).withLargeLeftGap()
            }
          }
          row {
            checkBox(cdWarnings).enableIf(enabled.selected)
          }
          row {
            val useLigatures = EditorColorsManager.getInstance().globalScheme.fontPreferences.useLigatures()
            checkBox(cdLigatures).enableIf(enabled.selected).enabled(!useLigatures)

            if (useLigatures) {
              component(createGoToGlobalSettings("editor.preferences.fonts.default")).withLargeLeftGap()
            }
          }
          row {
            checkBox(cdLineSpacing)
            commentNoWrap(LangBundle.message("checkbox.reader.mode.line.height.comment")).withLargeLeftGap()
          }.enableIf(enabled.selected)
          row {
            checkBox(cdInlays).enableIf(enabled.selected).visible(PlatformUtils.isIdeaCommunity() || PlatformUtils.isIdeaEducational() || PlatformUtils.isIdeaUltimate())
          }
        }.enableIf(enabled.selected)
      }
    }
  }

  override fun apply() {
    super.apply()
    project.messageBus.syncPublisher(ReaderModeSettingsListener.TOPIC).modeChanged(project)
  }

  companion object {
    private fun createGoToGlobalSettings(configurableID: String) = HyperlinkLabel().apply {
      setTextWithHyperlink(LangBundle.message("checkbox.reader.mode.go.to.global.settings"))
      font = UIUtil.getFont(UIUtil.FontSize.SMALL, font)
      foreground = UIUtil.getLabelFontColor(UIUtil.FontColor.BRIGHTER)
      addHyperlinkListener(HyperlinkListener {
        DataManager.getInstance().dataContextFromFocusAsync.onSuccess { context ->
          context?.let { dataContext ->
            Settings.KEY.getData(dataContext)?.let { settings ->
              settings.select(settings.find(configurableID))
              ReaderModeStatsCollector.logSeeAlsoNavigation()
            }
          }
        }
      })
    }

  }
}