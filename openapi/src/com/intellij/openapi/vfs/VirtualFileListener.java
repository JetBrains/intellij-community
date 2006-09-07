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

import java.util.EventListener;

/**
 * Receives notifications about changes in the virtual file system.
 *
 * @see com.intellij.openapi.vfs.VirtualFileManager#addVirtualFileListener(VirtualFileListener)
 * @see com.intellij.openapi.vfs.VirtualFileAdapter
 */
public interface VirtualFileListener extends EventListener {
  /**
   * Fired when a virtual file is renamed from within IDEA, or its writable status is changed.
   * For files renamed externally, {@link #fileCreated} and {@link #fileDeleted} events will be fired.
   *
   * @param event the event object containing information about the change.
   */
  void propertyChanged(VirtualFilePropertyEvent event);

  /**
   * Fired when the contents of a virtual file is changed.
   *
   * @param event the event object containing information about the change.
   */
  void contentsChanged(VirtualFileEvent event);

  /**
   * Fired when a virtual file is created. This event is not fired for files discovered during initial VFS initialization.
   *
   * @param event the event object containing information about the change.
   */
  void fileCreated(VirtualFileEvent event);

  /**
   * Fired when a virtual file is deleted.
   *
   * @param event the event object containing information about the change.
   */
  void fileDeleted(VirtualFileEvent event);

  /**
   * Fired when a virtual file is moved from within IDEA.
   *
   * @param event the event object containing information about the change.
   */
  void fileMoved(VirtualFileMoveEvent event);

  /**
   * Fired before the change of a name or writable status of a file is processed.
   *
   * @param event the event object containing information about the change.
   */
  void beforePropertyChange(VirtualFilePropertyEvent event);

  /**
   * Fired before the change of contents of a file is processed.
   *
   * @param event the event object containing information about the change.
   */
  void beforeContentsChange(VirtualFileEvent event);

  /**
   * Fired before the deletion of a file is processed.
   *
   * @param event the event object containing information about the change.
   */
  void beforeFileDeletion(VirtualFileEvent event);

  /**
   * Fired before the movement of a file is processed.
   *
   * @param event the event object containing information about the change.
   */
  void beforeFileMovement(VirtualFileMoveEvent event);
}
