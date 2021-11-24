// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.favoritesTreeView.actions;

import com.intellij.ide.actions.QuickSwitchSchemeAction;
import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.ide.favoritesTreeView.FavoritesTreeViewPanel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AddToFavoritesPopupAction extends QuickSwitchSchemeAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(Registry.is("ide.favorites.tool.window.applicable", false));
  }

  @Override
  protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
    group.removeAll();

    final List<String> availableFavoritesLists = FavoritesManager.getInstance(project).getAvailableFavoritesListNames();
    availableFavoritesLists.remove(FavoritesTreeViewPanel.FAVORITES_LIST_NAME_DATA_KEY.getData(dataContext));

    for (String favoritesList : availableFavoritesLists) {
      group.add(new AddToFavoritesAction(favoritesList));
    }
    group.add(new AddToNewFavoritesListAction());
  }
}
