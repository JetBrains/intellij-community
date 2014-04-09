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

import com.intellij.ide.favoritesTreeView.FavoritesListNode;
import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.ide.favoritesTreeView.FavoritesTreeNodeDescriptor;
import com.intellij.ide.favoritesTreeView.FavoritesTreeViewPanel;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

import java.util.Collections;

/**
 * @author anna
 * @author Konstantin Bulenkov
 */
public class SendToFavoritesAction extends AnAction implements DumbAware {
  private final String toName;

  public SendToFavoritesAction(String name) {
    super(name);
    toName = name;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = e.getProject();
    final FavoritesManager favoritesManager = FavoritesManager.getInstance(project);

    FavoritesTreeNodeDescriptor[] roots = FavoritesTreeViewPanel.CONTEXT_FAVORITES_ROOTS_DATA_KEY.getData(dataContext);
    if (roots == null) return;

    for (FavoritesTreeNodeDescriptor root : roots) {
      FavoritesTreeNodeDescriptor listNode = root.getFavoritesRoot();
      if (listNode != null && listNode !=root && listNode.getElement() instanceof FavoritesListNode) {
        doSend(favoritesManager, new FavoritesTreeNodeDescriptor[]{root}, listNode.getElement().getName());
      }
    }
  }

  public void doSend(final FavoritesManager favoritesManager, final FavoritesTreeNodeDescriptor[] roots, final String listName) {
    for (FavoritesTreeNodeDescriptor root : roots) {
      final AbstractTreeNode rootElement = root.getElement();
      String name = listName;
      if (name == null) {
        name = root.getFavoritesRoot().getName();
      }
      favoritesManager.removeRoot(name, Collections.singletonList(rootElement));
      favoritesManager.addRoots(toName, Collections.singletonList(rootElement));
    }
  }


  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(e));
  }

  static boolean isEnabled(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return false;
    }
    FavoritesTreeNodeDescriptor[] roots = FavoritesTreeViewPanel.CONTEXT_FAVORITES_ROOTS_DATA_KEY.getData(e.getDataContext());
    if (roots == null || roots.length == 0) {
      return false;
    }
    for (FavoritesTreeNodeDescriptor root : roots) {
      FavoritesTreeNodeDescriptor listNode = root.getFavoritesRoot();
      if (listNode == null || listNode ==root || !(listNode.getElement() instanceof FavoritesListNode))
        return false;
    }
    return true;
  }
}
