// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.search;

import com.intellij.find.SearchReplaceComponent;
import com.intellij.largeFilesEditor.editor.EditorManager;
import com.intellij.largeFilesEditor.editor.Page;
import com.intellij.largeFilesEditor.search.searchTask.SearchTaskBase;
import com.intellij.largeFilesEditor.search.searchTask.SearchTaskOptions;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface SearchManager {

  void updateSearchManageGUIActions();

  SearchReplaceComponent getSearchManageGUI();

  @NotNull
  JBList<SearchResult> getResultList();

  SearchTaskBase getLastExecutedSearchTask();

  void onShowingPageChanged();

  void onSearchActionHandlerExecuted();

  Collection<SearchResult> getSearchResultsForPage(Page page);

  SearchResult getSelectedSearchResult();

  @NotNull
  EditorManager getEditorManager();

  void launchNewRangeSearch(long fromPageNumber, long toPageNumber, boolean forwardDirection);

  void launchRangeSearch(SearchTaskOptions searchTaskOptions, boolean needToClearPrevSearchResults);

  void gotoNextOccurrence(boolean directionForward);

  void onEscapePressed();

  String getStatusText();

  void updateStatusText();

  @CalledInAwt
  void onSearchParametersChanged();

  void onCaretPositionChanged(CaretEvent e);

  void askToStopSearching();

  void dispose();

  void tellSearchResultsToolWindowWasClosed();
}
