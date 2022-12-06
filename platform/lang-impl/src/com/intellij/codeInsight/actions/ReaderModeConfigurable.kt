// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.application.options.colors.ReaderModeStatsCollector
import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.application.options.schemes.SchemesCombo
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.lang.LangBundle
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.psi.codeStyle.CodeStyleScheme
import com.intellij.psi.codeStyle.CodeStyleSchemes
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.util.PlatformUtils

internal class ReaderModeConfigurable(private val project: Project) : BoundSearchableConfigurable(
  LangBundle.message("configurable.reader.mode"), "editor.reader.mode") {
  private val settings get() = ReaderModeSettings.getInstance(project)

  private val codeStyleSchemesModel = CodeStyleSchemesModel(project)
  private val comboVisualFormattingLayerScheme
    get() = object : SchemesCombo<CodeStyleScheme>() {
      override fun supportsProjectSchemes(): Boolean = true

      override fun isProjectScheme(scheme: CodeStyleScheme): Boolean = codeStyleSchemesModel.isProjectScheme(scheme)

      override fun getSchemeAttributes(scheme: CodeStyleScheme?): SimpleTextAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES

    }
  private val cdVisualFormattingLayer
    get() = CheckboxDescriptor(IdeBundle.message("checkbox.show.visual.formatting.layer"), settings::useVisualFormattingLayer)
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
          lateinit var visualFormattingLayer: Cell<JBCheckBox>
          row {
            visualFormattingLayer = checkBox(cdVisualFormattingLayer)
          }
          indent {
            row(IdeBundle.message("combobox.label.visual.formatting.layer.scheme")) {
              val combo = comboVisualFormattingLayerScheme
              cell(combo)
                .onReset {
                  combo.resetSchemes(codeStyleSchemesModel.allSortedSchemes)
                  combo.selectScheme(getVisualFormattingLayerScheme())
                }
                .onIsModified {
                  val selected = combo.selectedScheme
                  selected == null ||
                  selected.name != settings.visualFormattingLayerScheme.name ||
                  codeStyleSchemesModel.isProjectScheme(selected)!= settings.visualFormattingLayerScheme.isProjectLevel
                }
                .onApply {
                  settings.visualFormattingLayerScheme = ReaderModeSettings.State.SchemeState().apply {
                    val selected = combo.selectedScheme
                    if (selected != null) {
                      name = selected.name
                      isProjectLevel = codeStyleSchemesModel.isProjectScheme(selected)
                    }
                  }
                }
            }.enabledIf(visualFormattingLayer.selected)
          }
        }
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

  private fun getVisualFormattingLayerScheme(): CodeStyleScheme = codeStyleSchemesModel.schemes.find {
      settings.visualFormattingLayerScheme.name == it.name &&
      settings.visualFormattingLayerScheme.isProjectLevel == codeStyleSchemesModel.isProjectScheme(it)
    } ?: CodeStyleSchemes.getInstance().defaultScheme
}