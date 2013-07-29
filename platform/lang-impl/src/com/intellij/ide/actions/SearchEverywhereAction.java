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

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.util.gotoByName.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
  private JList myList = new JList(); //don't use JBList here!!! todo[kb]
  private AnActionEvent myActionEvent;
  private Component myContextComponent;
  private CalcThread myCalcThread;

  public SearchEverywhereAction() {
    createSearchField();
    LafManager.getInstance().addLafManagerListener(new LafManagerListener() {
      @Override
      public void lookAndFeelChanged(LafManager source) {
        createSearchField();
      }
    });
    myList.setCellRenderer(new MyListRenderer());
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        onFocusLost(field.getTextEditor());
      }
    });
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(Registry.is("search.everywhere.enabled"));
  }


  private void createSearchField() {
    field = new MySearchTextField();

    final JTextField editor = field.getTextEditor();
    onFocusLost(editor);
    editor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        final String pattern = editor.getText();
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(new Runnable() {
          @Override
          public void run() {
            if (StringUtil.isEmpty(pattern)) {
              //noinspection SSBasedInspection
              SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                  if (myPopup != null && myPopup.isVisible()) {
                    myPopup.cancel();
                  }
                }
              });
              return;
            }
            rebuildList(pattern);
          }
        }, 300);
      }
    });
    editor.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        field.setText("");
        field.getTextEditor().setForeground(UIUtil.getLabelForeground());

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
        onFocusLost(editor);
      }
    });

    editor.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (keyCode == KeyEvent.VK_ESCAPE && (myPopup == null || !myPopup.isVisible())) {
          IdeFocusManager focusManager = IdeFocusManager.findInstanceByComponent(editor);
          focusManager.requestDefaultFocus(true);
        }
        else if (keyCode == KeyEvent.VK_ENTER) {
          doNavigate();
        }
      }
    });
  }

  private void onFocusLost(final JTextField editor) {
    editor.setColumns(SystemInfo.isMac ? 5 : 8);
    field.getTextEditor().setForeground(UIUtil.getLabelDisabledForeground());
    field.setText(" " + KeymapUtil.getFirstKeyboardShortcutText(this));

    myAlarm.cancelAllRequests();
    myList.setModel(new DefaultListModel());

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

  private void doNavigate() {
    final Object value = myList.getSelectedValue();
    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(field.getTextEditor()));
    IdeFocusManager focusManager = IdeFocusManager.findInstanceByComponent(field.getTextEditor());
    if (myPopup != null && myPopup.isVisible()) {
      myPopup.cancel();
    }
    AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
    try {
    if (value instanceof PsiElement) {
      NavigationUtil.activateFileWithPsiElement((PsiElement)value, true);
      return;
    } else if (value instanceof VirtualFile) {
      OpenFileDescriptor navigatable = new OpenFileDescriptor(project, (VirtualFile)value);
      if (navigatable.canNavigate()) {
        navigatable.navigate(true);
        return;
      }
    } else {
      focusManager.requestDefaultFocus(true);
      IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
        @Override
        public void run() {
          GotoActionAction.openOptionOrPerformAction(value, field.getText(), project, myContextComponent ,myActionEvent);
        }
      });
      return;
    }
    } finally {
      token.finish();
    }
    focusManager.requestDefaultFocus(true);
  }

  private void rebuildList(final String pattern) {
    if (myCalcThread != null) {
      myCalcThread.cancel();
    }
    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(field.getTextEditor()));

    assert project != null;
    myCalcThread = new CalcThread(project, pattern);
    myCalcThread.start();
  }


  @Override
  public void actionPerformed(AnActionEvent e) {
    IdeFocusManager focusManager = IdeFocusManager.getInstance(e.getProject());
    focusManager.requestFocus(field.getTextEditor(), true);
    myActionEvent = e;
    myContextComponent = PlatformDataKeys.CONTEXT_COMPONENT.getData(e.getDataContext());
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    return field;
  }

  private static class MySearchTextField extends SearchTextField {
    public MySearchTextField() {
      super(false);
      setOpaque(false);
      getTextEditor().setOpaque(false);
    }

    @Override
    protected void showPopup() {
    }
  }

  private class MyListRenderer extends ColoredListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      Component cmp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

      String title = getTitle(value, index == 0 ? null : list.getModel().getElementAt(index -1));
      if (title == null) {
        return cmp;
      } else {
        JPanel titlePanel = new JPanel(new BorderLayout());
        JBLabel titleLabel = new JBLabel();
        titlePanel.add(titleLabel, BorderLayout.NORTH);
        titlePanel.add(this, BorderLayout.CENTER);
        titlePanel.setBackground(UIUtil.getListBackground());
        titleLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.ITALIC, UIUtil.getFontSize(UIUtil.FontSize.SMALL)));
        titleLabel.setForeground(UIUtil.getLabelDisabledForeground());
        titleLabel.setText(" " + title);
        return titlePanel;
      }
    }

    private String getTitle(Object value, Object prevValue) {
      String gotoClass = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("GotoClass"));
      gotoClass = StringUtil.isEmpty(gotoClass) ? "Classes" : "Classes (" + gotoClass + ")";
      String gotoFile = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("GotoFile"));
      gotoFile = StringUtil.isEmpty(gotoFile) ? "Files" : "Files (" + gotoFile + ")";
      String gotoAction = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("GotoAction"));
      gotoAction = StringUtil.isEmpty(gotoAction) ? "Actions" : "Actions (" + gotoAction + ")";
      if (prevValue == null) { // firstElement
        if (value instanceof PsiElement)return gotoClass;
        if (value instanceof VirtualFile)return gotoFile;
        return gotoAction;
      } else {
        if (!(prevValue instanceof VirtualFile) && value instanceof VirtualFile) return gotoFile;
        if ((prevValue instanceof PsiElement || prevValue instanceof VirtualFile) && !(value instanceof PsiElement || value instanceof VirtualFile)) return gotoAction;
      }
      return null;
    }

    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
      try {
        if (value instanceof PsiElement) {
          String name = myClassModel.getElementName(value);
          assert name != null;
          append("    " + name);
        }
        else if (value instanceof VirtualFile) {
          append("    " + ((VirtualFile)value).getName());
        }
        else {
          append("    " + value.toString());
        }
      }
      finally {
        token.finish();
      }
    }
  }

  private class CalcThread implements Runnable {
    private final Project project;
    private final String pattern;
    private ProgressIndicator myProgressIndicator = new ProgressIndicatorBase();

    public CalcThread(Project project, String pattern) {
      this.project = project;
      this.pattern = pattern;
    }

    @Override
    public void run() {
      if (myClassModel == null) {
        myClassModel = new GotoClassModel2(project);
        myFileModel = new GotoFileModel(project);
        myActionModel = new GotoActionModel(project, myFocusComponent);
        myClasses = myClassModel.getNames(false);
        myFiles = myFileModel.getNames(false);
        myActions = myActionModel.getNames(true);
      }
      final AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
      try {
        List<MatchResult> classes = collectResults(pattern, myClasses, myClassModel);
        List<MatchResult> files = collectResults(pattern, myFiles, myFileModel);
        List<MatchResult> actions = collectResults(pattern, myActions, myActionModel);
        final DefaultListModel listModel = new DefaultListModel();
        Set<VirtualFile> alreadyAddedFiles = new HashSet<VirtualFile>();

        for (MatchResult o : classes) {
          myProgressIndicator.checkCanceled();
          Object[] objects = myClassModel.getElementsByName(o.elementName, false, pattern);
          for (Object object : objects) {
            myProgressIndicator.checkCanceled();
            if (!listModel.contains(object)) {
              listModel.addElement(object);
              if (object instanceof PsiElement) {
                VirtualFile file = PsiUtilCore.getVirtualFile((PsiElement)object);
                if (file != null) {
                  alreadyAddedFiles.add(file);
                }
              }
            }
          }
        }
        for (MatchResult o : files) {
          myProgressIndicator.checkCanceled();
          Object[] objects = myFileModel.getElementsByName(o.elementName, false, pattern, myProgressIndicator);
          for (Object object : objects) {
            myProgressIndicator.checkCanceled();
            if (!listModel.contains(object)) {
              if (object instanceof PsiFile) {
                object = ((PsiFile)object).getVirtualFile();
              }
              if (!alreadyAddedFiles.contains(object)) {
                myProgressIndicator.checkCanceled();
                listModel.addElement(object);
              }
            }
          }
        }
        for (MatchResult o : actions) {
          myProgressIndicator.checkCanceled();
          Object[] objects = myActionModel.getElementsByName(o.elementName, true, pattern);
          for (Object object : objects) {
            myProgressIndicator.checkCanceled();
            listModel.addElement(object);
          }
        }

        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            myProgressIndicator.checkCanceled();
            myList.setModel(listModel);
            if (myPopup == null || !myPopup.isVisible()) {
              final ActionCallback callback = ListDelegationUtil.installKeyboardDelegation(field.getTextEditor(), myList);
              myPopup = JBPopupFactory.getInstance()
                .createListPopupBuilder(myList)
                .setRequestFocus(false)
                .createPopup();
              Disposer.register(myPopup, new Disposable() {
                @Override
                public void dispose() {
                  callback.setDone();
                }
              });
              myPopup.showUnderneathOf(field);
            } else {
              myList.revalidate();
              myList.repaint();
            }
            ListScrollingUtil.ensureSelectionExists(myList);
            if (myList.getModel().getSize() == 0) {
              myPopup.cancel();
            } else {
              final Dimension size = myList.getPreferredSize();
              myPopup.setSize(new Dimension(Math.min(600, Math.max(field.getWidth(), size.width + 2)), Math.min(600, size.height + 2)));
              final Point screen = field.getLocationOnScreen();
              final int x = screen.x + field.getWidth() - myPopup.getSize().width;

              myPopup.setLocation(new Point(x, myPopup.getLocationOnScreen().y));
            }
          }
        });
      }
      finally {
        token.finish();
      }
    }

    private List<MatchResult> collectResults(String pattern, String[] names, ChooseByNameModel model) {
      if (!pattern.startsWith("*")) {
        pattern = "*" + pattern;
      }
      final ArrayList<MatchResult> results = new ArrayList<MatchResult>();
      MinusculeMatcher matcher = NameUtil.buildMatcher(pattern, NameUtil.MatchingCaseSensitivity.NONE);
      MatchResult result;

      for (String name : names) {
        myProgressIndicator.checkCanceled();
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

    public void cancel() {
      myProgressIndicator.cancel();
    }

    public void start() {
      if (!myProgressIndicator.isCanceled()) {
        ProgressManager.getInstance().runProcess(this, myProgressIndicator);
      }
    }
  }
}
