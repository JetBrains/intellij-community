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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CodeCompletionPanel {
  JPanel myPanel;
  private JCheckBox myCbAutocompletion;
  private JTextField myAutocompletionDelayField;
  private JCheckBox myCbXmlAutocompletion;
  private JTextField myXmlAutocompletionDelayField;
  private JCheckBox myCbJavadocAutocompletion;
  private JTextField myFldJavadocAutocompletionDelayField;
  private JCheckBox myCbAutopopupJavaDoc;
  private JTextField myAutopopupJavaDocField;
  private JCheckBox myCbAutocompleteCommonPrefix;
  private JCheckBox myCbShowStaticAfterInstance;

  private JCheckBox myCbOnCodeCompletion;
  private JCheckBox myCbOnSmartTypeCompletion;
  private JCheckBox myCbOnClassNameCompletion;

  private JCheckBox myCbParameterInfoPopup;
  private JTextField myParameterInfoDelayField;
  private JCheckBox myCbShowFullParameterSignatures;

  private JComboBox myCaseSensitiveCombo;
  private static final String CASE_SENSITIVE_ALL = ApplicationBundle.message("combobox.autocomplete.casesensitive.all");
  private static final String CASE_SENSITIVE_NONE = ApplicationBundle.message("combobox.autocomplete.casesensitive.none");
  private static final String CASE_SENSITIVE_FIRST_LETTER = ApplicationBundle.message("combobox.autocomplete.casesensitive.first.letter");
  ButtonGroup buttonGroup = new ButtonGroup();

  public CodeCompletionPanel(){
   myCaseSensitiveCombo.setModel(new DefaultComboBoxModel(new String[]{CASE_SENSITIVE_ALL, CASE_SENSITIVE_NONE, CASE_SENSITIVE_FIRST_LETTER}));


   myCbAutocompletion.addActionListener(
     new ActionListener() {
       public void actionPerformed(ActionEvent event) {
         myAutocompletionDelayField.setEnabled(myCbAutocompletion.isSelected());
       }
     }
   );

   myCbXmlAutocompletion.addActionListener(
     new ActionListener() {
       public void actionPerformed(ActionEvent event) {
         myXmlAutocompletionDelayField.setEnabled(myCbXmlAutocompletion.isSelected());
       }
     }
   );

   myCbJavadocAutocompletion.addActionListener(new ActionListener() {
     public void actionPerformed(ActionEvent e) {
       myFldJavadocAutocompletionDelayField.setEnabled(myCbJavadocAutocompletion.isSelected());
     }
   });

   myCbAutopopupJavaDoc.addActionListener(
     new ActionListener() {
       public void actionPerformed(ActionEvent event) {
         myAutopopupJavaDocField.setEnabled(myCbAutopopupJavaDoc.isSelected());
       }
     }
   );

   myCbParameterInfoPopup.addActionListener(
     new ActionListener() {
       public void actionPerformed(ActionEvent event) {
         myParameterInfoDelayField.setEnabled(myCbParameterInfoPopup.isSelected());
       }
     }
   );

    hideOption(myCbShowStaticAfterInstance, OptionId.COMPLETION_SHOW_STATIC_AFTER_IMPORT);
    hideOption(myCbXmlAutocompletion, OptionId.COMPLETION_AUTO_POPUP_XML);
    hideOption(myXmlAutocompletionDelayField, OptionId.COMPLETION_AUTO_POPUP_XML);
    hideOption(myCbJavadocAutocompletion, OptionId.COMPLETION_AUTO_POPUP_DOC_COMMENT);
    hideOption(myFldJavadocAutocompletionDelayField, OptionId.COMPLETION_AUTO_POPUP_DOC_COMMENT);
    hideOption(myCbOnSmartTypeCompletion, OptionId.COMPLETION_SMART_TYPE);
    hideOption(myCbOnClassNameCompletion, OptionId.COMPLETION_CLASS_NAME);

    reset();
  }

  private static void hideOption(JComponent component, OptionId id) {
    component.setVisible(OptionsApplicabilityFilter.isApplicable(id));
  }

    /*
    */

  public void reset() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();

    String value = "";
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

    myCbOnCodeCompletion.setSelected(codeInsightSettings.AUTOCOMPLETE_ON_CODE_COMPLETION);
    myCbOnSmartTypeCompletion.setSelected(codeInsightSettings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION);
    myCbOnClassNameCompletion.setSelected(codeInsightSettings.AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION);
    myCbAutocompleteCommonPrefix.setSelected(codeInsightSettings.AUTOCOMPLETE_COMMON_PREFIX);
    myCbShowStaticAfterInstance.setSelected(codeInsightSettings.SHOW_STATIC_AFTER_INSTANCE);

    myCbAutocompletion.setSelected(codeInsightSettings.AUTO_POPUP_MEMBER_LOOKUP);
    myAutocompletionDelayField.setEnabled(codeInsightSettings.AUTO_POPUP_MEMBER_LOOKUP);
    myAutocompletionDelayField.setText(String.valueOf(codeInsightSettings.MEMBER_LOOKUP_DELAY));

    myCbXmlAutocompletion.setSelected(codeInsightSettings.AUTO_POPUP_XML_LOOKUP);
    myXmlAutocompletionDelayField.setEnabled(codeInsightSettings.AUTO_POPUP_XML_LOOKUP);
    myXmlAutocompletionDelayField.setText(String.valueOf(codeInsightSettings.XML_LOOKUP_DELAY));

    myCbJavadocAutocompletion.setSelected(codeInsightSettings.AUTO_POPUP_JAVADOC_LOOKUP);
    myFldJavadocAutocompletionDelayField.setEnabled(codeInsightSettings.AUTO_POPUP_JAVADOC_LOOKUP);
    myFldJavadocAutocompletionDelayField.setText(String.valueOf(codeInsightSettings.JAVADOC_LOOKUP_DELAY));

    myCbAutopopupJavaDoc.setSelected(codeInsightSettings.AUTO_POPUP_JAVADOC_INFO);
    myAutopopupJavaDocField.setEnabled(codeInsightSettings.AUTO_POPUP_JAVADOC_INFO);
    myAutopopupJavaDocField.setText(String.valueOf(codeInsightSettings.JAVADOC_INFO_DELAY));

    myCbParameterInfoPopup.setSelected(codeInsightSettings.AUTO_POPUP_PARAMETER_INFO);
    myParameterInfoDelayField.setEnabled(codeInsightSettings.AUTO_POPUP_PARAMETER_INFO);
    myParameterInfoDelayField.setText(String.valueOf(codeInsightSettings.PARAMETER_INFO_DELAY));
    myCbShowFullParameterSignatures.setSelected(codeInsightSettings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO);

    myCbAutocompletion.setSelected(codeInsightSettings.AUTO_POPUP_MEMBER_LOOKUP);
  }

  public void apply() {

    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();

    codeInsightSettings.COMPLETION_CASE_SENSITIVE = getCaseSensitiveValue();

    codeInsightSettings.AUTOCOMPLETE_ON_CODE_COMPLETION = myCbOnCodeCompletion.isSelected();
    codeInsightSettings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = myCbOnSmartTypeCompletion.isSelected();
    codeInsightSettings.AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION = myCbOnClassNameCompletion.isSelected();
    codeInsightSettings.AUTOCOMPLETE_COMMON_PREFIX = myCbAutocompleteCommonPrefix.isSelected();
    codeInsightSettings.SHOW_STATIC_AFTER_INSTANCE = myCbShowStaticAfterInstance.isSelected();
    codeInsightSettings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO = myCbShowFullParameterSignatures.isSelected();

    codeInsightSettings.AUTO_POPUP_PARAMETER_INFO = myCbParameterInfoPopup.isSelected();
    codeInsightSettings.AUTO_POPUP_MEMBER_LOOKUP = myCbAutocompletion.isSelected();
    codeInsightSettings.AUTO_POPUP_XML_LOOKUP = myCbXmlAutocompletion.isSelected();
    codeInsightSettings.AUTO_POPUP_JAVADOC_LOOKUP = myCbJavadocAutocompletion.isSelected();
    codeInsightSettings.AUTO_POPUP_JAVADOC_INFO = myCbAutopopupJavaDoc.isSelected();

    codeInsightSettings.MEMBER_LOOKUP_DELAY = getIntegerValue(myAutocompletionDelayField.getText(), 0);
    codeInsightSettings.XML_LOOKUP_DELAY = getIntegerValue(myXmlAutocompletionDelayField.getText(), 0);
    codeInsightSettings.JAVADOC_LOOKUP_DELAY = getIntegerValue(myFldJavadocAutocompletionDelayField.getText(), 0);
    codeInsightSettings.PARAMETER_INFO_DELAY = getIntegerValue(myParameterInfoDelayField.getText(), 0);
    codeInsightSettings.JAVADOC_INFO_DELAY = getIntegerValue(myAutopopupJavaDocField.getText(), 0);

    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myPanel));
    if (project != null){
      DaemonCodeAnalyzer.getInstance(project).settingsChanged();
    }
  }

  public boolean isModified() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    boolean isModified = false;

    isModified |= (getCaseSensitiveValue() != codeInsightSettings.COMPLETION_CASE_SENSITIVE);

    isModified |= isModified(myCbOnCodeCompletion, codeInsightSettings.AUTOCOMPLETE_ON_CODE_COMPLETION);
    isModified |= isModified(myCbOnSmartTypeCompletion, codeInsightSettings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION);
    isModified |= isModified(myCbOnClassNameCompletion, codeInsightSettings.AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION);
    isModified |= isModified(myCbAutocompleteCommonPrefix, codeInsightSettings.AUTOCOMPLETE_COMMON_PREFIX);
    isModified |= isModified(myCbShowStaticAfterInstance, codeInsightSettings.SHOW_STATIC_AFTER_INSTANCE);
    isModified |= isModified(myCbShowFullParameterSignatures, codeInsightSettings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO);
    isModified |= isModified(myCbParameterInfoPopup, codeInsightSettings.AUTO_POPUP_PARAMETER_INFO);
    isModified |= isModified(myCbAutocompletion, codeInsightSettings.AUTO_POPUP_MEMBER_LOOKUP);
    isModified |= isModified(myCbXmlAutocompletion, codeInsightSettings.AUTO_POPUP_XML_LOOKUP);
    isModified |= isModified(myCbJavadocAutocompletion, codeInsightSettings.AUTO_POPUP_JAVADOC_LOOKUP);

    isModified |= isModified(myCbAutopopupJavaDoc, codeInsightSettings.AUTO_POPUP_JAVADOC_INFO);
    isModified |= isModified(myAutocompletionDelayField, codeInsightSettings.MEMBER_LOOKUP_DELAY, 0);
    isModified |= isModified(myXmlAutocompletionDelayField, codeInsightSettings.XML_LOOKUP_DELAY, 0);
    isModified |= isModified(myFldJavadocAutocompletionDelayField, codeInsightSettings.JAVADOC_LOOKUP_DELAY, 0);
    isModified |= isModified(myParameterInfoDelayField, codeInsightSettings.PARAMETER_INFO_DELAY, 0);
    isModified |= isModified(myAutopopupJavaDocField, codeInsightSettings.JAVADOC_INFO_DELAY, 0);

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
    catch (NumberFormatException e) {
    }
    return value;
  }

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
}