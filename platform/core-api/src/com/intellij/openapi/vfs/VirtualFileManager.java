// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.util.io.URLUtil;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Manages virtual file systems.
 *
 * @see VirtualFileSystem
 */
public abstract class VirtualFileManager implements ModificationTracker {
  @Topic.AppLevel
  public static final Topic<BulkFileListener> VFS_CHANGES = new Topic<>(BulkFileListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  public static final @NotNull ModificationTracker VFS_STRUCTURE_MODIFICATIONS = () -> getInstance().getStructureModificationCount();

  private static VirtualFileManager ourInstance = CachedSingletonsRegistry.markCachedField(VirtualFileManager.class);

  /**
   * Gets the instance of {@code VirtualFileManager}.
   *
   * @return {@code VirtualFileManager}
   */
  public static @NotNull VirtualFileManager getInstance() {
    VirtualFileManager result = ourInstance;
    if (result == null) {
      ourInstance = result = ApplicationManager.getApplication().getService(VirtualFileManager.class);
    }
    return result;
  }

  /**
   * Gets VirtualFileSystem with the specified protocol.
   *
   * @param protocol String representing the protocol
   * @return {@link VirtualFileSystem}
   * @see VirtualFileSystem#getProtocol
   */
  public abstract VirtualFileSystem getFileSystem(String protocol);

  /**
   * <p>Refreshes the cached file systems information from the physical file systems synchronously.<p/>
   *
   * <p><strong>Note</strong>: this method should be only called within a write-action
   * (see {@linkplain com.intellij.openapi.application.Application#runWriteAction})</p>
   *
   * @return refresh session ID.
   */
  public abstract long syncRefresh();

  /**
   * Refreshes the cached file systems information from the physical file systems asynchronously.
   * Launches specified action when refresh is finished.
   *
   * @return refresh session ID.
   */
  public abstract long asyncRefresh(@Nullable Runnable postAction);

  public abstract void refreshWithoutFileWatcher(boolean asynchronous);

  /**
   * Searches for a file specified by the given {@link VirtualFile#getUrl() URL}.
   *
   * @param url the URL to find file by
   * @return <code>{@link VirtualFile}</code> if the file was found, {@code null} otherwise
   * @see VirtualFile#getUrl
   * @see VirtualFileSystem#findFileByPath
   * @see #refreshAndFindFileByUrl
   */
  public @Nullable VirtualFile findFileByUrl(@NonNls @NotNull String url) {
    return null;
  }

  /**
   * Looks for a related {@link VirtualFile} for a given {@link Path}
   * @return <code>{@link VirtualFile}</code> if the file was found, {@code null} otherwise
   * @see VirtualFile#getUrl
   * @see VirtualFileSystem#findFileByPath
   * @see #refreshAndFindFileByUrl
   */
  public @Nullable VirtualFile findFileByNioPath(@NotNull Path path) {
    return null;
  }

  /**
   * <p>Refreshes only the part of the file system needed for searching the file by the given URL and finds file
   * by the given URL.</p>
   *
   * <p>This method is useful when the file was created externally and you need to find <code>{@link VirtualFile}</code>
   * corresponding to it.</p>
   *
   * <p>If this method is invoked not from Swing event dispatch thread, then it must not happen inside a read action.</p>
   *
   * @param url the URL
   * @return <code>{@link VirtualFile}</code> if the file was found, {@code null} otherwise
   * @see VirtualFileSystem#findFileByPath
   * @see VirtualFileSystem#refreshAndFindFileByPath
   */
  public @Nullable VirtualFile refreshAndFindFileByUrl(@NotNull String url) {
    return null;
  }

  /**
   * <p>Refreshes only the part of the file system needed for searching the file by the given URL and finds file
   * by the given URL.</p>
   *
   * <p>This method is useful when the file was created externally and you need to find <code>{@link VirtualFile}</code>
   * corresponding to it.</p>
   *
   * <p>If this method is invoked not from Swing event dispatch thread, then it must not happen inside a read action.</p>
   *
   * @return <code>{@link VirtualFile}</code> if the file was found, {@code null} otherwise
   * @see VirtualFileSystem#findFileByPath
   * @see VirtualFileSystem#refreshAndFindFileByPath
   **/
  public @Nullable VirtualFile refreshAndFindFileByNioPath(@NotNull Path path) {
    return null;
  }

  /**
   * @deprecated Use {@link #VFS_CHANGES} message bus topic.
   */
  @Deprecated
  public abstract void addVirtualFileListener(@NotNull VirtualFileListener listener);

  /**
   * @deprecated Use {@link #VFS_CHANGES} message bus topic.
   */
  @Deprecated
  public abstract void addVirtualFileListener(@NotNull VirtualFileListener listener, @NotNull Disposable parentDisposable);

  /**
   * @deprecated Use {@link #VFS_CHANGES} message bus topic.
   */
  @Deprecated
  public abstract void removeVirtualFileListener(@NotNull VirtualFileListener listener);

  /**
   * Consider using extension point {@code vfs.asyncListener}.
   */
  public abstract void addAsyncFileListener(@NotNull AsyncFileListener listener, @NotNull Disposable parentDisposable);

  /**
   * Constructs a {@link VirtualFile#getUrl() URL} by specified protocol and path.
   *
   * @param protocol the protocol
   * @param path     the path
   * @return URL
   * @see VirtualFile#getUrl
   */
  public static @NotNull String constructUrl(@NotNull String protocol, @NotNull String path) {
    return protocol + URLUtil.SCHEME_SEPARATOR + path;
  }

  /**
   * Extracts protocol from the given URL. Protocol is a substring from the beginning of the URL till "://".
   *
   * @param url the URL
   * @return protocol or {@code null} if there is no "://" in the URL
   * @see VirtualFileSystem#getProtocol
   */
  public static @Nullable String extractProtocol(@NotNull String url) {
    int index = url.indexOf(URLUtil.SCHEME_SEPARATOR);
    if (index < 0) return null;
    return url.substring(0, index);
  }

  /**
   * @see URLUtil#extractPath(String)
   */
  public static @NotNull String extractPath(@NotNull String url) {
    return URLUtil.extractPath(url);
  }

  public abstract void addVirtualFileManagerListener(@NotNull VirtualFileManagerListener listener);

  public abstract void addVirtualFileManagerListener(@NotNull VirtualFileManagerListener listener, @NotNull Disposable parentDisposable);

  public abstract void removeVirtualFileManagerListener(@NotNull VirtualFileManagerListener listener);

  public abstract void notifyPropertyChanged(@NotNull VirtualFile virtualFile,
                                             @VirtualFile.PropName @NotNull String property,
                                             Object oldValue,
                                             Object newValue);

  /**
   * @return a number that's incremented every time something changes in the VFS, i.e. file hierarchy, names, flags, attributes, contents.
   * This only counts modifications done in current IDE session.
   * @see #getStructureModificationCount()
   */
  @Override
  public abstract long getModificationCount();

  /**
   * @return a number that's incremented every time something changes in the VFS structure, i.e. file hierarchy or names.
   * This only counts modifications done in current IDE session.
   * @see #getModificationCount()
   */
  public abstract long getStructureModificationCount();

  public VirtualFile findFileById(int id) {
    return null;
  }

  @ApiStatus.Internal
  public abstract int storeName(@NotNull String name);

  @ApiStatus.Internal
  public abstract @NotNull CharSequence getVFileName(int nameId);
}