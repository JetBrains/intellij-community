/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.ApplicationManager;

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
  public abstract void refresh(boolean asynchronous, Runnable postAction);

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
  public abstract VirtualFile findFileByUrl(String url);

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
  public abstract VirtualFile refreshAndFindFileByUrl(String url);

  /**
   * Adds listener to the file system.
   *
   * @param listener  the listener
   * @see VirtualFileListener
   */
  public abstract void addVirtualFileListener(VirtualFileListener listener);

  /**
   * Removes listener form the file system.
   *
   * @param listener  the listener
   */
  public abstract void removeVirtualFileListener(VirtualFileListener listener);

  public abstract void dispatchPendingEvent(VirtualFileListener listener);

  public abstract void addModificationAttemptListener(ModificationAttemptListener listener);
  public abstract void removeModificationAttemptListener(ModificationAttemptListener listener);

  public abstract void fireReadOnlyModificationAttempt(VirtualFile[] files);

  /**
   * Constructs URL by specified protocol and path. URL is a string which uniquely identifies file in all
   * file systems.
   *
   * @param protocol the protocol
   * @param path the path
   * @return URL
   */
  public static String constructUrl(String protocol, String path){
    return protocol + "://" + path;
  }

  /**
   * Extracts protocol from the given URL. Protocol is a substing from the beginning of the URL till "://".
   *
   * @param url the URL
   * @return protocol or <code>null</code> if there is no "://" in the URL
   * @see VirtualFileSystem#getProtocol
   */
  public static String extractProtocol(String url){
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
  public static String extractPath(String url){
    int index = url.indexOf("://");
    if (index < 0) return url;
    return url.substring(index + "://".length());
  }
}