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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

/**
 * User: anna
 * Date: Feb 28, 2005
 */
class AddAllOpenFilesToNewFavoritesListAction extends AnAction {
 public AddAllOpenFilesToNewFavoritesListAction() {
   super(IdeBundle.message("action.add.all.open.tabs.to.new.favorites.list"),
         IdeBundle.message("action.add.to.new.favorites.list"), IconLoader.getIcon("/general/addFavoritesList.png"));
 }

 public void actionPerformed(AnActionEvent e) {
   final DataContext dataContext = e.getDataContext();
   final String newName = AddNewFavoritesListAction.doAddNewFavoritesList(PlatformDataKeys.PROJECT.getData(dataContext));
   if (newName != null){
     new AddAllOpenFilesToFavorites(newName).actionPerformed(e);
   }
 }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      e.getPresentation().setEnabled(false);
    }
    else {
      e.getPresentation().setEnabled(!AddAllOpenFilesToFavorites.getFilesToAdd(project).isEmpty());
    }
  }
}
