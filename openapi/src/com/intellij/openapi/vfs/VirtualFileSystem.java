/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.ApplicationManager;

import java.io.File;
import java.util.ArrayList;

/**
 * Represents a virtual file system.
 *
 * @see VirtualFile
 * @see VirtualFileManager
 */
public abstract class VirtualFileSystem {
  protected VirtualFileSystem() {
  }

  private final ArrayList myFileListeners = new ArrayList();
  private VirtualFileListener[] myCachedFileListeners = new VirtualFileListener[0];

  /**
   * Gets the protocol for this file system. Protocols should differ for all file systems.
   *
   * @return String representing the protocol
   * @see VirtualFile#getUrl
   * @see VirtualFileManager#getFileSystem
   */
  public abstract String getProtocol();

  /**
   * Searches for the file specified by given path. Path is a string which uniquely identifies file within given
   * <code>{@link VirtualFileSystem}</code>. Format of the path depends on the concrete file system.
   * For <code>LocalFileSystem</code> it is an absoulute file path with file separator characters (File.separatorChar)
   * replaced to the forward slash ('/').<p>
   *
   * Example: to find a <code>{@link VirtualFile}</code> corresponding to the physical file with the specified path one
   * can use the followoing code: <code>LocalFileSystem.getInstance().findFileByPath(path.replace(File.separatorChar, '/'));</code>
   *
   * @param path  the path to find file by
   * @return <code>{@link VirtualFile}</code> if the file was found, <code>null</code> otherwise
   */
  public abstract VirtualFile findFileByPath(String path);

  /**
   * Fetches presentable URL of file with the given path in this file system.
   *
   * @param path  the path to get presentable URL for
   * @return presentable URL
   * @see VirtualFile#getPresentableUrl
   */
  public String extractPresentableUrl(String path){
    return path.replace('/', File.separatorChar);
  }

  /**
   * Refreshes the cached information for all files in this file system from the physical file system.<p>
   *
   * If <code>asynchronous</code> is <code>false</code> this method should be only called within write-action.
   * See {@link com.intellij.openapi.application.Application#runWriteAction}.
   *
   * @param asynchronous if <code>true</code> then the operation will be performed in a separate thread,
   * otherwise will be performed immediately
   *
   * @see VirtualFile#refresh
   * @see VirtualFileManager#refresh
   */
  public abstract void refresh(boolean asynchronous);

  /**
   * Refreshes only the part of the file system needed for searching the file by the given path and finds file
   * by the given path.<br>
   *
   * This method is useful when the file was created externally and you need to find <code>{@link VirtualFile}</code>
   * corresponding to it.<p>
   *
   * This method should be only called within write-action.
   * See {@link com.intellij.openapi.application.Application#runWriteAction}.
   *
   * @param path  the path
   * @return <code>{@link VirtualFile}</code> if the file was found, <code>null</code> otherwise
   */
  public abstract VirtualFile refreshAndFindFileByPath(String path);

  /**
   * Adds listener to the file system. Normally one should use {@link VirtualFileManager#addVirtualFileListener}.
   *
   * @param listener  the listener
   * @see VirtualFileListener
   * @see VirtualFileManager#addVirtualFileListener
   */
  public void addVirtualFileListener(VirtualFileListener listener){
    synchronized(myFileListeners){
      myFileListeners.add(listener);
      myCachedFileListeners = null;
    }
  }

  /**
   * Removes listener form the file system.
   *
   * @param listener  the listener
   */
  public void removeVirtualFileListener(VirtualFileListener listener){
    synchronized(myFileListeners){
      myFileListeners.remove(listener);
      myCachedFileListeners = null;
    }
  }

  protected void firePropertyChanged(Object requestor, VirtualFile file, String propertyName, Object oldValue, Object newValue){
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualFileListener[] listeners = getListeners();
    if (listeners.length > 0){
      VirtualFilePropertyEvent event = new VirtualFilePropertyEvent(requestor, file, propertyName, oldValue, newValue);
      for(int i = 0; i < listeners.length; i++){
        listeners[i].propertyChanged(event);
      }
    }
  }

  protected void fireContentsChanged(Object requestor, VirtualFile file, long oldModificationStamp){
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualFileListener[] listeners = getListeners();
    if (listeners.length > 0){
      VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getParent(), oldModificationStamp, file.getModificationStamp());
      for(int i = 0; i < listeners.length; i++){
        listeners[i].contentsChanged(event);
      }
    }
  }

  protected void fireFileCreated(Object requestor, VirtualFile file){
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualFileListener[] listeners = getListeners();
    if (listeners.length > 0){
      VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getName(), file.isDirectory(), file.getParent());
      for(int i = 0; i < listeners.length; i++){
        listeners[i].fileCreated(event);
      }
    }
  }

  protected void fireFileDeleted(Object requestor, VirtualFile file, String fileName, boolean isDirectory, VirtualFile parent){
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualFileListener[] listeners = getListeners();
    if (listeners.length > 0){
      VirtualFileEvent event = new VirtualFileEvent(requestor, file, fileName, isDirectory, parent);
      for(int i = 0; i < listeners.length; i++){
        listeners[i].fileDeleted(event);
      }
    }
  }

  protected void fireFileMoved(Object requestor, VirtualFile file, VirtualFile oldParent){
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualFileListener[] listeners = getListeners();
    if (listeners.length > 0){
      VirtualFileMoveEvent event = new VirtualFileMoveEvent(requestor, file, oldParent, file.getParent());
      for(int i = 0; i < listeners.length; i++){
        listeners[i].fileMoved(event);
      }
    }
  }

  protected void fireBeforePropertyChange(Object requestor, VirtualFile file, String propertyName, Object oldValue, Object newValue){
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualFileListener[] listeners = getListeners();
    if (listeners.length > 0){
      VirtualFilePropertyEvent event = new VirtualFilePropertyEvent(requestor, file, propertyName, oldValue, newValue);
      for(int i = 0; i < listeners.length; i++){
        listeners[i].beforePropertyChange(event);
      }
    }
  }

  protected void fireBeforeContentsChange(Object requestor, VirtualFile file){
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualFileListener[] listeners = getListeners();
    if (listeners.length > 0){
      VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getName(), false, file.getParent());
      for(int i = 0; i < listeners.length; i++){
        listeners[i].beforeContentsChange(event);
      }
    }
  }

  protected void fireBeforeFileDeletion(Object requestor, VirtualFile file){
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualFileListener[] listeners = getListeners();
    if (listeners.length > 0){
      VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getName(), file.isDirectory(), file.getParent());
      for(int i = 0; i < listeners.length; i++){
        listeners[i].beforeFileDeletion(event);
      }
    }
  }

  protected void fireBeforeFileMovement(Object requestor, VirtualFile file, VirtualFile newParent){
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualFileListener[] listeners = getListeners();
    if (listeners.length > 0){
      VirtualFileMoveEvent event = new VirtualFileMoveEvent(requestor, file, file.getParent(), newParent);
      for(int i = 0; i < listeners.length; i++){
        listeners[i].beforeFileMovement(event);
      }
    }
  }

  private VirtualFileListener[] getListeners(){
    if (myCachedFileListeners == null){
      myCachedFileListeners = (VirtualFileListener[])myFileListeners.toArray(new VirtualFileListener[myFileListeners.size()]);
    }
    return myCachedFileListeners;
  }

}
