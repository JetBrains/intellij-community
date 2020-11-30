// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail.Sokolov
 */
public interface SearchEverywhereManager {

  static SearchEverywhereManager getInstance(Project project) {
    return project != null ? ServiceManager.getService(project, SearchEverywhereManager.class)
                           : ApplicationManager.getApplication().getService(SearchEverywhereManager.class);
  }

  boolean isShown();

  void show(@NotNull String contributorID, @Nullable String searchText, @NotNull AnActionEvent initEvent);

  @NotNull
  String getSelectedTabID();

  void setSelectedTabID(@NotNull String tabID);

  void toggleEverywhereFilter();

  // todo remove
  boolean isEverywhere();

}
