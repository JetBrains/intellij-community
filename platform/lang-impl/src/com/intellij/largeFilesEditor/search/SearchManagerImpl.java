// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.search;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.find.SearchReplaceComponent;
import com.intellij.largeFilesEditor.editor.EditorGui;
import com.intellij.largeFilesEditor.editor.EditorManager;
import com.intellij.largeFilesEditor.editor.Page;
import com.intellij.largeFilesEditor.search.actions.*;
import com.intellij.largeFilesEditor.search.searchResultsPanel.SearchResultsToolWindow;
import com.intellij.largeFilesEditor.search.searchTask.*;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public class SearchManagerImpl implements SearchManager, SearchTaskCallback {
  private static final int CONTEXT_ONE_SIDE_LENGTH = 100;
  private static final long STATUS_TEXT_LIFE_TIME = 3000;

  private static final Logger logger = Logger.getInstance(SearchManagerImpl.class);
  private static final long PROGRESS_STATUS_UPDATE_PERIOD = 150;

  private final EditorManager editorManager;
  private final FileDataProviderForSearch fileDataProviderForSearch;
  private final SearchResultsPanelManagerAccessor searchResultsPanelManagerAccessor;

  private final JBList<SearchResult> closeSearchResultsList;

  private SearchTaskBase lastExecutedSearchTask;
  private boolean notFoundState;
  private long lastProgressStatusUpdateTime = System.currentTimeMillis();

  private SearchReplaceComponent searchManageGUI;
  private FindAllAction findAllAction;
  private FindForwardBackwardAction findForwardAction;
  private FindForwardBackwardAction findBackwardAction;
  private PrevNextOccurrenceAction nextOccurrenceAction;
  private PrevNextOccurrenceAction prevOccurrenceAction;
  private ToggleAction toggleCaseSensitiveAction;
  private ToggleAction toggleWholeWordsAction;
  private StatusTextAction statusTextAction;

  private String statusText;
  private boolean isStatusTextHidden;
  private long lastTimeStatusTextWasChanged;

  public SearchManagerImpl(@NotNull EditorManager editorManager,
                           FileDataProviderForSearch fileDataProviderForSearch,
                           @NotNull SearchResultsPanelManagerAccessor searchResultsPanelManagerAccessor) {
    this.editorManager = editorManager;
    this.fileDataProviderForSearch = fileDataProviderForSearch;
    this.searchResultsPanelManagerAccessor = searchResultsPanelManagerAccessor;

    createActions();
    createSearchManageGUI();
    attachListenersToSearchManageGUI();

    closeSearchResultsList = createCloseSearchResultsList();

    lastExecutedSearchTask = null;
    notFoundState = false;

    statusText = "";
    isStatusTextHidden = true;
    lastTimeStatusTextWasChanged = System.currentTimeMillis();
  }

  @Override
  public SearchReplaceComponent getSearchManageGUI() {
    return searchManageGUI;
  }

  @NotNull
  @Override
  public JBList<SearchResult> getResultList() {
    return closeSearchResultsList;
  }

  @Override
  public SearchTaskBase getLastExecutedSearchTask() {
    return lastExecutedSearchTask;
  }

  @Override
  public void onShowingPageChanged() {
    boolean launchSearch = false;

    if (editorManager.getSearchPanelsViewState() != EditorGui.SearchPanelsViewState.ALL_HIDDEN) {
      if (lastExecutedSearchTask != null) {
        if (lastExecutedSearchTask instanceof CloseSearchTask) {
          if (!lastExecutedSearchTask.isShouldStop()) {
            if (lastExecutedSearchTask.getOptions().searchForwardDirection
                && lastExecutedSearchTask.getOptions().leftBoundPageNumber != editorManager.getCurrentPageNumber()
                || !lastExecutedSearchTask.getOptions().searchForwardDirection
                   && lastExecutedSearchTask.getOptions().rightBoundPageNumber != editorManager.getCurrentPageNumber()) {
              if (closeSearchResultsList.isEmpty()) {
                launchSearch = true;
              }
              else {
                if (closeSearchResultsList.getSelectedValue() == null) {
                  launchSearch = true;
                }
                else if (closeSearchResultsList.getSelectedValue().startPosition.pageNumber != editorManager.getCurrentPageNumber()) {
                  launchSearch = true;
                }
              }
            }
          }
        }
      }
    }

    if (launchSearch) {
      SearchTaskOptions options = generateOptionsForNormalCloseSearch(true, true);
      launchCloseSearch(options);
    }
  }

  @Override
  public void onSearchActionHandlerExecuted() {
    editorManager.setSearchPanelsViewState(EditorGui.SearchPanelsViewState.ONLY_MANAGING_PANEL);
    searchManageGUI.getSearchTextComponent().selectAll();
    IdeFocusManager.getInstance(editorManager.getProject()).requestFocus(
      searchManageGUI.getSearchTextComponent(), true);
  }


  @Override
  public Collection<SearchResult> getSearchResultsForPage(Page page) {
    HashSet<SearchResult> searchResults = new HashSet<>();

    @SuppressWarnings("unchecked")  // closeSearchResultsList is based on CollectionListModel
      CollectionListModel<SearchResult> model = (CollectionListModel)closeSearchResultsList.getModel();

    SearchResult searchResult;
    for (int i = 0; i < model.getSize(); i++) {
      searchResult = model.getElementAt(i);

      if (searchResult.startPosition.pageNumber <= page.getPageNumber()
          && searchResult.endPostion.pageNumber >= page.getPageNumber()) {
        searchResults.add(searchResult);
      }
    }

    return searchResults;
  }

  @Override
  public SearchResult getSelectedSearchResult() {
    return closeSearchResultsList.getSelectedValue();
  }

  @NotNull
  @Override
  public EditorManager getEditorManager() {
    return editorManager;
  }

  @Override
  public void launchNewRangeSearch(long fromPageNumber, long toPageNumber, boolean forwardDirection) {

    SearchResultsToolWindow searchResultsToolWindow = searchResultsPanelManagerAccessor.getSearchResultsToolWindow(
      true,
      editorManager.getProject(), editorManager.getVirtualFile());

    if (searchResultsToolWindow == null) {
      logger.warn("launchNewRangeSearch(...): searchResultsToolWindow is null, however it shouldn't be.");
      Messages.showWarningDialog("Can't show tool window with search results. " +
                                 "Unexpected problem. Search is stopped.", "Error");
      return;
    }

    searchResultsPanelManagerAccessor.showSearchResultsToolWindow(searchResultsToolWindow);

    searchResultsToolWindow.clearAllResults();

    long pageNumber;
    if (forwardDirection) {
      pageNumber = fromPageNumber;
      if (pageNumber == SearchTaskOptions.NO_LIMIT) {
        pageNumber = 0;
      }
    }
    else {
      pageNumber = toPageNumber;
      if (pageNumber == SearchTaskOptions.NO_LIMIT) {
        try {
          pageNumber = fileDataProviderForSearch.getPagesAmount();
        }
        catch (IOException e) {
          logger.warn(e);
          Messages.showWarningDialog(
            "Can't launch range search because of error of working with file.", "Error");
          return;
        }
      }
    }
    searchResultsToolWindow.setLeftBorderPageNumber(pageNumber);
    searchResultsToolWindow.setRightBorderPageNumber(pageNumber);
    //searchResultsToolWindow.updateSearchFurtherBtns(); todo add this line???

    SearchTaskOptions options = new SearchTaskOptions()
      .setStringToFind(searchManageGUI.getSearchTextComponent().getText())
      .setSearchDirectionForward(forwardDirection)
      .setSearchBounds(fromPageNumber, SearchTaskOptions.NO_LIMIT,
                       toPageNumber, SearchTaskOptions.NO_LIMIT)
      .setCaseSensetive(toggleCaseSensitiveAction.isSelected(null))
      .setWholeWords(toggleWholeWordsAction.isSelected(null))
      .setContextOneSideLength(CONTEXT_ONE_SIDE_LENGTH);

    searchResultsToolWindow.setSearchTaskOptions(options);
    searchResultsToolWindow.updateTabName();

    launchRangeSearch(options, true);
  }

  @Override
  public void launchRangeSearch(SearchTaskOptions searchTaskOptions, boolean needToClearPrevSearchResults) {
    editorManager.setSearchPanelsViewState(EditorGui.SearchPanelsViewState.ONLY_MANAGING_PANEL);
    stopSearchTaskIfItExists();


    SearchResultsToolWindow searchResultsToolWindow = searchResultsPanelManagerAccessor.getSearchResultsToolWindow(
      true,
      editorManager.getProject(), editorManager.getVirtualFile());

    if (searchResultsToolWindow == null) {
      logger.warn("launchRangeSearch(...): searchResultsToolWindow is null, however it shouldn't be.");
      Messages.showWarningDialog("Can't show tool window with search results. " +
                                 "Unexpected problem. Search is stopped.", "Error");
      return;
    }

    searchResultsPanelManagerAccessor.showSearchResultsToolWindow(searchResultsToolWindow);

    searchResultsToolWindow.setSearchTaskOptions(searchTaskOptions);
    searchResultsToolWindow.setAdditionalStatusText(null);

    long pagesAmount;
    try {
      pagesAmount = fileDataProviderForSearch.getPagesAmount();
    }
    catch (IOException e) {
      logger.warn(e);
      Messages.showWarningDialog("Working with file error.", "Error");
      return;
    }

    if (needToClearPrevSearchResults) {
      searchResultsToolWindow.clearAllResults();
      searchResultsToolWindow.setLeftBorderPageNumber(searchTaskOptions.leftBoundPageNumber == SearchTaskOptions.NO_LIMIT ?
                                                      0 : searchTaskOptions.leftBoundPageNumber);
      searchResultsToolWindow.setRightBorderPageNumber(searchTaskOptions.rightBoundPageNumber == SearchTaskOptions.NO_LIMIT ?
                                                       pagesAmount - 1 : searchTaskOptions.rightBoundPageNumber);
      searchResultsToolWindow.updateSearchFurtherBtns();
    }

    final RangeSearchTask newRangeSearchTask = new RangeSearchTask(
      searchTaskOptions, editorManager.getProject(), fileDataProviderForSearch, this);
    lastExecutedSearchTask = newRangeSearchTask;
    String title = newRangeSearchTask.getTitleForBackgroundableTask();
    Task.Backgroundable task = new Task.Backgroundable(null, title, true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        newRangeSearchTask.setProgressIndicator(indicator);
        newRangeSearchTask.run();
      }
    };
    ProgressManager.getInstance().run(task);
  }

  @Override
  public void gotoNextOccurrence(boolean directionForward) {
    int gotoSearchResultIndex = getNextOccurrenceIndexIfCan(directionForward,
                                                            editorManager.getCurrentPageNumber(),
                                                            editorManager.getCaretPageOffset(),
                                                            closeSearchResultsList);

    if (gotoSearchResultIndex == -1) {

      boolean launchedLoopedCloseSearch = false;

      SearchTaskOptions normalCloseSearchOptions = generateOptionsForNormalCloseSearch(
        directionForward, false);

      if (notFoundState) {
        notFoundState = false;
        launchedLoopedCloseSearch = launchLoopedCloseSearchTaskIfNeeded(normalCloseSearchOptions);
      }

      if (!launchedLoopedCloseSearch) {
        launchCloseSearch(normalCloseSearchOptions);
      }
    }
    else {
      closeSearchResultsList.setSelectedIndex(gotoSearchResultIndex);
      setNewStatusText("");
    }
  }

  @CalledInAwt
  private void launchCloseSearch(SearchTaskOptions options) {
    if (StringUtil.isEmpty(searchManageGUI.getSearchTextComponent().getText())) {
      return;
    }

    editorManager.setSearchPanelsViewState(EditorGui.SearchPanelsViewState.ONLY_MANAGING_PANEL);

    stopSearchTaskIfItExists();
    lastExecutedSearchTask = new CloseSearchTask(
      options, editorManager.getProject(), fileDataProviderForSearch, this);
    ApplicationManager.getApplication().executeOnPooledThread(lastExecutedSearchTask);
  }

  private boolean launchLoopedCloseSearchTaskIfNeeded(SearchTaskOptions normalCloseSearchOptions) {
    if (!(lastExecutedSearchTask instanceof CloseSearchTask)) {
      return false;
    }
    if (!lastExecutedSearchTask.isFinished()) {
      return false;
    }

    SearchTaskOptions oldOptions = lastExecutedSearchTask.getOptions();
    if (oldOptions.loopedPhase) {
      return false;
    }
    if (!normalCloseSearchOptions.stringToFind.equals(oldOptions.stringToFind)
        || normalCloseSearchOptions.onlyOnePageSearch != oldOptions.onlyOnePageSearch
        || normalCloseSearchOptions.wholeWords != oldOptions.wholeWords
        || normalCloseSearchOptions.caseSensitive != oldOptions.caseSensitive
        || normalCloseSearchOptions.searchForwardDirection != oldOptions.searchForwardDirection
        || normalCloseSearchOptions.leftBoundPageNumber != oldOptions.leftBoundPageNumber
        || normalCloseSearchOptions.leftBoundCaretPageOffset != oldOptions.leftBoundCaretPageOffset
        || normalCloseSearchOptions.rightBoundPageNumber != oldOptions.rightBoundPageNumber
        || normalCloseSearchOptions.rightBoundCaretPageOffset != oldOptions.rightBoundCaretPageOffset) {
      return false;
    }

    SearchTaskOptions loopedOptions;
    try {
      loopedOptions = normalCloseSearchOptions.clone();
    }
    catch (CloneNotSupportedException e) {
      logger.warn(e);
      Messages.showWarningDialog("Error while searching.", "Search Error");
      return false;
    }
    loopedOptions.loopedPhase = true;
    if (loopedOptions.searchForwardDirection) {
      loopedOptions.setSearchBounds(
        0, SearchTaskOptions.NO_LIMIT,
        normalCloseSearchOptions.leftBoundPageNumber, normalCloseSearchOptions.leftBoundCaretPageOffset);
    }
    else {
      loopedOptions.setSearchBounds(
        normalCloseSearchOptions.rightBoundPageNumber, normalCloseSearchOptions.rightBoundCaretPageOffset,
        SearchTaskOptions.NO_LIMIT, SearchTaskOptions.NO_LIMIT);
    }

    launchCloseSearch(loopedOptions);

    return true;
  }

  private SearchTaskOptions generateOptionsForNormalCloseSearch(boolean directionForward, boolean onlyOnePageSearch) {

    SearchTaskOptions options = new SearchTaskOptions()
      .setOnlyOnePageSearch(onlyOnePageSearch)
      .setSearchDirectionForward(directionForward)
      .setStringToFind(searchManageGUI.getSearchTextComponent().getText())
      .setCaseSensetive(toggleCaseSensitiveAction.isSelected(null))
      .setWholeWords(toggleWholeWordsAction.isSelected(null))
      .setContextOneSideLength(CONTEXT_ONE_SIDE_LENGTH);

    if (onlyOnePageSearch) {
      long pageNumber = editorManager.getCurrentPageNumber();
      options.setSearchBounds(pageNumber, SearchTaskOptions.NO_LIMIT,
                              pageNumber, SearchTaskOptions.NO_LIMIT);
    }
    else if (!closeSearchResultsList.isEmpty() && closeSearchResultsList.getSelectedIndex() != -1) {
      Position position = closeSearchResultsList.getSelectedValue().startPosition;
      if (directionForward) {
        options.setSearchBounds(
          //position.pageNumber, position.symbolOffsetInPage + 1,
          position.pageNumber + 1, 0,
          SearchTaskOptions.NO_LIMIT, SearchTaskOptions.NO_LIMIT);
      }
      else {
        options.setSearchBounds(SearchTaskOptions.NO_LIMIT, SearchTaskOptions.NO_LIMIT,
                                position.pageNumber, position.symbolOffsetInPage);
      }
    }
    else {
      long caretPageNumber = editorManager.getCurrentPageNumber();
      int caretPageOffset = editorManager.getCaretPageOffset();
      if (directionForward) {
        options.setSearchBounds(caretPageNumber, caretPageOffset,
                                SearchTaskOptions.NO_LIMIT, SearchTaskOptions.NO_LIMIT);
      }
      else {
        options.setSearchBounds(SearchTaskOptions.NO_LIMIT, SearchTaskOptions.NO_LIMIT,
                                caretPageNumber, caretPageOffset);
      }
    }

    return options;
  }

  @Override
  public void tellFrameSearchResultsFound(RangeSearchTask caller,
                                          ArrayList<SearchResult> allMatchesAtFrame) {
    if (!allMatchesAtFrame.isEmpty()) {
      ApplicationManager.getApplication().invokeLater(() -> {
        SearchTaskOptions options = caller.getOptions();
        if (!caller.isShouldStop()) {
          SearchResultsToolWindow searchResultsToolWindow = searchResultsPanelManagerAccessor.getSearchResultsToolWindow(
            false,
            editorManager.getProject(), editorManager.getVirtualFile());

          if (searchResultsToolWindow == null) {
            caller.shouldStop();
            logger.warn("tellFrameSearchResultsFound(...): searchResultsToolWindow is null, " +
                        "however it should be created when the searching was launched.");
            Messages.showWarningDialog("Can't show tool window with search results. " +
                                       "Unexpected problem. Search is stopped.", "Error");
            return;
          }

          if (options.searchForwardDirection) {
            searchResultsToolWindow.addSearchResultsIntoEnd(allMatchesAtFrame);
          }
          else {
            searchResultsToolWindow.addSearchResultsIntoBeginning(allMatchesAtFrame);
          }

          if (allMatchesAtFrame.get(0).startPosition.pageNumber == editorManager.getCurrentPageNumber()) {
            editorManager.updateSearchHighlightingAndSelectedSearchResult();
          }

          if (searchResultsToolWindow.getAmountOfStoredSearchResults() > options.criticalAmountOfSearchResults) {
            stopSearchTaskIfItExists();
            if (options.searchForwardDirection) {
              searchResultsToolWindow.setRightBorderPageNumber(allMatchesAtFrame.get(0).startPosition.pageNumber);
            }
            else {
              searchResultsToolWindow.setLeftBorderPageNumber(allMatchesAtFrame.get(0).startPosition.pageNumber);
            }
            searchResultsToolWindow.setAdditionalStatusText("Search stopped because too many results were found.");
            searchResultsToolWindow.updateSearchFurtherBtns();
          }
        }
      });
    }
  }


  @Override
  public void tellSearchProgress(SearchTaskBase caller, long curPageNumber, long pagesAmount) {
    long time = System.currentTimeMillis();
    if (time - lastProgressStatusUpdateTime > PROGRESS_STATUS_UPDATE_PERIOD) {
      lastProgressStatusUpdateTime = time;
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!caller.isShouldStop()) {
          if (caller instanceof CloseSearchTask) {
            setNewStatusText("Searching on page " + (curPageNumber + 1) + "...");
          }
          else {
            // caller is instance of RangeSearchTask
            SearchResultsToolWindow searchResultsToolWindow = searchResultsPanelManagerAccessor.getSearchResultsToolWindow(
              false, editorManager.getProject(), editorManager.getVirtualFile());
            if (searchResultsToolWindow != null) {
              if (caller.getOptions().searchForwardDirection) {
                searchResultsToolWindow.setRightBorderPageNumber(curPageNumber);
              }
              else {
                searchResultsToolWindow.setLeftBorderPageNumber(curPageNumber);
              }
              searchResultsToolWindow.updateSearchFurtherBtns();
            }
          }
        }
      });
    }
  }

  @Override
  public void tellClosestResultFound(CloseSearchTask caller, ArrayList<SearchResult> allMatchesAtFrame,
                                     int indexOfClosestResult) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (!caller.isShouldStop()) {
        ((CollectionListModel<SearchResult>)closeSearchResultsList.getModel()).removeAll();
        // allMatchesAtFrame are sorted by definition...
        CollectionListModel<SearchResult> listModel = (CollectionListModel<SearchResult>)closeSearchResultsList.getModel();
        listModel.add(allMatchesAtFrame);
        closeSearchResultsList.setSelectedIndex(indexOfClosestResult);
        setNewStatusText("");
      }
    });
  }

  @Override
  public void tellSearchIsFinished(SearchTaskBase caller, long lastScannedPageNumber) {
    ApplicationManager.getApplication().invokeLater(() -> {

      SearchTaskOptions options = caller.getOptions();
      if (!caller.isShouldStop()) {
        if (caller instanceof CloseSearchTask) {
          if (options.loopedPhase) {
            setNewStatusText("Search complete. No more matches.");
            searchManageGUI.setNotFoundBackground();
          }
          else {
            if (options.onlyOnePageSearch) {
              if (!closeSearchResultsList.isEmpty()
                  && closeSearchResultsList.getModel().getElementAt(0).startPosition.pageNumber
                     != lastScannedPageNumber) {
                ((CollectionListModel<SearchResult>)closeSearchResultsList.getModel()).removeAll();
                editorManager.updateSearchHighlightingAndSelectedSearchResult();
              }
            }
            else {
              notFoundState = true;

              AnAction action = ActionManager.getInstance().getAction(
                options.searchForwardDirection ? IdeActions.ACTION_FIND_NEXT : IdeActions.ACTION_FIND_PREVIOUS);
              String shortcutsText = KeymapUtil.getFirstKeyboardShortcutText(action);
              String findAgainFromText = options.searchForwardDirection ? "start" : "end";
              String message;
              setNewStatusText("");
              if (!shortcutsText.isEmpty()) {
                message = String.format("\"%s\" not found, press %s to search from the %s",
                                        options.stringToFind, shortcutsText, findAgainFromText);
              }
              else {
                message = String.format("\"%s\" not found, perform \"%s\" action again to search from the %s",
                                        options.stringToFind, action.getTemplatePresentation().getText(), findAgainFromText);
              }

              JComponent hintComponent = HintUtil.createInformationLabel(message);
              final LightweightHint hint = new LightweightHint(hintComponent);
              HintManagerImpl.getInstanceImpl().showEditorHint(hint,
                                                               editorManager.getEditor(),
                                                               HintManager.UNDER,
                                                               HintManager.HIDE_BY_ANY_KEY |
                                                               HintManager.HIDE_BY_TEXT_CHANGE |
                                                               HintManager.HIDE_BY_SCROLLING,
                                                               0, false);
            }
          }
        }
        else {
          // caller is instance of RangeSearchTask
          SearchResultsToolWindow searchResultsToolWindow = searchResultsPanelManagerAccessor.getSearchResultsToolWindow(
            false,
            editorManager.getProject(), editorManager.getVirtualFile());
          if (searchResultsToolWindow != null) {
            if (options.searchForwardDirection) {
              searchResultsToolWindow.setRightBorderPageNumber(lastScannedPageNumber);
            }
            else {
              searchResultsToolWindow.setLeftBorderPageNumber(lastScannedPageNumber);
            }
            searchResultsToolWindow.setAdditionalStatusText("Search complete.");
            searchResultsToolWindow.updateSearchFurtherBtns();
          }
        }
      }
    });
  }

  @Override
  public void tellSearchWasStopped(SearchTaskBase caller, long curPageNumber) {
    if (caller instanceof RangeSearchTask) {
      ApplicationManager.getApplication().invokeLater(() -> {
        SearchResultsToolWindow searchResultsToolWindow = searchResultsPanelManagerAccessor.getSearchResultsToolWindow(
          false,
          editorManager.getProject(), editorManager.getVirtualFile());
        if (searchResultsToolWindow != null) {
          searchResultsToolWindow.updateSearchFurtherBtns();
        }
      });
    }
  }

  @Override
  public void tellSearchWasCatchedException(SearchTaskBase caller, IOException e) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (!caller.isShouldStop()) {
        if (caller instanceof CloseSearchTask) {
          setNewStatusText("Search stopped because something went wrong.");
        }
        else {
          SearchResultsToolWindow searchResultsToolWindow = searchResultsPanelManagerAccessor.getSearchResultsToolWindow(
            false,
            editorManager.getProject(), editorManager.getVirtualFile());
          if (searchResultsToolWindow != null) {
            searchResultsToolWindow.setAdditionalStatusText("Search stopped because something went wrong.");
          }
        }
      }
    });
  }

  @Override
  public void onEscapePressed() {
    if (lastExecutedSearchTask != null
        && !lastExecutedSearchTask.isShouldStop()
        && !lastExecutedSearchTask.isFinished()) {
      stopSearchTaskIfItExists();
      if (lastExecutedSearchTask instanceof CloseSearchTask) {
        setNewStatusText("Stopped by user.");
      }
    }
    else {
      stopSearchTaskIfItExists();
      ((CollectionListModel<SearchResult>)closeSearchResultsList.getModel()).removeAll();
      editorManager.setSearchPanelsViewState(EditorGui.SearchPanelsViewState.ALL_HIDDEN);
      IdeFocusManager
        .getInstance(editorManager.getProject())
        .requestFocus(editorManager.getEditor().getContentComponent(), false);
      editorManager.updateSearchHighlightingAndSelectedSearchResult();
    }
  }

  @Override
  public String getStatusText() {
    return statusText;
  }

  @Override
  public void updateStatusText() {
    if (isStatusTextHidden) {
      return;
    }

    if (System.currentTimeMillis() - lastTimeStatusTextWasChanged > STATUS_TEXT_LIFE_TIME) {
      statusText = "";
      isStatusTextHidden = true;
    }
  }

  @Override
  public void updateSearchManageGUIActions() {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      searchManageGUI.updateActions();
    }
    else {
      ApplicationManager.getApplication().invokeLater(() -> searchManageGUI.updateActions());
    }
  }


  @CalledInAwt
  @Override
  public void onSearchParametersChanged() {
    if (lastExecutedSearchTask instanceof CloseSearchTask) {
      lastExecutedSearchTask.shouldStop();
      setNewStatusText("");
    }
    ((CollectionListModel<SearchResult>)closeSearchResultsList.getModel()).removeAll();
    editorManager.updateSearchHighlightingAndSelectedSearchResult();
    searchManageGUI.setRegularBackground();
  }

  @Override
  public void onCaretPositionChanged(CaretEvent e) {
    if (closeSearchResultsList.getSelectedIndex() != -1
        && e.getEditor().getCaretModel().getOffset() != closeSearchResultsList.getSelectedValue().startPosition.symbolOffsetInPage
        && e.getEditor().getCaretModel().getOffset() != 0) {
      closeSearchResultsList.clearSelection();
    }
  }

  @CalledInAwt
  @Override
  public void askToStopSearching() {
    stopSearchTaskIfItExists();
    ((CollectionListModel<SearchResult>)closeSearchResultsList.getModel()).removeAll();
    editorManager.updateSearchHighlightingAndSelectedSearchResult();
  }

  @Override
  public void dispose() {
    if (lastExecutedSearchTask instanceof RangeSearchTask
        && !lastExecutedSearchTask.isShouldStop()
        && !lastExecutedSearchTask.isFinished()) {
      SearchResultsToolWindow searchResultsToolWindow = searchResultsPanelManagerAccessor.getSearchResultsToolWindow(
        false,
        editorManager.getProject(), editorManager.getVirtualFile());
      if (searchResultsToolWindow != null) {
        searchResultsToolWindow.setAdditionalStatusText("Search stopped because the editor was closed.");
      }
    }
    stopSearchTaskIfItExists();
  }

  @Override
  public void tellSearchResultsToolWindowWasClosed() {
    if (lastExecutedSearchTask instanceof RangeSearchTask) {
      lastExecutedSearchTask.shouldStop();
    }
  }

  private void createActions() {
    nextOccurrenceAction = new PrevNextOccurrenceAction(this, true);
    prevOccurrenceAction = new PrevNextOccurrenceAction(this, false);
    findAllAction = new FindAllAction(this);
    findForwardAction = new FindForwardBackwardAction(true, this);
    findBackwardAction = new FindForwardBackwardAction(false, this);
    toggleCaseSensitiveAction = new ToggleAction(this, "Match &Case");
    toggleWholeWordsAction = new ToggleAction(this, "W&ords");
    statusTextAction = new StatusTextAction(this);
  }

  private void createSearchManageGUI() {
    searchManageGUI = SearchReplaceComponent
      .buildFor(editorManager.getProject(),
                editorManager.getEditor().getContentComponent())
      .addPrimarySearchActions(prevOccurrenceAction,
                               nextOccurrenceAction,
                               new Separator(),
                               findAllAction,
                               findBackwardAction,
                               findForwardAction)
      .addExtraSearchActions(toggleCaseSensitiveAction,
                             toggleWholeWordsAction,
                             statusTextAction)
      //.addSearchFieldActions(new RestorePreviousSettingsAction())
      .withCloseAction(this::onEscapePressed)
      .build();
  }

  private void attachListenersToSearchManageGUI() {
    searchManageGUI.addListener(new SearchReplaceComponent.Listener() {
      @Override
      public void searchFieldDocumentChanged() {
        onSearchParametersChanged();
      }

      @Override
      public void replaceFieldDocumentChanged() {
      }

      @Override
      public void multilineStateChanged() {
      }
    });
  }

  private JBList<SearchResult> createCloseSearchResultsList() {
    CollectionListModel<SearchResult> model = new CollectionListModel<>();
    JBList<SearchResult> list = new JBList<>(model);
    list.addListSelectionListener(new CloseSearchResultsListSelectionListener(list));
    return list;
  }

  private void stopSearchTaskIfItExists() {
    if (lastExecutedSearchTask != null) {
      lastExecutedSearchTask.shouldStop();
    }
  }

  private void setNewStatusText(String newStatusText) {
    statusText = newStatusText;
    lastTimeStatusTextWasChanged = System.currentTimeMillis();

    isStatusTextHidden = StringUtil.isEmpty(newStatusText);

    updateSearchManageGUIActions();
  }

  private static int getNextOccurrenceIndexIfCan(boolean directionForward,
                                                 long currentPageNumber,
                                                 int caretPageOffset,
                                                 JBList<SearchResult> listResult) {
    ListModel<SearchResult> model = listResult.getModel();
    int index;
    SearchResult searchResult;

    if (model.getSize() == -1) {
      return -1;
    }

    if (listResult.getSelectedIndex() != -1) {

      index = listResult.getSelectedIndex();
      if (directionForward) {
        index++;
      }
      else {
        index--;
      }
    }
    else {

      index = 0;
      while (true) {
        if (index >= model.getSize()) {
          if (directionForward) {
            return -1;
          }
          else {
            return model.getSize() - 1;
          }
        }
        else {
          searchResult = model.getElementAt(index);
          if (currentPageNumber > searchResult.startPosition.pageNumber
              || currentPageNumber == searchResult.startPosition.pageNumber
                 && caretPageOffset >= searchResult.startPosition.symbolOffsetInPage) {
            index++;
          }
          else {
            break;
          }
        }
      }

      if (!directionForward) {
        index--;
      }
    }

    if (index < 0 || index >= model.getSize()) {
      return -1;
    }
    else {
      return index;
    }
  }


  private class CloseSearchResultsListSelectionListener implements ListSelectionListener {
    private final JBList<SearchResult> list;

    CloseSearchResultsListSelectionListener(JBList<SearchResult> list) {
      this.list = list;
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
      if (!e.getValueIsAdjusting()) { // it happens when the selecting process is over and the selected position is set finaly
        SearchResult selectedSearchResult = list.getSelectedValue();
        if (selectedSearchResult != null)
          editorManager.showSearchResult(selectedSearchResult);
      }
    }
  }
}
