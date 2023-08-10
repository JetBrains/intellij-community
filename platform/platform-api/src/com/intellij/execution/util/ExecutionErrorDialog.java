// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.util;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public final class ExecutionErrorDialog {
  private ExecutionErrorDialog() {
  }

  public static void show(final ExecutionException e, final @NlsContexts.DialogTitle String title, final Project project) {
    if (e instanceof RunCanceledByUserException) {
      return;
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException(e.getLocalizedMessage());
    }
    final String message = e.getMessage();
    if (message == null || message.length() < 100) {
      Messages.showErrorDialog(project, message == null ? IdeBundle.message("error.message.exception.was.thrown") : message, title);
      return;
    }
    final DialogBuilder builder = new DialogBuilder(project);
    builder.setTitle(title);
    final JTextArea textArea = new JTextArea();
    textArea.setEditable(false);
    textArea.setForeground(UIUtil.getLabelForeground());
    textArea.setBackground(UIUtil.getLabelBackground());
    textArea.setFont(StartupUiUtil.getLabelFont());
    textArea.setText(message);
    textArea.setWrapStyleWord(false);
    textArea.setLineWrap(true);
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(textArea);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    final JPanel panel = new JPanel(new BorderLayout(10, 0));
    panel.setPreferredSize(JBUI.size(500, 200));
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.add(new JLabel(Messages.getErrorIcon()), BorderLayout.WEST);
    builder.setCenterPanel(panel);
    builder.addOkAction();
    builder.show();
  }
}
