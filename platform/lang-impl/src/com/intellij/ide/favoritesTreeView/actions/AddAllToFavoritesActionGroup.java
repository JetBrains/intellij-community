/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ide.favoritesTreeView.actions;

import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.ide.favoritesTreeView.FavoritesTreeViewPanel;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: anna
 * Date: Mar 3, 2005
 */
public class AddAllToFavoritesActionGroup extends ActionGroup implements DumbAware {
  @Override
  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return AnAction.EMPTY_ARRAY;
    final Project project = e.getProject();
    if (project == null) {
      return AnAction.EMPTY_ARRAY;
    }
    final List<String> listNames = FavoritesManager.getInstance(project).getAvailableFavoritesListNames();
    final List<String> availableFavoritesLists = FavoritesManager.getInstance(project).getAvailableFavoritesListNames();
    availableFavoritesLists.remove(FavoritesTreeViewPanel.FAVORITES_LIST_NAME_DATA_KEY.getData(e.getDataContext()));
    if (availableFavoritesLists.isEmpty()) {
      return new AnAction[]{new AddAllOpenFilesToNewFavoritesListAction()};
    }

    AnAction[] actions = new AnAction[listNames.size() + 2];
    int idx = 0;
    for (String favoritesList : listNames) {
      actions[idx++] = new AddAllOpenFilesToFavorites(favoritesList);
    }
    actions[idx++] = Separator.getInstance();
    actions[idx] = new AddAllOpenFilesToNewFavoritesListAction();
    return actions;
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(AddToFavoritesAction.canCreateNodes(e));
  }
}
