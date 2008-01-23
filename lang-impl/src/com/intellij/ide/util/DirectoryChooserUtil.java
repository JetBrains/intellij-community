package com.intellij.ide.util;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;

public class DirectoryChooserUtil {
  private DirectoryChooserUtil() {
  }

  public static PsiDirectory getOrChooseDirectory(IdeView view) {
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

  public static PsiDirectory selectDirectory(Project project,
                                             PsiDirectory[] packageDirectories,
                                             PsiDirectory defaultDirectory,
                                             String postfixToShow) {
    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    ArrayList<PsiDirectory> possibleDirs = new ArrayList<PsiDirectory>();
    for (PsiDirectory dir : packageDirectories) {
      if (!dir.isValid()) continue;
      if (!dir.isWritable()) continue;
      if (possibleDirs.contains(dir)) continue;
      if (!projectFileIndex.isInContent(dir.getVirtualFile())) continue;
      possibleDirs.add(dir);
    }

    if (possibleDirs.size() == 0) return null;
    if (possibleDirs.size() == 1) return possibleDirs.get(0);

    if (ApplicationManager.getApplication().isUnitTestMode()) return possibleDirs.get(0);

    DirectoryChooser chooser = new DirectoryChooser(project);
    chooser.setTitle(IdeBundle.message("title.choose.destination.directory"));
    chooser.fillList(possibleDirs.toArray(new PsiDirectory[possibleDirs.size()]), defaultDirectory, project, postfixToShow);
    chooser.show();
    return chooser.isOK() ? chooser.getSelectedDirectory() : null;
  }

  public static @Nullable
  PsiDirectory chooseDirectory(PsiDirectory[] targetDirectories,
                                                       @Nullable PsiDirectory initialDirectory, Project project,
                                                       Map<PsiDirectory, String> relativePathsToCreate) {
    final DirectoryChooser chooser = new DirectoryChooser(project, new DirectoryChooserModuleTreeView(project));
    chooser.setTitle(RefactoringBundle.message("choose.destination.directory"));
    chooser.fillList(
      targetDirectories,
      initialDirectory,
      project,
      relativePathsToCreate
    );
    chooser.show();
    if (!chooser.isOK()) return null;
    return chooser.getSelectedDirectory();
  }
}
