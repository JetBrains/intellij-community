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

package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
public class LayoutProjectCodeDialog extends DialogWrapper {
  private static @NonNls final String OPTIMIZE_IMPORTS_KEY = "LayoutCode.optimizeImports";
  private static @NonNls final String HELP_ID = "editing.codeReformatting";

  private final String myText;
  private final boolean mySuggestOptmizeImports;
  private JCheckBox myCbOptimizeImports;

  public LayoutProjectCodeDialog(Project project, String title, String text, boolean suggestOptmizeImports) {
    super(project, false);
    myText = text;
    mySuggestOptmizeImports = suggestOptmizeImports;
    setOKButtonText(CodeInsightBundle.message("reformat.code.accept.button.text"));
    setTitle(title);
    init();
  }

  protected JComponent createCenterPanel() {
    if (!mySuggestOptmizeImports) return new JLabel(myText);
    JPanel panel = new JPanel(new GridLayout(2, 1));
    panel.add(new JLabel(myText));
    myCbOptimizeImports = new JCheckBox(CodeInsightBundle.message("reformat.option.optimize.imports"));
    panel.add(myCbOptimizeImports);
    myCbOptimizeImports.setSelected(Boolean.toString(true).equals(PropertiesComponent.getInstance().getValue(OPTIMIZE_IMPORTS_KEY)));
    return panel;
  }

  private void setOptimizeImportsOption(boolean state) {
    PropertiesComponent.getInstance().setValue(OPTIMIZE_IMPORTS_KEY, Boolean.toString(state));
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HELP_ID);
  }

  protected void doOKAction() {
    super.doOKAction();
    if (mySuggestOptmizeImports) {
      setOptimizeImportsOption(isOptimizeImports());
    }
  }

  public boolean isOptimizeImports() {
    return myCbOptimizeImports.isSelected();
  }
}
