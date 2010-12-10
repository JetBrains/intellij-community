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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.ide.favoritesTreeView.FavoritesTreeViewPanel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: Feb 23, 2005
 */
public class DeleteAllFavoritesListsButThisAction extends AnAction implements DumbAware {
  public DeleteAllFavoritesListsButThisAction() {
    super(IdeBundle.message("action.delete.all.favorites.lists.but.this",""));
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
    String listName = FavoritesTreeViewPanel.FAVORITES_LIST_NAME_DATA_KEY.getData(dataContext);
    String[] lists = favoritesManager.getAvailableFavoritesLists();
    for (String list : lists) {
      if (!list.equals(listName)) {
        favoritesManager.removeFavoritesList(list);
      }
    }
  }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final Presentation presentation = e.getPresentation();
    if (project == null){
      presentation.setEnabled(false);
      return;
    }
    final String listName = FavoritesTreeViewPanel.FAVORITES_LIST_NAME_DATA_KEY.getData(dataContext);
    presentation.setEnabled(false);
    if (listName != null) {
      final String[] favoritesLists = FavoritesManager.getInstance(project).getAvailableFavoritesLists();
      if (listName.equals(project.getName())) {
        presentation.setEnabled(favoritesLists.length > 1);
      }  else {
        presentation.setEnabled(favoritesLists.length > 2);
      }
      presentation.setText(IdeBundle.message("action.delete.all.favorites.lists.but.this", listName));
      presentation.setDescription(IdeBundle.message("action.delete.all.favorites.lists.but.this", listName));
    }
  }
}
