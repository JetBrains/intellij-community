/*
 * User: anna
 * Date: 17-Jan-2008
 */
package com.intellij.ide.projectView.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;

public class ProjectRootsUtil {
  private ProjectRootsUtil() {
  }

  public static boolean isSourceRoot(final PsiDirectory psiDirectory) {
    return psiDirectory.getVirtualFile().equals(
      ProjectRootManager.getInstance(psiDirectory.getProject()).getFileIndex()
        .getSourceRootForFile(psiDirectory.getVirtualFile()));
  }

  public static boolean isInTestSource(final VirtualFile directoryFile, final Project project) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return projectFileIndex.isInTestSourceContent(directoryFile);
  }

  public static boolean isSourceOrTestRoot(final VirtualFile virtualFile, final Project project) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Module module = projectFileIndex.getModuleForFile(virtualFile);
    if (module == null) return false;
    ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
    for (ContentEntry contentEntry : contentEntries) {
      SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (SourceFolder sourceFolder : sourceFolders) {
        if (virtualFile == sourceFolder.getFile()) return true;
      }
    }
    return false;
  }

  public static boolean isLibraryRoot(VirtualFile directoryFile, Project project) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (projectFileIndex.isInLibraryClasses(directoryFile)) {
      final VirtualFile parent = directoryFile.getParent();
      return parent == null || !projectFileIndex.isInLibraryClasses(parent);
    }
    return false;
  }
}