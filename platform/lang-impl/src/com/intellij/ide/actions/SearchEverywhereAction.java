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

import com.intellij.ide.DataManager;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.util.gotoByName.GotoActionModel;
import com.intellij.ide.util.gotoByName.GotoClassModel2;
import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * @author Konstantin Bulenkov
 */
public class SearchEverywhereAction extends AnAction implements CustomComponentAction {
  SearchTextField field;
  private GotoClassModel2 myClassModel;
  private GotoFileModel myFileModel;
  private GotoActionModel myActionModel;
  private String[] myClasses;
  private String[] myFiles;
  private String[] myActions;

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
      columns = 7;
    }

    final JTextField editor = field.getTextEditor();
    editor.setColumns(columns);
    editor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        final String text = editor.getText();
        final int i = myClasses.length +
                      myFiles.length +
                      myActions.length +
                      myFileModel.hashCode() +
                      myClassModel.hashCode() +
                      myActionModel.hashCode();
      }
    });
    editor.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor));
        myClassModel = new GotoClassModel2(project);
        myFileModel = new GotoFileModel(project);
        myActionModel = new GotoActionModel(project, e.getOppositeComponent());
        myClasses = myClassModel.getNames(false);
        myFiles = myFileModel.getNames(false);
        myActions = myActionModel.getNames(true);

        editor.setColumns(25);
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            final JComponent parent = (JComponent)editor.getParent();
            parent.revalidate();
            parent.repaint();
          }
        });

      }

      @Override
      public void focusLost(FocusEvent e) {
        editor.setColumns(7);
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            final JComponent parent = (JComponent)editor.getParent();
            parent.revalidate();
            parent.repaint();
          }
        });
      }
    });
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
