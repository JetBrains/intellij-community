/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CodeCompletionPanel {
  JPanel myPanel;
  private JCheckBox myCbAutocompletion;
  private JCheckBox myCbAutopopupJavaDoc;
  private JTextField myAutopopupJavaDocField;

  private  JLabel myAutoInsertLabel;
  private JCheckBox myCbOnCodeCompletion;
  private JCheckBox myCbOnSmartTypeCompletion;

  private JCheckBox myCbParameterInfoPopup;
  private JTextField myParameterInfoDelayField;
  private JCheckBox myCbShowFullParameterSignatures;

  private JCheckBox myCbSorting;
  private JBCheckBox myCbSelectByChars;
  private JCheckBox myCbCompleteFunctionWithParameters;
  private JBCheckBox myCbMatchCase;
  private JBRadioButton myFirstLetterOnly;
  private JBRadioButton myAllLetters;
  private JBLabel myBasicShortcut;
  private JBLabel mySmartShortcut;
  private JPanel myCbOnCodeCompletionPanel;
  private JPanel myCbOnSmartTypeCompletionPanel;

  public CodeCompletionPanel() {
    ChangeListener updateCaseCheckboxes = __ -> {
      myFirstLetterOnly.setEnabled(myCbMatchCase.isSelected());
      myAllLetters.setEnabled(myCbMatchCase.isSelected());
    };
    myCbMatchCase.addChangeListener(updateCaseCheckboxes);
    updateCaseCheckboxes.stateChanged(null);
    
    ActionManager actionManager = ActionManager.getInstance();
    myBasicShortcut.setText(KeymapUtil.getFirstKeyboardShortcutText(actionManager.getAction(IdeActions.ACTION_CODE_COMPLETION)));
    mySmartShortcut.setText(KeymapUtil.getFirstKeyboardShortcutText(actionManager.getAction(IdeActions.ACTION_SMART_TYPE_COMPLETION)));

    myBasicShortcut.setForeground(JBColor.GRAY);
    mySmartShortcut.setForeground(JBColor.GRAY);

    myCbAutocompletion.addActionListener(
     new ActionListener() {
       @Override
       public void actionPerformed(@NotNull ActionEvent event) {
         boolean selected = myCbAutocompletion.isSelected();
         myCbSelectByChars.setEnabled(selected);
       }
     }
   );

   myCbAutopopupJavaDoc.addActionListener(
     new ActionListener() {
       @Override
       public void actionPerformed(@NotNull ActionEvent event) {
         myAutopopupJavaDocField.setEnabled(myCbAutopopupJavaDoc.isSelected());
       }
     }
   );

   myCbParameterInfoPopup.addActionListener(
     new ActionListener() {
       @Override
       public void actionPerformed(@NotNull ActionEvent event) {
         myParameterInfoDelayField.setEnabled(myCbParameterInfoPopup.isSelected());
       }
     }
   );

    hideOption(myCbOnSmartTypeCompletionPanel, OptionId.COMPLETION_SMART_TYPE);
    hideOption(myCbOnCodeCompletionPanel, OptionId.AUTOCOMPLETE_ON_BASIC_CODE_COMPLETION);
    if(!myCbOnSmartTypeCompletionPanel.isVisible() && !myCbOnCodeCompletionPanel.isVisible())
      myAutoInsertLabel.setVisible(false);

    reset();
  }

  private static void hideOption(JComponent component, OptionId id) {
    component.setVisible(OptionsApplicabilityFilter.isApplicable(id));
  }

  public void reset() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();

    switch(codeInsightSettings.COMPLETION_CASE_SENSITIVE){
      case CodeInsightSettings.ALL:
        myCbMatchCase.setSelected(true);
        myAllLetters.setSelected(true);
        break;

      case CodeInsightSettings.NONE:
        myCbMatchCase.setSelected(false);
        break;

      default:
        myCbMatchCase.setSelected(true);
        myFirstLetterOnly.setSelected(true);
        break;
    }

    myCbSelectByChars.setSelected(codeInsightSettings.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS);

    myCbOnCodeCompletion.setSelected(codeInsightSettings.AUTOCOMPLETE_ON_CODE_COMPLETION);
    myCbOnSmartTypeCompletion.setSelected(codeInsightSettings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION);

    myCbAutocompletion.setSelected(codeInsightSettings.AUTO_POPUP_COMPLETION_LOOKUP);

    myCbAutopopupJavaDoc.setSelected(codeInsightSettings.AUTO_POPUP_JAVADOC_INFO);
    myAutopopupJavaDocField.setEnabled(codeInsightSettings.AUTO_POPUP_JAVADOC_INFO);
    myAutopopupJavaDocField.setText(String.valueOf(codeInsightSettings.JAVADOC_INFO_DELAY));

    myCbParameterInfoPopup.setSelected(codeInsightSettings.AUTO_POPUP_PARAMETER_INFO);
    myParameterInfoDelayField.setEnabled(codeInsightSettings.AUTO_POPUP_PARAMETER_INFO);
    myParameterInfoDelayField.setText(String.valueOf(codeInsightSettings.PARAMETER_INFO_DELAY));
    myCbShowFullParameterSignatures.setSelected(codeInsightSettings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO);
    myCbCompleteFunctionWithParameters.setSelected(codeInsightSettings.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION);

    myCbAutocompletion.setSelected(codeInsightSettings.AUTO_POPUP_COMPLETION_LOOKUP);
    myCbSorting.setSelected(UISettings.getInstance().getSortLookupElementsLexicographically());
    
    myCbAutocompletion.setText(ApplicationBundle.message("editbox.auto.complete") + 
                               (PowerSaveMode.isEnabled() ? " (not available in Power Save mode)" : ""));
  }

  public void apply() {

    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();

    codeInsightSettings.COMPLETION_CASE_SENSITIVE = getCaseSensitiveValue();

    codeInsightSettings.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = myCbSelectByChars.isSelected();
    codeInsightSettings.AUTOCOMPLETE_ON_CODE_COMPLETION = myCbOnCodeCompletion.isSelected();
    codeInsightSettings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = myCbOnSmartTypeCompletion.isSelected();
    codeInsightSettings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO = myCbShowFullParameterSignatures.isSelected();

    codeInsightSettings.AUTO_POPUP_PARAMETER_INFO = myCbParameterInfoPopup.isSelected();
    codeInsightSettings.AUTO_POPUP_COMPLETION_LOOKUP = myCbAutocompletion.isSelected();
    codeInsightSettings.AUTO_POPUP_JAVADOC_INFO = myCbAutopopupJavaDoc.isSelected();

    codeInsightSettings.PARAMETER_INFO_DELAY = getIntegerValue(myParameterInfoDelayField.getText());
    codeInsightSettings.JAVADOC_INFO_DELAY = getIntegerValue(myAutopopupJavaDocField.getText());
    
    codeInsightSettings.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION = myCbCompleteFunctionWithParameters.isSelected();
    
    UISettings.getInstance().setSortLookupElementsLexicographically(myCbSorting.isSelected());

    final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myPanel));
    if (project != null){
      DaemonCodeAnalyzer.getInstance(project).settingsChanged();
    }
  }

  public boolean isModified() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    boolean isModified = false;

    //noinspection ConstantConditions
    isModified |= getCaseSensitiveValue() != codeInsightSettings.COMPLETION_CASE_SENSITIVE;

    isModified |= isModified(myCbOnCodeCompletion, codeInsightSettings.AUTOCOMPLETE_ON_CODE_COMPLETION);
    isModified |= isModified(myCbSelectByChars, codeInsightSettings.SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS);
    isModified |= isModified(myCbOnSmartTypeCompletion, codeInsightSettings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION);
    isModified |= isModified(myCbShowFullParameterSignatures, codeInsightSettings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO);
    isModified |= isModified(myCbParameterInfoPopup, codeInsightSettings.AUTO_POPUP_PARAMETER_INFO);
    isModified |= isModified(myCbAutocompletion, codeInsightSettings.AUTO_POPUP_COMPLETION_LOOKUP);
    isModified |= isModified(myCbCompleteFunctionWithParameters, codeInsightSettings.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION);
    isModified |= isModified(myCbAutopopupJavaDoc, codeInsightSettings.AUTO_POPUP_JAVADOC_INFO);
    isModified |= isModified(myParameterInfoDelayField, codeInsightSettings.PARAMETER_INFO_DELAY);
    isModified |= isModified(myAutopopupJavaDocField, codeInsightSettings.JAVADOC_INFO_DELAY);
    isModified |= isModified(myCbSorting, UISettings.getInstance().getSortLookupElementsLexicographically());

    return isModified;
  }

  private static boolean isModified(JCheckBox checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  private static boolean isModified(JTextField textField, int value) {
    return getIntegerValue(textField.getText()) != value;
  }

  private static int getIntegerValue(String s) {
    int value = StringUtilRt.parseInt(s, 0);
    return value < 0 ? 0 : value;
  }

  @MagicConstant(intValues = {CodeInsightSettings.ALL, CodeInsightSettings.NONE, CodeInsightSettings.FIRST_LETTER})
  private int getCaseSensitiveValue() {
    if (!myCbMatchCase.isSelected()) return CodeInsightSettings.NONE;
    
    return myAllLetters.isSelected() ? CodeInsightSettings.ALL : CodeInsightSettings.FIRST_LETTER;
  }

}