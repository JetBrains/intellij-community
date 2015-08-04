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

import com.intellij.ide.favoritesTreeView.FavoritesListProvider;
import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.ide.favoritesTreeView.FavoritesTreeViewPanel;
import com.intellij.ide.favoritesTreeView.FavoritesViewTreeBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.ui.CommonActionsPanel;

import java.util.Set;

/**
 * User: Vassiliy.Kudryashov
 */
public class EditFavoritesAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    FavoritesViewTreeBuilder treeBuilder = FavoritesTreeViewPanel.FAVORITES_TREE_BUILDER_KEY.getData(e.getDataContext());
    String listName = FavoritesTreeViewPanel.FAVORITES_LIST_NAME_DATA_KEY.getData(e.getDataContext());
    if (project == null || treeBuilder == null || listName == null) {
      return;
    }
    FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
    FavoritesListProvider provider = favoritesManager.getListProvider(listName);
    Set<Object> selection = treeBuilder.getSelectedElements();
    if (provider != null && provider.willHandle(CommonActionsPanel.Buttons.EDIT, project, selection)) {
      provider.handle(CommonActionsPanel.Buttons.EDIT, project, selection, treeBuilder.getTree());
      return;
    }
    favoritesManager.renameList(project, listName);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setText(getTemplatePresentation().getText());
    e.getPresentation().setIcon(CommonActionsPanel.Buttons.EDIT.getIcon());
    e.getPresentation().setEnabled(true);
    Project project = e.getProject();
    FavoritesViewTreeBuilder treeBuilder = FavoritesTreeViewPanel.FAVORITES_TREE_BUILDER_KEY.getData(e.getDataContext());
    String listName = FavoritesTreeViewPanel.FAVORITES_LIST_NAME_DATA_KEY.getData(e.getDataContext());
    if (project == null || treeBuilder == null || listName == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
    FavoritesListProvider provider = favoritesManager.getListProvider(listName);
    Set<Object> selection = treeBuilder.getSelectedElements();
    if (provider != null) {
      e.getPresentation().setEnabled(provider.willHandle(CommonActionsPanel.Buttons.EDIT, project, selection));
      e.getPresentation().setText(provider.getCustomName(CommonActionsPanel.Buttons.EDIT));
    }
  }
}
