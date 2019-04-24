// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.editor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.largeFilesEditor.accessGettingPageTokens.AccessGettingPageToken;
import com.intellij.largeFilesEditor.accessGettingPageTokens.ITokenLock;
import com.intellij.largeFilesEditor.accessGettingPageTokens.Reason;
import com.intellij.largeFilesEditor.accessGettingPageTokens.TokenLockBase;
import com.intellij.largeFilesEditor.changes.*;
import com.intellij.largeFilesEditor.encoding.EditorManagerAccess;
import com.intellij.largeFilesEditor.encoding.EditorManagerAccessorImpl;
import com.intellij.largeFilesEditor.encoding.EncodingWidget;
import com.intellij.largeFilesEditor.file.*;
import com.intellij.largeFilesEditor.search.SearchManager;
import com.intellij.largeFilesEditor.search.SearchManagerImpl;
import com.intellij.largeFilesEditor.search.SearchResult;
import com.intellij.largeFilesEditor.search.SearchResultsPanelManagerAccessorImpl;
import com.intellij.largeFilesEditor.search.searchTask.FileDataProviderForSearch;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;

public class EditorManagerImpl extends UserDataHolderBase
  implements EditorManager, LoadPageCallback, SaveFileCallback {

  private static final Key<Change> KEY_UNDO_REDO_CHANGE = new Key<>("lfe.undoRedoChange");

  private static final int DEFAULT_HIDE_SAVING_PANEL_AFTER_FINISHING_DELAY_MS = 1000;

  private static final Logger logger = Logger.getInstance(EditorManagerImpl.class);
  private final boolean isExperimentalModeOn;
  private final Project project;
  private FileManager fileManager;
  private final EditorModel editorModel;
  private final DocumentEx document;
  private final VirtualFile vFile;
  private final int changePageDelay;
  private SearchManager searchManager;
  private final ChangesManager changesManager;

  private boolean isDisposed = false;

  private ITokenLock tokenLock;


  public EditorManagerImpl(Project project, VirtualFile vFile) {
    this.vFile = vFile;
    this.project = project;

    int customPageSize = PropertiesGetter.getPageSize();
    int customBorderShift = PropertiesGetter.getMaxPageBorderShiftBytes();
    changePageDelay = PropertiesGetter.getChangePageInvisibleDelayMs();
    isExperimentalModeOn = PropertiesGetter.getIsExperimentalModeOn();

    if (isExperimentalModeOn) {
      changesManager = new ChangesManagerImpl();
    }
    else {
      changesManager = new MockChangesManager();
    }

    document = createSpecialDocument();
    document.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        changesManager.onDocumentChangedEvent(event);
      }
    });

    editorModel = new EditorModel(document, project, implementDataProviderForEditorModel(), implementEventHandlerForEditorModel());
    editorModel.setViewState(EditorGui.State.LOADING_FILE);
    editorModel.setShowingSavingStatus(false);

    try {
      fileManager = new FileManagerImpl(vFile, customPageSize, customBorderShift,
                                        this, this, changesManager);
    }
    catch (FileNotFoundException e) {
      logger.warn(e);
      editorModel.setViewState(EditorGui.State.ERROR_OPENING);
      return;
    }

    changesManager.setEnabledListeningDocumentChanges(document, true);

    editorModel.putUserDataToEditor(KEY_EDITOR_MARK, new Object());
    editorModel.putUserDataToEditor(KEY_EDITOR_MANAGER, this);

    searchManager = new SearchManagerImpl(this,
                                          fileManager.getFileDataProviderForSearch(), new SearchResultsPanelManagerAccessorImpl());
    editorModel.setEditorHeaderComponent(searchManager.getSearchManageGUI());
    setSearchPanelsViewState(EditorGui.SearchPanelsViewState.ALL_HIDDEN);

    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    final StatusBarWidget existedWidget = statusBar.getWidget(EncodingWidget.WIDGET_ID);
    boolean needToAddNewWidget = false;
    if (existedWidget == null) {
      needToAddNewWidget = true;
    }
    else {
      if (existedWidget instanceof EncodingWidget &&
          ((EncodingWidget)existedWidget)._getProject() != project) {
        statusBar.removeWidget(existedWidget.ID());
        needToAddNewWidget = true;
      }
    }
    if (needToAddNewWidget) {
      statusBar.addWidget(new EncodingWidget(project, new EditorManagerAccessorImpl()));
    }

    editorModel.addCaretListener(new MyCaretListener());

    setSavingModeOn(false);

    tokenLock = createTokenLock();

    //tokenLock.trySetNewToken(new AccessGettingPageToken(Reason.NAVIGATION_BY_USER, 0));
    //fileManager.needToOpenNewPage(tokenLock.getActiveToken());
    editorModel.updateCurrentPageLabelAndAdditionalInfo();
    editorModel.updateGuiActions();
  }

  @Override
  public SearchManager getSearchManager() {
    return searchManager;
  }

  @Override
  public boolean requestRunUnderToken(AccessGettingPageToken token, Runnable runnerIfAllowed, Runnable runnerIfDenied) {
    if (tokenLock.trySetNewToken(token)) {
      if (runnerIfAllowed != null) {
        runnerIfAllowed.run();
      }
      return true;
    }
    else {
      if (runnerIfDenied != null) {
        runnerIfDenied.run();
      }
      return false;
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return editorModel.getComponent();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return editorModel.getComponent();
  }

  @NotNull
  @Override
  public String getName() {
    return "Large File Editor";
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void selectNotify() {
  }

  @Override
  public void deselectNotify() {
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public void dispose() {
    isDisposed = true;
    if (searchManager != null) {
      searchManager.dispose();
    }
    if (fileManager != null) {
      fileManager.dispose();
    }
    editorModel.dispose();
  }

  @Override
  public long getExpectedPageNumber() {
    AccessGettingPageToken activeToken = tokenLock.getActiveToken();
    if (activeToken != null && activeToken.getPageNumber() >= 0) {
      return activeToken.getPageNumber();
    }
    return editorModel.getCurrentPageNumber();
  }

  @Override
  public void setSearchPanelsViewState(EditorGui.SearchPanelsViewState searchPanelsViewState) {
    editorModel.setSearchPanelsViewState(searchPanelsViewState);
  }

  @Override
  public EditorGui.SearchPanelsViewState getSearchPanelsViewState() {
    return editorModel.getSearchPanelsViewState();
  }

  @CalledInAwt
  @Override
  public void showSearchResult(SearchResult searchResult) {
    if (searchResult.startPosition.pageNumber != getCurrentPageNumber()) {
      AccessGettingPageToken token = new AccessGettingPageToken(Reason.SHOWING_SEARCH_RESULT, searchResult.startPosition.pageNumber);
      token.putUserData(SearchResult.KEY, searchResult);
      trySwitchToNewPage(token);
    }
    else {
      updateSearchHighlightingAndSelectedSearchResult();
      editorModel.updateSelectedSearchResultSelection(searchResult);
    }
  }

  @Override
  public Project getProject() {
    return project;
  }

  @Override
  public long getCurrentPageNumber() {
    return editorModel.getCurrentPageNumber();
  }

  @Override
  public int getCaretPageOffset() {
    return editorModel.getCaretPageOffset();
  }

  @Override
  public Editor getEditor() {
    return editorModel.getEditor();
  }

  @Override
  @CalledInAwt
  public void updateSearchHighlightingAndSelectedSearchResult() {
    editorModel.updateSearchHighlightingAndSelectedSearchResult();
  }

  @Override
  public void tellPageIsLoaded(Page page, AccessGettingPageToken token) {
    ApplicationManager.getApplication().invokeLater(() -> {

      if (isDisposed) {
        return;
      }

      final SearchResult selectedSearchResult;
      if (getSearchPanelsViewState() == EditorGui.SearchPanelsViewState.ALL_HIDDEN) {
        selectedSearchResult = null;
      }
      else {
        selectedSearchResult = searchManager.getSelectedSearchResult();
      }

      if (tokenLock.getActiveToken() == token) {

        boolean wasDocumentReadOnly = !document.isWritable();
        changesManager.setEnabledListeningDocumentChanges(document, false);

        ApplicationManager.getApplication().runWriteAction(() ->
                                                             document.setText(page.getText()));

        document.putUserData(KEY_DOCUMENT_PAGE_NUMBER, page.getPageNumber());
        changesManager.setEnabledListeningDocumentChanges(document, true);
        document.setReadOnly(wasDocumentReadOnly);

        editorModel.setCurrentShowingPage(page);
        editorModel.setViewState(EditorGui.State.NORMAL_PAGE_VIEW);
        editorModel.updateCurrentPageLabelAndAdditionalInfo();
        editorModel.updateGuiActions();
        updateEncodingWidget();

        updateSearchHighlightingAndSelectedSearchResult();
        editorModel.updateSelectedSearchResultSelection(selectedSearchResult);

        searchManager.onShowingPageChanged();

        Reason tokenReason = token.getReason();
        switch (tokenReason) {
          case UNDO:
          case REDO:
            Change change = token.getUserData(KEY_UNDO_REDO_CHANGE);
            if (change instanceof LocalChange) {
              LocalChange localChange = (LocalChange)change;
              editorModel.showPlaceWhereUndoRedoWasDone(tokenReason == Reason.UNDO, localChange);
            }
            break;
          case SHOWING_SEARCH_RESULT:
            SearchResult searchResult = token.getUserData(SearchResult.KEY);
            editorModel.updateSelectedSearchResultSelection(searchResult);
            break;
          default:
            break;
        }

        tokenLock.release();
      }
    });
  }

  @Override
  public void tellCatchedErrorWhileLoadingPage() {
    ApplicationManager.getApplication().invokeLater(() -> {
      editorModel.setCurrentShowingPage(null);
      editorModel.setViewState(EditorGui.State.ERROR_OPENING);
      tokenLock.release();
    });
  }

  @Override
  public void tellSavingFileWasCompleteSuccessfully() {
    ApplicationManager.getApplication().invokeLater(() -> {
      tokenLock.release();
      setSavingModeOn(false);

      editorModel.setSavingProgress(100);
      editorModel.setSavingStatusText("Saving complete!");

      setTimerToHideSavingResultStatus();

      try {
        if (getCurrentPageNumber() >= fileManager.getPagesAmount()) {
          trySwitchToNewPage(new AccessGettingPageToken(
            Reason.SAVING, fileManager.getPagesAmount() - 1));
        }
      }
      catch (IOException e) {
        logger.warn(e);
        editorModel.setViewState(EditorGui.State.ERROR_OPENING);
      }
    });
  }


  @Override
  public void tellSavingFileWasCanceledAndEverythingWasReestablished() {
    ApplicationManager.getApplication().invokeLater(() -> {
      tokenLock.release();
      setSavingModeOn(false);

      editorModel.setSavingProgress(0);
      editorModel.setSavingStatusText("Saving canceled!");

      setTimerToHideSavingResultStatus();
    });
  }

  @Nullable
  @Override
  public VirtualFile getFile() {
    return getVirtualFile();
  }

  @NotNull
  @Override
  public VirtualFile getVirtualFile() {
    return vFile;
  }

  @Override
  public EditorManagerAccess createAccessForEncodingWidget() {
    //EditorManagerImpl currentEditorManager = this;
    return new EditorManagerAccess() {
      @NotNull
      @Override
      public VirtualFile getVirtualFile() {
        return EditorManagerImpl.this.getVirtualFile();
      }

      @NotNull
      @Override
      public Editor getEditor() {
        return EditorManagerImpl.this.getEditor();
      }

      @Override
      public boolean tryChangeEncoding(@NotNull Charset charset) {
        AccessGettingPageToken token = new AccessGettingPageToken(
          Reason.CHANGING_ENCODING, getExpectedPageNumber());

        if (!fileManager.canFileBeReloadedInOtherCharset()) {
          Messages.showWarningDialog("Can't change file encoding, because it has BOM (Byte order mark)", "Warning");
          return false;
        }

        return requestRunUnderToken(token,
                                    () -> {
                                      fileManager.reset(charset);
                                      trySwitchToNewPage(token);
                                    },
                                    () -> {
                                      logger.warn("Can't change encoding, token was dismissed");
                                      Messages.showWarningDialog("Can't change encoding now", "Warning");
                                    }
        );
      }

      @Override
      public String getCharsetName() {
        return fileManager.getCharsetName();
      }
    };
  }

  @Override
  public FileDataProviderForSearch getFileDataProviderForSearch() {
    return fileManager.getFileDataProviderForSearch();
  }

  @NotNull
  @Override
  public EditorModel getEditorModel() {
    return editorModel;
  }

  @Override
  public void tellSavingFileWasCorrupted(String messageToUser) {
    ApplicationManager.getApplication().invokeLater(() -> {
      tokenLock.release();
      setSavingModeOn(false);
      editorModel.setSavingProgress(0);
      editorModel.setSavingStatusText("Saving error!");

      setTimerToHideSavingResultStatus();

      editorModel.setViewState(EditorGui.State.ERROR_OPENING);
      Messages.showErrorDialog(messageToUser, "Save Result");
    });
  }

  @Override
  public void tellSavingProgress(int progress) {
    ApplicationManager.getApplication().invokeLater(() -> editorModel.setSavingProgress(progress));
  }

  @Deprecated
  public boolean isExperimentalModeOn() {
    return isExperimentalModeOn;
  }

  private static DocumentEx createSpecialDocument() {
    DocumentEx doc = new DocumentImpl("", true, false); // + allowing "\r\n" line separators
    UndoUtil.disableUndoFor(doc); // disabling Undo-functionality, provided by IDEA
    return doc;
  }

  @CalledInAwt
  private void trySave() {
    AccessGettingPageToken token = new AccessGettingPageToken(Reason.SAVING, getExpectedPageNumber());
    if (!tokenLock.trySetNewToken(token)) {
      return;
    }
    setSavingModeOn(true);
    fileManager.beginSavingFile();
  }


  @CalledInAwt
  private void onUndoRedoActionInvoked(boolean isUndo) {
    Reason reason = isUndo ? Reason.UNDO : Reason.REDO;
    if (!tokenLock.canBeSet(new AccessGettingPageToken(reason, -1))) {
      Messages.showInfoMessage("Can't undo/redo now.", "Warning...");
      logger.info("Can't apply undo/redo action because token lock can't be set: token lock state = {" + tokenLock.toString() + "}");
      return;
    }

    Change change = isUndo ? changesManager.tryRegisterUndoAndGetCorrespondingChange()
                           : changesManager.tryRegisterRedoAndGetCorrespondingChange();

    if (change == null) {
      return;
    }

    if (change instanceof LocalChange) {
      LocalChange localChange = (LocalChange)change;
      if (localChange.getPageNumber() == getCurrentPageNumber()) {

        changesManager.setEnabledListeningDocumentChanges(document, false);
        ApplicationManager.getApplication().runWriteAction(
          () -> CommandProcessor.getInstance().runUndoTransparentAction(
            () -> {
              if (isUndo) {
                localChange.performUndo(document);
              }
              else {
                localChange.performRedo(document);
              }
            }));
        changesManager.setEnabledListeningDocumentChanges(document, true);
        editorModel.showPlaceWhereUndoRedoWasDone(isUndo, localChange);
      }
      else {

        AccessGettingPageToken token = new AccessGettingPageToken(Reason.UNDO, localChange.getPageNumber());
        token.putUserData(KEY_UNDO_REDO_CHANGE, localChange);
        if (!tokenLock.trySetNewToken(token)) {
          Messages.showInfoMessage("Can't undo/redo now.", "Warning...");
          logger.info("Can't open changed page after undo/redo registration because token lock can't be set: token lock state = {" +
                      tokenLock.toString() +
                      "}");
          return;
        }
        trySwitchToNewPage(tokenLock.getActiveToken());
      }
    }
  }

  private static ITokenLock createTokenLock() {
    return new TokenLockBase() {
      @Override
      public boolean canBeSet(@NotNull AccessGettingPageToken token) {
        if (getActiveToken() == null || getActiveToken() == token) {
          return true;
        }

        Reason activeTokenReason = getActiveToken().getReason();
        Reason newTokenReason = token.getReason();

        if (activeTokenReason == Reason.NAVIGATION_BY_USER && newTokenReason == Reason.SHOWING_SEARCH_RESULT) {
          return true;
        }
        else if (activeTokenReason != newTokenReason) {
          return false;
        }
        else {
          return newTokenReason != Reason.SAVING;
        }
      }
    };
  }

  @CalledInAwt
  private void trySwitchToNewPage(@NotNull AccessGettingPageToken token) {
    long newPageNumber = token.getPageNumber();

    if (!tokenLock.trySetNewToken(token)) {
      return;
    }

    long amountOfPages;
    try {
      amountOfPages = fileManager.getPagesAmount();
    }
    catch (IOException e) {
      Messages.showErrorDialog("Error opening file. Please, try to reopen it.", "ERROR");
      logger.warn(e);
      return;
    }

    if (newPageNumber < 0 || newPageNumber >= amountOfPages) {
      return;
    }

    if (getCurrentPageNumber() != getExpectedPageNumber()) {
      editorModel.resetCaretAndSelection();
    }

    editorModel.updateCurrentPageLabelAndAdditionalInfo();

    fileManager.needToOpenNewPage(tokenLock.getActiveToken());

    Timer timer = new Timer(changePageDelay, e -> {
      if (tokenLock.getActiveToken() != null &&
          (editorModel.getCurrentShowingPage() == null ||
           editorModel.getCurrentShowingPage().getPageNumber() != tokenLock.getActiveToken().getPageNumber())) {
        editorModel.setViewState(EditorGui.State.LOADING_PAGE);
      }
    });
    timer.setRepeats(false);
    timer.start();
  }

  private void updateEncodingWidget() {
    StatusBarWidget widget = WindowManager.getInstance()
      .getStatusBar(project).getWidget(EncodingWidget.WIDGET_ID);
    if (widget instanceof EncodingWidget) {
      ((EncodingWidget)widget).update();
    }
  }

  private class MyCaretListener implements CaretListener {
    @Override
    public void caretPositionChanged(@NotNull CaretEvent e) {
      searchManager.onCaretPositionChanged(e);
    }
  }

  @CalledInAwt
  private void setSavingModeOn(boolean on) {
    document.setReadOnly(on);
    searchManager.askToStopSearching();
    editorModel.setShowingSavingStatus(on);
    editorModel.setSavingProgress(0);
    editorModel.setSavingStatusText("Saving begins...");
  }

  private void setTimerToHideSavingResultStatus() {
    Timer timer = new Timer(DEFAULT_HIDE_SAVING_PANEL_AFTER_FINISHING_DELAY_MS, e -> {
      if (tokenLock.getActiveToken() == null || tokenLock.getActiveToken().getReason() != Reason.SAVING) {
        editorModel.setShowingSavingStatus(false);
      }
    });
    timer.setRepeats(false);
    timer.start();
  }

  private EditorModel.DataProvider implementDataProviderForEditorModel() {
    return new EditorModel.DataProvider() {
      @Override
      public Page getPage(long pageNumber) throws IOException {
        return fileManager.getPage_wait(pageNumber);
      }

      @Override
      public long getPagesAmount() throws IOException {
        return fileManager.getPagesAmount();
      }

      @Override
      public long getExpectedPageNumber() {
        return EditorManagerImpl.this.getExpectedPageNumber();
      }

      @Override
      public Project getProject() {
        return project;
      }

      @Override
      public ListModel<SearchResult> getSearchResultsListToHighlight() {
        return searchManager.getResultList().getModel();
      }

      @Override
      public boolean isSavingLaunched() {
        return tokenLock.getActiveToken() != null && tokenLock.getActiveToken().getReason() == Reason.SAVING;
      }

      @Override
      public void requestReadPage(long pageNumber, ReadingPageResultHandler readingPageResultHandler) {
        fileManager.requestReadPage(pageNumber, readingPageResultHandler);
      }
    };
  }

  private EditorModel.EventsHandler implementEventHandlerForEditorModel() {
    return new EditorModel.EventsHandler() {
      @Override
      public void trySwitchToNewPage(long newPageNumber) {
        EditorManagerImpl.this.trySwitchToNewPage(
          new AccessGettingPageToken(Reason.NAVIGATION_BY_USER, newPageNumber));
      }

      @Override
      public void trySave() {
        EditorManagerImpl.this.trySave();
      }

      @Override
      public void cancelSaving() {
        tryCancelSaving();
      }

      @Override
      public void onUndoRedoActionInvoked(boolean isUndo) {
        EditorManagerImpl.this.onUndoRedoActionInvoked(isUndo);
      }
    };
  }

  private void tryCancelSaving() {
    fileManager.cancelSaving();
  }
}