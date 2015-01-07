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
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.psi.codeStyle.JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS;
import static com.intellij.psi.codeStyle.JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED;
import static com.intellij.psi.codeStyle.JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT;

public class FullyQualifiedNamesInJavadocOptionProvider {
  
  private JRadioButton myFullyQualifyNamesAlways;
  private JRadioButton myShortenNamesAlways;
  private JRadioButton myFullyQualifyIfNotImported;
  
  private JPanel myPanel;

  public FullyQualifiedNamesInJavadocOptionProvider(@NotNull CodeStyleSettings settings) {
    composePanel();
    reset(settings);
  }
  
  public void reset(@NotNull CodeStyleSettings settings) {
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    int classNamesInJavadoc = javaSettings.CLASS_NAMES_IN_JAVADOC;
    
    if (classNamesInJavadoc == FULLY_QUALIFY_NAMES_ALWAYS) {
      myFullyQualifyNamesAlways.setSelected(true);
    }
    else if (classNamesInJavadoc == SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT) {
      myShortenNamesAlways.setSelected(true);
    }
    else {
      myFullyQualifyIfNotImported.setSelected(true);  
    }
  }
  
  public void apply(@NotNull CodeStyleSettings settings) {
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    javaSettings.CLASS_NAMES_IN_JAVADOC = getIntValueFromSelectedRadioButton();
  }
  
  public boolean isModified(CodeStyleSettings settings) {
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    return javaSettings.CLASS_NAMES_IN_JAVADOC != getIntValueFromSelectedRadioButton();
  }
  
  private int getIntValueFromSelectedRadioButton() {
    if (myFullyQualifyNamesAlways.isSelected()) {
      return FULLY_QUALIFY_NAMES_ALWAYS;
    }
    else if (myShortenNamesAlways.isSelected()) {
      return SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT;
    }
    else {
      return FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED;
    }
  }
  
  @NotNull
  public JPanel getPanel() {
    return myPanel;
  }

  private void composePanel() {
    myPanel = new JPanel();
    BoxLayout boxLayout = new BoxLayout(myPanel, BoxLayout.Y_AXIS);
    myPanel.setLayout(boxLayout);

    JLabel title = new JLabel(ApplicationBundle.message("radio.use.fully.qualified.class.names.in.javadoc"));
    title.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
    
    myFullyQualifyNamesAlways = new JRadioButton(ApplicationBundle.message("radio.use.fully.qualified.class.names.in.javadoc.always"));
    myFullyQualifyNamesAlways.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 0));

    myShortenNamesAlways = new JRadioButton(ApplicationBundle.message("radio.use.fully.qualified.class.names.in.javadoc.never"));
    myShortenNamesAlways.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 0));

    myFullyQualifyIfNotImported = new JRadioButton(ApplicationBundle.message("radio.use.fully.qualified.class.names.in.javadoc.if.not.imported"));
    myFullyQualifyIfNotImported.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 0));

    ButtonGroup group = new ButtonGroup();
    group.add(myFullyQualifyNamesAlways);
    group.add(myShortenNamesAlways);
    group.add(myFullyQualifyIfNotImported);

    myPanel.add(title);
    myPanel.add(myFullyQualifyNamesAlways);
    myPanel.add(myFullyQualifyIfNotImported);
    myPanel.add(myShortenNamesAlways);
  }
}