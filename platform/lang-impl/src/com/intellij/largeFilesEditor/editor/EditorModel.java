// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.editor;

import com.intellij.largeFilesEditor.changes.LocalChange;
import com.intellij.largeFilesEditor.file.ReadingPageResultHandler;
import com.intellij.largeFilesEditor.search.SearchResult;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

class EditorModel {
  private static final Logger LOG = Logger.getInstance(EditorModel.class);

  private static final int EDITOR_LINE_BEGINNING_INDENT = 5;
  private static final long NO_NEED_TO_READ_PAGE_CODE = -1;

  private final DataProvider dataProvider;
  private final EventsHandler eventsHandler;

  private final Editor editor;
  private final Document document;
  private Page currentShowingPage;
  private final Collection<RangeHighlighter> pageRangeHighlighters;
  private ActionToolbar toolbarNavigationActions;
  private ActionToolbar toolbarSaveAction;
  private ActionToolbar toolbarCancelSavingAction;

  ArrayList<Page> pagesInDocument = new ArrayList<>();
  List<Page> pagesCash = new LinkedList<>();
  List<Long> numbersOfRequestedForReadingPages = new LinkedList<>();
  final AbsoluteEditorPosition targetVisiblePosition = new AbsoluteEditorPosition(0, 0);
  private boolean isLocalScrollBarStabilized = false;
  AtomicBoolean isUpdateRequested = new AtomicBoolean(false);

  private boolean isRealCaretCanAffectOnTarget = true;
  private AbsoluteCaretPostioin targetCaretPosition = new AbsoluteCaretPostioin(0, 0);
  private boolean isNeedToShowCaret = false;

  private final JPanel panelMain;
  private final ExecutorService myPageReaderExecutor =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("Large File Editor Page Reader Executor", 1);
  private final GlobalScrollBar myGlobalScrollBar;
  private final LocalInvisibleScrollBar myLocalInvisibleScrollBar;


