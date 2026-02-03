// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.search;

import com.intellij.find.SearchReplaceComponent;
import com.intellij.largeFilesEditor.editor.LargeFileEditor;
import com.intellij.largeFilesEditor.editor.Page;
import com.intellij.largeFilesEditor.search.searchTask.CloseSearchTask;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface LfeSearchManager {

  void updateSearchReplaceComponentActions();

  SearchReplaceComponent getSearchReplaceComponent();

  @ApiStatus.Internal
  CloseSearchTask getLastExecutedCloseSearchTask();

  void onSearchActionHandlerExecuted();

  @NotNull
  LargeFileEditor getLargeFileEditor();

  void launchNewRangeSearch(long fromPageNumber, long toPageNumber, boolean forwardDirection);

  void gotoNextOccurrence(boolean directionForward);

  void onEscapePressed();

  @NlsContexts.StatusText String getStatusText();

  void updateStatusText();

  @RequiresEdt
  void onSearchParametersChanged();

  void onCaretPositionChanged(CaretEvent e);

  void dispose();

  @ApiStatus.Internal
  List<SearchResult> getSearchResultsInPage(Page page);

  boolean isSearchWorkingNow();

  boolean canShowRegexSearchWarning();
}
