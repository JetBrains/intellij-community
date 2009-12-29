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
import com.intellij.ide.favoritesTreeView.FavoritesTreeNodeDescriptor;
import com.intellij.ide.favoritesTreeView.FavoritesTreeViewPanel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: Feb 23, 2005
 */
public class DeleteFromFavoritesAction extends AnAction implements DumbAware {
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
    String listName = FavoritesTreeViewPanel.FAVORITES_LIST_NAME_DATA_KEY.getData(dataContext);
    assert roots != null;
    assert listName != null;
    for (FavoritesTreeNodeDescriptor root : roots) {
      final Object value = root.getElement().getValue();
      LOG.assertTrue(value != null, root.getElement());
      favoritesManager.removeRoot(listName, value);
    }
  }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null){
      e.getPresentation().setEnabled(false);
      return;
    }
    final String listName = FavoritesTreeViewPanel.FAVORITES_LIST_NAME_DATA_KEY.getData(dataContext);
    FavoritesTreeNodeDescriptor[] roots = FavoritesTreeViewPanel.CONTEXT_FAVORITES_ROOTS_DATA_KEY.getData(dataContext);
    e.getPresentation().setEnabled(listName != null && roots != null && roots.length != 0);
  }
}
