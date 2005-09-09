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
import org.jetbrains.annotations.NotNull;

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

  private final ArrayList<VirtualFileListener> myFileListeners = new ArrayList<VirtualFileListener>();
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
   * <p/>
   * Example: to find a <code>{@link VirtualFile}</code> corresponding to the physical file with the specified path one
   * can use the followoing code: <code>LocalFileSystem.getInstance().findFileByPath(path.replace(File.separatorChar, '/'));</code>
   *
   * @param path the path to find file by
   * @return <code>{@link VirtualFile}</code> if the file was found, <code>null</code> otherwise
   */
  public abstract VirtualFile findFileByPath(String path);

  /**
   * Fetches presentable URL of file with the given path in this file system.
   *
   * @param path the path to get presentable URL for
   * @return presentable URL
   * @see VirtualFile#getPresentableUrl
   */
  public String extractPresentableUrl(String path) {
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
  public abstract VirtualFile refreshAndFindFileByPath(String path);

  /**
   * Adds listener to the file system. Normally one should use {@link VirtualFileManager#addVirtualFileListener}.
   *
   * @param listener the listener
   * @see VirtualFileListener
   * @see VirtualFileManager#addVirtualFileListener
   */
  public void addVirtualFileListener(VirtualFileListener listener) {
    synchronized (myFileListeners) {
      myFileListeners.add(listener);
      myCachedFileListeners = null;
    }
  }

  /**
   * Removes listener form the file system.
   *
   * @param listener the listener
   */
  public void removeVirtualFileListener(VirtualFileListener listener) {
    synchronized (myFileListeners) {
      myFileListeners.remove(listener);
      myCachedFileListeners = null;
    }
  }

  protected void firePropertyChanged(Object requestor, VirtualFile file, String propertyName, Object oldValue, Object newValue) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualFileListener[] listeners = getListeners();
    if (listeners.length > 0) {
      VirtualFilePropertyEvent event = new VirtualFilePropertyEvent(requestor, file, propertyName, oldValue, newValue);
      for (VirtualFileListener listener : listeners) {
        listener.propertyChanged(event);
      }
    }
  }

  protected void fireContentsChanged(Object requestor, VirtualFile file, long oldModificationStamp) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualFileListener[] listeners = getListeners();
    if (listeners.length > 0) {
      VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getParent(), oldModificationStamp, file.getModificationStamp());
      for (VirtualFileListener listener : listeners) {
        listener.contentsChanged(event);
      }
    }
  }

  protected void fireFileCreated(Object requestor, VirtualFile file) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualFileListener[] listeners = getListeners();
    if (listeners.length > 0) {
      VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getName(), file.isDirectory(), file.getParent());
      for (VirtualFileListener listener : listeners) {
        listener.fileCreated(event);
      }
    }
  }

  protected void fireFileDeleted(Object requestor, VirtualFile file, String fileName, boolean isDirectory, VirtualFile parent) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualFileListener[] listeners = getListeners();
    if (listeners.length > 0) {
      VirtualFileEvent event = new VirtualFileEvent(requestor, file, fileName, isDirectory, parent);
      for (VirtualFileListener listener : listeners) {
        listener.fileDeleted(event);
      }
    }
  }

  protected void fireFileMoved(Object requestor, VirtualFile file, VirtualFile oldParent) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualFileListener[] listeners = getListeners();
    if (listeners.length > 0) {
      VirtualFileMoveEvent event = new VirtualFileMoveEvent(requestor, file, oldParent, file.getParent());
      for (VirtualFileListener listener : listeners) {
        listener.fileMoved(event);
      }
    }
  }

  protected void fireBeforePropertyChange(Object requestor, VirtualFile file, String propertyName, Object oldValue, Object newValue) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualFileListener[] listeners = getListeners();
    if (listeners.length > 0) {
      VirtualFilePropertyEvent event = new VirtualFilePropertyEvent(requestor, file, propertyName, oldValue, newValue);
      for (VirtualFileListener listener : listeners) {
        listener.beforePropertyChange(event);
      }
    }
  }

  protected void fireBeforeContentsChange(Object requestor, VirtualFile file) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualFileListener[] listeners = getListeners();
    if (listeners.length > 0) {
      VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getName(), false, file.getParent());
      for (VirtualFileListener listener : listeners) {
        listener.beforeContentsChange(event);
      }
    }
  }

  protected void fireBeforeFileDeletion(Object requestor, VirtualFile file) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualFileListener[] listeners = getListeners();
    if (listeners.length > 0) {
      VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getName(), file.isDirectory(), file.getParent());
      for (VirtualFileListener listener : listeners) {
        listener.beforeFileDeletion(event);
      }
    }
  }

  protected void fireBeforeFileMovement(Object requestor, VirtualFile file, VirtualFile newParent) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualFileListener[] listeners = getListeners();
    if (listeners.length > 0) {
      VirtualFileMoveEvent event = new VirtualFileMoveEvent(requestor, file, file.getParent(), newParent);
      for (VirtualFileListener listener : listeners) {
        listener.beforeFileMovement(event);
      }
    }
  }

  private VirtualFileListener[] getListeners() {
    if (myCachedFileListeners == null) {
      myCachedFileListeners = myFileListeners.toArray(new VirtualFileListener[myFileListeners.size()]);
    }
    return myCachedFileListeners;
  }

  public abstract void forceRefreshFile(VirtualFile file);

  /**
   * Reloads files from disk regardless of their changed timestamp/contents
   *
   * @param asynchronous if true, the reload is done asynchronously.
   * @param files        the list of files to refresh (must not contain directories).
   * @since 5.0.2
   */
  public void forceRefreshFiles(final boolean asynchronous, @NotNull VirtualFile... files) {
    for (VirtualFile file : files) {
      forceRefreshFile(file);
    }
  }
}
