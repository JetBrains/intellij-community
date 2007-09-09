package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

/**
 * Manages asynchronous file status updating for files under VCS.
 *
 * @author max
 * @since 6.0
 */
public abstract class VcsDirtyScopeManager {
  public static VcsDirtyScopeManager getInstance(Project project) {
    return project.getComponent(VcsDirtyScopeManager.class);
  }

  /**
   * Requests an asynchronous file status update for all files in the project.
   */
  public abstract void markEverythingDirty();

  /**
   * Requests an asynchronous file status update for the specified virtual file. Must be called from a read action.
   *
   * @param file the file for which the status update is requested.
   */
  public abstract void fileDirty(VirtualFile file);

  /**
   * Requests an asynchronous file status update for the specified file path. Must be called from a read action.
   *
   * @param file the file path for which the status update is requested.
   */
  public abstract void fileDirty(FilePath file);

  /**
   * Requests an asynchronous file status update for all files under the specified directory.
   *
   * @param dir the directory for which the file status update is requested.
   * @deprecated Use single-parameter version instead.
   */
  public abstract void dirDirtyRecursively(VirtualFile dir, final boolean scheduleUpdate);

  /**
   * Requests an asynchronous file status update for all files under the specified directory.
   *
   * @param dir the directory for which the file status update is requested.
   */
  public abstract void dirDirtyRecursively(VirtualFile dir);

  public abstract void dirDirtyRecursively(FilePath path);

  public abstract List<VcsDirtyScope> retrieveScopes();
}
