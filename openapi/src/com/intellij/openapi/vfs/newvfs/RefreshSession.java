/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.Arrays;
import java.util.Collection;

public abstract class RefreshSession {
  public abstract boolean isAsynchronous();

  public abstract void addFile(VirtualFile file);
  public abstract void addAllFiles(Collection<VirtualFile> files);

  public void addAllFiles(VirtualFile[] files) {
    addAllFiles(Arrays.asList(files));
  }

  public abstract void launch();
}
