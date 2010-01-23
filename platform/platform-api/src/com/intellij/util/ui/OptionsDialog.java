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
package com.intellij.util.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.awt.*;

/**
 * author: lesya
 */

public abstract class OptionsDialog extends DialogWrapper {

  private JCheckBox myCheckBoxDoNotShowDialog;

  protected String getDoNotShowMessage() {
    return CommonBundle.message("dialog.options.do.not.show");
  }

  protected final Project myProject;

  protected OptionsDialog(Project project) {
    super(project, true);
    myProject = project;
  }

  protected OptionsDialog(Project project, boolean canBeParent) {
    super(project, canBeParent);
    myProject = project;
  }

  protected OptionsDialog(boolean canBeParent) {
    super(canBeParent);
    myProject = null;
  }

  protected OptionsDialog(Component parent, boolean canBeParent) {
    super(parent, canBeParent);
    myProject = null;
  }

  protected JComponent createSouthPanel() {

    myCheckBoxDoNotShowDialog = new JCheckBox(getDoNotShowMessage());

    JComponent southPanel = super.createSouthPanel();

    if (!canBeHidden()) {
      return southPanel;
    }

    final JPanel panel = addDoNotShowCheckBox(southPanel, myCheckBoxDoNotShowDialog);
    myCheckBoxDoNotShowDialog.setSelected(!isToBeShown());
    return panel;
  }

  public static JPanel addDoNotShowCheckBox(JComponent southPanel, JCheckBox checkBox) {
    final JPanel panel = new JPanel(new GridBagLayout());
    checkBox.setVerticalAlignment(SwingConstants.BOTTOM);
    
    panel.add(checkBox, new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(15, 0, 0, 0), 0, 0));
    panel.add(southPanel, new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    checkBox.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 20));
    return panel;
  }

  public static boolean shiftIsPressed(int inputEventModifiers) {
    return (inputEventModifiers & Event.SHIFT_MASK) != 0;
  }

  protected void doOKAction() {
    if (canBeHidden()) {
      setToBeShown(toBeShown(), true);
    }
    super.doOKAction();
  }

  protected boolean toBeShown() {
    return !myCheckBoxDoNotShowDialog.isSelected();
  }

  public void doCancelAction() {
    if (shouldSaveOptionsOnCancel() && canBeHidden()) {
      setToBeShown(toBeShown(), false);
    }
    super.doCancelAction();
  }

  protected abstract boolean isToBeShown();

  protected abstract void setToBeShown(boolean value, boolean onOk);

  protected boolean canBeHidden() {
    return true;
  }

  protected abstract boolean shouldSaveOptionsOnCancel();
}
