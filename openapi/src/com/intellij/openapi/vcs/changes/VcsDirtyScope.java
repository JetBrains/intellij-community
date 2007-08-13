package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;

import java.util.Collection;
import java.util.Set;

/**
 * @author max
 */
public abstract class VcsDirtyScope {
  public abstract Collection<VirtualFile> getAffectedContentRoots();
  public abstract Project getProject();
  public abstract AbstractVcs getVcs();
  public abstract Set<FilePath> getDirtyFiles();
  public abstract Set<FilePath> getDirtyFilesNoExpand();
  public abstract Set<FilePath> getRecursivelyDirtyDirectories();
  public abstract void iterate(Processor<FilePath> iterator);
  public abstract boolean belongsTo(final FilePath path);
}
