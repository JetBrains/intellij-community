// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.largeFilesEditor.editor;

import com.intellij.largeFilesEditor.PlatformActionsReplacer;
import com.intellij.largeFilesEditor.encoding.LargeFileEditorAccess;
import com.intellij.largeFilesEditor.file.LargeFileManager;
import com.intellij.largeFilesEditor.file.LargeFileManagerImpl;
import com.intellij.largeFilesEditor.file.ReadingPageResultHandler;
import com.intellij.largeFilesEditor.search.LfeSearchManager;
import com.intellij.largeFilesEditor.search.LfeSearchManagerImpl;
import com.intellij.largeFilesEditor.search.RangeSearchCreatorImpl;
import com.intellij.largeFilesEditor.search.SearchResult;
import com.intellij.largeFilesEditor.search.searchTask.FileDataProviderForSearch;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

public final class LargeFileEditorImpl extends UserDataHolderBase implements LargeFileEditor {

  private static final Logger logger = Logger.getInstance(LargeFileEditorImpl.class);
  private final Project project;
  private LargeFileManager fileManager;
  private final EditorModel editorModel;
  private final VirtualFile vFile;
  private LfeSearchManager searchManager;

  public LargeFileEditorImpl(Project project, VirtualFile vFile) {
    this.vFile = vFile;
    this.project = project;

    int customPageSize = PropertiesGetter.getPageSize();
    int customBorderShift = PropertiesGetter.getMaxPageBorderShiftBytes();

    DocumentEx document = createSpecialDocument();

    editorModel = new EditorModel(document, project, implementDataProviderForEditorModel());
    editorModel.putUserDataToEditor(LARGE_FILE_EDITOR_MARK_KEY, new Object());
    editorModel.putUserDataToEditor(LARGE_FILE_EDITOR_KEY, this);
    editorModel.putUserDataToEditor(LARGE_FILE_EDITOR_SOFT_WRAP_KEY, true);

    try {
      fileManager = new LargeFileManagerImpl(vFile, customPageSize, customBorderShift);
    }
    catch (FileNotFoundException e) {
      logger.warn(e);
      editorModel.setBrokenMode();
      Messages.showWarningDialog(EditorBundle.message("large.file.editor.message.cant.open.file.because.file.not.found"),
                                 EditorBundle.message("large.file.editor.title.warning"));
      requestClosingEditorTab();
      return;
    }

    searchManager = new LfeSearchManagerImpl(
      this, fileManager.getFileDataProviderForSearch(), new RangeSearchCreatorImpl());

    PlatformActionsReplacer.makeAdaptingOfPlatformActionsIfNeed();

    editorModel.addCaretListener(new MyCaretListener());

    fileManager.addFileChangeListener((Page lastPage, boolean isLengthIncreased) -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        editorModel.onFileChanged(lastPage, isLengthIncreased);
      });
    });
  }

  private void requestClosingEditorTab() {
    ApplicationManager.getApplication().invokeLater(
      () -> FileEditorManager.getInstance(project).closeFile(vFile));
  }

  @Override
  public LfeSearchManager getSearchManager() {
    return searchManager;
  }

  @Override
  public @NotNull JComponent getComponent() {
    return editorModel.getComponent();
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return editorModel.getEditor().getContentComponent();
  }

  @Override
  public @NotNull String getName() {
    return EditorBundle.message("large.file.editor.title");
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
    if (state instanceof LargeFileEditorState largeFileEditorState) {
      editorModel.setCaretAndShow(largeFileEditorState.caretPageNumber,
                                  largeFileEditorState.caretSymbolOffsetInPage);
    }
  }

  @Override
  public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) {
    LargeFileEditorState state = new LargeFileEditorState();
    state.caretPageNumber = editorModel.getCaretPageNumber();
    state.caretSymbolOffsetInPage = editorModel.getCaretPageOffset();
    return state;
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
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void dispose() {
    if (searchManager != null) {
      searchManager.dispose();
    }
    if (fileManager != null) {
      Disposer.dispose(fileManager);
    }
    editorModel.dispose();

    vFile.putUserData(FileDocumentManagerImpl.HARD_REF_TO_DOCUMENT_KEY, null);
  }

  @RequiresEdt
  @Override
  public void showSearchResult(SearchResult searchResult) {
    editorModel.showSearchResult(searchResult);
  }

  @Override
  public Project getProject() {
    return project;
  }

  @Override
  public void trySetHighlighter(@NotNull EditorHighlighter highlighter) {
    editorModel.trySetHighlighter(highlighter);
  }

  @Override
  public long getCaretPageNumber() {
    return editorModel.getCaretPageNumber();
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
  public @NotNull VirtualFile getFile() {
    return vFile;
  }

  @Override
  public LargeFileEditorAccess createAccessForEncodingWidget() {
    return new LargeFileEditorAccess() {
      @Override
      public @NotNull VirtualFile getVirtualFile() {
        return getFile();
      }

      @Override
      public @NotNull Editor getEditor() {
        return LargeFileEditorImpl.this.getEditor();
      }

      @Override
      public boolean tryChangeEncoding(@NotNull Charset charset) {

        if (fileManager.hasBOM()) {
          Messages.showWarningDialog(
            EditorBundle.message("large.file.editor.message.cant.change.encoding.because.it.has.bom.byte.order.mark"),
            EditorBundle.message("large.file.editor.title.warning"));
          return false;
        }

        if (searchManager.isSearchWorkingNow()) {
          Messages.showInfoMessage(EditorBundle.message("large.file.editor.message.cant.change.encoding.because.search.is.working.now"),
                                   EditorBundle.message("large.file.editor.title.cant.change.encoding"));
          return false;
        }

        fileManager.reset(charset);
        editorModel.onEncodingChanged();
        return true;
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

  @Override
  public @NotNull EditorModel getEditorModel() {
    return editorModel;
  }

  @Override
  public int getPageSize() {
    return fileManager.getPageSize();
  }

  private static DocumentEx createSpecialDocument() {
    DocumentEx doc = new DocumentImpl("", false, false); // restrict "\r\n" line separators
    doc.putUserData(FileDocumentManagerImpl.NOT_RELOADABLE_DOCUMENT_KEY,
                    new Object());  // to protect document from illegal content changes (see usages of the key)
    UndoUtil.disableUndoFor(doc); // disabling Undo-functionality, provided by IDEA
    return doc;
  }

  private final class MyCaretListener implements CaretListener {
    @Override
    public void caretPositionChanged(@NotNull CaretEvent e) {
      searchManager.onCaretPositionChanged(e);
    }
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
      public Project getProject() {
        return project;
      }

      @Override
      public void requestReadPage(long pageNumber, ReadingPageResultHandler readingPageResultHandler) {
        fileManager.requestReadPage(pageNumber, readingPageResultHandler);
      }

      @Override
      public List<SearchResult> getSearchResultsInPage(Page page) {
        if (searchManager != null) {
          return searchManager.getSearchResultsInPage(page);
        }
        return null;
      }
    };
  }
}