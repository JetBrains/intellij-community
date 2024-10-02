// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.statistics.SearchFieldStatisticsCollector;
import com.intellij.navigation.ChooseByNameRegistry;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class GotoSymbolAction extends SearchEverywhereBaseAction implements DumbAware {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    e = SearchFieldStatisticsCollector.wrapEventWithActionStartData(e);
    Project project = e.getProject();
    if (project == null) return;

    boolean dumb = DumbService.isDumb(project);
    if (!dumb || new SymbolSearchEverywhereContributor(e).isDumbAware()) {
      String tabID = SymbolSearchEverywhereContributor.class.getSimpleName();
      showInSearchEverywherePopup(tabID, e, true, true);
    }
    else {
      GotoClassAction.invokeGoToFile(project, e, this);
    }
  }

  @Override
  protected boolean hasContributors(@NotNull DataContext dataContext) {
    return ChooseByNameRegistry.getInstance().getSymbolModelContributors().size() > 0;
  }
}