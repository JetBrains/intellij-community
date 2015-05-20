/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Common interface of archive-based file systems (jar://, phar:// etc).
 *
 * @since 138.100
 */
public abstract class ArchiveFileSystem extends NewVirtualFileSystem {
  /**
   * Returns a root entry of an archive hosted by a given local file
   * (i.e.: file:///path/to/jar.jar => jar:///path/to/jar.jar!/),
   * or null if the file does not host this file system.
   */
  @Nullable
  public VirtualFile getRootByLocal(@NotNull VirtualFile file) {
    return findFileByPath(composeRootPath(file.getPath()));
  }

  /**
   * Returns a root entry of an archive which hosts a given entry file
   * (i.e.: jar:///path/to/jar.jar!/resource.xml => jar:///path/to/jar.jar!/),
   * or null if the file does not belong to this file system.
   */
  @Nullable
  public VirtualFile getRootByEntry(@NotNull VirtualFile entry) {
    if (entry.getFileSystem() != this) return null;
    String rootPath = extractRootPath(entry.getPath());
    return findFileByPath(rootPath);
  }

  /**
   * Returns a local file of an archive which hosts a given entry file
   * (i.e.: jar:///path/to/jar.jar!/resource.xml => file:///path/to/jar.jar),
   * or null if the file does not belong to this file system.
   */
  @Nullable
  public VirtualFile getLocalByEntry(@NotNull VirtualFile entry) {
    if (entry.getFileSystem() != this) return null;
    String localPath = extractLocalPath(extractRootPath(entry.getPath()));
    return LocalFileSystem.getInstance().findFileByPath(localPath);
  }

  /**
   * Strips any separator chars from a root path (obtained via {@link #extractRootPath(String)}) to obtain a path to a local file.
   */
  @NotNull
  protected abstract String extractLocalPath(@NotNull String rootPath);

  /**
   * A reverse to {@link #extractLocalPath(String)} - i.e. dresses a local file path to make it a suitable root path for this filesystem.
   */
  @NotNull
  protected abstract String composeRootPath(@NotNull String localPath);

  @NotNull
  protected abstract ArchiveHandler getHandler(@NotNull VirtualFile entryFile);

  // standard implementations

  @Override
  public int getRank() {
    return LocalFileSystem.getInstance().getRank() + 1;
  }

  @NotNull
  @Override
  public VirtualFile copyFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent, @NotNull String copyName) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", file.getUrl()));
  }

  @NotNull
  @Override
  public VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile parent, @NotNull String dir) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", parent.getUrl()));
  }

  @NotNull
  @Override
  public VirtualFile createChildFile(Object requestor, @NotNull VirtualFile parent, @NotNull String file) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", parent.getUrl()));
  }

  @Override
  public void deleteFile(Object requestor, @NotNull VirtualFile file) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", file.getUrl()));
  }

  @Override
  public void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", file.getUrl()));
  }

  @Override
  public void renameFile(Object requestor, @NotNull VirtualFile file, @NotNull String newName) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", file.getUrl()));
  }

  @NotNull
  protected String getRelativePath(@NotNull VirtualFile file) {
    String path = file.getPath();
    String relativePath = path.substring(extractRootPath(path).length());
    return StringUtil.startsWithChar(relativePath, '/') ? relativePath.substring(1) : relativePath;
  }

  @Nullable
  @Override
  public FileAttributes getAttributes(@NotNull VirtualFile file) {
    return getHandler(file).getAttributes(getRelativePath(file));
  }

  @NotNull
  @Override
  public String[] list(@NotNull VirtualFile file) {
    return getHandler(file).list(getRelativePath(file));
  }

  @Override
  public boolean exists(@NotNull VirtualFile file) {
    if (file.getParent() == null) {
      return getLocalByEntry(file) != null;
    }
    else {
      return getAttributes(file) != null;
    }
  }

  @Override
  public boolean isDirectory(@NotNull VirtualFile file) {
    if (file.getParent() == null) return true;
    FileAttributes attributes = getAttributes(file);
    return attributes == null || attributes.isDirectory();
  }

  @Override
  public boolean isWritable(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public long getTimeStamp(@NotNull VirtualFile file) {
    if (file.getParent() == null) {
      VirtualFile host = getLocalByEntry(file);
      if (host != null) return host.getTimeStamp();
    }
    else {
      FileAttributes attributes = getAttributes(file);
      if (attributes != null) return attributes.lastModified;
    }
    return ArchiveHandler.DEFAULT_TIMESTAMP;
  }

  @Override
  public long getLength(@NotNull VirtualFile file) {
    if (file.getParent() == null) {
      VirtualFile host = getLocalByEntry(file);
      if (host != null) return host.getLength();
    }
    else {
      FileAttributes attributes = getAttributes(file);
      if (attributes != null) return attributes.length;
    }
    return ArchiveHandler.DEFAULT_LENGTH;
  }

  @NotNull
  @Override
  public byte[] contentsToByteArray(@NotNull VirtualFile file) throws IOException {
    return getHandler(file).contentsToByteArray(getRelativePath(file));
  }

  @NotNull
  @Override
  public InputStream getInputStream(@NotNull VirtualFile file) throws IOException {
    return new BufferExposingByteArrayInputStream(contentsToByteArray(file));
  }

  @Override
  public void setTimeStamp(@NotNull VirtualFile file, long timeStamp) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", file.getUrl()));
  }

  @Override
  public void setWritable(@NotNull VirtualFile file, boolean writableFlag) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", file.getUrl()));
  }

  @NotNull
  @Override
  public OutputStream getOutputStream(@NotNull VirtualFile file, Object requestor, long modStamp, long timeStamp) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", file.getUrl()));
  }
}
