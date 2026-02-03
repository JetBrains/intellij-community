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
import com.intellij.psi.codeStyle.CodeStyleScheme
import com.intellij.psi.codeStyle.CodeStyleSchemes
import com.intellij.psi.codeStyle.CodeStyleSettingsChangeEvent
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.util.PlatformUtils

internal class ReaderModeConfigurable(private val project: Project) : BoundSearchableConfigurable(
  LangBundle.message("configurable.reader.mode"), "settings.reader.mode", "editor.reader.mode") {
  private val settings get() = ReaderModeSettings.getInstance(project)

  private val cdVisualFormatting
    get() = CheckboxDescriptor(LangBundle.message("checkbox.reader.mode.show.visual.formatting.layer"), settings::enableVisualFormatting)
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
              .commentRight(LangBundle.message("checkbox.reader.mode.line.height.comment"))
          }.enabledIf(enabled.selected)
          row {
            checkBox(cdInlays).visible(PlatformUtils.isIntelliJ())
          }
        }
        lateinit var visualFormattingEnabled: Cell<JBCheckBox>
        row {
          visualFormattingEnabled = checkBox(cdVisualFormatting)
        }
        buttonsGroup(indent = true) {
          row {
            radioButton("", true).apply {
              onReset {
                component.text = (LangBundle.message("radio.reader.mode.use.active.scheme.visual.formatting",
                                                     getCurrentCodeStyleSchemeName(project)))
              }
            }
            // ensure label is updated when active code style scheme changes
            val listener = { e: CodeStyleSettingsChangeEvent -> if (e.virtualFile == null) reset() }
            CodeStyleSettingsManager.getInstance(project).subscribe(listener, disposable!!)
          }
          row {
            val radio = radioButton(LangBundle.message("radio.reader.mode.choose.visual.formatting.scheme"), false)
              .gap(RightGap.SMALL)
            val combo = VisualFormattingSchemesCombo(project)
            cell(combo)
              .onReset { combo.onReset(settings) }
              .onIsModified { combo.onIsModified(settings) }
              .onApply { combo.onApply(settings) }
              .enabledIf(radio.selected)
          }
        }.bind(settings::useActiveSchemeForVisualFormatting)
          .enabledIf(visualFormattingEnabled.selected)
      }.enabledIf(enabled.selected)
    }
  }

  override fun apply() {
    super.apply()
    project.messageBus.syncPublisher(ReaderModeSettingsListener.TOPIC).modeChanged(project)
  }

  private fun goToGlobalSettings(configurableID: String) {
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

private fun getCurrentCodeStyleSchemeName(project: Project) =
  CodeStyleSettingsManager.getInstance(project).run {
    if (USE_PER_PROJECT_SETTINGS) CodeStyleScheme.PROJECT_SCHEME_NAME
    else CodeStyleSchemes.getInstance().findPreferredScheme(PREFERRED_PROJECT_CODE_STYLE).displayName
  }