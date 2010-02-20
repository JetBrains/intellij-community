/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * Represents a virtual file system.
 *
 * @see VirtualFile
 * @see VirtualFileManager
 */
public abstract class VirtualFileSystem {
  protected VirtualFileSystem() {
  }

  /**
   * Gets the protocol for this file system. Protocols should differ for all file systems.
   *
   * @return String representing the protocol
   * @see VirtualFile#getUrl
   * @see VirtualFileManager#getFileSystem
   */
  @NonNls @NotNull 
  public abstract String getProtocol();

  /**
   * Searches for the file specified by given path. Path is a string which uniquely identifies file within given
   * <code>{@link VirtualFileSystem}</code>. Format of the path depends on the concrete file system.
   * For <code>LocalFileSystem</code> it is an absoulute file path with file separator characters (File.separatorChar)
   * replaced to the forward slash ('/').<p>
   * <p/>
   * Example: to find a <code>{@link VirtualFile}</code> corresponding to the physical file with the specified path one
   * can use the followoing code: <code>LocalFileSystem.getInstance().findFileByPath(path.replace(File.separatorChar, '/'));</code>
   *
   * @param path the path to find file by
   * @return <code>{@link VirtualFile}</code> if the file was found, <code>null</code> otherwise
   */
  @Nullable
  public abstract VirtualFile findFileByPath(@NotNull @NonNls String path);

  /**
   * Fetches presentable URL of file with the given path in this file system.
   *
   * @param path the path to get presentable URL for
   * @return presentable URL
   * @see VirtualFile#getPresentableUrl
   */
  public String extractPresentableUrl(@NotNull String path) {
    return path.replace('/', File.separatorChar);
  }

  /**
   * Refreshes the cached information for all files in this file system from the physical file system.<p>
   * <p/>
   * If <code>asynchronous</code> is <code>false</code> this method should be only called within write-action.
   * See {@link com.intellij.openapi.application.Application#runWriteAction}.
   *
   * @param asynchronous if <code>true</code> then the operation will be performed in a separate thread,
   *                     otherwise will be performed immediately
   * @see VirtualFile#refresh
   * @see VirtualFileManager#refresh
   */
  public abstract void refresh(boolean asynchronous);

  /**
   * Refreshes only the part of the file system needed for searching the file by the given path and finds file
   * by the given path.<br>
   * <p/>
   * This method is useful when the file was created externally and you need to find <code>{@link VirtualFile}</code>
   * corresponding to it.<p>
   * <p/>
   * This method should be only called within write-action.
   * See {@link com.intellij.openapi.application.Application#runWriteAction}.
   *
   * @param path the path
   * @return <code>{@link VirtualFile}</code> if the file was found, <code>null</code> otherwise
   */
  @Nullable
  public abstract VirtualFile refreshAndFindFileByPath(@NotNull String path);

  /**
   * Adds listener to the file system. Normally one should use {@link VirtualFileManager#addVirtualFileListener}.
   *
   * @param listener the listener
   * @see VirtualFileListener
   * @see VirtualFileManager#addVirtualFileListener
   */
  public abstract void addVirtualFileListener(@NotNull VirtualFileListener listener);

  /**
   * Removes listener form the file system.
   *
   * @param listener the listener
   */
  public abstract void removeVirtualFileListener(@NotNull VirtualFileListener listener);

  @Deprecated
  /**
   * Deprecated. Current implementation blindly calls plain refresh against the file passed
   */
  public void forceRefreshFile(final boolean asynchronous, @NotNull VirtualFile file) {
    file.refresh(asynchronous, false);
  }

  /**
   * Implementation of deleting files in this file system
   *
   * @see VirtualFile#delete(Object)
   */
  protected abstract void deleteFile(Object requestor, @NotNull VirtualFile vFile) throws IOException;

  /**
   * Implementation of moving files in this file system
   *
   * @see VirtualFile#move(Object,VirtualFile)
   */
  protected abstract void moveFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent) throws IOException;

  /**
   * Implementation of renaming files in this file system
   *
   * @see VirtualFile#rename(Object,String)
   */
  protected abstract void renameFile(Object requestor, @NotNull VirtualFile vFile, @NotNull String newName) throws IOException;

  /**
   * Implementation of adding files in this file system
   *
   * @see VirtualFile#createChildData(Object,String)
   */
  protected abstract VirtualFile createChildFile(Object requestor, @NotNull VirtualFile vDir, @NotNull String fileName) throws IOException;

  /**
   * Implementation of adding directories in this file system
   *
   * @see VirtualFile#createChildDirectory(Object,String)
   */
  @NotNull
  protected abstract VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile vDir, @NotNull String dirName) throws IOException;

  /**
   * Implementation of copying files in this file system
   *
   * @see VirtualFile#copy(Object,VirtualFile,String)
   */
  protected abstract VirtualFile copyFile(final Object requestor,
                                       @NotNull VirtualFile virtualFile,
                                       @NotNull VirtualFile newParent,
                                       @NotNull String copyName) throws IOException;

  public abstract boolean isReadOnly();
}
