/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class NewVirtualFileSystem extends VirtualFileSystem {
  @NonNls private static final String FILE_SEPARATORS = "/" + File.separator;

  @NonNls
  public abstract String getProtocol();

  @NonNls
  public abstract String getBaseUrl();

  public abstract boolean exists(VirtualFile fileOrDirectory);

  public abstract String[] list(VirtualFile file);
  public abstract VirtualFile[] listFiles(VirtualFile file);

  public abstract boolean isDirectory(VirtualFile file);

  public abstract long getTimeStamp(VirtualFile file);
  public abstract void setTimeStamp(VirtualFile file, long modstamp) throws IOException;

  public abstract boolean isWritable(VirtualFile file);
  public abstract void setWritable(VirtualFile file, boolean writableFlag) throws IOException;

  public abstract VirtualFile createChildDirectory(final Object requestor, VirtualFile parent, String dir) throws IOException;
  public abstract VirtualFile createChildFile(final Object requestor, VirtualFile parent, String file) throws IOException;

  public abstract void deleteFile(final Object requestor, VirtualFile file) throws IOException;
  public abstract void moveFile(final Object requestor, VirtualFile from, VirtualFile newParent) throws IOException;
  public abstract void renameFile(final Object requestor, VirtualFile from, String newName) throws IOException;
  public abstract VirtualFile copyFile(final Object requestor, VirtualFile from, VirtualFile newParent, final String copyName) throws IOException;

  public abstract InputStream getInputStream(VirtualFile file) throws IOException;
  public abstract OutputStream getOutputStream(VirtualFile file, final Object requestor, final long modStamp, final long timeStamp) throws IOException;

  public abstract String extractPresentableUrl(String path);

  /**
   * @return the CRC-32 checksum of the file content, or -1 if not known
   */
  public abstract long getCRC(VirtualFile file);

  public abstract long getLength(VirtualFile file);

  public abstract boolean isCaseSensitive();

  public abstract VirtualFile getRoot();

  public abstract int getId(final VirtualFile parent, final String childName);

  @Nullable
  public VirtualFile findFileByPath(@NotNull @NonNls final String path) {
    final String basePath = getRoot().getPath();
    VirtualFile file = getRoot();
    for (String pathElement : StringUtil.tokenize(path.substring(basePath.length()), FILE_SEPARATORS)) {
      if (pathElement.length() == 0) continue;
      file = file.findChild(pathElement);
      if (file == null) return null;
    }

    return file;
  }

  public void forceRefreshFiles(final boolean asynchronous, @NotNull final VirtualFile... files) {
    // TODO
  }

  public void refresh(final boolean asynchronous) {
    //TODO
  }

  @Nullable
  public VirtualFile refreshAndFindFileByPath(final String path) {
    refresh(false);
    return findFileByPath(path);
  }
}