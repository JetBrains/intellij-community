/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.awt.*;

/**
 * author: lesya
 */

public abstract class OptionsDialog extends DialogWrapper {

  private final JCheckBox myCheckBoxDoNotShowDialog = new JCheckBox("Do not show this dialog in the future");

  protected final Project myProject;

  protected OptionsDialog(Project project) {
    super(project, true);
    myProject = project;
  }

  protected JComponent createSouthPanel() {
    JComponent southPanel = super.createSouthPanel();

    if (!canBeHidden()) {
      return southPanel;
    }

    final JPanel panel = new JPanel(new GridBagLayout());
    panel.add(myCheckBoxDoNotShowDialog, new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    panel.add(southPanel, new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    myCheckBoxDoNotShowDialog.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 20));
    myCheckBoxDoNotShowDialog.setSelected(!isToBeShown());
    return panel;
  }

  public static boolean shiftIsPressed(int inputEventModifiers) {
    return (inputEventModifiers & Event.SHIFT_MASK) != 0;
  }

  protected void doOKAction() {
    doOKAction(!myCheckBoxDoNotShowDialog.isSelected());
  }

  private void doOKAction(boolean toBeShown) {
    if (canBeHidden()) {
      setToBeShown(toBeShown, true);
    }
    super.doOKAction();
  }

  public void doCancelAction() {
    if (shouldSaveOptionsOnCancel() && canBeHidden()) {
      setToBeShown(!myCheckBoxDoNotShowDialog.isSelected(), false);
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
