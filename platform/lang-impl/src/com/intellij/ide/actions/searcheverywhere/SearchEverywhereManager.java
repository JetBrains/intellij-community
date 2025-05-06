// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.actions.SearchEverywhereAction;
import com.intellij.ide.actions.SearchEverywhereManagerFactory;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail.Sokolov
 */
public interface SearchEverywhereManager {

  static SearchEverywhereManager getInstance(Project project) {
    if (project != null && project.getProjectFilePath() == null) {
      // Avoid initializing SearchEverywhereManager for a project mock.
      project = null;
    }

    for (SearchEverywhereManagerFactory entryPoint : SearchEverywhereManagerFactory.EP_NAME.getExtensionList()) {
      try {
        if (entryPoint.isAvailable()) {
          return entryPoint.getManager(project);
        }
      }
      catch (Throwable t) {
        Logger.getInstance(SearchEverywhereAction.class).error(t);
      }
    }

    Logger.getInstance(SearchEverywhereAction.class).error("SearchEverywhereManager is not available for project: " + project);
    return null;
  }

  boolean isShown();

  @Nullable
  @ApiStatus.Experimental
  SearchEverywherePopupInstance getCurrentlyShownPopupInstance();

  /**
   * @deprecated Use {@link #getCurrentlyShownPopupInstance()} instead
   */
  @Deprecated
  @NotNull
  SearchEverywhereUI getCurrentlyShownUI();

  void show(@NotNull String contributorID, @Nullable String searchText, @NotNull AnActionEvent initEvent);

  @NotNull
  String getSelectedTabID();

  void setSelectedTabID(@NotNull String tabID);

  void toggleEverywhereFilter();

  // todo remove
  boolean isEverywhere();

}
