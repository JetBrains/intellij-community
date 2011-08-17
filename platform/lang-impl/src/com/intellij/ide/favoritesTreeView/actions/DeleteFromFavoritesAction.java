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
import com.intellij.ide.favoritesTreeView.FavoritesListNode;
import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.ide.favoritesTreeView.FavoritesTreeNodeDescriptor;
import com.intellij.ide.favoritesTreeView.FavoritesTreeViewPanel;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AnActionButton;

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
    assert roots != null;
    for (FavoritesTreeNodeDescriptor root : roots) {
      final AbstractTreeNode node = root.getElement();
      if (node instanceof FavoritesListNode) {
        favoritesManager.removeFavoritesList((String)node.getValue());
      }
      else {
        final Object value = node.getValue();
        LOG.assertTrue(value != null, node);
        final NodeDescriptor parent = root.getParentDescriptor();
        if (parent instanceof FavoritesTreeNodeDescriptor) {
          final String name = ((FavoritesTreeNodeDescriptor)parent).getName();
          favoritesManager.removeRoot(name, value);
        }
      }
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
    if (roots == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    if (roots.length == 1
        && roots[0].getElement() instanceof FavoritesListNode
        && project.getName().equals(roots[0].getElement().getValue())) {
      e.getPresentation().setEnabled(false);
      return;
    }
    e.getPresentation().setEnabled(true);
  }
}
