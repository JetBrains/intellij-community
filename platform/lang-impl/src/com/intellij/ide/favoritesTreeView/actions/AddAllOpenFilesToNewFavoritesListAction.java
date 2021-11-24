// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.favoritesTreeView.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

class AddAllOpenFilesToNewFavoritesListAction extends AnAction implements DumbAware {
  AddAllOpenFilesToNewFavoritesListAction() {
    super(IdeBundle.messagePointer("action.add.all.open.tabs.to.new.favorites.list"),
          IdeBundle.messagePointer("action.add.to.new.favorites.list"), AllIcons.General.Add);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final String newName = AddNewFavoritesListAction.doAddNewFavoritesList(e.getProject());
    if (newName != null) {
      new AddAllOpenFilesToFavorites(newName).actionPerformed(e);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (!Registry.is("ide.favorites.tool.window.applicable", false)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabled(false);
    }
    else {
      e.getPresentation().setEnabled(!AddAllOpenFilesToFavorites.getFilesToAdd(project).isEmpty());
    }
  }
}
