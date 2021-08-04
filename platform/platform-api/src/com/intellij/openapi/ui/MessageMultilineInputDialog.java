// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;

public class MessageMultilineInputDialog extends Messages.InputDialog {
  public MessageMultilineInputDialog(Project project,
                                     @NlsContexts.DialogMessage String message,
                                     @NlsContexts.DialogTitle String title,
                                     @Nullable Icon icon,
                                     @Nullable @NonNls String initialValue,
                                     @Nullable InputValidator validator,
                                     String @NotNull @NlsContexts.Button [] options,
                                     int defaultOption) {
    super(project, message, title, icon, initialValue, validator, options, defaultOption);
  }

  @Override
  protected JTextComponent createTextFieldComponent() {
    return new JTextArea(7, 50);
  }

  @Override
  protected JComponent createScrollableTextComponent() {
    return new JBScrollPane(myField);
  }

  @Override
  protected JComponent createNorthPanel() {
    return null;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel messagePanel = new JPanel(new BorderLayout());
    if (myMessage != null) {
      JComponent textComponent = createTextComponent();
      messagePanel.add(textComponent, BorderLayout.NORTH);
    }

    myField = createTextFieldComponent();
    messagePanel.add(createScrollableTextComponent(), BorderLayout.CENTER);
    return messagePanel;
  }
}
