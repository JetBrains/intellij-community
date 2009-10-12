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
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

import java.util.Collection;

/**
 * User: anna
 * Date: Feb 28, 2005
 */
class AddToNewFavoritesListAction extends AnAction {
 public AddToNewFavoritesListAction() {
   super(IdeBundle.message("action.add.to.new.favorites.list"),
         IdeBundle.message("action.add.to.new.favorites.list"), IconLoader.getIcon("/general/addFavoritesList.png"));
 }

 public void actionPerformed(AnActionEvent e) {
   final DataContext dataContext = e.getDataContext();
   Project project = PlatformDataKeys.PROJECT.getData(dataContext);
   Collection<AbstractTreeNode> nodesToAdd = AddToFavoritesAction.getNodesToAdd(dataContext, true);
   if (nodesToAdd != null) {
     final String newName = AddNewFavoritesListAction.doAddNewFavoritesList(PlatformDataKeys.PROJECT.getData(dataContext));
     if (newName != null) {
       FavoritesManager.getInstance(project).addRoots(newName, nodesToAdd);
     }
   }
 }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      e.getPresentation().setEnabled(false);
    }
    else {
      e.getPresentation().setEnabled(AddToFavoritesAction.canCreateNodes(dataContext, e));
    }
  }
}
