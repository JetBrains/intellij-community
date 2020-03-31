// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.favoritesTreeView.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.favoritesTreeView.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.CommonActionsPanel;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author anna
 * @author Konstantin Bulenkov
 */
public class DeleteFromFavoritesAction extends AnActionButton implements DumbAware {
  private static final Logger LOG = Logger.getInstance(DeleteFromFavoritesAction.class);

  public DeleteFromFavoritesAction() {
    super(IdeBundle.messagePointer("action.remove.from.current.favorites"), IconUtil.getRemoveIcon());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = e.getProject();
    FavoritesViewTreeBuilder builder = FavoritesTreeViewPanel.FAVORITES_TREE_BUILDER_KEY.getData(dataContext);
    if (project == null || builder == null) {
      return;
    }
    Set<Object> selection = builder.getSelectedElements();
    if (selection.isEmpty()) {
      return;
    }
    FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
    String listName = FavoritesTreeViewPanel.FAVORITES_LIST_NAME_DATA_KEY.getData(dataContext);
    FavoritesListProvider provider = favoritesManager.getListProvider(listName);
    if (provider != null && provider.willHandle(CommonActionsPanel.Buttons.REMOVE, project, selection)) {
      provider.handle(CommonActionsPanel.Buttons.REMOVE, project, selection, builder.getTree());
      return;
    }
    FavoriteTreeNodeDescriptor[] roots = FavoritesTreeViewPanel.CONTEXT_FAVORITES_ROOTS_DATA_KEY.getData(dataContext);
    final DnDAwareTree tree = FavoritesTreeViewPanel.FAVORITES_TREE_KEY.getData(dataContext);

    assert roots != null && tree != null;
    Map<String, List<AbstractTreeNode<?>>> toRemove = new HashMap<>();
    for (FavoriteTreeNodeDescriptor root : roots) {
      final AbstractTreeNode node = root.getElement();
      if (node instanceof FavoritesListNode) {
        favoritesManager.removeFavoritesList((String)node.getValue());
      }
      else {
        final FavoritesListNode listNode = FavoritesTreeUtil.extractParentList(root);
        LOG.assertTrue(listNode != null);
        final String name = listNode.getName();
        if (!toRemove.containsKey(name)) {
          toRemove.put(name, new ArrayList<>());
        }
        toRemove.get(name).add(node);
      }
    }

    for (String name : toRemove.keySet()) {
      favoritesManager.removeRoot(name, toRemove.get(name));
    }
  }

  @Override
  public void updateButton(@NotNull AnActionEvent e) {
    e.getPresentation().setText(getTemplatePresentation().getText());
    final DataContext dataContext = e.getDataContext();
    Project project = e.getProject();
    FavoritesViewTreeBuilder builder = FavoritesTreeViewPanel.FAVORITES_TREE_BUILDER_KEY.getData(dataContext);
    if (project == null || builder == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    Set<Object> selection = builder.getSelectedElements();
    String listName = FavoritesTreeViewPanel.FAVORITES_LIST_NAME_DATA_KEY.getData(dataContext);
    if (listName == null) {//Selection is empty or contains several items under favorites/bookmarks/breakpoints at the same time
      e.getPresentation().setText(CommonActionsPanel.Buttons.REMOVE.getText());
      e.getPresentation().setEnabled(false);
      return;
    }

    FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
    FavoritesListProvider provider = favoritesManager.getListProvider(listName);
    if (provider != null) {
      boolean willHandle = provider.willHandle(CommonActionsPanel.Buttons.REMOVE, project, selection);
      e.getPresentation().setEnabled(willHandle);
      if (willHandle) {
        e.getPresentation().setText(provider.getCustomName(CommonActionsPanel.Buttons.REMOVE));
      } else {
        e.getPresentation().setText(CommonActionsPanel.Buttons.REMOVE.getText());
      }
      return;
    }

    FavoriteTreeNodeDescriptor[] roots = FavoritesTreeViewPanel.CONTEXT_FAVORITES_ROOTS_DATA_KEY.getData(dataContext);

    if (roots == null || roots.length == 0 || selection.isEmpty()) {
      e.getPresentation().setEnabled(false);
      return;
    }
    for (Object o : selection) {
      if (o instanceof AbstractTreeNode) {
        AbstractTreeNode node = (AbstractTreeNode)o;
        int deep = getDeep(node);
        if (deep != 2 && deep != 3) {//favorite list or it's nested "root"
          e.getPresentation().setEnabled(false);
          return;
        }
      }
      else {
        e.getPresentation().setEnabled(false);
        return;
      }
    }
  }

  private static int getDeep(AbstractTreeNode node) {
    int result = 0;
    while (node != null) {
      node = node.getParent();
      result++;
    }
    return result;
  }
}
