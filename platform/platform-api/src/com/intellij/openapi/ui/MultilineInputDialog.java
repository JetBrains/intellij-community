/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
