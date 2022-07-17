// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class PredefinedSearchScopeProvider {
  public static PredefinedSearchScopeProvider getInstance() {
    return ApplicationManager.getApplication().getService(PredefinedSearchScopeProvider.class);
  }

  /**
   * Don't use this method with option {@code usageView } enabled as it may cause UI freezes.
   * Prefer to use {@link PredefinedSearchScopeProvider#getPredefinedScopesAsync} instead wherever possible.
   * @param suggestSearchInLibs  add <i>Project and Libraries</i> scope
   * @param prevSearchFiles  add <i>Files in Previous Search Result</i> instead of <i>Previous Search Result</i> (only if {@code usageView == true})
   * @param currentSelection  add <i>Selection</i> scope if text is selected in the editor
   * @param usageView  add <i>Previous Search Result</i> and <i>Hierarchy 'X' (visible nodes only)</i> scopes if there are search results or hierarchies open
   * @param showEmptyScopes  add <i>Current File</i> and <i>Open Files</i> scopes even if there are no files open
   */
  public abstract List<SearchScope> getPredefinedScopes(@NotNull final Project project,
                                                        @Nullable final DataContext dataContext,
                                                        boolean suggestSearchInLibs,
                                                        boolean prevSearchFiles,
                                                        boolean currentSelection,
                                                        boolean usageView,
                                                        boolean showEmptyScopes);

  /**
   * @param suggestSearchInLibs add <i>Project and Libraries</i> scope
   * @param prevSearchFiles     add <i>Files in Previous Search Result</i> instead of <i>Previous Search Result</i> (only if {@code usageView == true})
   * @param currentSelection    add <i>Selection</i> scope if text is selected in the editor
   * @param usageView           add <i>Previous Search Result</i> and <i>Hierarchy 'X' (visible nodes only)</i> scopes if there are search results or hierarchies open
   * @param showEmptyScopes     add <i>Current File</i> and <i>Open Files</i> scopes even if there are no files open
   */
  public Promise<List<SearchScope>> getPredefinedScopesAsync(@NotNull final Project project,
                                                                      @Nullable final DataContext dataContext,
                                                                      boolean suggestSearchInLibs,
                                                                      boolean prevSearchFiles,
                                                                      boolean currentSelection,
                                                                      boolean usageView,
                                                                      boolean showEmptyScopes) {
    List<SearchScope> scopes =
      getPredefinedScopes(project, dataContext, suggestSearchInLibs, prevSearchFiles, currentSelection, usageView, showEmptyScopes);
    return Promises.resolvedPromise(scopes);
  }
}
