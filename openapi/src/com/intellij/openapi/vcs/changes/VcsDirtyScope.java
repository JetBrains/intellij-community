package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import gnu.trove.THashSet;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
public class VcsDirtyScope {
  private final VirtualFile myScopeRoot;
  private final Set<FilePath> myDirtyFiles = new THashSet<FilePath>();
  private final Set<FilePath> myDirtyDirectoriesRecursively = new THashSet<FilePath>();
  private final ProjectFileIndex myIndex;
  private Project myProject;

  public VcsDirtyScope(final VirtualFile root, final Project project) {
    myProject = project;
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

    if (newcomer.isDirectory()) {
      final List<FilePath> files = new ArrayList<FilePath>(myDirtyFiles);
      for (FilePath oldBoy : files) {
        if (!oldBoy.isDirectory() && oldBoy.getVirtualFileParent() == newcomer.getVirtualFile()) {
          myDirtyFiles.remove(oldBoy);
        }
      }
    }
    else {
      for (FilePath oldBoy : myDirtyFiles) {
        if (oldBoy.isDirectory() && newcomer.getVirtualFileParent() == oldBoy.getVirtualFile()) {
          return;
        }
      }
    }

    myDirtyFiles.add(newcomer);
  }

  public VirtualFile getScopeRoot() {
    return myScopeRoot;
  }

  public Module getScopeModule() {
    if (myProject.isDisposed()) return null;
    return myIndex.getModuleForFile(myScopeRoot);
  }

  public Set<FilePath> getDirtyFiles() {
    return myDirtyFiles;
  }

  public Set<FilePath> getRecursivelyDirtyDirectories() {
    return myDirtyDirectoriesRecursively;
  }

  public void iterate(ContentIterator iterator) {
    if (myProject.isDisposed()) return;
    final Module module = myIndex.getModuleForFile(myScopeRoot);
    final ModuleFileIndex index = ModuleRootManager.getInstance(module).getFileIndex();

    for (FilePath dir : myDirtyDirectoriesRecursively) {
      final VirtualFile vFile = dir.getVirtualFile();
      if (vFile != null && vFile.isValid()) {
        index.iterateContentUnderDirectory(vFile, iterator);
      }
    }

    for (FilePath file : myDirtyFiles) {
      final VirtualFile vFile = file.getVirtualFile();
      if (vFile != null && vFile.isValid()) {
        iterator.processFile(vFile);
        if (vFile.isDirectory()) {
          for (VirtualFile child : vFile.getChildren()) {
            iterator.processFile(child);
          }
        }
      }
    }
  }

  public boolean belongsTo(FilePath path) {
    if (myProject.isDisposed()) return false;
    if (getRootFor(myIndex, path) != myScopeRoot) return false;

    for (FilePath filePath : myDirtyDirectoriesRecursively) {
      if (path.isUnder(filePath, false)) return true;
    }

    FilePath parent = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(path.getIOFile().getParentFile());
    for (FilePath filePath : myDirtyFiles) {
      if (filePath.equals(parent) || filePath.equals(path)) return true;
    }

    return false;
  }

  public static VirtualFile getRootFor(ProjectFileIndex index, FilePath file) {
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
