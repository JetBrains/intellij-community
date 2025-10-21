// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options

import com.intellij.application.options.editor.EditorOptionsProvider
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.PowerSaveMode
import com.intellij.ide.ui.UISettings
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
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
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected

class CodeCompletionConfigurable : BoundCompositeConfigurable<UnnamedConfigurable>(
  ApplicationBundle.message("title.code.completion"), "reference.settingsdialog.IDE.editor.code.completion"),
                              EditorOptionsProvider, WithEpDependencies {

  companion object {
    const val ID: String = "editor.preferences.completion"
    private val LOG = Logger.getInstance(CodeCompletionConfigurable::class.java)
  }

  private lateinit var cbMatchCase: JBCheckBox
  private lateinit var rbLettersOnly: JBRadioButton
  private lateinit var rbAllOnly: JBRadioButton

  override fun createConfigurables(): List<UnnamedConfigurable> =
    ConfigurableWrapper.createConfigurables(CodeCompletionConfigurableEP.EP_NAME)

  override fun getId(): String = ID

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
    super.apply()

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
      buttonsGroup {
        row {
          cbMatchCase = checkBox(ApplicationBundle.message("completion.option.match.case"))
            .component
          rbLettersOnly = radioButton(ApplicationBundle.message("completion.option.first.letter.only"))
            .enabledIf(cbMatchCase.selected)
            .component.apply { isSelected = true }
          rbAllOnly = radioButton(ApplicationBundle.message("completion.option.all.letters"))
            .enabledIf(cbMatchCase.selected)
            .component
        }
      }

      val codeCompletion = OptionsApplicabilityFilter.isApplicable(OptionId.AUTOCOMPLETE_ON_BASIC_CODE_COMPLETION)
      val smartTypeCompletion = OptionsApplicabilityFilter.isApplicable(OptionId.COMPLETION_SMART_TYPE)

      if (codeCompletion || smartTypeCompletion) {
        buttonsGroup(ApplicationBundle.message("label.autocomplete.when.only.one.choice")) {
          if (codeCompletion) {
            row {
              checkBox(ApplicationBundle.message("checkbox.autocomplete.basic"))
                .bindSelected(settings::AUTOCOMPLETE_ON_CODE_COMPLETION)
                .gap(RightGap.SMALL)
                .commentRight(KeymapUtil.getFirstKeyboardShortcutText(actionManager.getAction(IdeActions.ACTION_CODE_COMPLETION)))
            }
          }

          if (smartTypeCompletion) {
            row {
              checkBox(ApplicationBundle.message("checkbox.autocomplete.smart.type"))
                .bindSelected(settings::AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION)
                .gap(RightGap.SMALL)
                .commentRight(KeymapUtil.getFirstKeyboardShortcutText(actionManager.getAction(IdeActions.ACTION_SMART_TYPE_COMPLETION)))
            }
          }
        }
      }

      row {
        checkBox(ApplicationBundle.message("completion.option.sort.suggestions.alphabetically"))
          .bindSelected(UISettings.getInstance()::sortLookupElementsLexicographically)
      }

      lateinit var cbAutocompletion: Cell<JBCheckBox>
      row {
        cbAutocompletion = checkBox(ApplicationBundle.message("editbox.auto.complete") +
                                    if (PowerSaveMode.isEnabled()) LangBundle.message("label.not.available.in.power.save.mode") else "")
          .bindSelected(settings::AUTO_POPUP_COMPLETION_LOOKUP)
      }

      indent {
        row {
          checkBox(IdeUICustomization.getInstance().selectAutopopupByCharsText)
            .bindSelected(settings::isSelectAutopopupSuggestionsByChars, settings::setSelectAutopopupSuggestionsByChars)
            .enabledIf(cbAutocompletion.selected)
        }
      }

      row {
        val cbAutopopupJavaDoc = checkBox(ApplicationBundle.message("editbox.autopopup.javadoc.in"))
          .bindSelected(settings::AUTO_POPUP_JAVADOC_INFO)
          .gap(RightGap.SMALL)
        intTextField(CodeInsightSettings.JAVADOC_INFO_DELAY_RANGE.asRange(), 100)
          .bindIntText(settings::JAVADOC_INFO_DELAY)
          .columns(4)
          .enabledIf(cbAutopopupJavaDoc.selected)
          .gap(RightGap.SMALL)
        @Suppress("DialogTitleCapitalization")
        label(ApplicationBundle.message("editbox.ms"))
      }

      if (OptionsApplicabilityFilter.isApplicable(OptionId.INSERT_PARENTHESES_AUTOMATICALLY)) {
        row {
          checkBox(ApplicationBundle.message("completion.option.insert.parentheses"))
            .bindSelected(EditorSettingsExternalizable.getInstance()::isInsertParenthesesAutomatically,
                          EditorSettingsExternalizable.getInstance()::setInsertParenthesesAutomatically)
        }
      }

      addOptions()

      group(ApplicationBundle.message("title.parameter.info")) {
        if (OptionsApplicabilityFilter.isApplicable(OptionId.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION)) {
          row {
            checkBox(ApplicationBundle.message("editbox.complete.with.parameters"))
              .bindSelected(settings::SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION)
          }
        }

        row {
          val cbParameterInfoPopup = checkBox(ApplicationBundle.message("editbox.autopopup.in"))
            .bindSelected(settings::AUTO_POPUP_PARAMETER_INFO)
            .gap(RightGap.SMALL)
          intTextField(CodeInsightSettings.PARAMETER_INFO_DELAY_RANGE.asRange(), 100)
            .bindIntText(settings::PARAMETER_INFO_DELAY)
            .columns(4)
            .enabledIf(cbParameterInfoPopup.selected)
            .gap(RightGap.SMALL)
          @Suppress("DialogTitleCapitalization")
          label(ApplicationBundle.message("editbox.ms"))
        }

        row {
          checkBox(ApplicationBundle.message("checkbox.show.full.signatures"))
            .bindSelected(settings::SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO)
        }
      }

      addSections()
    }
  }

  private fun Panel.addOptions() {
    configurables.filter { it !is CodeCompletionOptionsCustomSection }
      .forEach { appendDslConfigurable(it) }
  }

  private fun Panel.addSections() {
    configurables.filterIsInstance<CodeCompletionOptionsCustomSection>()
      .sortedWith(Comparator.comparing { c ->
        (c as? Configurable)?.displayName ?: ""
      })
      .forEach { appendDslConfigurable(it) }
  }
}