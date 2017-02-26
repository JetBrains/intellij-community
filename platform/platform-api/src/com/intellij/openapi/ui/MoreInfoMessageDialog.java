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
import java.awt.*;

class MoreInfoMessageDialog extends MessageDialog {
  @Nullable private final String myInfoText;

  public MoreInfoMessageDialog(Project project,
                               String message,
                               @Nls(capitalization = Nls.Capitalization.Title) String title,
                               @Nullable String moreInfo,
                               @NotNull String[] options,
                               int defaultOptionIndex,
                               int focusedOptionIndex,
                               Icon icon) {
    super(project);
    myInfoText = moreInfo;
    _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, null);
  }

  @Override
  protected JComponent createNorthPanel() {
    return doCreateCenterPanel();
  }

  @Override
  protected JComponent createCenterPanel() {
    if (myInfoText == null) {
      return null;
    }
    final JPanel panel = new JPanel(new BorderLayout());
    final JTextArea area = new JTextArea(myInfoText);
    area.setEditable(false);
    final JBScrollPane scrollPane = new JBScrollPane(area) {
      @Override
      public Dimension getPreferredSize() {
        final Dimension preferredSize = super.getPreferredSize();
        final Container parent = getParent();
        if (parent != null) {
          return new Dimension(preferredSize.width, Math.min(150, preferredSize.height));
        }
        return preferredSize;
      }
    };
    panel.add(scrollPane);
    return panel;
  }
}
