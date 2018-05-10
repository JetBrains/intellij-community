// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.messages;

import com.intellij.util.PairFunction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class TwoStepConfirmationDialog extends MessageDialog {
  private JCheckBox myCheckBox;
  private final String myCheckboxText;
  private final boolean myChecked;
  private final PairFunction<Integer, JCheckBox, Integer> myExitFunc;

  public TwoStepConfirmationDialog(String message,
                                   @Nls(capitalization = Nls.Capitalization.Title) String title,
                                   @NotNull String[] options,
                                   String checkboxText,
                                   boolean checked,
                                   final int defaultOptionIndexed,
                                   final int focusedOptionIndex,
                                   Icon icon,
                                   @Nullable final PairFunction<Integer, JCheckBox, Integer> exitFunc) {
    myCheckboxText = checkboxText;
    myChecked = checked;
    myExitFunc = exitFunc;

    _init(title, message, options, defaultOptionIndexed, focusedOptionIndex, icon, null);
  }

  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = createIconPanel();
    JPanel messagePanel = createMessagePanel();

    messagePanel.add(createCheckComponent(), BorderLayout.SOUTH);

    panel.add(messagePanel, BorderLayout.CENTER);

    return panel;
  }

  @NotNull
  protected JComponent createCheckComponent() {
    myCheckBox = new JCheckBox(myCheckboxText);
    myCheckBox.setSelected(myChecked);
    return myCheckBox;
  }

  @Override
  public int getExitCode() {
    final int exitCode = super.getExitCode();
    if (myExitFunc != null) {
      return myExitFunc.fun(exitCode, myCheckBox);
    }

    boolean checkBoxSelected = (myCheckBox != null && myCheckBox.isSelected());

    boolean okExitCode = (exitCode == OK_EXIT_CODE);

    return checkBoxSelected && okExitCode ? OK_EXIT_CODE : CANCEL_EXIT_CODE;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDefaultOptionIndex == -1 ? myCheckBox : super.getPreferredFocusedComponent();
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }
}
