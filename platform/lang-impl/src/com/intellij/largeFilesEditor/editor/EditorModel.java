// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.editor;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.icons.AllIcons;
import com.intellij.largeFilesEditor.changes.LocalChange;
import com.intellij.largeFilesEditor.editor.actinos.SimpleAction;
import com.intellij.largeFilesEditor.search.SearchResult;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Callable;

class EditorModel {
  private static final Logger logger = Logger.getInstance(EditorModel.class);

  private static final int EDITOR_LINE_BEGINNING_INDENT = 5;

  private final DataProvider dataProvider;
  private final EventsHandler eventsHandler;

  private final EditorGui gui;
  private final Editor editor;
  private Page currentShowingPage;
  private final Collection<RangeHighlighter> pageRangeHighlighters;
  private ActionToolbar toolbarNavigationActions;
  private ActionToolbar toolbarSaveAction;
  private ActionToolbar toolbarCancelSavingAction;


  EditorModel(Document document, Project project, DataProvider dataProvider, EventsHandler eventsHandler) {
    this.dataProvider = dataProvider;
    this.eventsHandler = eventsHandler;
    pageRangeHighlighters = new ArrayList<>();

    gui = new EditorGui();
    createBtnsForGui();
    setGuiListeners();

    editor = createSpecialEditor(document, project);
    gui.setEditorComponent(editor);

    registerShortcutsActions();
  }

  long getCurrentPageNumber() {
    return currentShowingPage != null ? currentShowingPage.getPageNumber() : -1;
  }

  Page getCurrentShowingPage() {
    return currentShowingPage;
  }

  Editor getEditor() {
    return editor;
  }

  JComponent getComponent() {
    return gui.panelMain;
  }

  EditorGui.SearchPanelsViewState getSearchPanelsViewState() {
    return gui.getCurrentSearchPanelsViewState();
  }

  int getCaretPageOffset() {
    return editor.getCaretModel().getOffset();
  }

  void setSearchPanelsViewState(EditorGui.SearchPanelsViewState searchPanelsViewState) {
    gui.setSearchPanelsViewState(searchPanelsViewState);
  }

  void setCurrentShowingPage(Page currentShowingPage) {
    this.currentShowingPage = currentShowingPage;
  }

  void setViewState(EditorGui.State pageView) {
    gui.setViewState(pageView);
  }

  void setEditorHeaderComponent(JComponent component) {
    gui.setEditorHeaderComponent(component);
  }

  void setSavingProgress(int progress) {
    gui.setSavingProgress(progress);
  }

  void setSavingStatusText(String text) {
    gui.setSavingStatusText(text);
  }

  void setShowingSavingStatus(boolean on) {
    gui.txtbxCurPageNum.setEnabled(!on);
    gui.setVisibleSavingStatusPanel(on);
  }

  void addCaretListener(CaretListener caretListener) {
    editor.getCaretModel().addCaretListener(caretListener);
  }

  <T> void putUserDataToEditor(@NotNull Key<T> key, T value) {
    editor.putUserData(key, value);
  }

  void updateCurrentPageLabelAndAdditionalInfo() {
    updateCurrentPageLabel();
    updateAdditionalPageInfo();
  }

