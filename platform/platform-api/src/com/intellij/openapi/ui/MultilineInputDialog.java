// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;

class MultilineInputDialog extends InputDialog {
  public MultilineInputDialog(Project project,
                              String message,
                              @Nls(capitalization = Nls.Capitalization.Title) String title,
                              @Nullable Icon icon,
                              @Nullable String initialValue,
                              @Nullable InputValidator validator,
                              @NotNull String[] options,
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
}
