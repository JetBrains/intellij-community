// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.ide.IdeView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.PsiDirectory;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.ide.IdeBundle.message;
import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.roots.ProjectRootManager.getInstance;

public final class DirectoryChooserUtil {
  private DirectoryChooserUtil() {
  }

  public static @Nullable PsiDirectory getOrChooseDirectory(@NotNull IdeView view) {
    PsiDirectory[] dirs = view.getDirectories();
    if (dirs.length == 0) return null;
    if (dirs.length == 1) {
      return dirs[0];
    }
    else {
      Project project = dirs[0].getProject();
      return selectDirectory(project, dirs, null, "");
    }
  }

  public static @Nullable PsiDirectory selectDirectory(@NotNull Project project,
                                                       PsiDirectory @NotNull [] packageDirectories,
                                                       @Nullable PsiDirectory defaultDirectory,
                                                       @Nullable String postfixToShow) {
    ProjectFileIndex projectFileIndex = getInstance(project).getFileIndex();

    List<PsiDirectory> possibleDirs = new ArrayList<>();
    for (PsiDirectory dir : packageDirectories) {
      if (!dir.isValid() || !dir.isWritable() || possibleDirs.contains(dir) || !projectFileIndex.isInContent(dir.getVirtualFile())) {
        continue;
      }
      possibleDirs.add(dir);
    }

    if (possibleDirs.isEmpty()) return null;
    if (possibleDirs.size() == 1) return possibleDirs.get(0);

    if (getApplication().isUnitTestMode()) return possibleDirs.get(0);

    DirectoryChooser chooser = new DirectoryChooser(project);
    chooser.setTitle(message("title.choose.destination.directory"));
    chooser.fillList(possibleDirs.toArray(PsiDirectory.EMPTY_ARRAY), defaultDirectory, project, postfixToShow);
    return chooser.showAndGet() ? chooser.getSelectedDirectory() : null;
  }

  public static @Nullable PsiDirectory chooseDirectory(PsiDirectory @NotNull [] targetDirectories,
                                                       @Nullable PsiDirectory initialDirectory,
                                                       @NotNull Project project,
                                                       @Nullable Map<PsiDirectory, String> relativePathsToCreate) {
    final DirectoryChooser chooser = new DirectoryChooser(project, new DirectoryChooserModuleTreeView(project));
    chooser.setTitle(RefactoringBundle.message("choose.destination.directory"));
    chooser.fillList(
      targetDirectories,
      initialDirectory,
      project,
      relativePathsToCreate
    );
    if (!chooser.showAndGet()) {
      return null;
    }
    return chooser.getSelectedDirectory();
  }
}
