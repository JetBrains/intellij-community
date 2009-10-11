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

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import javax.swing.*;

public class CodeStyleImportsConfigurable extends BaseConfigurable {
  private CodeStyleImportsPanel myPanel;
  private final CodeStyleSettings mySettings;

  public CodeStyleImportsConfigurable(CodeStyleSettings settings) {
    mySettings = settings;
  }

  public boolean isModified() {
    return myPanel != null && myPanel.isModified();
  }

  public JComponent createComponent() {
    myPanel = new CodeStyleImportsPanel(mySettings);
    return myPanel;
  }

  public String getDisplayName() {
    return ApplicationBundle.message("title.imports");
  }

  public Icon getIcon() {
    return StdFileTypes.JAVA.getIcon();
  }

  public void reset() {
    if (myPanel != null) {
      myPanel.reset();
    }
  }

  public void apply() {
    if (myPanel != null) {
      myPanel.apply();
    }
  }

  public void disposeUIResources() {
    myPanel = null;
  }

  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.globalcodestyle.imports";
  }
}
