/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vcs.ui;

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

    if (!canBeHidden()) return southPanel;

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(myCheckBoxDoNotShowDialog, BorderLayout.WEST);
    myCheckBoxDoNotShowDialog.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 20));
    myCheckBoxDoNotShowDialog.setSelected(!isToBeShown());
    panel.add(southPanel, BorderLayout.CENTER);
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
