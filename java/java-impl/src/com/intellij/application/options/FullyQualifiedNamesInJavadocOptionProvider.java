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
package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.ui.ListCellRendererWrapper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import java.awt.*;

import static com.intellij.psi.codeStyle.JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS;
import static com.intellij.psi.codeStyle.JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED;
import static com.intellij.psi.codeStyle.JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT;

public class FullyQualifiedNamesInJavadocOptionProvider {

  private JPanel myPanel;
  private ComboBox myComboBox;

  public FullyQualifiedNamesInJavadocOptionProvider(@NotNull CodeStyleSettings settings) {
    composePanel();
    reset(settings);
  }

  public void reset(@NotNull CodeStyleSettings settings) {
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    QualifyJavadocOptions option = QualifyJavadocOptions.fromIntValue(javaSettings.CLASS_NAMES_IN_JAVADOC);
    myComboBox.setSelectedItem(option);
  }
  
  public void apply(@NotNull CodeStyleSettings settings) {
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    javaSettings.CLASS_NAMES_IN_JAVADOC = getSelectedIntOptionValue();
  }
  
  public boolean isModified(CodeStyleSettings settings) {
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    return javaSettings.CLASS_NAMES_IN_JAVADOC != getSelectedIntOptionValue();
  }
  
  private int getSelectedIntOptionValue() {
    QualifyJavadocOptions item = (QualifyJavadocOptions)myComboBox.getSelectedItem();
    return item.getIntOptionValue();
  }
  
  @NotNull
  public JPanel getPanel() {
    return myPanel;
  }

  private void composePanel() {
    myPanel = new JPanel(new GridBagLayout());

    myComboBox = new ComboBox();
    for (QualifyJavadocOptions options : QualifyJavadocOptions.values()) {
      myComboBox.addItem(options);
    }
    myComboBox.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(final JList list, final Object value, final int index, final boolean selected, final boolean hasFocus) {
        if (value instanceof QualifyJavadocOptions) {
          setText(((QualifyJavadocOptions)value).getPresentableText());
        }
      }
    });

    JLabel title = new JLabel(ApplicationBundle.message("radio.use.fully.qualified.class.names.in.javadoc"));
    myPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

    GridBagConstraints left = new GridBagConstraints();
    left.anchor = GridBagConstraints.WEST;

    GridBagConstraints right = new GridBagConstraints();
    right.anchor = GridBagConstraints.WEST;
    right.weightx = 1.0;
    right.insets = new Insets(0, 5, 0, 0);

    myPanel.add(title, left);
    myPanel.add(myComboBox, right);
  }
}

enum QualifyJavadocOptions {

  FQ_ALWAYS(FULLY_QUALIFY_NAMES_ALWAYS, ApplicationBundle.message("radio.use.fully.qualified.class.names.in.javadoc.always")),
  SHORTEN_ALWAYS(SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT, ApplicationBundle.message("radio.use.fully.qualified.class.names.in.javadoc.never")),
  FQ_WHEN_NOT_IMPORTED(FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED, ApplicationBundle.message("radio.use.fully.qualified.class.names.in.javadoc.if.not.imported"));

  private final String myText;
  private final int myOption;

  public String getPresentableText() {
    return myText;
  }

  public int getIntOptionValue() {
    return myOption;
  }

  public static QualifyJavadocOptions fromIntValue(int value) {
    for (QualifyJavadocOptions option : values()) {
      if (option.myOption == value) return option;
    }
    return FQ_WHEN_NOT_IMPORTED;
  }

  QualifyJavadocOptions(int option, String text) {
    myOption = option;
    myText = text;
  }
}