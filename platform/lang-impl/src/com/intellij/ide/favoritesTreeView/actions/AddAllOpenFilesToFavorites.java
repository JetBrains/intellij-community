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

import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.util.ArrayList;

/**
 * User: anna
 * Date: Apr 5, 2005
 */
public class AddAllOpenFilesToFavorites extends AnAction {
  private final String myFavoritesName;

  public AddAllOpenFilesToFavorites(String chosenList) {
    getTemplatePresentation().setText(chosenList, false);
    myFavoritesName = chosenList;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
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
    ArrayList<PsiFile> result = new ArrayList<PsiFile>();
    final FileEditorManager editorManager = FileEditorManager.getInstance(project);
    final PsiManager psiManager = PsiManager.getInstance(project);
    final VirtualFile[] openFiles = editorManager.getOpenFiles();
    for (VirtualFile openFile : openFiles) {
      if (!openFile.isValid()) continue;
      final PsiFile psiFile = psiManager.findFile(openFile);
      if (psiFile != null) {
        result.add(psiFile);
      }
    }
    return result;
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    e.getPresentation().setEnabled(!getFilesToAdd(project).isEmpty());
  }
}
