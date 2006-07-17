package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;

import java.io.File;
import java.util.Collection;
import java.util.Set;

/**
 * @author max
 */
public abstract class VcsDirtyScope {
  public abstract Collection<VirtualFile> getAffectedContentRoots();
  public abstract AbstractVcs getVcs();
  public abstract Set<FilePath> getDirtyFiles();
  public abstract Set<FilePath> getRecursivelyDirtyDirectories();
  public abstract void iterate(Processor<FilePath> iterator);
  public abstract boolean belongsTo(final FilePath path);

  public static VirtualFile getRootFor(ProjectFileIndex index, FilePath file) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    VirtualFile parent = file.getVirtualFile();
    if (parent == null) {
      parent = file.getVirtualFileParent();
    }
    if (parent == null) {
      File ioFile = file.getIOFile();
      do {
        parent = LocalFileSystem.getInstance().findFileByIoFile(ioFile);
        if (parent != null) break;
        ioFile = ioFile.getParentFile();
        if (ioFile == null) return null;
      }
      while (true);
    }

    return index.getContentRootForFile(parent);
  }
}