  void updateSearchHighlightingAndSelectedSearchResult() {
    final HighlightManager highlightManager = HighlightManager.getInstance(dataProvider.getProject());
    for (RangeHighlighter pageRangeHighlighter : pageRangeHighlighters) {
      highlightManager.removeSegmentHighlighter(editor, pageRangeHighlighter);
    }
    pageRangeHighlighters.clear();

    if (gui.getCurrentSearchPanelsViewState() != EditorGui.SearchPanelsViewState.ALL_HIDDEN) {
      int firstSearchResultOnPageIndex;
      int lastSearchResultOnPageIndex;
      ListModel<SearchResult> searchResultsListModel = dataProvider.getSearchResultsListToHighlight();
      if (searchResultsListModel.getSize() > 0) {
        firstSearchResultOnPageIndex = 0;
        while (firstSearchResultOnPageIndex < searchResultsListModel.getSize()
               && searchResultsListModel.getElementAt(firstSearchResultOnPageIndex).startPosition.pageNumber < getCurrentPageNumber()
               && searchResultsListModel.getElementAt(firstSearchResultOnPageIndex).endPostion.pageNumber < getCurrentPageNumber()) {
          firstSearchResultOnPageIndex++;
        }

        if (firstSearchResultOnPageIndex >= searchResultsListModel.getSize()
            || searchResultsListModel.getElementAt(firstSearchResultOnPageIndex).startPosition.pageNumber > getCurrentPageNumber()) {
          return;
        }

        lastSearchResultOnPageIndex = firstSearchResultOnPageIndex + 1;
        while (lastSearchResultOnPageIndex < searchResultsListModel.getSize()
               && searchResultsListModel.getElementAt(lastSearchResultOnPageIndex).startPosition.pageNumber == getCurrentPageNumber()) {
          lastSearchResultOnPageIndex++;
        }
        lastSearchResultOnPageIndex--;

        SearchResult searchResult;
        TextAttributes textAttributes = EditorColorsManager.getInstance().getGlobalScheme()
          .getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES);

        int highlightStartOffset, highlightEndOffset;
        for (int i = firstSearchResultOnPageIndex; i <= lastSearchResultOnPageIndex; i++) {
          searchResult = searchResultsListModel.getElementAt(i);
          highlightStartOffset = calculateHighlightStartOffset(searchResult, getCurrentShowingPage());
          highlightEndOffset = calculateHighlightEndOffset(searchResult, getCurrentShowingPage());

          if (highlightStartOffset != -1 && highlightEndOffset != -1) {
            highlightManager.addRangeHighlight(
              editor,
              highlightStartOffset,
              highlightEndOffset,
              textAttributes, true, pageRangeHighlighters);
          }
        }
      }
    }
  }

  @CalledInAwt
  void updateSelectedSearchResultSelection(SearchResult selectedSearchResult) {
    if (selectedSearchResult != null) {

      int highlightStartOffset = calculateHighlightStartOffset(selectedSearchResult, getCurrentShowingPage());
      int highlightEndOffset = calculateHighlightEndOffset(selectedSearchResult, getCurrentShowingPage());

      if (highlightEndOffset != -1 && highlightStartOffset != -1) {
        SelectionModel selectionModel = editor.getSelectionModel();
        selectionModel.setSelection(highlightStartOffset, highlightEndOffset);
        CaretModel caretModel = editor.getCaretModel();
        caretModel.moveToOffset(highlightStartOffset);
        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      }
    }
  }

  void resetCaretAndSelection() {
    editor.getSelectionModel().removeSelection();
    editor.getCaretModel().moveToOffset(0);
  }

  void dispose() {
    if (editor != null) {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }

  private Editor createSpecialEditor(Document document, Project project) {
    boolean isExperimentalModeOn = dataProvider instanceof EditorManagerImpl && ((EditorManagerImpl)dataProvider).isExperimentalModeOn();
    Editor editor = isExperimentalModeOn ?
                    EditorFactory.getInstance().createEditor(document, project, EditorKind.MAIN_EDITOR) :
                    EditorFactory.getInstance().createViewer(document, project, EditorKind.MAIN_EDITOR);

    editor.getSettings().setLineMarkerAreaShown(false);
    editor.getSettings().setLineNumbersShown(false);
    editor.getSettings().setFoldingOutlineShown(false);
    editor.getContentComponent().setBorder(JBUI.Borders.emptyLeft(EDITOR_LINE_BEGINNING_INDENT));

    return editor;
  }

  /**
   * @param searchResult   - search result
   * @param curShowingPage - number of current showing page
   * @return start offset for highlighting of search result if it exists at least partly at current showing page
   * or '-1' if not
   */
  private int calculateHighlightStartOffset(SearchResult searchResult, Page curShowingPage) {
    if (searchResult.startPosition.pageNumber < curShowingPage.getPageNumber()) {
      return 0;
    }
    else if (searchResult.startPosition.pageNumber == curShowingPage.getPageNumber()) {
      return searchResult.startPosition.symbolOffsetInPage;
    }
    else {
      return -1;
    }
  }

  /**
   * @param searchResult   - search result
   * @param curShowingPage - number of current showing page
   * @return end offset for highlighting of search result if it exists at least partly at current showing page
   * or '-1' if not
   */
  private int calculateHighlightEndOffset(SearchResult searchResult, Page curShowingPage) {
    if (searchResult.endPostion.pageNumber > curShowingPage.getPageNumber()) {
      return curShowingPage.getText().length();
    }
    else if (searchResult.endPostion.pageNumber == curShowingPage.getPageNumber()) {
      return searchResult.endPostion.symbolOffsetInPage;
    }
    else {
      return -1;
    }
  }

  private void updateCurrentPageLabel() {
    if (!gui.txtbxCurPageNum.hasFocus()) {
      gui.txtbxCurPageNum.setText(String.valueOf(dataProvider.getExpectedPageNumber() + 1));
    }
  }

  private void updateAdditionalPageInfo() {
    try {
      long pagesAmount = dataProvider.getPagesAmount();
      gui.lblTextAfterCurPageNum.setText("of " + pagesAmount);

      if (pagesAmount >= 1) {
        gui.lblPositionPercents.setText("  " + dataProvider.getExpectedPageNumber() * 100 / (pagesAmount - 1) + "%  ");
      }
      else {
        gui.lblPositionPercents.setText("  100%  ");
      }
    }
    catch (IOException e) {
      gui.lblTextAfterCurPageNum.setText("of <error> ");
      logger.warn(e);
    }
  }

  void updateGuiActions() {
    toolbarNavigationActions.updateActionsImmediately();
    toolbarSaveAction.updateActionsImmediately();
    toolbarCancelSavingAction.updateActionsImmediately();
  }

  void showPlaceWhereUndoRedoWasDone(boolean isUndo, LocalChange localChange) {
    int startOffset = localChange.getOffset();
    int additionalOffset = isUndo ? localChange.getOldString().length() : localChange.getNewString().length();

    SelectionModel selectionModel = editor.getSelectionModel();
    selectionModel.removeSelection();
    CaretModel caretModel = editor.getCaretModel();
    caretModel.moveToOffset(startOffset + additionalOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    editor.getContentComponent().requestFocus();
  }

  private void onClickBtnGotoGo() {
    try {
      long pageNumber = Long.parseLong(gui.txtbxCurPageNum.getText()) - 1; // minus 1, because user's pages begin from 1, not from 0
      long maxAvailablePageNumber = dataProvider.getPagesAmount() - 1;

      if (pageNumber > maxAvailablePageNumber) {
        pageNumber = maxAvailablePageNumber;
      }
      else if (pageNumber < 0) {
        pageNumber = 0;
      }

      gui.txtbxCurPageNum.setText(String.valueOf(pageNumber + 1));

      if (dataProvider.getExpectedPageNumber() != pageNumber) {
        eventsHandler.trySwitchToNewPage(pageNumber);
      }
    }
    catch (NumberFormatException ex) {
      Messages
        .showInfoMessage("Wrong number format! Page number should be positive integer not bigger than amount of pages. Try again, please.",
                         "Warning...");
    }
    catch (IOException e) {
      Messages.showErrorDialog("Error opening file. Please, try to reopen it.", "ERROR");
      logger.warn(e);
    }

    gui.txtbxCurPageNum.selectAll();
  }

  private void onClickBtnSwitchFirst() {
    setFocusToEditorComponent();
    eventsHandler.trySwitchToNewPage(0);
  }

  private void onClickBtnSwitchPrev() {
    setFocusToEditorComponent();
    eventsHandler.trySwitchToNewPage(dataProvider.getExpectedPageNumber() - 1);
  }

  private void onClickBtnSwitchNext() {
    setFocusToEditorComponent();
    eventsHandler.trySwitchToNewPage(dataProvider.getExpectedPageNumber() + 1);
  }

  private void onClickBtnSwitchLast() {
    setFocusToEditorComponent();
    try {
      long pagesAmount = dataProvider.getPagesAmount();
      eventsHandler.trySwitchToNewPage(pagesAmount - 1);
    }
    catch (IOException e) {
      Messages.showErrorDialog("Error opening file. Please, try to reopen it.", "ERROR");
      logger.warn(e);
    }
  }

  private void setFocusToEditorComponent() {
    editor.getComponent().requestFocus();
  }

  private void setGuiListeners() {
    gui.txtbxCurPageNum.addActionListener(e -> {    // invokes only if ENTER-key pressed for JTextField
      onClickBtnGotoGo();
    });
  }

  private void createBtnsForGui() {
    Callable<Boolean> isEnabledCallableForPrev =
      //() -> (tokenLock.getActiveToken() == null || tokenLock.getActiveToken().getReason() != Reason.SAVING)
      () -> !dataProvider.isSavingLaunched()
            && dataProvider.getExpectedPageNumber() > 0;
    Callable<Boolean> isEnabledCallableForNext =
      () -> !dataProvider.isSavingLaunched()
            && dataProvider.getExpectedPageNumber() < dataProvider.getPagesAmount() - 1;

    AnAction actionFirstPage = new SimpleAction("First Page", "Switch to the first page",
                                                AllIcons.Actions.Play_first,
                                                isEnabledCallableForPrev, this::onClickBtnSwitchFirst);

    AnAction actionPrevPage = new SimpleAction("Previous Page", "Switch to the previous page",
                                               AllIcons.Actions.Play_back,
                                               isEnabledCallableForPrev, this::onClickBtnSwitchPrev);

    AnAction actionNextPage = new SimpleAction("Next Page", "Switch to the next page",
                                               AllIcons.Actions.Play_forward,
                                               isEnabledCallableForNext, this::onClickBtnSwitchNext);

    AnAction actionLastPage = new SimpleAction("Last Page", "Switch to the last page",
                                               AllIcons.Actions.Play_last,
                                               isEnabledCallableForNext, this::onClickBtnSwitchLast);

    DefaultActionGroup actionGroupNavigation = new DefaultActionGroup();
    actionGroupNavigation.add(actionFirstPage);
    actionGroupNavigation.add(actionPrevPage);
    actionGroupNavigation.add(actionNextPage);
    actionGroupNavigation.add(actionLastPage);

    toolbarNavigationActions = createTweakedHorizontalActionToolbar(actionGroupNavigation);
    gui.panelNavigationBtns.add(toolbarNavigationActions.getComponent());

    Callable<Boolean> isEnabledCallableForSave =
      () -> !dataProvider.isSavingLaunched();

    AnAction actionSave = new SimpleAction("", "",
                                           AllIcons.Actions.Menu_saveall,
                                           isEnabledCallableForSave,
                                           eventsHandler::trySave);

    DefaultActionGroup actionGroupSave = new DefaultActionGroup();
    if (dataProvider instanceof EditorManagerImpl && ((EditorManagerImpl)dataProvider).isExperimentalModeOn()) {
      actionGroupSave.add(actionSave);
    }
    toolbarSaveAction = createTweakedHorizontalActionToolbar(actionGroupSave);
    gui.panelSaveActions.add(toolbarSaveAction.getComponent());

    AnAction actionCancelSaving = new SimpleAction("", "",
                                                   AllIcons.Actions.Cancel,
                                                   dataProvider::isSavingLaunched,
                                                   eventsHandler::cancelSaving);

    DefaultActionGroup actionGroupCancelSaving = new DefaultActionGroup();
    actionGroupCancelSaving.add(actionCancelSaving);
    toolbarCancelSavingAction = createTweakedHorizontalActionToolbar(actionGroupCancelSaving);
    gui.panelSavingStatus.add(toolbarCancelSavingAction.getComponent());
  }

  private ActionToolbar createTweakedHorizontalActionToolbar(DefaultActionGroup group) {
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.TOOLBAR, group, true);
    if (toolbar instanceof ActionToolbarImpl) {
      ((ActionToolbarImpl)toolbar).setBorder(null);
    }
    return toolbar;
  }

  private void registerShortcutsActions() {
    new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        eventsHandler.onUndoRedoActionInvoked(true);
      }
    }.registerCustomShortcutSet(
      KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_UNDO),
      gui.getRootComponent());


    new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        eventsHandler.onUndoRedoActionInvoked(false);
      }
    }.registerCustomShortcutSet(
      KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_REDO),
      gui.getRootComponent());
  }

  interface DataProvider {

    long getPagesAmount() throws IOException;

    long getExpectedPageNumber();

    Project getProject();

    ListModel<SearchResult> getSearchResultsListToHighlight();

    boolean isSavingLaunched();
  }

  interface EventsHandler {

    void trySwitchToNewPage(long newPageNumber);

    void trySave();

    void cancelSaving();

    void onUndoRedoActionInvoked(boolean isUndo);
  }
}
