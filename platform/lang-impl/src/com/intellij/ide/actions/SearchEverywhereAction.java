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
import com.intellij.ide.util.gotoByName.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBList;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;

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
  private Component myFocusComponent;
  private JBPopup myPopup;

  private Alarm myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication());
  private JBList myList = new JBList();

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
    field = new MySearchTextField();
    int columns = 20;
    if (UIUtil.isUnderDarcula() || UIUtil.isUnderAquaLookAndFeel()) {
      columns = 7;
    }

    final JTextField editor = field.getTextEditor();
    editor.setColumns(columns);
    editor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        final String pattern = editor.getText();
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(new Runnable() {
          @Override
          public void run() {
            rebuildList(pattern);
          }
        }, 300);
      }
    });
    editor.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor));

        editor.setColumns(25);
        myFocusComponent = e.getOppositeComponent();
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
        myAlarm.cancelAllRequests();

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

  private void rebuildList(String pattern) {
    if (myClassModel == null) {
      final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(field.getTextEditor()));

      assert project != null;

      myClassModel = new GotoClassModel2(project);
      myFileModel = new GotoFileModel(project);
      myActionModel = new GotoActionModel(project, myFocusComponent);
      myClasses = myClassModel.getNames(false);
      myFiles = myFileModel.getNames(false);
      myActions = myActionModel.getNames(true);
    }

    List<MatchResult> classes = ContainerUtil.getFirstItems(collectResults(pattern, myClasses, myClassModel), 20);
    List<MatchResult> files = ContainerUtil.getFirstItems(collectResults(pattern, myFiles, myFileModel), 20);
    List<MatchResult> actions = ContainerUtil.getFirstItems(collectResults(pattern, myActions, myActionModel), 20);
    final DefaultListModel listModel = new DefaultListModel();
    for (MatchResult o : classes) listModel.addElement(o);
    for (MatchResult o : files) listModel.addElement(o);
    for (MatchResult o : actions) listModel.addElement(o);
    myList.setModel(listModel);

    if (myPopup == null || !myPopup.isVisible()) {
      myPopup = JBPopupFactory.getInstance()
        .createListPopupBuilder(myList)
        .setRequestFocus(false)
        .createPopup();
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          myPopup.showUnderneathOf(field.getTextEditor());
        }
      });
    } else {
      myList.revalidate();
      myList.repaint();
    }

  }

  private static List<MatchResult> collectResults(String pattern, String[] names, ChooseByNameModel model) {
    final ArrayList<MatchResult> results = new ArrayList<MatchResult>();
    MinusculeMatcher matcher = NameUtil.buildMatcher(pattern, NameUtil.MatchingCaseSensitivity.NONE);
    MatchResult result;

    for (String name : names) {
      ProgressManager.checkCanceled();
      result = null;
      if (model instanceof CustomMatcherModel) {
        try {
          result = ((CustomMatcherModel)model).matches(name, pattern) ? new MatchResult(name, 0, true) : null;
        }
        catch (Exception ignore) {
        }
      }
      else {
        result = matcher.matches(name) ? new MatchResult(name, matcher.matchingDegree(name), matcher.isStartMatch(name)) : null;
      }

      if (result != null) {
        results.add(result);
      }
    }
    return results;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    IdeFocusManager.getInstance(e.getProject()).requestFocus(field.getTextEditor(), true);
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    return field;
  }

  private class MySearchTextField extends SearchTextField {
    public MySearchTextField() {
      super(false);
    }

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
        }
        else {
          g.drawString(shortcut, 20, baseline + 4);
        }
        config.restore();
      }
    }
  }
}
