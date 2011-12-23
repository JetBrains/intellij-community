/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: Mar 3, 2005
 */
public class AddToFavoritesActionGroup extends ActionGroup {

  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return AnAction.EMPTY_ARRAY;
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null){
      return AnAction.EMPTY_ARRAY;
    }
    final String[] availableFavoritesLists = FavoritesManager.getInstance(project).getAvailableFavoritesLists();
    AnAction[] actions = new AnAction[availableFavoritesLists.length + 2];
    int idx = 0;
    for (String favoritesList : availableFavoritesLists) {
      actions[idx++] = new AddToFavoritesAction(favoritesList);
    }
    actions[idx++] = Separator.getInstance();
    actions[idx] = new AddToNewFavoritesListAction();
    return actions;
  }
}
