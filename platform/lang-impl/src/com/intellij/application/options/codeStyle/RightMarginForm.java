/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.ui.components.fields.CommaSeparatedIntegersField;
import com.intellij.ui.components.fields.IntegerField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Can be used for languages which do not use standard "Wrapping and Braces" panel.
 * <p>
 * <strong>Note</strong>: besides adding the panel to UI it is necessary to make sure that language's own
 * {@code LanguageCodeStyleSettingsProvider} explicitly supports RIGHT_MARGIN field in {@code customizeSettings()}
 * method as shown below:
 * <pre>
 * public void customizeSettings(...) {
 *   if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
 *     consumer.showStandardOptions("RIGHT_MARGIN");
 *   }
 * }
 * </pre>
 * @author Rustam Vishnyakov
 */
public class RightMarginForm {
  private IntegerField myRightMarginField;
  private JCheckBox myDefaultGeneralCheckBox;
  private JPanel myTopPanel;
  private JComboBox myWrapOnTypingCombo;
  private CommaSeparatedIntegersField myVisualGuidesField;
  private final Language myLanguage;
  private final int myDefaultRightMargin;

  public RightMarginForm(@NotNull Language language, @NotNull CodeStyleSettings settings) {
    myLanguage = language;
    myDefaultRightMargin = settings.getDefaultRightMargin();
    myDefaultGeneralCheckBox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        if (myDefaultGeneralCheckBox.isSelected()) {
          myRightMarginField.setText(Integer.toString(myDefaultRightMargin));
          myRightMarginField.setEnabled(false);
        }
        else {
          myRightMarginField.setEnabled(true);
        }
      }
    });

    //noinspection unchecked
    myWrapOnTypingCombo.setModel(new DefaultComboBoxModel(
      CodeStyleSettingsCustomizable.WRAP_ON_TYPING_OPTIONS
    ));
  }

  void createUIComponents() {
    myRightMarginField = new IntegerField(ApplicationBundle.message("editbox.right.margin.columns"), 0, CodeStyleSettings.MAX_RIGHT_MARGIN);
    myVisualGuidesField = new CommaSeparatedIntegersField(ApplicationBundle.message("settings.code.style.visual.guides"), 0, CodeStyleSettings.MAX_RIGHT_MARGIN, "Optional");
  }

  public void reset(@NotNull CodeStyleSettings settings) {
    CommonCodeStyleSettings langSettings = settings.getCommonSettings(myLanguage);
    if (langSettings != settings && langSettings.RIGHT_MARGIN >= 0) {
      myDefaultGeneralCheckBox.setSelected(false);
      myRightMarginField.setText(Integer.toString(langSettings.RIGHT_MARGIN));
    }
    else {
      myDefaultGeneralCheckBox.setSelected(true);
      myRightMarginField.setText(Integer.toString(settings.getDefaultRightMargin()));
      if (langSettings == settings) {
        myDefaultGeneralCheckBox.setEnabled(false);
        myRightMarginField.setEnabled(false);
      }
    }
    for (int i = 0; i < CodeStyleSettingsCustomizable.WRAP_ON_TYPING_VALUES.length; i ++) {
      if (langSettings.WRAP_ON_TYPING == CodeStyleSettingsCustomizable.WRAP_ON_TYPING_VALUES[i]) {
        myWrapOnTypingCombo.setSelectedIndex(i);
        break;
      }
    }
    myVisualGuidesField.setValue(settings.getSoftMargins(myLanguage));
  }

  public void apply(@NotNull CodeStyleSettings settings) throws ConfigurationException {
    myRightMarginField.validateContent();
    myVisualGuidesField.validateContent();
    CommonCodeStyleSettings langSettings = settings.getCommonSettings(myLanguage);
    if (langSettings != settings) {
      if (myDefaultGeneralCheckBox.isSelected()) {
        langSettings.RIGHT_MARGIN = -1;
      }
      else {
        langSettings.RIGHT_MARGIN = getFieldRightMargin(settings.getDefaultRightMargin());
      }
    }
    langSettings.WRAP_ON_TYPING = getSelectedWrapOnTypingValue();
    settings.setSoftMargins(myLanguage, myVisualGuidesField.getValue());
  }

  public boolean isModified(@NotNull CodeStyleSettings settings) {
    CommonCodeStyleSettings langSettings = settings.getCommonSettings(myLanguage);
    boolean rightMarginModified =
      myDefaultGeneralCheckBox.isSelected() ?
      langSettings.RIGHT_MARGIN >= 0 :
      langSettings.RIGHT_MARGIN != getFieldRightMargin(settings.getDefaultRightMargin());
    return rightMarginModified ||
           langSettings.WRAP_ON_TYPING != getSelectedWrapOnTypingValue() ||
           !settings.getSoftMargins(myLanguage).equals(myVisualGuidesField.getValue());
  }

  private int getFieldRightMargin(int fallBackValue) {
    String strValue = myRightMarginField.getText();
    if (!strValue.trim().isEmpty()) {
      try {
        return Integer.parseInt(strValue);
      }
      catch (NumberFormatException e) {
        myRightMarginField.setText(Integer.toString(fallBackValue));
      }
    }
    return fallBackValue;
  }

  private int getSelectedWrapOnTypingValue() {
    int i = myWrapOnTypingCombo.getSelectedIndex();
    if (i >= 0 && i < CodeStyleSettingsCustomizable.WRAP_ON_TYPING_VALUES.length) {
      return CodeStyleSettingsCustomizable.WRAP_ON_TYPING_VALUES[i];
    }
    return CommonCodeStyleSettings.WrapOnTyping.DEFAULT.intValue;
  }

  public JPanel getTopPanel() {
    return myTopPanel;
  }
}
