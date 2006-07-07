package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author max
 */
public abstract class VcsDirtyScopeManager {
  public static VcsDirtyScopeManager getInstance(Project project) {
    return project.getComponent(VcsDirtyScopeManager.class);
  }

  public abstract void markEverythingDirty();
  public abstract void fileDirty(VirtualFile file);
  public abstract void fileDirty(FilePath file);
  public abstract void dirDirtyRecursively(VirtualFile dir, final boolean scheduleUpdate);
}
