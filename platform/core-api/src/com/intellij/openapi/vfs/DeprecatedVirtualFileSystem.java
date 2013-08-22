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

/*
 * @author max
 */
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DeprecatedVirtualFileSystem extends VirtualFileSystem {
  private final EventDispatcher<VirtualFileListener> myEventDispatcher = EventDispatcher.create(VirtualFileListener.class);

  protected void startEventPropagation() {
    Application application = ApplicationManager.getApplication();
    if (application == null) {
      return;
    }

    application.getMessageBus().connect().subscribe(
      VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(myEventDispatcher.getMulticaster(), this));
  }

  @Override
  public void addVirtualFileListener(@NotNull VirtualFileListener listener) {
    myEventDispatcher.addListener(listener);
  }

  /**
   * Removes listener form the file system.
   *
   * @param listener the listener
   */
  @Override
  public void removeVirtualFileListener(@NotNull VirtualFileListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  protected void firePropertyChanged(Object requestor,
                                     @NotNull VirtualFile file,
                                     @NotNull String propertyName,
                                     Object oldValue,
                                     Object newValue) {
    assertWriteAccessAllowed();
    VirtualFilePropertyEvent event = new VirtualFilePropertyEvent(requestor, file, propertyName, oldValue, newValue);
    myEventDispatcher.getMulticaster().propertyChanged(event);
  }

  protected void fireContentsChanged(Object requestor, @NotNull VirtualFile file, long oldModificationStamp) {
    assertWriteAccessAllowed();
    VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getParent(), oldModificationStamp, file.getModificationStamp());
    myEventDispatcher.getMulticaster().contentsChanged(event);
  }

  protected void fireFileCreated(@Nullable Object requestor, @NotNull VirtualFile file) {
    assertWriteAccessAllowed();
    VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getName(), file.getParent());
    myEventDispatcher.getMulticaster().fileCreated(event);
  }

  protected void fireFileDeleted(Object requestor, @NotNull VirtualFile file, @NotNull String fileName, VirtualFile parent) {
    assertWriteAccessAllowed();
    VirtualFileEvent event = new VirtualFileEvent(requestor, file, fileName, parent);
    myEventDispatcher.getMulticaster().fileDeleted(event);
  }

  protected void fireFileMoved(Object requestor, @NotNull VirtualFile file, VirtualFile oldParent) {
    assertWriteAccessAllowed();
    VirtualFileMoveEvent event = new VirtualFileMoveEvent(requestor, file, oldParent, file.getParent());
    myEventDispatcher.getMulticaster().fileMoved(event);
  }

  protected void fireFileCopied(@Nullable Object requestor, @NotNull VirtualFile originalFile, @NotNull final VirtualFile createdFile) {
    assertWriteAccessAllowed();
    VirtualFileCopyEvent event = new VirtualFileCopyEvent(requestor, originalFile, createdFile);
    try {
      myEventDispatcher.getMulticaster().fileCopied(event);
    }
    catch (AbstractMethodError e) { //compatibility with 6.0
      myEventDispatcher.getMulticaster().fileCreated(event);
    }
  }

  protected void fireBeforePropertyChange(Object requestor,
                                          @NotNull VirtualFile file,
                                          @NotNull String propertyName,
                                          Object oldValue,
                                          Object newValue) {
    assertWriteAccessAllowed();
    VirtualFilePropertyEvent event = new VirtualFilePropertyEvent(requestor, file, propertyName, oldValue, newValue);
    myEventDispatcher.getMulticaster().beforePropertyChange(event);
  }

  protected void fireBeforeContentsChange(Object requestor, @NotNull VirtualFile file) {
    assertWriteAccessAllowed();
    VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getName(), file.getParent());
    myEventDispatcher.getMulticaster().beforeContentsChange(event);
  }

  protected void fireBeforeFileDeletion(Object requestor, @NotNull VirtualFile file) {
    assertWriteAccessAllowed();
    VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getName(), file.getParent());
    myEventDispatcher.getMulticaster().beforeFileDeletion(event);
  }

  protected void fireBeforeFileMovement(Object requestor, @NotNull VirtualFile file, VirtualFile newParent) {
    assertWriteAccessAllowed();
    VirtualFileMoveEvent event = new VirtualFileMoveEvent(requestor, file, file.getParent(), newParent);
    myEventDispatcher.getMulticaster().beforeFileMovement(event);
  }

  protected void assertWriteAccessAllowed() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }
}