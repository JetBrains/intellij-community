// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.util.PlatformUtils

internal class ReaderModeConfigurable(private val project: Project) : BoundSearchableConfigurable(
  LangBundle.message("configurable.reader.mode"), "READER_MODE_HELP") {
  private val settings get() = ReaderModeSettings.getInstance(project)

  private val cdInlays get() = CheckboxDescriptor(LangBundle.message("checkbox.inlays"), settings::showInlaysHints)
  private val cdRenderedDocs get() = CheckboxDescriptor(LangBundle.message("checkbox.rendered.docs"), settings::showRenderedDocs)
  private val cdLigatures get() = CheckboxDescriptor(LangBundle.message("checkbox.ligatures"), settings::showLigatures)
  private val cdLineSpacing get() = CheckboxDescriptor(LangBundle.message("checkbox.line.spacing"), settings::increaseLineSpacing)
  private val cdWarnings get() = CheckboxDescriptor(LangBundle.message("checkbox.hide.warnings"), settings::showWarnings)
  private val cdEnabled get() = CheckboxDescriptor(LangBundle.message("checkbox.reader.mode.toggle"), settings::enabled)

  override fun createPanel(): DialogPanel {
    return panel {
      lateinit var enabled: Cell<JBCheckBox>
      row {
        enabled = checkBox(cdEnabled).comment("")
          .comment(LangBundle.message("checkbox.reader.mode.toggle.comment"))
      }
      indent {
        buttonsGroup(LangBundle.message("titled.border.reader.mode.settings")) {
          row {
            val renderedDocs = EditorSettingsExternalizable.getInstance().isDocCommentRenderingEnabled
            checkBox(cdRenderedDocs).enabled(!renderedDocs)

            if (renderedDocs) {
              comment(LangBundle.message("checkbox.reader.mode.go.to.global.settings")) {
                goToGlobalSettings("editor.preferences.appearance")
              }
            }
          }
          row {
            checkBox(cdWarnings)
          }
          row {
            val useLigatures = EditorColorsManager.getInstance().globalScheme.fontPreferences.useLigatures()
            checkBox(cdLigatures).enabled(!useLigatures)

            if (useLigatures) {
              comment(LangBundle.message("checkbox.reader.mode.go.to.global.settings")) {
                goToGlobalSettings("editor.preferences.fonts.default")
              }
            }
          }
          row {
            checkBox(cdLineSpacing)
            comment(LangBundle.message("checkbox.reader.mode.line.height.comment"))
          }.enabledIf(enabled.selected)
          row {
            checkBox(cdInlays).visible(PlatformUtils.isIntelliJ())
          }
        }
      }.enabledIf(enabled.selected)
    }
  }

  override fun apply() {
    super.apply()
    project.messageBus.syncPublisher(ReaderModeSettingsListener.TOPIC).modeChanged(project)
  }

  fun goToGlobalSettings(configurableID: String) {
    DataManager.getInstance().dataContextFromFocusAsync.onSuccess { context ->
      context?.let { dataContext ->
        Settings.KEY.getData(dataContext)?.let { settings ->
          settings.select(settings.find(configurableID))
          ReaderModeStatsCollector.logSeeAlsoNavigation()
        }
      }
    }
  }
}