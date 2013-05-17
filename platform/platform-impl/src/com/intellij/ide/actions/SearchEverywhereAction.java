/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.SearchTextField;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class SearchEverywhereAction extends AnAction implements CustomComponentAction {
  SearchTextField field;

  public SearchEverywhereAction() {
    createSearchField();
    LafManager.getInstance().addLafManagerListener(new LafManagerListener() {
      @Override
      public void lookAndFeelChanged(LafManager source) {
        createSearchField();
      }
    });
  }

  private void createSearchField() {
    field = new SearchTextField() {
      @Override
      public void paint(Graphics g) {
        super.paint(g);
        final JTextField editor = field.getTextEditor();
        if (StringUtil.isEmpty(editor.getText()) && !editor.hasFocus()) {
          final int baseline = editor.getUI().getBaseline(editor, editor.getWidth(), editor.getHeight());
          final Color color = UIUtil.getInactiveTextColor();
          final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
          g.setColor(color);
          final Font font = editor.getFont();
          g.setFont(new Font(font.getName(), Font.ITALIC, font.getSize()));
          //final String shortcut = KeymapUtil.getFirstKeyboardShortcutText(SearchEverywhereAction.this); //todo[kb]
          final String shortcut = "Ctrl + F10";
          if (UIUtil.isUnderDarcula()) {
            g.drawString(shortcut, 30, baseline + 2);
          } else {
            g.drawString(shortcut, 20 , baseline + 4);
          }
          config.restore();
        }
      }
    };
    int columns = 20;
    if (UIUtil.isUnderDarcula() || UIUtil.isUnderAquaLookAndFeel()) {
      columns = 25;
    }
    field.getTextEditor().setColumns(columns);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    IdeFocusManager.getInstance(e.getProject()).requestFocus(field.getTextEditor(), true);
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    return field;
  }
}
