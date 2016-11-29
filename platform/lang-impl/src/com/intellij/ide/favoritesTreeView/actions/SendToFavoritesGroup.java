/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: Feb 24, 2005
 */
public class SendToFavoritesGroup extends ActionGroup implements DumbAware {
  @Override
  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) {
      return EMPTY_ARRAY;
    }
    final Project project = e.getProject();
    final List<String> availableFavoritesLists = FavoritesManager.getInstance(project).getAvailableFavoritesListNames();
    availableFavoritesLists.remove(FavoritesTreeViewPanel.FAVORITES_LIST_NAME_DATA_KEY.getData(e.getDataContext()));
    if (availableFavoritesLists.isEmpty()) {
      return new AnAction[]{new SendToNewFavoritesListAction()};
    }

    List<AnAction> actions = new ArrayList<>();

    for (String list : availableFavoritesLists) {
      actions.add(new SendToFavoritesAction(list));
    }
    actions.add(Separator.getInstance());
    actions.add(new SendToNewFavoritesListAction());
    return actions.toArray(new AnAction[actions.size()]);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setVisible(SendToFavoritesAction.isEnabled(e)
                                   && FavoritesTreeViewPanel.FAVORITES_LIST_NAME_DATA_KEY.getData(e.getDataContext()) != null);
  }

  private static class SendToNewFavoritesListAction extends AnAction {
    public SendToNewFavoritesListAction() {
      super(IdeBundle.message("action.send.to.new.favorites.list"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final DataContext dataContext = e.getDataContext();
      Project project = e.getProject();
      FavoritesTreeNodeDescriptor[] roots = FavoritesTreeViewPanel.CONTEXT_FAVORITES_ROOTS_DATA_KEY.getData(dataContext);
      String listName = FavoritesTreeViewPanel.FAVORITES_LIST_NAME_DATA_KEY.getData(dataContext);

      String newName = AddNewFavoritesListAction.doAddNewFavoritesList(project);
      if (newName != null) {
        new SendToFavoritesAction(newName).doSend(FavoritesManager.getInstance(project), roots, listName);
      }
    }
  }
}
