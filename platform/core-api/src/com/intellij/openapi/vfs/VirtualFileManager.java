/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.util.io.URLUtil;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages virtual file systems.
 *
 * @see VirtualFileSystem
 */
public abstract class VirtualFileManager implements ModificationTracker {
  public static final Topic<BulkFileListener> VFS_CHANGES = new Topic<>("NewVirtualFileSystem changes", BulkFileListener.class);

  public static final ModificationTracker VFS_STRUCTURE_MODIFICATIONS = () -> getInstance().getStructureModificationCount();

  private static VirtualFileManager ourInstance = CachedSingletonsRegistry.markCachedField(VirtualFileManager.class);

  /**
   * Gets the instance of {@code VirtualFileManager}.
   *
   * @return {@code VirtualFileManager}
   */
  @NotNull
  public static VirtualFileManager getInstance() {
    VirtualFileManager result = ourInstance;
    if (result == null) {
      ourInstance = result = ApplicationManager.getApplication().getComponent(VirtualFileManager.class);
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
   * Searches for the file specified by given URL. URL is a string which uniquely identifies file in all
   * file systems.
   *
   * @param url the URL to find file by
   * @return <code>{@link VirtualFile}</code> if the file was found, {@code null} otherwise
   * @see VirtualFile#getUrl
   * @see VirtualFileSystem#findFileByPath
   * @see #refreshAndFindFileByUrl
   */
  @Nullable
  public abstract VirtualFile findFileByUrl(@NonNls @NotNull String url);

  /**
   * Refreshes only the part of the file system needed for searching the file by the given URL and finds file
   * by the given URL.<br>
   * <p/>
   * This method is useful when the file was created externally and you need to find <code>{@link VirtualFile}</code>
   * corresponding to it.<p>
   * <p/>
   * If this method is invoked not from Swing event dispatch thread, then it must not happen inside a read action.
   *
   * @param url the URL
   * @return <code>{@link VirtualFile}</code> if the file was found, {@code null} otherwise
   * @see VirtualFileSystem#findFileByPath
   * @see VirtualFileSystem#refreshAndFindFileByPath
   */
  @Nullable
  public abstract VirtualFile refreshAndFindFileByUrl(@NotNull String url);

  /**
   * Adds listener to the file system.
   *
   * @param listener the listener
   * @see VirtualFileListener
   */
  public abstract void addVirtualFileListener(@NotNull VirtualFileListener listener);

  public abstract void addVirtualFileListener(@NotNull VirtualFileListener listener, @NotNull Disposable parentDisposable);

  /**
   * Removes listener form the file system.
   *
   * @param listener the listener
   */
  public abstract void removeVirtualFileListener(@NotNull VirtualFileListener listener);

  /**
   * Constructs URL by specified protocol and path. URL is a string which uniquely identifies file in all
   * file systems.
   *
   * @param protocol the protocol
   * @param path     the path
   * @return URL
   */
  @NotNull
  public static String constructUrl(@NotNull String protocol, @NotNull String path) {
    return protocol + URLUtil.SCHEME_SEPARATOR + path;
  }

  /**
   * Extracts protocol from the given URL. Protocol is a substring from the beginning of the URL till "://".
   *
   * @param url the URL
   * @return protocol or {@code null} if there is no "://" in the URL
   * @see VirtualFileSystem#getProtocol
   */
  @Nullable
  public static String extractProtocol(@NotNull String url) {
    int index = url.indexOf(URLUtil.SCHEME_SEPARATOR);
    if (index < 0) return null;
    return url.substring(0, index);
  }

  /**
   * Extracts path from the given URL. Path is a substring from "://" till the end of URL. If there is no "://" URL
   * itself is returned.
   *
   * @param url the URL
   * @return path
   */
  @NotNull
  public static String extractPath(@NotNull String url) {
    int index = url.indexOf(URLUtil.SCHEME_SEPARATOR);
    if (index < 0) return url;
    return url.substring(index + URLUtil.SCHEME_SEPARATOR.length());
  }

  public abstract void addVirtualFileManagerListener(@NotNull VirtualFileManagerListener listener);

  public abstract void addVirtualFileManagerListener(@NotNull VirtualFileManagerListener listener, @NotNull Disposable parentDisposable);

  public abstract void removeVirtualFileManagerListener(@NotNull VirtualFileManagerListener listener);

  public abstract void notifyPropertyChanged(@NotNull VirtualFile virtualFile, @NotNull String property, Object oldValue, Object newValue);

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
}
