package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import gnu.trove.THashSet;

import java.io.File;
import java.util.Set;

/**
 * @author max
 */
public class VcsDirtyScope {
  private final VirtualFile myScopeRoot;
  private final Set<FilePath> myDirtyFiles = new THashSet<FilePath>();
  private final Set<FilePath> myDirtyDirectoriesRecursively = new THashSet<FilePath>();
  private final ProjectFileIndex myIndex;

  public VcsDirtyScope(final VirtualFile root, final Project project) {
    myScopeRoot = root;
    myIndex = ProjectRootManager.getInstance(project).getFileIndex();
  }

  public void addDirtyDirRecursively(FilePath newcomer) {
    for (FilePath oldBoy : myDirtyDirectoriesRecursively) {
      if (newcomer.isUnder(oldBoy, false)) {
        return;
      }

      if (oldBoy.isUnder(newcomer, false)) {
        myDirtyDirectoriesRecursively.remove(oldBoy);
        myDirtyDirectoriesRecursively.add(newcomer);
        return;
      }
    }
    myDirtyDirectoriesRecursively.add(newcomer);
  }

  public void addDirtyFile(FilePath newcomer) {
    for (FilePath oldBoy : myDirtyDirectoriesRecursively) {
      if (newcomer.isUnder(oldBoy, false)) {
        return;
      }
    }

    myDirtyFiles.add(newcomer);
  }

  public VirtualFile getScopeRoot() {
    return myScopeRoot;
  }

  public Set<FilePath> getDirtyFiles() {
    return myDirtyFiles;
  }

  public Set<FilePath> getRecursivelyDirtyDirectories() {
    return myDirtyDirectoriesRecursively;
  }

  public boolean belongsTo(FilePath path) {
    if (getRootFor(myIndex, path) != myScopeRoot) return false;

    for (FilePath filePath : myDirtyDirectoriesRecursively) {
      if (path.isUnder(filePath, false)) return true;
    }

    FilePath parent = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(path.getVirtualFileParent());
    for (FilePath filePath : myDirtyFiles) {
      if (filePath.equals(parent) || filePath.equals(path)) return true;
    }

    return false;
  }

  public static VirtualFile getRootFor(ProjectFileIndex index, FilePath file) {
    VirtualFile parent = file.getVirtualFileParent();
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
