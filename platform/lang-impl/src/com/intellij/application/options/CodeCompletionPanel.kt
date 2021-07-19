// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.DataManager
import com.intellij.ide.PowerSaveMode
import com.intellij.ide.ui.UISettings.Companion.instance
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.ui.IdeUICustomization
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.*
import javax.swing.JComponent
import javax.swing.JTextField
import kotlin.math.max

class CodeCompletionPanel {

  companion object {
    private val LOG = Logger.getInstance(CodeCompletionPanel::class.java)
  }

  private lateinit var panel: DialogPanel
  private lateinit var cbMatchCase: JBCheckBox
  private lateinit var rbLettersOnly: JBRadioButton
  private lateinit var rbAllOnly: JBRadioButton
  private var cbCodeCompletion: JBCheckBox? = null
  private var cbSmartTypeCompletion: JBCheckBox? = null
  private lateinit var cbSorting: JBCheckBox
  private lateinit var cbAutocompletion: JBCheckBox
  private lateinit var cbSelectByChars: JBCheckBox
  private lateinit var cbAutopopupJavaDoc: JBCheckBox
  private lateinit var tfAutopopupJavaDoc: JBTextField
  private var cbCompleteFunctionWithParameters: JBCheckBox? = null
  private lateinit var cbParameterInfoPopup: JBCheckBox
  private lateinit var tfParameterInfoDelay: JBTextField
  private lateinit var cbShowFullParameterSignatures: JBCheckBox

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
          rbLettersOnly.setSelected(false)
        }

        else -> LOG.warn("Unsupported caseSensitive: $value")
      }


  fun reset() {
    val codeInsightSettings = CodeInsightSettings.getInstance()

    caseSensitive = codeInsightSettings.completionCaseSensitive
    cbCodeCompletion?.isSelected = codeInsightSettings.AUTOCOMPLETE_ON_CODE_COMPLETION
    cbSmartTypeCompletion?.isSelected = codeInsightSettings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION
    cbSorting.isSelected = instance.sortLookupElementsLexicographically
    cbAutocompletion.isSelected = codeInsightSettings.AUTO_POPUP_COMPLETION_LOOKUP
    cbSelectByChars.isSelected = codeInsightSettings.isSelectAutopopupSuggestionsByChars
    cbAutopopupJavaDoc.isSelected = codeInsightSettings.AUTO_POPUP_JAVADOC_INFO
    tfAutopopupJavaDoc.text = codeInsightSettings.JAVADOC_INFO_DELAY.toString()
    cbCompleteFunctionWithParameters?.isSelected = codeInsightSettings.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION
    cbParameterInfoPopup.isSelected = codeInsightSettings.AUTO_POPUP_PARAMETER_INFO
    tfParameterInfoDelay.text = codeInsightSettings.PARAMETER_INFO_DELAY.toString()
    cbShowFullParameterSignatures.isSelected = codeInsightSettings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO
  }

  fun apply() {
    val codeInsightSettings = CodeInsightSettings.getInstance()

    codeInsightSettings.completionCaseSensitive = caseSensitive
    cbCodeCompletion?.let { codeInsightSettings.AUTOCOMPLETE_ON_CODE_COMPLETION = it.isSelected }
    cbSmartTypeCompletion?.let { codeInsightSettings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = it.isSelected }
    instance.sortLookupElementsLexicographically = cbSorting.isSelected
    codeInsightSettings.AUTO_POPUP_COMPLETION_LOOKUP = cbAutocompletion.isSelected
    codeInsightSettings.isSelectAutopopupSuggestionsByChars = cbSelectByChars.isSelected
    codeInsightSettings.AUTO_POPUP_JAVADOC_INFO = cbAutopopupJavaDoc.isSelected
    codeInsightSettings.JAVADOC_INFO_DELAY = getIntegerValue(tfAutopopupJavaDoc.text)
    cbCompleteFunctionWithParameters?.let { codeInsightSettings.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION = it.isSelected }
    codeInsightSettings.AUTO_POPUP_PARAMETER_INFO = cbParameterInfoPopup.isSelected
    codeInsightSettings.PARAMETER_INFO_DELAY = getIntegerValue(tfParameterInfoDelay.text)
    codeInsightSettings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO = cbShowFullParameterSignatures.isSelected

    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(panel))
    if (project != null) {
      DaemonCodeAnalyzer.getInstance(project).settingsChanged()
    }
  }

  fun isModified(): Boolean {
    val codeInsightSettings = CodeInsightSettings.getInstance()

    return (caseSensitive != codeInsightSettings.completionCaseSensitive) or
      isModified(cbCodeCompletion, codeInsightSettings.AUTOCOMPLETE_ON_CODE_COMPLETION) or
      isModified(cbSmartTypeCompletion, codeInsightSettings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION) or
      isModified(cbSorting, instance.sortLookupElementsLexicographically) or
      isModified(cbAutocompletion, codeInsightSettings.AUTO_POPUP_COMPLETION_LOOKUP) or
      isModified(cbSelectByChars, codeInsightSettings.isSelectAutopopupSuggestionsByChars) or
      isModified(cbAutopopupJavaDoc, codeInsightSettings.AUTO_POPUP_JAVADOC_INFO) or
      isModified(tfAutopopupJavaDoc, codeInsightSettings.JAVADOC_INFO_DELAY) or
      isModified(cbCompleteFunctionWithParameters, codeInsightSettings.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION) or
      isModified(cbParameterInfoPopup, codeInsightSettings.AUTO_POPUP_PARAMETER_INFO) or
      isModified(tfParameterInfoDelay, codeInsightSettings.PARAMETER_INFO_DELAY) or
      isModified(cbShowFullParameterSignatures, codeInsightSettings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO)
  }

  fun createPanel(optionAddons: List<UnnamedConfigurable>, sectionAddons: List<UnnamedConfigurable>): DialogPanel {
    val actionManager = ActionManager.getInstance()
    panel = panel {
      buttonGroup {
        fullRow {
          val cbMatchCaseCell = checkBox(ApplicationBundle.message("completion.option.match.case"))
          cbMatchCase = cbMatchCaseCell.component

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
              cbCodeCompletion = checkBox(ApplicationBundle.message("checkbox.autocomplete.basic")).component
              label(KeymapUtil.getFirstKeyboardShortcutText(actionManager.getAction(IdeActions.ACTION_CODE_COMPLETION)))
                .component.foreground = JBColor.GRAY
            }
          }

          if (smartTypeCompletion) {
            fullRow {
              cbSmartTypeCompletion = checkBox(ApplicationBundle.message("checkbox.autocomplete.smart.type")).component
              label(KeymapUtil.getFirstKeyboardShortcutText(actionManager.getAction(IdeActions.ACTION_SMART_TYPE_COMPLETION)))
                .component.foreground = JBColor.GRAY
            }
          }
        }
      }

      row {
        cbSorting = checkBox(ApplicationBundle.message("completion.option.sort.suggestions.alphabetically")).component
      }

      row {
        cbAutocompletion = checkBox(ApplicationBundle.message("editbox.auto.complete") +
                                    if (PowerSaveMode.isEnabled()) LangBundle.message("label.not.available.in.power.save.mode")
                                    else "")
          .component
      }

      row {
        row {
          cbSelectByChars = checkBox(IdeUICustomization.getInstance().selectAutopopupByCharsText)
            .enableIf(cbAutocompletion.selected)
            .component
        }
      }

      fullRow {
        cbAutopopupJavaDoc = checkBox(ApplicationBundle.message("editbox.autopopup.javadoc.in")).component
        tfAutopopupJavaDoc = intTextField(columns = 4, range = CodeInsightSettings.JAVADOC_INFO_DELAY_RANGE.asRange(), step = 100)
          .enableIf(cbAutopopupJavaDoc.selected)
          .component
        label(ApplicationBundle.message("editbox.ms"))
      }

      addExtensions(optionAddons)

      titledRow(ApplicationBundle.message("title.parameter.info")) {
        if (OptionsApplicabilityFilter.isApplicable(OptionId.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION)) {
          row {
            cbCompleteFunctionWithParameters = checkBox(ApplicationBundle.message("editbox.complete.with.parameters")).component
          }
        }

        fullRow {
          cbParameterInfoPopup = checkBox(ApplicationBundle.message("editbox.autopopup.in")).component
          tfParameterInfoDelay = intTextField(columns = 4, range = CodeInsightSettings.PARAMETER_INFO_DELAY_RANGE.asRange(), step = 100)
            .enableIf(cbParameterInfoPopup.selected)
            .component
          label(ApplicationBundle.message("editbox.ms"))
        }

        row {
          cbShowFullParameterSignatures = checkBox(ApplicationBundle.message("checkbox.show.full.signatures")).component
        }
      }

      addExtensions(sectionAddons)
    }
    reset()
    return panel
  }

  private fun getIntegerValue(s: String): Int {
    val value = StringUtilRt.parseInt(s, 0)
    return max(value, 0)
  }

  private fun isModified(textField: JTextField, value: Int): Boolean {
    return getIntegerValue(textField.text) != value
  }

  private fun isModified(checkBox: JBCheckBox?, value: Boolean): Boolean {
    return if (checkBox == null) {
      false
    }
    else {
      checkBox.isSelected != value
    }
  }

  private fun RowBuilder.addExtensions(addons: List<UnnamedConfigurable>) {
    for (addon in addons) {
      val component: JComponent? = addon.createComponent()
      component?.let {
        row {
          component(it)
        }
      }
    }
  }
}