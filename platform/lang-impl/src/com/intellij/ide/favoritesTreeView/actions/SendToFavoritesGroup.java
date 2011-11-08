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
import com.intellij.ide.favoritesTreeView.FavoritesListNode;
import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.ide.favoritesTreeView.FavoritesTreeNodeDescriptor;
import com.intellij.ide.favoritesTreeView.FavoritesTreeViewPanel;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: Feb 24, 2005
 */
public class SendToFavoritesGroup extends ActionGroup{
  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return EMPTY_ARRAY;
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null){
      return EMPTY_ARRAY;
    }
    final FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
    final DataContext dataContext = e.getDataContext();
    FavoritesTreeNodeDescriptor[] roots = FavoritesTreeViewPanel.CONTEXT_FAVORITES_ROOTS_DATA_KEY.getData(dataContext);
    
    if (roots == null || roots.length == 0) {
      return EMPTY_ARRAY;
    }
    
    String listName = null;
    for (FavoritesTreeNodeDescriptor root : roots) {
      final AbstractTreeNode element = root.getElement();
      if (element instanceof FavoritesListNode) {
        return EMPTY_ARRAY;
      }
      final NodeDescriptor parent = root.getParentDescriptor();
      if (!(parent instanceof FavoritesTreeNodeDescriptor)) {
        return EMPTY_ARRAY;
      }
      final Object parentElement = parent.getElement();
      if (parentElement instanceof FavoritesListNode) {
        String name = ((FavoritesListNode)parentElement).getName();
        if (listName == null) {
          listName = name;
        }

        if (!StringUtil.equals(listName, name)) {
          return EMPTY_ARRAY;
        }
      }
    }
    
    
    final String[] allLists = favoritesManager.getAvailableFavoritesLists();
    List<AnAction> actions = new ArrayList<AnAction>();

    for (String list : allLists) {
      if (!list.equals(listName)) {
        actions.add(new SendToFavoritesAction(list));
      }
    }
    if (actions.size() != 0) {
      actions.add(Separator.getInstance());
    }
    actions.add(new SendToNewFavoritesListAction());
    return actions.toArray(new AnAction[actions.size()]);
  }

  private static class SendToNewFavoritesListAction extends AnAction {
    public SendToNewFavoritesListAction() {
      super(IdeBundle.message("action.send.to.new.favorites.list"));
    }

    public void actionPerformed(AnActionEvent e) {
      final DataContext dataContext = e.getDataContext();
      Project project = PlatformDataKeys.PROJECT.getData(dataContext);
      FavoritesTreeNodeDescriptor[] roots = FavoritesTreeViewPanel.CONTEXT_FAVORITES_ROOTS_DATA_KEY.getData(dataContext);
      String listName = FavoritesTreeViewPanel.FAVORITES_LIST_NAME_DATA_KEY.getData(dataContext);
      
      String newName = AddNewFavoritesListAction.doAddNewFavoritesList(project);
      if (newName != null) {
        new SendToFavoritesAction(newName).doSend(FavoritesManager.getInstance(project), roots, listName);
      }
    }
  }
}
