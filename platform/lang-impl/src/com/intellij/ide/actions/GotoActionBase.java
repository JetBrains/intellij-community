/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.util.gotoByName.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;

/**
 * Author: msk
 */
public abstract class GotoActionBase extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.GotoActionBase");

  protected static Class myInAction = null;
  private static final Map<Class, Pair<String, Integer>> ourLastStrings = ContainerUtil.newHashMap();
  private static final Map<Class, List<String>> ourHistory = ContainerUtil.newHashMap();
  private int myHistoryIndex = 0;

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    LOG.assertTrue(!getClass().equals(myInAction));
    try {
      myInAction = getClass();
      List<String> strings = ourHistory.get(myInAction);
      myHistoryIndex = strings == null || strings.size() <= 1 || !ourLastStrings.containsKey(myInAction) ? 0 : 1;
      gotoActionPerformed(e);
    }
    catch (ProcessCanceledException e1) {
      myInAction = null;
    }
    catch (Throwable t) {
      LOG.error(t);
      myInAction = null;
    }
  }

  protected abstract void gotoActionPerformed(AnActionEvent e);

  @Override
  public void update(@NotNull final AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final DataContext dataContext = event.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    presentation.setEnabled(!getClass().equals (myInAction) && (!requiresProject() || project != null) && hasContributors(dataContext));
    presentation.setVisible(hasContributors(dataContext));
  }

  protected boolean hasContributors(final DataContext dataContext) {
    return true;
  }

  protected boolean requiresProject() {
    return true;
  }

  @Nullable
  public static PsiElement getPsiContext(final AnActionEvent e) {
    PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    if (file != null) return file;
    Project project = e.getData(CommonDataKeys.PROJECT);
    return getPsiContext(project);
  }

  @Nullable
  public static PsiElement getPsiContext(final Project project) {
    if (project == null) return null;
    Editor selectedEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (selectedEditor == null) return null;
    Document document = selectedEditor.getDocument();
    return PsiDocumentManager.getInstance(project).getPsiFile(document);
  }

  protected abstract static class GotoActionCallback<T> {
    @Nullable
    protected ChooseByNameFilter<T> createFilter(@NotNull ChooseByNamePopup popup) {
      return null;
    }

    public abstract void elementChosen(ChooseByNamePopup popup, Object element);
  }

  protected static Pair<String, Integer> getInitialText(boolean useEditorSelection, AnActionEvent e) {
    final String predefined = e.getData(PlatformDataKeys.PREDEFINED_TEXT);
    if (!StringUtil.isEmpty(predefined)) {
      return Pair.create(predefined, 0);
    }
    if (useEditorSelection) {
      final Editor editor = e.getData(CommonDataKeys.EDITOR);
      if (editor != null) {
        final String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText != null && !selectedText.contains("\n")) {
          return Pair.create(selectedText, 0);
        }
      }
    }

    final String query = e.getData(SpeedSearchSupply.SPEED_SEARCH_CURRENT_QUERY);
    if (!StringUtil.isEmpty(query)) {
      return Pair.create(query, 0);
    }

    final Component focusOwner = IdeFocusManager.getInstance(getEventProject(e)).getFocusOwner();
    if (focusOwner instanceof JComponent) {
      final SpeedSearchSupply supply = SpeedSearchSupply.getSupply((JComponent)focusOwner);
      if (supply != null) {
        return Pair.create(supply.getEnteredPrefix(), 0);
      }
    }

    if (myInAction != null) {
      final Pair<String, Integer> lastString = ourLastStrings.get(myInAction);
      if (lastString != null) {
        return lastString;
      }
    }

    return Pair.create("", 0);
  }

  protected <T> void showNavigationPopup(AnActionEvent e, ChooseByNameModel model, final GotoActionCallback<T> callback) {
    showNavigationPopup(e, model, callback, true);
  }

  protected <T> void showNavigationPopup(AnActionEvent e,
                                         ChooseByNameModel model,
                                         final GotoActionCallback<T> callback,
                                         final boolean allowMultipleSelection) {
    showNavigationPopup(e, model, callback, null, true, allowMultipleSelection);
  }

  protected <T> void showNavigationPopup(AnActionEvent e,
                                         ChooseByNameModel model,
                                         final GotoActionCallback<T> callback,
                                         @Nullable final String findUsagesTitle,
                                         boolean useSelectionFromEditor) {
    showNavigationPopup(e, model, callback, findUsagesTitle, useSelectionFromEditor, true);
  }

  protected <T> void showNavigationPopup(AnActionEvent e,
                                         ChooseByNameModel model,
                                         final GotoActionCallback<T> callback,
                                         @Nullable final String findUsagesTitle,
                                         boolean useSelectionFromEditor,
                                         final boolean allowMultipleSelection) {
    showNavigationPopup(e, model, callback, findUsagesTitle, useSelectionFromEditor, allowMultipleSelection,
                        new DefaultChooseByNameItemProvider(getPsiContext(e)));
  }

  protected <T> void showNavigationPopup(AnActionEvent e,
                                         ChooseByNameModel model,
                                         final GotoActionCallback<T> callback,
                                         @Nullable final String findUsagesTitle,
                                         boolean useSelectionFromEditor,
                                         final boolean allowMultipleSelection,
                                         final DefaultChooseByNameItemProvider itemProvider) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    boolean mayRequestOpenInCurrentWindow = model.willOpenEditor() && FileEditorManagerEx.getInstanceEx(project).hasSplitOrUndockedWindows();
    Pair<String, Integer> start = getInitialText(useSelectionFromEditor, e);
    showNavigationPopup(callback, findUsagesTitle,
                        ChooseByNamePopup.createPopup(project, model, itemProvider, start.first,
                                                      mayRequestOpenInCurrentWindow,
                                                      start.second), allowMultipleSelection);
  }

  protected <T> void showNavigationPopup(final GotoActionCallback<T> callback,
                                         @Nullable final String findUsagesTitle,
                                         final ChooseByNamePopup popup) {
    showNavigationPopup(callback, findUsagesTitle, popup, true);
  }

  protected <T> void showNavigationPopup(final GotoActionCallback<T> callback,
                                         @Nullable final String findUsagesTitle,
                                         final ChooseByNamePopup popup,
                                         final boolean allowMultipleSelection) {

    final Class startedAction = myInAction;
    LOG.assertTrue(startedAction != null);

    popup.setCheckBoxShortcut(getShortcutSet());
    popup.setFindUsagesTitle(findUsagesTitle);
    final ChooseByNameFilter<T> filter = callback.createFilter(popup);

    if (historyEnabled() && popup.getAdText() == null) {
      popup.setAdText("Press " +
                      KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_MASK)) + " or " +
                      KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_MASK)) +
                      " to navigate through the history");
    }

    popup.invoke(new ChooseByNamePopupComponent.Callback() {
      @Override
      public void onClose() {
        //noinspection ConstantConditions
        if (startedAction != null && startedAction.equals(myInAction)) {
          String text = popup.getEnteredText();
          ourLastStrings.put(myInAction, Pair.create(text, popup.getSelectedIndex()));
          updateHistory(text);
          myInAction = null;
        }
        if (filter != null) {
          filter.close();
        }
      }

      private void updateHistory(@Nullable String text) {
        if (!StringUtil.isEmptyOrSpaces(text)) {
          List<String> history = ourHistory.get(myInAction);
          if (history == null) history = ContainerUtil.newArrayList();
          if (!text.equals(ContainerUtil.getFirstItem(history))) {
            history.add(0, text);
          }
          ourHistory.put(myInAction, history);
        }
      }

      @Override
      public void elementChosen(Object element) {
        callback.elementChosen(popup, element);
      }
    }, ModalityState.current(), allowMultipleSelection);

    final JTextField editor = popup.getTextField();

    final DocumentAdapter historyResetListener = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        myHistoryIndex = 0;
      }
    };

    abstract class HistoryAction extends DumbAwareAction {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(historyEnabled());
      }

      void setText(@NotNull List<String> strings) {
        javax.swing.text.Document document = editor.getDocument();
        document.removeDocumentListener(historyResetListener);
        editor.setText(strings.get(myHistoryIndex));
        document.addDocumentListener(historyResetListener);
        editor.selectAll();
      }
    }

    editor.getDocument().addDocumentListener(historyResetListener);

    new HistoryAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        List<String> strings = ourHistory.get(myInAction);
        setText(strings);
        myHistoryIndex = myHistoryIndex >= strings.size() - 1 ? 0 : myHistoryIndex + 1;
      }

    }.registerCustomShortcutSet(CustomShortcutSet.fromString("ctrl UP"), editor);

    new HistoryAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        List<String> strings = ourHistory.get(myInAction);
        setText(strings);
        myHistoryIndex = myHistoryIndex <= 0 ? strings.size() - 1 : myHistoryIndex - 1;
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("ctrl DOWN"), editor);
  }

  private static boolean historyEnabled() {
    return !ContainerUtil.isEmpty(ourHistory.get(myInAction));
  }
}
