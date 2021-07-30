// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options

import com.intellij.application.options.editor.EditorOptionsProvider
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.PowerSaveMode
import com.intellij.ide.ui.UISettings.Companion.instance
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.BoundCompositeConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.Configurable.WithEpDependencies
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.IdeUICustomization
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI

class CodeCompletionOptions : BoundCompositeConfigurable<UnnamedConfigurable>(
  ApplicationBundle.message("title.code.completion"), "reference.settingsdialog.IDE.editor.code.completion"),
                              EditorOptionsProvider, WithEpDependencies {

  companion object {
    const val ID = "editor.preferences.completion"
    private val LOG = Logger.getInstance(CodeCompletionOptions::class.java)
  }

  private lateinit var cbMatchCase: JBCheckBox
  private lateinit var rbLettersOnly: JBRadioButton
  private lateinit var rbAllOnly: JBRadioButton

  override fun createConfigurables(): List<UnnamedConfigurable> =
    ConfigurableWrapper.createConfigurables(CodeCompletionConfigurableEP.EP_NAME)

  override fun getId() = ID

  override fun getDependencies(): Collection<BaseExtensionPointName<*>> =
    listOf(CodeCompletionConfigurableEP.EP_NAME)

  var caseSensitive: Int
    get() =
      if (cbMatchCase.isSelected) {
        if (rbAllOnly.isSelected) CodeInsightSettings.ALL else CodeInsightSettings.FIRST_LETTER
      }
      else {
        CodeInsightSettings.NONE
      }
    set(value) =
      when (value) {
        CodeInsightSettings.ALL -> {
          cbMatchCase.isSelected = true
          rbAllOnly.setSelected(true)
        }

        CodeInsightSettings.NONE -> {
          cbMatchCase.isSelected = false
        }

        CodeInsightSettings.FIRST_LETTER -> {
          cbMatchCase.isSelected = true
          rbLettersOnly.setSelected(true)
        }

        else -> LOG.warn("Unsupported caseSensitive: $value")
      }


  override fun reset() {
    super<BoundCompositeConfigurable>.reset()

    val codeInsightSettings = CodeInsightSettings.getInstance()

    caseSensitive = codeInsightSettings.completionCaseSensitive
  }

  override fun apply() {
    super<BoundCompositeConfigurable>.apply()

    val codeInsightSettings = CodeInsightSettings.getInstance()
    codeInsightSettings.completionCaseSensitive = caseSensitive
    for (project in ProjectManager.getInstance().openProjects) {
      DaemonCodeAnalyzer.getInstance(project).settingsChanged()
    }
  }

  override fun isModified(): Boolean {
    val result = super<BoundCompositeConfigurable>.isModified()
    val codeInsightSettings = CodeInsightSettings.getInstance()

    return result || caseSensitive != codeInsightSettings.completionCaseSensitive
  }

  override fun createPanel(): DialogPanel {
    val actionManager = ActionManager.getInstance()
    val settings = CodeInsightSettings.getInstance()

    return panel {
      buttonGroup {
        fullRow {
          cbMatchCase = checkBox(ApplicationBundle.message("completion.option.match.case")).component
          rbLettersOnly = radioButton(ApplicationBundle.message("completion.option.first.letter.only"))
            .enableIf(cbMatchCase.selected)
            .component.apply { isSelected = true }
          rbAllOnly = radioButton(ApplicationBundle.message("completion.option.all.letters"))
            .enableIf(cbMatchCase.selected)
            .component
        }
      }

      val codeCompletion = OptionsApplicabilityFilter.isApplicable(OptionId.AUTOCOMPLETE_ON_BASIC_CODE_COMPLETION)
      val smartTypeCompletion = OptionsApplicabilityFilter.isApplicable(OptionId.COMPLETION_SMART_TYPE)

      if (codeCompletion || smartTypeCompletion) {
        row {
          label(ApplicationBundle.message("label.autocomplete.when.only.one.choice"))

          if (codeCompletion) {
            fullRow {
              checkBox(ApplicationBundle.message("checkbox.autocomplete.basic"), prop = settings::AUTOCOMPLETE_ON_CODE_COMPLETION)
              label(KeymapUtil.getFirstKeyboardShortcutText(actionManager.getAction(IdeActions.ACTION_CODE_COMPLETION)))
                .component.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
            }
          }

          if (smartTypeCompletion) {
            fullRow {
              checkBox(ApplicationBundle.message("checkbox.autocomplete.smart.type"),
                       prop = settings::AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION)
              label(KeymapUtil.getFirstKeyboardShortcutText(actionManager.getAction(IdeActions.ACTION_SMART_TYPE_COMPLETION)))
                .component.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
            }
          }
        }
      }

      row {
        checkBox(ApplicationBundle.message("completion.option.sort.suggestions.alphabetically"),
                 prop = instance::sortLookupElementsLexicographically)
      }

      lateinit var cbAutocompletion: CellBuilder<JBCheckBox>

      row {
        cbAutocompletion = checkBox(ApplicationBundle.message("editbox.auto.complete") +
                                    if (PowerSaveMode.isEnabled()) LangBundle.message("label.not.available.in.power.save.mode") else "",
                                    prop = settings::AUTO_POPUP_COMPLETION_LOOKUP)
      }

      row {
        row {
          checkBox(IdeUICustomization.getInstance().selectAutopopupByCharsText,
                   getter = settings::isSelectAutopopupSuggestionsByChars,
                   setter = settings::setSelectAutopopupSuggestionsByChars)
            .enableIf(cbAutocompletion.selected)
        }
      }

      fullRow {
        val cbAutopopupJavaDoc = checkBox(ApplicationBundle.message("editbox.autopopup.javadoc.in"),
                                          prop = settings::AUTO_POPUP_JAVADOC_INFO)
        intTextField(prop = settings::JAVADOC_INFO_DELAY,
                     columns = 4,
                     range = CodeInsightSettings.JAVADOC_INFO_DELAY_RANGE.asRange(),
                     step = 100)
          .enableIf(cbAutopopupJavaDoc.selected)
        label(ApplicationBundle.message("editbox.ms"))
      }

      addOptions()

      titledRow(ApplicationBundle.message("title.parameter.info")) {
        if (OptionsApplicabilityFilter.isApplicable(OptionId.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION)) {
          row {
            checkBox(ApplicationBundle.message("editbox.complete.with.parameters"),
                     prop = settings::SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION)
          }
        }

        fullRow {
          val cbParameterInfoPopup = checkBox(ApplicationBundle.message("editbox.autopopup.in"), prop = settings::AUTO_POPUP_PARAMETER_INFO)
          intTextField(prop = settings::PARAMETER_INFO_DELAY,
                       columns = 4,
                       range = CodeInsightSettings.PARAMETER_INFO_DELAY_RANGE.asRange(),
                       step = 100)
            .enableIf(cbParameterInfoPopup.selected)
          label(ApplicationBundle.message("editbox.ms"))
        }

        row {
          checkBox(ApplicationBundle.message("checkbox.show.full.signatures"), prop = settings::SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO)
        }
      }

      addSections()
    }
  }

  private fun RowBuilder.addOptions() {
    configurables.filter { !(it is CodeCompletionOptionsCustomSection) }
      .forEach { appendDslConfigurableRow(it) }
  }

  private fun RowBuilder.addSections() {
    configurables.filterIsInstance<CodeCompletionOptionsCustomSection>()
      .sortedWith(Comparator.comparing { c ->
        (c as? Configurable)?.displayName ?: ""
      })
      .forEach { appendDslConfigurableRow(it) }
  }
}