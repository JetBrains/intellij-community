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

package com.intellij.execution.util;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public class ExecutionErrorDialog {
  private ExecutionErrorDialog() {
  }

  public static void show(final ExecutionException e, final String title, final Project project) {
    if (e instanceof RunCanceledByUserException) {
      return;
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException(e.getLocalizedMessage());
    }
    final String message = e.getMessage();
    if (message.length() < 100) {
      Messages.showErrorDialog(project, message, title);
      return;
    }
    final DialogBuilder builder = new DialogBuilder(project);
    builder.setTitle(title);
    final JTextArea textArea = new JTextArea();
    textArea.setEditable(false);
    textArea.setForeground(UIUtil.getLabelForeground());
    textArea.setBackground(UIUtil.getLabelBackground());
    textArea.setFont(UIUtil.getLabelFont());
    textArea.setText(message);
    textArea.setWrapStyleWord(false);
    textArea.setLineWrap(true);
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(textArea);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    final JPanel panel = new JPanel(new BorderLayout(10, 0));
    panel.setPreferredSize(new Dimension(500, 200));
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.add(new JLabel(Messages.getErrorIcon()), BorderLayout.WEST);
    builder.setCenterPanel(panel);
    builder.setButtonsAlignment(SwingConstants.CENTER);
    builder.addOkAction();
    builder.show();
  }
}
