/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * Manages virtual file systems
 *
 * @see VirtualFileSystem
 * @see LocalFileSystem
 * @see JarFileSystem
 */
public abstract class VirtualFileManager {
  /**
   * Gets the instance of <code>VirtualFileManager</code>.
   *
   * @return <code>VirtualFileManager</code>
   */
  @NotNull
  public static VirtualFileManager getInstance(){
    return ApplicationManager.getApplication().getComponent(VirtualFileManager.class);
  }

  /**
   * Gets the array of supported file systems.
   *
   * @return array of {@link VirtualFileSystem} objects
   */
  public abstract VirtualFileSystem[] getFileSystems();

  /**
   * Gets VirtualFileSystem with the specified protocol.
   *
   * @param protocol String representing the protocol
   * @return {@link VirtualFileSystem}
   * @see VirtualFileSystem#getProtocol
   */
  public abstract VirtualFileSystem getFileSystem(String protocol);

  /**
   * Refreshes the cached file system information from the physical file system.
   * <p>
   * This method should be only called within write-action.
   * See {@link com.intellij.openapi.application.Application#runWriteAction}.
   *
   * @param asynchronous if <code>true</code> then the operation will be performed in a separate thread,
   * otherwise will be performed immediately
   */
  public abstract void refresh(boolean asynchronous);

  /**
   * The same as {@link #refresh(boolean asynchronous)} but also runs <code>postRunnable</code>
   * after the operation is completed.
   */
  public abstract void refresh(boolean asynchronous, @Nullable Runnable postAction);

  /**
   * Searches for the file specified by given URL. URL is a string which uniquely identifies file in all
   * file systems.
   *
   * @param url the URL to find file by
   * @return <code>{@link VirtualFile}</code> if the file was found, <code>null</code> otherwise
   * @see VirtualFile#getUrl
   * @see VirtualFileSystem#findFileByPath
   * @see #refreshAndFindFileByUrl
   */
  @Nullable
  public abstract VirtualFile findFileByUrl(@NonNls @NotNull String url);

  /**
   * Refreshes only the part of the file system needed for searching the file by the given URL and finds file
   * by the given URL.<br>
   *
   * This method is useful when the file was created externally and you need to find <code>{@link VirtualFile}</code>
   * corresponding to it.<p>
   *
   * This method should be only called within write-action.
   * See {@link com.intellij.openapi.application.Application#runWriteAction}.
   *
   * @param url  the URL
   * @return <code>{@link VirtualFile}</code> if the file was found, <code>null</code> otherwise
   * @see VirtualFileSystem#findFileByPath
   * @see VirtualFileSystem#refreshAndFindFileByPath
   */
  @Nullable
  public abstract VirtualFile refreshAndFindFileByUrl(@NotNull String url);

  /**
   * Adds listener to the file system.
   *
   * @param listener  the listener
   * @see VirtualFileListener
   */
  public abstract void addVirtualFileListener(@NotNull VirtualFileListener listener);

  public abstract void addVirtualFileListener(@NotNull VirtualFileListener listener, Disposable parentDisposable);

  /**
   * Removes listener form the file system.
   *
   * @param listener  the listener
   */
  public abstract void removeVirtualFileListener(@NotNull VirtualFileListener listener);

  public abstract void dispatchPendingEvent(@NotNull VirtualFileListener listener);

  public abstract void addModificationAttemptListener(@NotNull ModificationAttemptListener listener);
  public abstract void removeModificationAttemptListener(@NotNull ModificationAttemptListener listener);

  public abstract void fireReadOnlyModificationAttempt(@NotNull VirtualFile... files);

  /**
   * Constructs URL by specified protocol and path. URL is a string which uniquely identifies file in all
   * file systems.
   *
   * @param protocol the protocol
   * @param path the path
   * @return URL
   */
  @NotNull
  public static String constructUrl(@NotNull String protocol, @NotNull String path){
    return protocol + "://" + path;
  }

  /**
   * Extracts protocol from the given URL. Protocol is a substing from the beginning of the URL till "://".
   *
   * @param url the URL
   * @return protocol or <code>null</code> if there is no "://" in the URL
   * @see VirtualFileSystem#getProtocol
   */
  @Nullable
  public static String extractProtocol(@NotNull String url){
    int index = url.indexOf("://");
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
  public static String extractPath(@NotNull String url){
    int index = url.indexOf("://");
    if (index < 0) return url;
    return url.substring(index + "://".length());
  }

  public abstract void addVirtualFileManagerListener(VirtualFileManagerListener listener);

  public abstract void removeVirtualFileManagerListener(VirtualFileManagerListener listener);
}
