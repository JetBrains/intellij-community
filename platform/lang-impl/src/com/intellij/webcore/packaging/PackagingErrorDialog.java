// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webcore.packaging;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class PackagingErrorDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JTextArea myCommandOutput;
  private JPanel myCommandOutputPanel;
  private JPanel myCommandPanel;
  private JTextPane myCommand;
  private JPanel mySolutionPanel;
  private JTextPane mySolution;
  private JPanel myDetailsPanel;
  private JTextArea myDetails;
  private JPanel myMessagePanel;
  private JBLabel myDetailsLabel;
  private JTextPane myMessage;
  private JBLabel myMessageIcon;

  public PackagingErrorDialog(@NotNull @NlsContexts.DialogTitle String title,
                              @NotNull PackageManagementService.ErrorDescription errorDescription) {
    super(false);
    init();
    setResizable(false);
    setTitle(title);
    final String command = errorDescription.getCommand();
    final String output = errorDescription.getOutput();
    final String message = errorDescription.getMessage();
    final String solution = errorDescription.getSolution();

    final boolean extendedInfo = command != null || output != null || solution != null;

    myDetailsPanel.setVisible(!extendedInfo);
    myMessagePanel.setVisible(extendedInfo);
    myCommandPanel.setVisible(command != null);
    myCommandOutputPanel.setVisible(output != null);
    mySolutionPanel.setVisible(solution != null);

    if (extendedInfo) {
      myMessage.setText(message);
      myMessageIcon.setIcon(Messages.getErrorIcon());
    }
    else {
      myDetails.setText(message);
      myDetailsLabel.setIcon(Messages.getErrorIcon());
    }

    if (command != null) {
      myCommand.setText(command);
    }
    if (output != null) {
      myCommandOutput.setText(output);
    }
    if (solution != null) {
      mySolution.setText(solution);
    }
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }
}
