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
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Can be used for languages which do not use standard "Wrapping and Braces" panel.
 * <p>
 * <strong>Note</strong>: besides adding the panel to UI it is necessary to make sure that language's own
 * <code>LanguageCodeStyleSettingsProvider</code> explicitly supports RIGHT_MARGIN field in <code>customizeSettings()</code>
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
  private JTextField myRightMarginField;
  private JCheckBox myDefaultGeneralCheckBox;
  private JPanel myTopPanel;
  private JComboBox myWrapOnTypingCombo;
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
  }

  public void apply(@NotNull CodeStyleSettings settings) {
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
  }

  public boolean isModified(@NotNull CodeStyleSettings settings) {
    CommonCodeStyleSettings langSettings = settings.getCommonSettings(myLanguage);
    boolean rightMarginModified =
      myDefaultGeneralCheckBox.isSelected() ?
      langSettings.RIGHT_MARGIN >= 0 :
      langSettings.RIGHT_MARGIN != getFieldRightMargin(settings.getDefaultRightMargin());
    return rightMarginModified || langSettings.WRAP_ON_TYPING != getSelectedWrapOnTypingValue();
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
