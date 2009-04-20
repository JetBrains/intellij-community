package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class ProjectExcludedFileIndex extends ExcludedFileIndex {
  private final ProjectRootManager myRootManager;
  private final DirectoryIndex myDirectoryIndex;

  public ProjectExcludedFileIndex(final Project project, final ProjectRootManager rootManager, final DirectoryIndex directoryIndex) {
    super(project);
    myRootManager = rootManager;
    myDirectoryIndex = directoryIndex;
  }

  public boolean isInContent(final VirtualFile file) {
    return myRootManager.getFileIndex().isInContent(file);
  }

  public boolean isExcludedFile(final VirtualFile file) {
    return myRootManager.getFileIndex().isIgnored(file);
  }

  public boolean isValidAncestor(final VirtualFile baseDir, VirtualFile childDir) {
    if (!childDir.isDirectory()) {
      childDir = childDir.getParent();
    }
    while (true) {
      if (childDir == null) return false;
      if (childDir.equals(baseDir)) return true;
      if (myDirectoryIndex.getInfoForDirectory(childDir) == null) return false;
      childDir = childDir.getParent();
    }
  }
}
