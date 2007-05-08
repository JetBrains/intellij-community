/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class NewVirtualFile extends VirtualFile {
  @NotNull
  public abstract String getName();

  public abstract boolean isDirectory();

  public boolean isValid() {
    return exists();
  }

  public byte[] contentsToByteArray() throws IOException {
    return FileUtil.adaptiveLoadBytes(getInputStream());
  }

  @NotNull
  public abstract VirtualFileSystem getFileSystem();
  public abstract VirtualFile getParent();

  @NotNull
  public abstract VirtualFile[] getChildren();

  public abstract @Nullable VirtualFile findChild(String name);

  public abstract @NotNull String getUrl();
  public abstract @NotNull String getPath();

  public abstract @NotNull InputStream getInputStream() throws IOException;
  public abstract @NotNull OutputStream getOutputStream(final Object requestor, final long modStamp, final long timeStamp) throws IOException;

  public abstract @NotNull VirtualFile createChildDirectory(final Object requestor, String name) throws IOException;
  public abstract @NotNull VirtualFile createChildData(final Object requestor, String name) throws IOException;

  public abstract boolean isWritable();
  public abstract long getTimeStamp();
  public abstract void setTimeStamp(final long time) throws IOException;
  public abstract long getLength();

  public abstract int getId();

  public void refresh(final boolean asynchronous, final boolean recursive, final Runnable postRunnable) {
    // TODO
  }
}