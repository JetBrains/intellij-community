/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.ui.Gray;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaWelcomeScreenButtonBase {
  protected void setup(final AnAction action, JPanel root, final JLabel name, final JLabel description, JLabel icon) {
    name.setText(action.getTemplatePresentation().getText());
    name.setUI(DarculaWelcomeScreenForm.TITLE_UI);
    icon.setIcon(action.getTemplatePresentation().getIcon());
    final String text = action.getTemplatePresentation().getDescription();
    final String html = "<html>" + MessageFormat.format(text, ApplicationNamesInfo.getInstance().getFullProductName()) + "</html>";
    root.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    description.setText(html);
    description.setForeground(Gray._90);
    root.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {

        description.setForeground(Gray._200);
        name.setForeground(new Color(0xE09600));
      }

      @Override
      public void mouseExited(MouseEvent e) {
        description.setForeground(Gray._90);
        name.setForeground(UIUtil.getPanelBackground());
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        final ActionManager actionManager = ActionManager.getInstance();
        AnActionEvent evt = new AnActionEvent(
          null,
          DataManager.getInstance().getDataContext(e.getComponent()),
          ActionPlaces.WELCOME_SCREEN,
          action.getTemplatePresentation(),
          actionManager,
          0
        );
        action.beforeActionPerformedUpdate(evt);
        if (evt.getPresentation().isEnabled()) {
          action.actionPerformed(evt);
        }
      }
    });

  }
}
