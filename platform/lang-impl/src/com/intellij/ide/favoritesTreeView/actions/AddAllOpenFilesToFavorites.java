// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.favoritesTreeView.actions;

import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class AddAllOpenFilesToFavorites extends AnAction implements DumbAware {
  private final String myFavoritesName;

  public AddAllOpenFilesToFavorites(String chosenList) {
    //noinspection HardCodedStringLiteral
    getTemplatePresentation().setText(chosenList, false);
    myFavoritesName = chosenList;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    final FavoritesManager favoritesManager = FavoritesManager.getInstance(project);

    final ArrayList<PsiFile> filesToAdd = getFilesToAdd(project);
    for (PsiFile file : filesToAdd) {
      favoritesManager.addRoots(myFavoritesName, null, file);
    }
  }

  static ArrayList<PsiFile> getFilesToAdd(Project project) {
    ArrayList<PsiFile> result = new ArrayList<>();
    final FileEditorManager editorManager = FileEditorManager.getInstance(project);
    final PsiManager psiManager = PsiManager.getInstance(project);
    for (VirtualFile openFile : editorManager.getOpenFiles()) {
      if (!openFile.isValid()) continue;
      final PsiFile psiFile = psiManager.findFile(openFile);
      if (psiFile != null) {
        result.add(psiFile);
      }
    }
    return result;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (!Registry.is("ide.favorites.tool.window.applicable", false)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    final Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    e.getPresentation().setEnabled(!getFilesToAdd(project).isEmpty());
  }
}
