/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.find.editorHeaderActions;

import com.intellij.find.SearchSession;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

public class StatusTextAction extends DumbAwareAction implements CustomComponentAction {
  @Override
  public void update(AnActionEvent e) {
    SearchSession search = e.getData(SearchSession.KEY);
    String statusText = search == null ? "" : search.getComponent().getStatusText();
    JLabel label = (JLabel)e.getPresentation().getClientProperty(CUSTOM_COMPONENT_PROPERTY);
    if (label != null) {
      label.setText(statusText);
      label.setVisible(StringUtil.isNotEmpty(statusText));
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    JLabel label = new JLabel() {
      @Override
      public Font getFont() {
        Font font = super.getFont();
        return font != null ? font.deriveFont(Font.BOLD) : null;
      }
    };
    label.setBorder(JBUI.Borders.empty(2, 20, 0, 20));
    return label;
  }
}
