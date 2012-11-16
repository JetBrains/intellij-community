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

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.ide.favoritesTreeView.FavoritesTreeViewPanel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;

/**
 * User: anna
 * Date: Feb 23, 2005
 */
public class RenameFavoritesListAction extends AnAction implements DumbAware {
  public RenameFavoritesListAction() {
    super(IdeBundle.message("action.rename.favorites.list"), IdeBundle.message("action.rename.favorites.list"),
          AllIcons.Actions.Menu_replace);
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    final FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
    String listName = FavoritesTreeViewPanel.FAVORITES_LIST_NAME_DATA_KEY.getData(dataContext);
    if (! isFavoritesListReadWrite(project, listName)) return;
    final String newName = Messages
      .showInputDialog(project, IdeBundle.message("prompt.input.favorites.list.new.name"), IdeBundle.message("title.rename.favorites.list"),
                       Messages.getInformationIcon(), listName, new InputValidator() {
          public boolean checkInput(String inputString) {
            return inputString != null && inputString.trim().length() > 0;
          }

          public boolean canClose(String inputString) {
            String[] lists = favoritesManager.getAvailableFavoritesListNames();
            final boolean isNew = ArrayUtil.find(lists, inputString.trim()) == -1;
            if (!isNew) {
              Messages.showErrorDialog(project, IdeBundle.message("error.favorites.list.already.exists", inputString.trim()),
                                       IdeBundle.message("title.unable.to.add.favorites.list"));
              return false;
            }
            return inputString.trim().length() > 0;
          }
        });

    if (listName != null && newName != null) {
      favoritesManager.renameFavoritesList(listName, newName);
    }
  }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    String listName = FavoritesTreeViewPanel.FAVORITES_LIST_NAME_DATA_KEY.getData(dataContext);
    boolean enabled = isFavoritesListReadWrite(project, listName);
    e.getPresentation().setEnabled(enabled);
  }

  public static boolean isFavoritesListReadWrite(Project project, String listName) {
    boolean enabled = listName != null && !listName.equals(project.getName());
    if (! StringUtil.isEmptyOrSpaces(listName)) {
      final FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
      enabled = ! favoritesManager.isReadOnly(listName);
    }
    return enabled;
  }
}
