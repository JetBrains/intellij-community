/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;

public abstract class ManagingFS implements FileSystemInterface {
  public static ManagingFS getInstance() {
    return ApplicationManager.getApplication().getComponent(ManagingFS.class);
  }

  @Nullable
  public abstract DataInputStream readAttribute(VirtualFile file, FileAttribute att);

  public abstract DataOutputStream writeAttribute(VirtualFile file, FileAttribute att);

  public abstract int getModificationCount(VirtualFile fileOrDirectory);

  public abstract int getFilesystemModificationCount();

  public abstract boolean areChildrenLoaded(VirtualFile dir);

  public abstract void processEvents(List<? extends VFileEvent> events);

  public abstract NewVirtualFile findRoot(final String basePath, NewVirtualFileSystem fs);

  public abstract void refresh(final boolean asynchronous);

  public abstract VirtualFile[] getRoots(final NewVirtualFileSystem fs);


  public abstract VirtualFile[] getRoots();
}