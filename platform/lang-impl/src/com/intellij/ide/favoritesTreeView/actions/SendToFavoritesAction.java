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

import com.intellij.ide.favoritesTreeView.FavoritesListNode;
import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.ide.favoritesTreeView.FavoritesTreeNodeDescriptor;
import com.intellij.ide.favoritesTreeView.FavoritesTreeViewPanel;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;

import java.util.Collections;

/**
 * @author anna
 * @author Konstantin Bulenkov
 */
public class SendToFavoritesAction extends AnAction{
  private final String toName;
  public SendToFavoritesAction(String name) {
    super(name);
    toName = name;
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final FavoritesManager favoritesManager = FavoritesManager.getInstance(project);

    FavoritesTreeNodeDescriptor[] roots = FavoritesTreeViewPanel.CONTEXT_FAVORITES_ROOTS_DATA_KEY.getData(dataContext);

    String listName = null;
    if (roots == null) return;
    for (FavoritesTreeNodeDescriptor root : roots) {
      final AbstractTreeNode element = root.getElement();
      if (element instanceof FavoritesListNode) {
        listName = null; break;
      }
      final NodeDescriptor parent = root.getParentDescriptor();
      if (!(parent instanceof FavoritesTreeNodeDescriptor)) {
        listName = null; break;
      }
      String name = ((FavoritesListNode)parent.getElement()).getName();
      if (listName == null) {
        listName = name;
      }
      if (!StringUtil.equals(listName, name)) {
        listName = null; break;
      }
    }

    doSend(favoritesManager, roots, listName);
  }

  public void doSend(final FavoritesManager favoritesManager, final FavoritesTreeNodeDescriptor[] roots, final String listName) {
    for (FavoritesTreeNodeDescriptor root : roots) {
      final AbstractTreeNode rootElement = root.getElement();
      String name = listName;
      if (name == null) {
        name = root.getFavoritesRoot().getName();
      }
      favoritesManager.removeRoot(name, rootElement.getValue());
      favoritesManager.addRoots(toName, Collections.singletonList(rootElement));
    }
  }


  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null){
      e.getPresentation().setEnabled(false);
      return;
    }
    //FavoritesTreeNodeDescriptor[] roots = FavoritesTreeViewPanel.CONTEXT_FAVORITES_ROOTS_DATA_KEY.getData(dataContext);
    //if (roots == null || roots.length == 0) {
    //  e.getPresentation().setEnabled(false);
    //  return;
    //}

    //String listName = FavoritesTreeViewPanel.FAVORITES_LIST_NAME_DATA_KEY.getData(dataContext);
    e.getPresentation().setEnabled(true);
  }
}
