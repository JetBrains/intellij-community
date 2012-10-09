/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.application.options;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.Project;
import org.intellij.lang.annotations.MagicConstant;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CodeCompletionPanel {
  JPanel myPanel;
  private JCheckBox myCbAutocompletion;
  private JTextField myAutocompletionDelayField;
  private JCheckBox myCbAutopopupJavaDoc;
  private JTextField myAutopopupJavaDocField;
  private JCheckBox myCbAutocompleteCommonPrefix;
  private JCheckBox myCbShowStaticAfterInstance;

  private JCheckBox myCbOnCodeCompletion;
  private JCheckBox myCbOnSmartTypeCompletion;

  private JCheckBox myCbParameterInfoPopup;
  private JTextField myParameterInfoDelayField;
  private JCheckBox myCbShowFullParameterSignatures;

  private JComboBox myCaseSensitiveCombo;
  private JComboBox myFocusLookup;
  private JCheckBox myCbSorting;
  private static final String CASE_SENSITIVE_ALL = ApplicationBundle.message("combobox.autocomplete.case.sensitive.all");
  private static final String CASE_SENSITIVE_NONE = ApplicationBundle.message("combobox.autocomplete.case.sensitive.none");
  private static final String CASE_SENSITIVE_FIRST_LETTER = ApplicationBundle.message("combobox.autocomplete.case.sensitive.first.letter");
  private static final String[] CASE_VARIANTS = {CASE_SENSITIVE_ALL, CASE_SENSITIVE_NONE, CASE_SENSITIVE_FIRST_LETTER};
  private static final String[] FOCUS_VARIANTS = {"Never", "'Smart'", "Always"};

  public CodeCompletionPanel(){
   myCaseSensitiveCombo.setModel(new DefaultComboBoxModel(CASE_VARIANTS));
   myFocusLookup.setModel(new DefaultComboBoxModel(FOCUS_VARIANTS));


   myCbAutocompletion.addActionListener(
     new ActionListener() {
       @Override
       public void actionPerformed(ActionEvent event) {
         myAutocompletionDelayField.setEnabled(myCbAutocompletion.isSelected());
       }
     }
   );

   myCbAutopopupJavaDoc.addActionListener(
     new ActionListener() {
       @Override
       public void actionPerformed(ActionEvent event) {
         myAutopopupJavaDocField.setEnabled(myCbAutopopupJavaDoc.isSelected());
       }
     }
   );

   myCbParameterInfoPopup.addActionListener(
     new ActionListener() {
       @Override
       public void actionPerformed(ActionEvent event) {
         myParameterInfoDelayField.setEnabled(myCbParameterInfoPopup.isSelected());
       }
     }
   );

    hideOption(myCbShowStaticAfterInstance, OptionId.COMPLETION_SHOW_STATIC_AFTER_IMPORT);
    hideOption(myCbOnSmartTypeCompletion, OptionId.COMPLETION_SMART_TYPE);
    myCbAutocompleteCommonPrefix.setVisible(false);

    reset();
  }

  private static void hideOption(JComponent component, OptionId id) {
    component.setVisible(OptionsApplicabilityFilter.isApplicable(id));
  }

  public void reset() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();

    final String value;
    switch(codeInsightSettings.COMPLETION_CASE_SENSITIVE){
      case CodeInsightSettings.ALL:
        value = CASE_SENSITIVE_ALL;
      break;

      case CodeInsightSettings.NONE:
        value = CASE_SENSITIVE_NONE;
      break;

      default:
        value = CASE_SENSITIVE_FIRST_LETTER;
      break;
    }
    myCaseSensitiveCombo.setSelectedItem(value);

    myFocusLookup.setSelectedIndex(Math.min(Math.max(codeInsightSettings.AUTOPOPUP_FOCUS_POLICY - 1, 0), FOCUS_VARIANTS.length - 1));

    myCbOnCodeCompletion.setSelected(codeInsightSettings.AUTOCOMPLETE_ON_CODE_COMPLETION);
    myCbOnSmartTypeCompletion.setSelected(codeInsightSettings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION);
    myCbAutocompleteCommonPrefix.setSelected(codeInsightSettings.AUTOCOMPLETE_COMMON_PREFIX);
    myCbShowStaticAfterInstance.setSelected(codeInsightSettings.SHOW_STATIC_AFTER_INSTANCE);

    myCbAutocompletion.setSelected(codeInsightSettings.AUTO_POPUP_COMPLETION_LOOKUP);
    myAutocompletionDelayField.setEnabled(codeInsightSettings.AUTO_POPUP_COMPLETION_LOOKUP);
    myAutocompletionDelayField.setText(String.valueOf(codeInsightSettings.AUTO_LOOKUP_DELAY));

    myCbAutopopupJavaDoc.setSelected(codeInsightSettings.AUTO_POPUP_JAVADOC_INFO);
    myAutopopupJavaDocField.setEnabled(codeInsightSettings.AUTO_POPUP_JAVADOC_INFO);
    myAutopopupJavaDocField.setText(String.valueOf(codeInsightSettings.JAVADOC_INFO_DELAY));

    myCbParameterInfoPopup.setSelected(codeInsightSettings.AUTO_POPUP_PARAMETER_INFO);
    myParameterInfoDelayField.setEnabled(codeInsightSettings.AUTO_POPUP_PARAMETER_INFO);
    myParameterInfoDelayField.setText(String.valueOf(codeInsightSettings.PARAMETER_INFO_DELAY));
    myCbShowFullParameterSignatures.setSelected(codeInsightSettings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO);

    myCbAutocompletion.setSelected(codeInsightSettings.AUTO_POPUP_COMPLETION_LOOKUP);
    myCbSorting.setSelected(UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY);
  }

  public void apply() {

    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();

    codeInsightSettings.COMPLETION_CASE_SENSITIVE = getCaseSensitiveValue();
    //noinspection MagicConstant
    codeInsightSettings.AUTOPOPUP_FOCUS_POLICY = getFocusLookupValue();

    codeInsightSettings.AUTOCOMPLETE_ON_CODE_COMPLETION = myCbOnCodeCompletion.isSelected();
    codeInsightSettings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = myCbOnSmartTypeCompletion.isSelected();
    codeInsightSettings.AUTOCOMPLETE_COMMON_PREFIX = myCbAutocompleteCommonPrefix.isSelected();
    codeInsightSettings.SHOW_STATIC_AFTER_INSTANCE = myCbShowStaticAfterInstance.isSelected();
    codeInsightSettings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO = myCbShowFullParameterSignatures.isSelected();

    codeInsightSettings.AUTO_POPUP_PARAMETER_INFO = myCbParameterInfoPopup.isSelected();
    codeInsightSettings.AUTO_POPUP_COMPLETION_LOOKUP = myCbAutocompletion.isSelected();
    codeInsightSettings.AUTO_POPUP_JAVADOC_INFO = myCbAutopopupJavaDoc.isSelected();

    codeInsightSettings.AUTO_LOOKUP_DELAY = getIntegerValue(myAutocompletionDelayField.getText(), 0);
    codeInsightSettings.PARAMETER_INFO_DELAY = getIntegerValue(myParameterInfoDelayField.getText(), 0);
    codeInsightSettings.JAVADOC_INFO_DELAY = getIntegerValue(myAutopopupJavaDocField.getText(), 0);
    
    UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY = myCbSorting.isSelected();

    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myPanel));
    if (project != null){
      DaemonCodeAnalyzer.getInstance(project).settingsChanged();
    }
  }

  public boolean isModified() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    boolean isModified = false;

    //noinspection ConstantConditions
    isModified |= getCaseSensitiveValue() != codeInsightSettings.COMPLETION_CASE_SENSITIVE;
    //noinspection MagicConstant
    isModified |= getFocusLookupValue() != codeInsightSettings.AUTOPOPUP_FOCUS_POLICY;

    isModified |= isModified(myCbOnCodeCompletion, codeInsightSettings.AUTOCOMPLETE_ON_CODE_COMPLETION);
    isModified |= isModified(myCbOnSmartTypeCompletion, codeInsightSettings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION);
    isModified |= isModified(myCbAutocompleteCommonPrefix, codeInsightSettings.AUTOCOMPLETE_COMMON_PREFIX);
    isModified |= isModified(myCbShowStaticAfterInstance, codeInsightSettings.SHOW_STATIC_AFTER_INSTANCE);
    isModified |= isModified(myCbShowFullParameterSignatures, codeInsightSettings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO);
    isModified |= isModified(myCbParameterInfoPopup, codeInsightSettings.AUTO_POPUP_PARAMETER_INFO);
    isModified |= isModified(myCbAutocompletion, codeInsightSettings.AUTO_POPUP_COMPLETION_LOOKUP);

    isModified |= isModified(myCbAutopopupJavaDoc, codeInsightSettings.AUTO_POPUP_JAVADOC_INFO);
    isModified |= isModified(myAutocompletionDelayField, codeInsightSettings.AUTO_LOOKUP_DELAY, 0);
    isModified |= isModified(myParameterInfoDelayField, codeInsightSettings.PARAMETER_INFO_DELAY, 0);
    isModified |= isModified(myAutopopupJavaDocField, codeInsightSettings.JAVADOC_INFO_DELAY, 0);
    isModified |= isModified(myCbSorting, UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY);

    return isModified;
  }

  private static boolean isModified(JCheckBox checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  private static boolean isModified(JTextField textField, int value, int defaultValue) {
    return getIntegerValue(textField.getText(), defaultValue) != value;
  }

  private static int getIntegerValue(String s, int defaultValue) {
    int value = defaultValue;
    try {
      value = Integer.parseInt(s);
      if(value < 0) {
        return defaultValue;
      }
    }
    catch (NumberFormatException ignored) {
    }
    return value;
  }

  @MagicConstant(intValues = {CodeInsightSettings.ALL, CodeInsightSettings.NONE, CodeInsightSettings.FIRST_LETTER})
  private int getCaseSensitiveValue() {
    Object value = myCaseSensitiveCombo.getSelectedItem();
    if (CASE_SENSITIVE_ALL.equals(value)){
      return CodeInsightSettings.ALL;
    }
    else if (CASE_SENSITIVE_NONE.equals(value)){
      return CodeInsightSettings.NONE;
    }
    else {
      return CodeInsightSettings.FIRST_LETTER;
    }
  }

  private int getFocusLookupValue() {
    return myFocusLookup.getSelectedIndex() + 1;
  }
}