  EditorModel(Document document, Project project, DataProvider dataProvider, EventsHandler eventsHandler) {
    this.document = document;
    this.dataProvider = dataProvider;
    this.eventsHandler = eventsHandler;
    pageRangeHighlighters = new ArrayList<>();

    //gui = new EditorGui();
    //createBtnsForGui();
    //setGuiListeners();

    editor = createSpecialEditor(document, project);

    editor.getCaretModel().addCaretListener(new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent event) {
        fireRealCaretPositionChanged();
      }
    });

    //gui.setEditorComponent(editor);
    //registerShortcutsActions();

    myGlobalScrollBar = new GlobalScrollBar(this);
    //globalScrollBar.setUI(new BasicScrollBarUI());
    myGlobalScrollBar.setValues(10, 10, 0, 100);

    myLocalInvisibleScrollBar = new LocalInvisibleScrollBar(this);
    ((EditorEx)editor).getScrollPane().setVerticalScrollBar(myLocalInvisibleScrollBar);
    ((EditorEx)editor).getScrollPane().getVerticalScrollBar().setOpaque(true);

    panelMain = new MyJPanel();
    panelMain.setLayout(new BorderLayout());
    panelMain.add(editor.getComponent(), BorderLayout.CENTER);
    panelMain.add(myGlobalScrollBar, BorderLayout.EAST);
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
    return panelMain;
  }

  EditorGui.SearchPanelsViewState getSearchPanelsViewState() {
    //return gui.getCurrentSearchPanelsViewState();
    return EditorGui.SearchPanelsViewState.ALL_HIDDEN;
  }

  int getCaretPageOffset() {
    return editor.getCaretModel().getOffset();
  }

  void setSearchPanelsViewState(EditorGui.SearchPanelsViewState searchPanelsViewState) {
    //gui.setSearchPanelsViewState(searchPanelsViewState);
  }

  void setCurrentShowingPage(Page currentShowingPage) {
    this.currentShowingPage = currentShowingPage;
  }

  void setViewState(EditorGui.State pageView) {
    //gui.setViewState(pageView);
  }

  void setEditorHeaderComponent(JComponent component) {
    //gui.setEditorHeaderComponent(component);
  }

  void setSavingProgress(int progress) {
    //gui.setSavingProgress(progress);
  }

  void setSavingStatusText(String text) {
    //gui.setSavingStatusText(text);
  }

  void setShowingSavingStatus(boolean on) {
    //gui.txtbxCurPageNum.setEnabled(!on);
    //gui.setVisibleSavingStatusPanel(on);
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
    /*final HighlightManager highlightManager = HighlightManager.getInstance(dataProvider.getProject());
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
    }*/
  }

  @CalledInAwt
  void updateSelectedSearchResultSelection(SearchResult selectedSearchResult) {
    /*if (selectedSearchResult != null) {

      int highlightStartOffset = calculateHighlightStartOffset(selectedSearchResult, getCurrentShowingPage());
      int highlightEndOffset = calculateHighlightEndOffset(selectedSearchResult, getCurrentShowingPage());

      if (highlightEndOffset != -1 && highlightStartOffset != -1) {
        SelectionModel selectionModel = editor.getSelectionModel();
        selectionModel.setSelection(highlightStartOffset, highlightEndOffset);
        CaretModel caretModel = editor.getCaretModel();
        caretModel.moveToOffset(highlightStartOffset);
        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      }
    }*/
  }

  private void fireRealCaretPositionChanged() {
    if (isRealCaretCanAffectOnTarget) {
      if (tryGetTargetCaretOffsetInDocumentWithMargin() != -1) {
        reflectRealToTargetCaretPostion();
        isNeedToShowCaret = true;
        requestUpdate();
      }
    }
  }

  private void reflectRealToTargetCaretPostion() {
    int caretOffset = editor.getCaretModel().getPrimaryCaret().getOffset();

    for (int i = pagesInDocument.size() - 1; i >= 0; i--) {
      int symbolOffsetToStartOfPage = getSymbolOffsetToStartOfPage(i, pagesInDocument);
      if (caretOffset >= symbolOffsetToStartOfPage) {
        targetCaretPosition.set(pagesInDocument.get(i).getPageNumber(), caretOffset - symbolOffsetToStartOfPage);
        break;
      }
    }
  }

  public void fireGlobalScrollBarValueChangedFromOutside(long pageNumber) {
    long pagesAmount;
    try {
      pagesAmount = dataProvider.getPagesAmount();
    }
    catch (IOException e) {
      LOG.info(e);
      return;
    }

    if (pageNumber < 0 || pageNumber > pagesAmount) {
      LOG.warn("[Large File Editor Subsystem] EditorModel.fireGlobalScrollBarValueChangedFromOutside(pageNumber):" +
               " Illegal argument pageNumber. Expected: 0 < pageNumber <= pagesAmount." +
               " Actual: pageNumber=" + pageNumber + " pagesAmount=" + pagesAmount);
      return;
    }
    targetVisiblePosition.set(pageNumber, 0);
    update();
  }

  public void setAbsoluteEditorPosition(AbsoluteEditorPosition newAbsoluteEditorPosition) {
    targetVisiblePosition.copyFrom(newAbsoluteEditorPosition);
    update();
  }

  void requestUpdate() {
    // elimination of duplicates of update() tasks in EDT queue
    if (isUpdateRequested.compareAndSet(false, true)) {
      ApplicationManager.getApplication().invokeLater(() -> {
        isUpdateRequested.set(false);
        update();
      });
    }
  }

  @CalledInAwt
  void update() {
    // TODO: 2019-04-05 encapsulate document+pagesInDocumentList ?

    if (isNeedToShowCaret) {
      targetVisiblePosition.set(targetCaretPosition.pageNumber,
                                targetVisiblePosition.verticalScrollOffset);
    }

    normalizePagesInDocumentListBeginning();

    if (pagesInDocument.isEmpty()) {
      long pageNumber = targetVisiblePosition.pageNumber == 0 ? 0 : targetVisiblePosition.pageNumber - 1;
      Page page = tryGetPageFromCash(pageNumber);
      if (page != null) {
        setFirstPageIntoEmptyDocument(page);
      }
      else {
        requestReadPage(pageNumber);
        return;
        // UPDATE CHAIN EXIT POINT
      }
    }

    normalizePagesInDocumentListEnding();

    tryReflectTargetCaretPositionToReal();

    tryNormalizeTargetVisiblePosition();

    tryShowWhatNeedToShow();

    updateGlobalStrollBarView();

    long nextPageNumberToAdd = -1;
    try {
      nextPageNumberToAdd = tryGetNextPageNumberToAdd();
    }
    catch (IOException e) {
      LOG.info(e);
      Messages.showErrorDialog("Error while working with file. Try to reopen it.", "ERROR");
    }
    if (nextPageNumberToAdd != -1) {
      Page nextPageToAdd = tryGetPageFromCash(nextPageNumberToAdd);
      if (nextPageToAdd != null) {
        setNextPageIntoDocument(nextPageToAdd);
        requestUpdate();
      }
      else {
        requestReadPage(nextPageNumberToAdd);
      }
      return;
    }

    pagesCash.clear();

    if (isNeedToShowCaret) {
      if (tryGetTargetCaretOffsetInDocumentWithMargin() != -1) {
        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        isNeedToShowCaret = false;
      }
      else {
        LOG.warn("[Large File Editor Subsystem] EditorMode.update(): can't show caret. "
                 + " targetCaretPosition.pageNumber=" + targetCaretPosition.pageNumber
                 + " targetCaretPosition.symbolOffsetInPage=" + targetCaretPosition.symbolOffsetInPage
                 + " targetVisiblePosition.pageNumber=" + targetVisiblePosition.pageNumber
                 + " targetVisiblePosition.verticalScrollOffset=" + targetVisiblePosition.verticalScrollOffset);
      }
    }
    else {
      if (!isRealCaretMathesTarget()) {
        setCaretToConvenientPosition();
      }
    }
  }

  private void setCaretToConvenientPosition() {
    int offset = tryGetConvenientOffsetForCaret();
    if (offset != -1) {
      editor.getCaretModel().moveToOffset(offset);
      reflectRealToTargetCaretPostion();
    }
    else {
      LOG.info("[Large File Editor Subsystem] EditorModel.setCaretToConvenientPosition(): " +
               "Can't set caret to convenient position.");
    }
  }

  private boolean isRealCaretMathesTarget() {
    int targetCaretOffsetInDocument = tryGetTargetCaretOffsetInDocumentWithMargin();
    return targetCaretOffsetInDocument == editor.getCaretModel().getOffset();
  }

  private void tryReflectTargetCaretPositionToReal() {
    int targetCaretOffsetInDocument = tryGetTargetCaretOffsetInDocumentWithMargin();
    if (targetCaretOffsetInDocument != -1) {
      runCaretListeningTransparentCommand(
        () -> editor.getCaretModel().moveToOffset(targetCaretOffsetInDocument));
    }
  }

  private int tryGetConvenientOffsetForCaret() {
    Rectangle visibleArea = editor.getScrollingModel().getVisibleAreaOnScrollingFinished();
    return editor.logicalPositionToOffset(editor.xyToLogicalPosition(new Point(
      0, visibleArea.y + editor.getLineHeight()
    )));

    /*int indexOfPage = tryGetIndexOfNeededPageInList(targetVisiblePosition.pageNumber, pagesInDocument);
    if (indexOfPage == -1) {
      return -1;
    }

    int symbolOffsetToStartOfPage = getSymbolOffsetToStartOfPage(indexOfPage, pagesInDocument);
    return symbolOffsetToStartOfPage;*/
    /*Point point = editor.offsetToXY(symbolOffsetToStartOfPage);
    if (point.x == 0) {
      return symbolOffsetToStartOfPage;
    } else {
      point.x = 0;
      return editor.logicalPositionToOffset(editor.xyToLogicalPosition(point));
    }*/
  }

  private int tryGetTargetCaretOffsetInDocumentWithMargin() {
    int offset = tryGetTargetCaretOffsetInDocument();
    if (offset == -1) return -1;

    int startOfAllowedRange = tryGetStartMarginForTargetCaretInDocument();

    int endOfAllowedRange = getSymbolOffsetToStartOfPage(pagesInDocument.size(), pagesInDocument);
    try {
      if (pagesInDocument.get(pagesInDocument.size() - 1).getPageNumber() != dataProvider.getPagesAmount() - 1) {
        endOfAllowedRange -= pagesInDocument.get(pagesInDocument.size() - 1).getText().length() / 2;
      }
    }
    catch (IOException e) {
      LOG.warn(e);
    }

    if (offset > startOfAllowedRange && offset < endOfAllowedRange) {
      return offset;
    }
    else {
      return -1;
    }
  }

  private int tryGetStartMarginForTargetCaretInDocument() {
    if (pagesInDocument.size() > 0 && pagesInDocument.get(0).getPageNumber() != 0) {
      return pagesInDocument.get(0).getText().length() / 2;
    }
    else {
      return -1;
    }
  }

  private int tryGetTargetCaretOffsetInDocument() {
    int indexOfPage = tryGetIndexOfNeededPageInList(targetCaretPosition.pageNumber, pagesInDocument);
    if (indexOfPage != -1) {
      int targetCaretOffsetInDocument =
        getSymbolOffsetToStartOfPage(indexOfPage, pagesInDocument) + targetCaretPosition.symbolOffsetInPage;

      if (targetCaretOffsetInDocument >= 0 && targetCaretOffsetInDocument < document.getTextLength()) {
        return targetCaretOffsetInDocument;
      }
      else {
        LOG.warn("[Large File Editor Subsystem] EditorModel.tryGetTargetCaretOffsetInDocument():"
                 + " Invalid targetCaretPosition state."
                 + " targetCaretPosition.pageNumber=" + targetCaretPosition.pageNumber
                 + " targetCaretPosition.symbolOffsetInPage=" + targetCaretPosition.symbolOffsetInPage
                 + " targetCaretOffsetInDocument=" + targetCaretOffsetInDocument
                 + " document.getTextLength()=" + document.getTextLength());
      }
    }
    return -1;
  }

  // TODO: 2019-04-10 need to handle possible 'long' values
  private void updateGlobalStrollBarView() {
    long pagesAmount;
    try {
      pagesAmount = dataProvider.getPagesAmount();
    }
    catch (IOException e) {
      LOG.warn(e);
      return;
    }

    int extent = 1; // to make thumb of minimum size
    myGlobalScrollBar.setValues((int)targetVisiblePosition.pageNumber, extent, 0, (int)pagesAmount + 1);
  }

  private long tryGetNextPageNumberToAdd() throws IOException {
    if (pagesInDocument.isEmpty()) {
      return targetVisiblePosition.pageNumber == 0
             ? targetVisiblePosition.pageNumber
             : targetVisiblePosition.pageNumber - 1;
    }

    int visibleTargetPageIndex = tryGetIndexOfNeededPageInList(targetVisiblePosition.pageNumber, pagesInDocument);
    if (visibleTargetPageIndex == -1) {
      // some pages before visible one exist and are located in list => just need to get next to last in list
      return tryGetNumberOfNextToDocumentPage();
    }

    // check if we really need some extra pages or already not
    int offsetToFirstVisibleSymbolOfTargetVisiblePage = getSymbolOffsetToStartOfPage(visibleTargetPageIndex, pagesInDocument);
    int topOfTargetVisiblePage = offsetToY(offsetToFirstVisibleSymbolOfTargetVisiblePage);
    int topOfTargetVisibleArea = topOfTargetVisiblePage + targetVisiblePosition.verticalScrollOffset;
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    int bottomOfTargetVisibleArea = topOfTargetVisibleArea + visibleArea.height;

    int lastVisiblePageIndex = visibleTargetPageIndex;
    int offsetToEndOfLastVisiblePage = getSymbolOffsetToStartOfPage(lastVisiblePageIndex + 1, pagesInDocument);
    while (lastVisiblePageIndex + 1 < pagesInDocument.size()
           && offsetToY(offsetToEndOfLastVisiblePage) <= bottomOfTargetVisibleArea) {
      lastVisiblePageIndex++;
      offsetToEndOfLastVisiblePage = getSymbolOffsetToStartOfPage(lastVisiblePageIndex + 1, pagesInDocument);
    }

    if (pagesInDocument.size() - 1 == lastVisiblePageIndex) {
      // we need at least 1 invisible page after visible ones, if it's not the end
      return tryGetNumberOfNextToDocumentPage();
    }
    return -1;
  }

  private long tryGetNumberOfNextToDocumentPage() throws IOException {
    long nextPageNumber = pagesInDocument.get(pagesInDocument.size() - 1).getPageNumber() + 1;
    return nextPageNumber < dataProvider.getPagesAmount() ? nextPageNumber : -1;
  }

  private void tryNormalizeTargetVisiblePosition() {
    boolean smthChanged = true;
    try {
      while (smthChanged) {
        smthChanged = tryNormalizeTargetEditorViewPosition_iteration();
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  private boolean tryNormalizeTargetEditorViewPosition_iteration() throws IOException {
    long pagesAmountInFile = dataProvider.getPagesAmount();
    if (targetVisiblePosition.pageNumber >= pagesAmountInFile) {
      targetVisiblePosition.set(pagesAmountInFile, -1);
    }

    if (targetVisiblePosition.verticalScrollOffset < 0) {
      if (targetVisiblePosition.pageNumber == 0) {
        targetVisiblePosition.set(0, 0);
        return true;
      }

      int prevPageIndex = tryGetIndexOfNeededPageInList(targetVisiblePosition.pageNumber - 1, pagesInDocument);
      if (prevPageIndex == -1) {
        return false;
      }

      int symbolOffsetToBeginningOfPrevPage = getSymbolOffsetToStartOfPage(prevPageIndex, pagesInDocument);
      int symbolOffsetToBeginningOfTargetPage = getSymbolOffsetToStartOfPage(prevPageIndex + 1, pagesInDocument);
      int verticalOffsetToBeginningOfPrevPage = offsetToY(symbolOffsetToBeginningOfPrevPage);
      int verticalOffsetToBeginningOfTargetPage = offsetToY(symbolOffsetToBeginningOfTargetPage);

      targetVisiblePosition.set(targetVisiblePosition.pageNumber - 1,
                                targetVisiblePosition.verticalScrollOffset
                                + verticalOffsetToBeginningOfTargetPage - verticalOffsetToBeginningOfPrevPage);
      return true;
    }

    // here targetVisiblePosition.pageNumber < pagesAmountInFile
    //   && targetVisiblePosition.verticalScrollOffset >= 0

    int visibleTargetPageIndex = tryGetIndexOfNeededPageInList(targetVisiblePosition.pageNumber, pagesInDocument);
    if (visibleTargetPageIndex == -1) {
      return false;
    }

    int symbolOffsetToBeginningOfTargetVisiblePage = getSymbolOffsetToStartOfPage(visibleTargetPageIndex, pagesInDocument);
    int symbolOffsetToEndOfTargetVisiblePage = getSymbolOffsetToStartOfPage(visibleTargetPageIndex + 1, pagesInDocument);
    int topOfTargetVisiblePage = offsetToY(symbolOffsetToBeginningOfTargetVisiblePage);

    int bottomOfExpectedVisibleArea =
      topOfTargetVisiblePage + targetVisiblePosition.verticalScrollOffset + editor.getScrollingModel().getVisibleArea().height;
    if (bottomOfExpectedVisibleArea > editor.getContentComponent().getHeight()) {
      int indexOfLastLastPage = tryGetIndexOfNeededPageInList(pagesAmountInFile - 1, pagesInDocument);
      if (indexOfLastLastPage == -1) {
        return false;
      }
      int extraDifference = bottomOfExpectedVisibleArea - editor.getContentComponent().getHeight();
      targetVisiblePosition.set(targetVisiblePosition.pageNumber,
                                targetVisiblePosition.verticalScrollOffset - extraDifference);
      return true;
    }

    // here targetVisiblePosition.pageNumber < pagesAmountInFile
    //   && targetVisiblePosition.verticalScrollOffset >= 0
    //   && bottomOfExpectedVisibleArea <= editor.getContentComponent().getHeight()

    int bottomOfTargetVisiblePage = offsetToY(symbolOffsetToEndOfTargetVisiblePage);
    if (topOfTargetVisiblePage + targetVisiblePosition.verticalScrollOffset >= bottomOfTargetVisiblePage) {
      targetVisiblePosition.set(targetVisiblePosition.pageNumber + 1,
                                targetVisiblePosition.verticalScrollOffset
                                - bottomOfTargetVisiblePage + topOfTargetVisiblePage);
      return true;
    }
    return false;
  }

  private void normalizePagesInDocumentListEnding() {
    int visibleTargetPageIndex = tryGetIndexOfNeededPageInList(targetVisiblePosition.pageNumber, pagesInDocument);
    if (visibleTargetPageIndex == -1) {
      return;
    }

    int offsetToFirstVisibleSymbolOfTargetVisiblePage = getSymbolOffsetToStartOfPage(visibleTargetPageIndex, pagesInDocument);
    int topOfTargetVisiblePage = offsetToY(offsetToFirstVisibleSymbolOfTargetVisiblePage);
    int topOfTargetVisibleArea = topOfTargetVisiblePage + targetVisiblePosition.verticalScrollOffset;
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    int bottomOfTargetVisibleArea = topOfTargetVisibleArea + visibleArea.height;

    //Dimension documentSize = editor.getContentComponent().getSize();

    int lastVisiblePageIndex = visibleTargetPageIndex;
    int offsetToEndOfLastVisiblePage = getSymbolOffsetToStartOfPage(lastVisiblePageIndex + 1, pagesInDocument);
    while (lastVisiblePageIndex + 1 < pagesInDocument.size()
           && offsetToY(offsetToEndOfLastVisiblePage) <= bottomOfTargetVisibleArea) {
      lastVisiblePageIndex++;
      offsetToEndOfLastVisiblePage = getSymbolOffsetToStartOfPage(lastVisiblePageIndex + 1, pagesInDocument);
    }

    int maxAllowedAmountOfPagesInDocumentHere = lastVisiblePageIndex + 2 + 1;  // max 2 invisible pages in the end
    if (pagesInDocument.size() > maxAllowedAmountOfPagesInDocumentHere) {
      for (int i = pagesInDocument.size() - 1; i >= maxAllowedAmountOfPagesInDocumentHere; i--) {
        pagesCash.add(pagesInDocument.get(i));
        pagesInDocument.remove(i);
      }
    }
  }

  private void tryShowWhatNeedToShow() {
    if (!isLocalScrollBarStabilized) {
      int visibleTargetPageIndex = tryGetIndexOfNeededPageInList(targetVisiblePosition.pageNumber, pagesInDocument);
      if (visibleTargetPageIndex == -1) {
        return;
      }

      int symbolOffsetToBeginningOfTargetVisiblePage = getSymbolOffsetToStartOfPage(visibleTargetPageIndex, pagesInDocument);
      int topOfTargetVisiblePage = offsetToY(symbolOffsetToBeginningOfTargetVisiblePage);
      int targetTopOfVisibleArea = topOfTargetVisiblePage + targetVisiblePosition.verticalScrollOffset;

      if (editor.getScrollingModel().getVisibleAreaOnScrollingFinished().y != targetTopOfVisibleArea) {
        editor.getScrollingModel().scrollVertically(targetTopOfVisibleArea);
      }

      if (editor.getScrollingModel().getVisibleArea().y == targetTopOfVisibleArea) {
        isLocalScrollBarStabilized = true;
      }
    }
  }


  private Page tryGetNeededPageFromList(long neededPageNumber, List<Page> list) {
    for (Page page : list) {
      if (page.getPageNumber() == neededPageNumber) {
        return page;
      }
    }
    return null;
  }

  // TODO: 2019-04-08 optimize by special data structure
  private int tryGetIndexOfNeededPageInList(long needPageNumber, List<Page> list) {
    for (int i = 0; i < list.size(); i++) {
      if (list.get(i).getPageNumber() == needPageNumber) {
        return i;
      }
    }
    return -1;
  }

  private int offsetToY(int offset) {
    return editor.offsetToXY(offset).y;
  }

  // TODO: 2019-04-08 optimize by special data structure
  private int getSymbolOffsetToStartOfPage(long indexOfPage, List<Page> list) {
    int offsetToStartSymbolOfPage = 0;
    for (int i = 0; i < indexOfPage; i++) {
      offsetToStartSymbolOfPage += list.get(i).getText().length();
    }
    return offsetToStartSymbolOfPage;
  }

  private void requestReadPage(long pageNumber) {
    if (!numbersOfRequestedForReadingPages.contains(pageNumber)) {
      dataProvider.requestReadPage(pageNumber, page -> tellPageWasRead(pageNumber, page));
      numbersOfRequestedForReadingPages.add(pageNumber);
    }
  }

  private void tellPageWasRead(long pageNumber, Page page) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (page == null) {
        LOG.warn("page with number " + pageNumber + " is null.");
        return;
      }

      pagesCash.add(page);
      numbersOfRequestedForReadingPages.remove(pageNumber);
      requestUpdate();
    });
  }

  private void setFirstPageIntoEmptyDocument(Page page) {
    pagesInDocument.add(page);
    runCaretListeningTransparentCommand(
      () -> WriteCommandAction.runWriteCommandAction(dataProvider.getProject(),
                                                     () -> document.setText(page.getText())));
  }

  private void setNextPageIntoDocument(Page page) {
    pagesInDocument.add(page);
    runCaretListeningTransparentCommand(
      () -> WriteCommandAction.runWriteCommandAction(
        dataProvider.getProject(),
        document.getTextLength() == 0 ? () -> document.setText(page.getText())
                                      : () -> document.insertString(document.getTextLength(), page.getText()))
    );
  }

  private void deleteAllPagesFromDocument() {
    pagesCash.addAll(pagesInDocument);
    pagesInDocument.clear();
    runCaretListeningTransparentCommand(
      () -> WriteCommandAction.runWriteCommandAction(
        dataProvider.getProject(),
        () -> document.deleteString(0, document.getTextLength()))
    );
  }

  private void runCaretListeningTransparentCommand(Runnable command) {
    isRealCaretCanAffectOnTarget = false;
    command.run();
    isRealCaretCanAffectOnTarget = true;
  }

  private Page tryGetPageFromCash(long pageNumber) {
    for (Page page : pagesCash) {
      if (page.getPageNumber() == pageNumber) {
        return page;
      }
    }
    return null;
  }

  private void normalizePagesInDocumentListBeginning() {
    if (!isOkBeginningOfPagesInDocumentList()) {
      isLocalScrollBarStabilized = false;
      deleteAllPagesFromDocument();
    }
  }

  private boolean isOkBeginningOfPagesInDocumentList() {
    int listSize = pagesInDocument.size();

    if (listSize < 1) {
      return true;
    }

    long numberOfPage0 = pagesInDocument.get(0).getPageNumber();
    if (numberOfPage0 != targetVisiblePosition.pageNumber - 2
        && numberOfPage0 != targetVisiblePosition.pageNumber - 1) {
      // we can have no any extra pages before visible one only in case, when target visible page is in the beginning of the file
      return numberOfPage0 == targetVisiblePosition.pageNumber
             && targetVisiblePosition.pageNumber == 0;
    }

    if (listSize < 2) {
      return true;
    }

    long numberOfPage1 = pagesInDocument.get(1).getPageNumber();
    if (numberOfPage1 == targetVisiblePosition.pageNumber) {
      return true;
    }
    if (numberOfPage1 != targetVisiblePosition.pageNumber - 1) {
      return false;
    }

    if (listSize < 3) {
      return true;
    }

    long numberOfPage2 = pagesInDocument.get(2).getPageNumber();
    return numberOfPage2 == targetVisiblePosition.pageNumber;
  }


  private void normalizePagesInDocumentList_() {
    int relativeNumberOfVisiblePageInPagesInDocumentList = -1;
    for (int i = 0; i < pagesInDocument.size(); i++) {
      if (pagesInDocument.get(i).getPageNumber() == targetVisiblePosition.pageNumber) {
        relativeNumberOfVisiblePageInPagesInDocumentList = i;
        break;
      }
    }

    if (relativeNumberOfVisiblePageInPagesInDocumentList == -1) {
      if (!isOkBeginningOfPagesInDocumentList()) {
        pagesInDocument.clear();
      }
      return;
    }

    // relativeNumberOfVisiblePageInPagesInDocumentList >= 0
    if (relativeNumberOfVisiblePageInPagesInDocumentList > 2) { //i.e. we have more then 2 invisible pages before visible one
      int amountOfPagesToRemoveFromBeginningOfDocument = relativeNumberOfVisiblePageInPagesInDocumentList - 2;
      // TODO: 2019-04-04 DELETE 2 pages from beginning of list
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
    myPageReaderExecutor.shutdown();
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
    /*if (!gui.txtbxCurPageNum.hasFocus()) {
      gui.txtbxCurPageNum.setText(String.valueOf(dataProvider.getExpectedPageNumber() + 1));
    }*/
  }

  private void updateAdditionalPageInfo() {
    /*try {
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
      LOG.warn(e);
    }*/
  }

  void updateGuiActions() {
    /*toolbarNavigationActions.updateActionsImmediately();
    toolbarSaveAction.updateActionsImmediately();
    toolbarCancelSavingAction.updateActionsImmediately();*/
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

  /*private void onClickBtnGotoGo() {
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
      LOG.warn(e);
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
      LOG.warn(e);
    }
  }*/

  private void setFocusToEditorComponent() {
    editor.getComponent().requestFocus();
  }

  public void fireLocalScrollBarValueChanged(int delta) {
    if (isLocalScrollBarStabilized) {
      reflectLocalScrollBarStateToTargetPosition();
    }
    requestUpdate();
  }

  private void reflectLocalScrollBarStateToTargetPosition() {
    if (pagesInDocument.isEmpty()) {
      LOG.warn("[Large File Editor Subsystem] EditorModel.reflectLocalScrollBarStateToTargetPosition(): pagesInDocument is empty");
    }

    int localScrollBarValue = myLocalInvisibleScrollBar.getValue();

    int indexOfPage = 0;
    int topOfPage = 0;
    int bottomOfPage = 0;
    while (indexOfPage < pagesInDocument.size()) {
      topOfPage = bottomOfPage;
      bottomOfPage = offsetToY(getSymbolOffsetToStartOfPage(indexOfPage + 1, pagesInDocument));

      if (localScrollBarValue < bottomOfPage) {
        targetVisiblePosition.set(pagesInDocument.get(indexOfPage).getPageNumber(),
                                  localScrollBarValue - topOfPage);
        return;
      }

      indexOfPage++;
    }

    LOG.warn("[Large File Editor Subsystem] EditorModel.reflectLocalScrollBarStateToTargetPosition():" +
             " can't reflect state." +
             " indexOfPage=" + indexOfPage + " localScrollBarValue=" + localScrollBarValue + " topOfPage=" + topOfPage
             + " bottomOfPage=" + bottomOfPage + " pagesInDocument.size()=" + pagesInDocument.size());
  }

  /*private void setGuiListeners() {
    gui.txtbxCurPageNum.addActionListener(e -> {    // invokes only if ENTER-key pressed for JTextField
      onClickBtnGotoGo();
    });
  }*/

  /*private void createBtnsForGui() {
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
  }*/

  /*private ActionToolbar createTweakedHorizontalActionToolbar(DefaultActionGroup group) {
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.TOOLBAR, group, true);
    if (toolbar instanceof ActionToolbarImpl) {
      ((ActionToolbarImpl)toolbar).setBorder(null);
    }
    return toolbar;
  }*/

  /*private void registerShortcutsActions() {
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
  }*/

  interface DataProvider {

    Page getPage(long pageNumber) throws IOException;

    long getPagesAmount() throws IOException;

    long getExpectedPageNumber();

    Project getProject();

    ListModel<SearchResult> getSearchResultsListToHighlight();

    boolean isSavingLaunched();

    void requestReadPage(long pageNumber, ReadingPageResultHandler readingPageResultHandler);
  }


  interface EventsHandler {

    void trySwitchToNewPage(long newPageNumber);

    void trySave();

    void cancelSaving();

    void onUndoRedoActionInvoked(boolean isUndo);
  }

  private class MyJPanel extends JPanel {

    @Override
    protected void paintChildren(Graphics g) {
      super.paintChildren(g);
    }
  }
}
