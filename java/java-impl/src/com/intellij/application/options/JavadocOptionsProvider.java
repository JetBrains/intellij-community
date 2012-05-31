/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.application.options.editor.EditorOptionsProvider;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.javadoc.JavadocBundle;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 2/2/11 12:32 PM
 */
public class JavadocOptionsProvider implements EditorOptionsProvider {
  
  private JPanel myWholePanel;
  private JCheckBox myAutoGenerateClosingTagCheckBox;

  @NotNull
  @Override
  public String getId() {
    return "editor.preferences.javadocOptions";
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return JavadocBundle.message("javadoc.generate.message.title");
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public JComponent createComponent() {
    return myWholePanel;
  }

  @Override
  public boolean isModified() {
    CodeInsightSettings settings = getSettings();
    return myAutoGenerateClosingTagCheckBox.isSelected() ^ settings.JAVADOC_GENERATE_CLOSING_TAG;
  }

  @Override
  public void apply() throws ConfigurationException {
    getSettings().JAVADOC_GENERATE_CLOSING_TAG = myAutoGenerateClosingTagCheckBox.isSelected();
  }

  @Override
  public void reset() {
    myAutoGenerateClosingTagCheckBox.setSelected(getSettings().JAVADOC_GENERATE_CLOSING_TAG);
  }

  @Override
  public void disposeUIResources() {
  }
  
  private static CodeInsightSettings getSettings() {
    return CodeInsightSettings.getInstance();
  }
}
