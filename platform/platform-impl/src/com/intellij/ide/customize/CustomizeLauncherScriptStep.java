/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.customize;

import com.intellij.ide.actions.CreateLauncherScriptAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public class CustomizeLauncherScriptStep extends AbstractCustomizeWizardStep {
  public static boolean isAvailable() {
    return System.getProperty("idea.skip.launcher.script.step") == null && CreateLauncherScriptAction.isAvailable();
  }

  private final JCheckBox myCreateScriptCheckBox = new JCheckBox(ActionsBundle.message("action.CreateLauncherScript.description"));
  private final JTextField myScriptPathTextField = new JTextField();

  public CustomizeLauncherScriptStep() {
    setLayout(new BorderLayout());

    myCreateScriptCheckBox.setOpaque(false);
    myCreateScriptCheckBox.setSelected(false);
    myCreateScriptCheckBox.addChangeListener(e -> myScriptPathTextField.setEnabled(myCreateScriptCheckBox.isSelected()));

    myScriptPathTextField.setEnabled(false);
    myScriptPathTextField.setText(CreateLauncherScriptAction.defaultScriptPath());

    JPanel content = new JPanel(createSmallBorderLayout());
    content.setBorder(createSmallEmptyBorder());

    JPanel controls = new JPanel(new GridBagLayout());
    controls.setOpaque(false);
    GridBag gbc = new GridBag().setDefaultAnchor(GridBagConstraints.WEST).setDefaultFill(GridBagConstraints.HORIZONTAL).setDefaultWeightX(1);

    controls.add(myCreateScriptCheckBox, gbc.nextLine());

    gbc.nextLine();
    gbc.insets.top = UIUtil.PANEL_REGULAR_INSETS.top;
    gbc.insets.left = UIUtil.PANEL_REGULAR_INSETS.left;
    controls.add(new JLabel("Please specify the path where the script should be created:"), gbc);

    controls.add(myScriptPathTextField, gbc.nextLine());

    content.add(controls, BorderLayout.NORTH);

    add(content, BorderLayout.CENTER);
  }

  @Override
  public boolean beforeOkAction() {
    if (myCreateScriptCheckBox.isSelected()) {
      try {
        CreateLauncherScriptAction.createLauncherScript(myScriptPathTextField.getText());
      }
      catch (Exception e) {
        Messages.showErrorDialog(ExceptionUtil.getNonEmptyMessage(e, "Internal error"), "Launcher Script Creation Failed");
        return false;
      }
    }

    return true;
  }

  @Override
  protected String getTitle() {
    return "Launcher Script";
  }

  @Override
  protected String getHTMLHeader() {
    return "<html><body><h2>Create Launcher Script</h2></body></html>";
  }

  @Override
  protected String getHTMLFooter() {
    return "Launcher script can be created later via Tools | Create Command-Line Launcher...";
  }
}