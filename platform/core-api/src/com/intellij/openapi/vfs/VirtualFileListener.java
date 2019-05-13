/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Receives notifications about changes in the virtual file system.
 *
 * @see VirtualFileManager#addVirtualFileListener(VirtualFileListener)
 * @see VirtualFileListener
 */
public interface VirtualFileListener extends EventListener {

  /**
   * Fired when a virtual file is renamed from within the IDE, or its writable status is changed.
   * For files renamed externally, {@link #fileCreated(VirtualFileEvent)} and {@link #fileDeleted(VirtualFileEvent)} events will be fired.
   *
   * @param event the event object containing information about the change.
   */
  default void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
  }

  /**
   * Fired when the contents of a virtual file is changed.
   *
   * @param event the event object containing information about the change.
   */
  default void contentsChanged(@NotNull VirtualFileEvent event) {
  }

  /**
   * Fired when a virtual file is created. This event is not fired for files discovered during initial VFS initialization.
   *
   * @param event the event object containing information about the change.
   */
  default void fileCreated(@NotNull VirtualFileEvent event) {
  }

  /**
   * Fired when a virtual file is deleted.
   *
   * @param event the event object containing information about the change.
   */
  default void fileDeleted(@NotNull VirtualFileEvent event) {
  }

  /**
   * Fired when a virtual file is moved from within the IDE.
   *
   * @param event the event object containing information about the change.
   */
  default void fileMoved(@NotNull VirtualFileMoveEvent event) {
  }

  /**
   * Fired when a virtual file is copied from within the IDE.
   *
   * @param event the event object containing information about the change.
   */
  default void fileCopied(@NotNull VirtualFileCopyEvent event) {
    fileCreated(event);
  }

  /**
   * Fired before the change of a name or writable status of a file is processed.
   *
   * @param event the event object containing information about the change.
   */
  default void beforePropertyChange(@NotNull VirtualFilePropertyEvent event) {
  }

  /**
   * Fired before the change of contents of a file is processed.
   *
   * @param event the event object containing information about the change.
   */
  default void beforeContentsChange(@NotNull VirtualFileEvent event) {
  }

  /**
   * Fired before the deletion of a file is processed.
   *
   * @param event the event object containing information about the change.
   */
  default void beforeFileDeletion(@NotNull VirtualFileEvent event) {
  }

  /**
   * Fired before the movement of a file is processed.
   *
   * @param event the event object containing information about the change.
   */
  default void beforeFileMovement(@NotNull VirtualFileMoveEvent event) {
  }
}