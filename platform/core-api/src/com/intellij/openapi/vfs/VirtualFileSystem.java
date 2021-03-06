// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.KeyedLazyInstanceEP;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Represents a Virtual File System (VFS).
 * <p>
 * See <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/virtual_file_system.html">Virtual File System</a> (SDK docs).
 *
 * @see VirtualFile
 * @see VirtualFileManager
 */
public abstract class VirtualFileSystem {
  protected VirtualFileSystem() { }

  /**
   * Gets the protocol for this file system. Protocols should differ for all file systems.
   * Should be the same as corresponding {@link KeyedLazyInstanceEP#key}.
   *
   * @return String representing the protocol
   * @see VirtualFile#getUrl
   * @see VirtualFileManager#getFileSystem
   */
  @NonNls
  public abstract @NotNull String getProtocol();

  /**
   * Searches for the file specified by given path. Path is a string which uniquely identifies file within given
   * {@link VirtualFileSystem}. Format of the path depends on the concrete file system.
   * For {@code LocalFileSystem} it is an absolute path (both Unix- and Windows-style separator chars are allowed).
   *
   * @param path the path to find file by
   * @return a virtual file if found, {@code null} otherwise
   */
  public abstract @Nullable VirtualFile findFileByPath(@NotNull @NonNls String path);

  /**
   * Fetches presentable URL of file with the given path in this file system.
   *
   * @param path the path to get presentable URL for
   * @return presentable URL
   * @see VirtualFile#getPresentableUrl
   */
  public @NotNull String extractPresentableUrl(@NotNull String path) {
    return path.replace('/', File.separatorChar);
  }

  /**
   * Refreshes the cached information for all files in this file system from the physical file system.<p>
   * <p/>
   * If {@code asynchronous} is {@code false} this method should be only called within write-action.
   * See {@link Application#runWriteAction}.
   *
   * @param asynchronous if {@code true} then the operation will be performed in a separate thread,
   *                     otherwise will be performed immediately
   * @see VirtualFile#refresh
   * @see VirtualFileManager#syncRefresh
   * @see VirtualFileManager#asyncRefresh
   */
  public abstract void refresh(boolean asynchronous);

  /**
   * Refreshes only the part of the file system needed for searching the file by the given path and finds file
   * by the given path.<br>
   * <p/>
   * This method is useful when the file was created externally and you need to find <code>{@link VirtualFile}</code>
   * corresponding to it.<p>
   * <p/>
   * If this method is invoked not from Swing event dispatch thread, then it must not happen inside a read action. The reason is that
   * then the method call won't return until proper VFS events are fired, which happens on Swing thread and in write action. So invoking
   * this method in a read action would result in a deadlock.
   *
   * @param path the path
   * @return <code>{@link VirtualFile}</code> if the file was found, {@code null} otherwise
   */
  public abstract @Nullable VirtualFile refreshAndFindFileByPath(@NotNull String path);

  /**
   * Adds listener to the file system. Normally one should use {@link VirtualFileManager#VFS_CHANGES} message bus topic.
   *
   * @param listener the listener
   * @see VirtualFileListener
   * @see VirtualFileManager#VFS_CHANGES
   */
  public abstract void addVirtualFileListener(@NotNull VirtualFileListener listener);

  /**
   * Same as {@link #addVirtualFileListener(VirtualFileListener)}, but automatically removes listener when {@code disposable} is disposed.
   */
  public final void addVirtualFileListener(@NotNull VirtualFileListener listener, @NotNull Disposable disposable) {
    addVirtualFileListener(listener);
    Disposer.register(disposable, () -> removeVirtualFileListener(listener));
  }

  /**
   * Removes listener form the file system.
   *
   * @param listener the listener
   */
  public abstract void removeVirtualFileListener(@NotNull VirtualFileListener listener);

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
  protected abstract @NotNull VirtualFile createChildFile(Object requestor, @NotNull VirtualFile vDir, @NotNull String fileName) throws IOException;

  /**
   * Implementation of adding directories in this file system
   *
   * @see VirtualFile#createChildDirectory(Object,String)
   */
  protected abstract @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile vDir, @NotNull String dirName) throws IOException;

  /**
   * Implementation of copying files in this file system
   *
   * @see VirtualFile#copy(Object,VirtualFile,String)
   */
  protected abstract @NotNull VirtualFile copyFile(Object requestor,
                                          @NotNull VirtualFile virtualFile,
                                          @NotNull VirtualFile newParent,
                                          @NotNull String copyName) throws IOException;

  public abstract boolean isReadOnly();

  public boolean isCaseSensitive() {
    return true;
  }

  public boolean isValidName(@NotNull String name) {
    return !name.isEmpty() && name.indexOf('\\') < 0 && name.indexOf('/') < 0;
  }

  /**
   * @return a related {@link Path} for a given virtual file where possible or
   * {@code null} otherwise.
   * <br />
   * The returned {@link Path} may not have a default filesystem behind.
   */
  public @Nullable Path getNioPath(@NotNull VirtualFile file) {
    return null;
  }
}