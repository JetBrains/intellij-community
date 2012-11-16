/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.favoritesTreeView.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AnActionButton;
import com.intellij.util.containers.hash.HashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author anna
 * @author Konstantin Bulenkov
 */
public class DeleteFromFavoritesAction extends AnActionButton implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#" + DeleteFromFavoritesAction.class.getName());

  public DeleteFromFavoritesAction() {
    super(IdeBundle.message("action.remove.from.current.favorites"));
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
    FavoritesTreeNodeDescriptor[] roots = FavoritesTreeViewPanel.CONTEXT_FAVORITES_ROOTS_DATA_KEY.getData(dataContext);
    final DnDAwareTree tree = FavoritesTreeViewPanel.FAVORITES_TREE_KEY.getData(dataContext);

    assert roots != null && tree != null;
    Map<String, List<AbstractTreeNode>> toRemove = new HashMap<String, List<AbstractTreeNode>>();
    for (FavoritesTreeNodeDescriptor root : roots) {
      final AbstractTreeNode node = root.getElement();
      if (node instanceof FavoritesListNode) {
        favoritesManager.removeFavoritesList((String)node.getValue());
      }
      else {
        final FavoritesListNode listNode = FavoritesTreeUtil.extractParentList(root);
        LOG.assertTrue(listNode != null);
        final String name = listNode.getName();
        if (! toRemove.containsKey(name)) {
          toRemove.put(name, new ArrayList<AbstractTreeNode>());
        }
        toRemove.get(name).add(node);
      }
    }

    for (String key : toRemove.keySet()) {
      favoritesManager.removeRoot(key, toRemove.get(key));
    }
  }

  public void updateButton(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    FavoritesTreeNodeDescriptor[] roots = FavoritesTreeViewPanel.CONTEXT_FAVORITES_ROOTS_DATA_KEY.getData(dataContext);
    if (roots == null || roots.length == 0) {
      e.getPresentation().setEnabled(false);
      return;
    }

    final FavoritesManager fm = FavoritesManager.getInstance(project);
    for (FavoritesTreeNodeDescriptor root : roots) {
      if (! FavoritesTreeUtil.extractParentList(root).isAllowsTree()) {
        if (!(root.getElement() instanceof FavoritesListNode) && root.getParentDescriptor() != null && (! (root.getParentDescriptor().getElement() instanceof FavoritesListNode))) {
          e.getPresentation().setEnabled(false);
          return;
        }
      }
    }
    if (roots.length == 1
        && roots[0].getElement() instanceof FavoritesListNode
        && fm.isReadOnly(((FavoritesListNode) roots[0].getElement()).getName())) {
      e.getPresentation().setEnabled(false);
      return;
    }
    e.getPresentation().setEnabled(true);
  }
}
