// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager;
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector;
import com.intellij.ide.util.gotoByName.*;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Author: msk
 */
public abstract class GotoActionBase extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.GotoActionBase");

  protected static Class myInAction;
  private static final Map<Class, Pair<String, Integer>> ourLastStrings = ContainerUtil.newHashMap();
  private static final Map<Class, List<String>> ourHistory = ContainerUtil.newHashMap();
  private int myHistoryIndex;

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

  protected abstract void gotoActionPerformed(@NotNull AnActionEvent e);

  @Override
  public void update(@NotNull final AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final DataContext dataContext = event.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    boolean hasContributors = hasContributors(dataContext);
    presentation.setEnabled(!getClass().equals (myInAction) && (!requiresProject() || project != null) && hasContributors);
    presentation.setVisible(hasContributors);
  }

  protected boolean hasContributors(@NotNull DataContext dataContext) {
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
      String selectedText = getInitialTextForNavigation(e.getData(CommonDataKeys.EDITOR));
      if (selectedText != null) return new Pair<>(selectedText, 0);
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

  @Nullable
  public static String getInitialTextForNavigation(@Nullable Editor editor) {
    if (editor != null) {
      final String selectedText = editor.getSelectionModel().getSelectedText();
      if (selectedText != null && !selectedText.contains("\n")) {
        return selectedText;
      }
    }
    return null;
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
                        ChooseByNameModelEx.getItemProvider(model, getPsiContext(e)));
  }

  @Deprecated
  protected <T> void showNavigationPopup(AnActionEvent e,
                                         ChooseByNameModel model,
                                         final GotoActionCallback<T> callback,
                                         @Nullable final String findUsagesTitle,
                                         boolean useSelectionFromEditor,
                                         final boolean allowMultipleSelection,
                                         final DefaultChooseByNameItemProvider itemProvider) {
    showNavigationPopup(e, model, callback, findUsagesTitle, useSelectionFromEditor, allowMultipleSelection, (ChooseByNameItemProvider)itemProvider);
  }

  protected <T> void showNavigationPopup(AnActionEvent e,
                                         ChooseByNameModel model,
                                         final GotoActionCallback<T> callback,
                                         @Nullable final String findUsagesTitle,
                                         boolean useSelectionFromEditor,
                                         final boolean allowMultipleSelection,
                                         final ChooseByNameItemProvider itemProvider) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    boolean mayRequestOpenInCurrentWindow = model.willOpenEditor() && FileEditorManagerEx.getInstanceEx(project).hasSplitOrUndockedWindows();
    Pair<String, Integer> start = getInitialText(useSelectionFromEditor, e);
    ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, model, itemProvider, start.first,
                                                            mayRequestOpenInCurrentWindow,
                                                            start.second);
    //UIUtil.typeAheadUntilFocused(e.getInputEvent(), popup.getTextField());
    showNavigationPopup(callback, findUsagesTitle,
                        popup, allowMultipleSelection);
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
      popup.setAdText(IdeBundle.message("searcheverywhere.history.shortcuts.hint",
                                        KeymapUtil.getKeystrokeText(SearchTextField.ALT_SHOW_HISTORY_KEYSTROKE),
                                        KeymapUtil.getKeystrokeText(SearchTextField.SHOW_HISTORY_KEYSTROKE)));
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
      protected void textChanged(@NotNull DocumentEvent e) {
        myHistoryIndex = 0;
      }
    };

    abstract class HistoryAction extends DumbAwareAction {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(historyEnabled());
      }

      void setText(@NotNull List<String> strings) {
        String text = strings.get(myHistoryIndex);
        if (Comparing.equal(text, editor.getText())) {//don't rebuild popup list, it blinks
          return;
        }
        javax.swing.text.Document document = editor.getDocument();
        document.removeDocumentListener(historyResetListener);
        editor.setText(text);
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

    }.registerCustomShortcutSet(SearchTextField.ALT_SHOW_HISTORY_SHORTCUT, editor);

    new HistoryAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        List<String> strings = ourHistory.get(myInAction);
        setText(strings);
        myHistoryIndex = myHistoryIndex <= 0 ? strings.size() - 1 : myHistoryIndex - 1;
      }
    }.registerCustomShortcutSet(SearchTextField.SHOW_HISTORY_SHORTCUT, editor);
  }

  protected void showInSearchEverywherePopup(String searchProviderID, AnActionEvent evnt, boolean useEditorSelection) {
    SearchEverywhereManager seManager = SearchEverywhereManager.getInstance(evnt.getProject());
    FeatureUsageTracker.getInstance().triggerFeatureUsed(IdeActions.ACTION_SEARCH_EVERYWHERE + "." + searchProviderID);

    if (seManager.isShown()) {
      if (searchProviderID.equals(seManager.getShownContributorID())) {
        seManager.setShowNonProjectItems(!seManager.isShowNonProjectItems());
      }
      else {
        seManager.setShownContributor(searchProviderID);
        FUSUsageContext context = Optional.ofNullable(KeymapUtil.getEventCallerKeystrokeText(evnt))
          .map(shortcut -> FUSUsageContext.create(searchProviderID, shortcut))
          .orElseGet(() -> FUSUsageContext.create(searchProviderID));
        SearchEverywhereUsageTriggerCollector.trigger(evnt.getProject(), SearchEverywhereUsageTriggerCollector.TAB_SWITCHED, context);
      }
      return;
    }

    SearchEverywhereUsageTriggerCollector.trigger(evnt.getProject(), SearchEverywhereUsageTriggerCollector.DIALOG_OPEN, FUSUsageContext.create(searchProviderID));
    IdeEventQueue.getInstance().getPopupManager().closeAllPopups(false);
    String searchText = StringUtil.nullize(getInitialText(useEditorSelection, evnt).first);
    seManager.show(searchProviderID, searchText, evnt);
  }

  private static boolean historyEnabled() {
    return !ContainerUtil.isEmpty(ourHistory.get(myInAction));
  }
}
