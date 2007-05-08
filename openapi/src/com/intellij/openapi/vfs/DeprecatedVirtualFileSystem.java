/*
 * @author max
 */
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DeprecatedVirtualFileSystem extends VirtualFileSystem {
  protected void firePropertyChanged(Object requestor, VirtualFile file, String propertyName, Object oldValue, Object newValue) {
    assertWriteAccessAllowed();

    if (!myFileListeners.isEmpty()) {
      VirtualFilePropertyEvent event = new VirtualFilePropertyEvent(requestor, file, propertyName, oldValue, newValue);
      for (VirtualFileListener listener : myFileListeners) {
        listener.propertyChanged(event);
      }
    }
  }

  protected void fireContentsChanged(Object requestor, VirtualFile file, long oldModificationStamp) {
    assertWriteAccessAllowed();

    if (!myFileListeners.isEmpty()) {
      VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getParent(), oldModificationStamp, file.getModificationStamp());
      for (VirtualFileListener listener : myFileListeners) {
        listener.contentsChanged(event);
      }
    }
  }

  protected void fireFileCreated(Object requestor, VirtualFile file) {
    assertWriteAccessAllowed();

    if (!myFileListeners.isEmpty()) {
      VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getName(), file.getParent());
      for (VirtualFileListener listener : myFileListeners) {
        listener.fileCreated(event);
      }
    }
  }

  protected void fireFileDeleted(Object requestor, VirtualFile file, String fileName, VirtualFile parent) {
    assertWriteAccessAllowed();

    if (!myFileListeners.isEmpty()) {
      VirtualFileEvent event = new VirtualFileEvent(requestor, file, fileName, parent);
      for (VirtualFileListener listener : myFileListeners) {
        listener.fileDeleted(event);
      }
    }
  }

  protected void fireFileMoved(Object requestor, VirtualFile file, VirtualFile oldParent) {
    assertWriteAccessAllowed();

    if (!myFileListeners.isEmpty()) {
      VirtualFileMoveEvent event = new VirtualFileMoveEvent(requestor, file, oldParent, file.getParent());
      for (VirtualFileListener listener : myFileListeners) {
        listener.fileMoved(event);
      }
    }
  }

  protected void fireFileCopied(@Nullable Object requestor, @NotNull VirtualFile originalFile, @NotNull final VirtualFile createdFile) {
    assertWriteAccessAllowed();

    if (!myFileListeners.isEmpty()) {
      VirtualFileCopyEvent event = new VirtualFileCopyEvent(requestor, originalFile, createdFile);
      for (VirtualFileListener listener : myFileListeners) {
        try {
          listener.fileCopied(event);
        }
        catch (AbstractMethodError e) { //compatibility with 6.0
          listener.fileCreated(event);
        }
      }
    }
  }

  protected void fireBeforePropertyChange(Object requestor, VirtualFile file, String propertyName, Object oldValue, Object newValue) {
    assertWriteAccessAllowed();

    if (!myFileListeners.isEmpty()) {
      VirtualFilePropertyEvent event = new VirtualFilePropertyEvent(requestor, file, propertyName, oldValue, newValue);
      for (VirtualFileListener listener : myFileListeners) {
        listener.beforePropertyChange(event);
      }
    }
  }

  protected void fireBeforeContentsChange(Object requestor, VirtualFile file) {
    assertWriteAccessAllowed();

    if (!myFileListeners.isEmpty()) {
      VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getName(), file.getParent());
      for (VirtualFileListener listener : myFileListeners) {
        listener.beforeContentsChange(event);
      }
    }
  }

  protected void fireBeforeFileDeletion(Object requestor, VirtualFile file) {
    assertWriteAccessAllowed();

    if (!myFileListeners.isEmpty()) {
      VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getName(), file.getParent());
      for (VirtualFileListener listener : myFileListeners) {
        listener.beforeFileDeletion(event);
      }
    }
  }

  protected void fireBeforeFileMovement(Object requestor, VirtualFile file, VirtualFile newParent) {
    assertWriteAccessAllowed();

    if (!myFileListeners.isEmpty()) {
      VirtualFileMoveEvent event = new VirtualFileMoveEvent(requestor, file, file.getParent(), newParent);
      for (VirtualFileListener listener : myFileListeners) {
        listener.beforeFileMovement(event);
      }
    }
  }

  protected void assertWriteAccessAllowed() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
  }
}