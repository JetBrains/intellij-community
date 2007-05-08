/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;

public abstract class ManagingFS extends NewVirtualFileSystem {
  @Nullable
  public abstract DataInputStream readAttribute(VirtualFile file, FileAttribute att);

  public abstract DataOutputStream writeAttribute(VirtualFile file, FileAttribute att);

  public abstract int getModificationCount(VirtualFile fileOrDirectory);

  public abstract int getFilesystemModificationCount();

  public abstract NewVirtualFileSystem getDelegate();

  public abstract boolean areChildrenLoaded(VirtualFile dir);
}