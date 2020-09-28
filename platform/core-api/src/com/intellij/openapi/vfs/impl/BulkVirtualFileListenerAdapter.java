// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BulkVirtualFileListenerAdapter implements BulkFileListener {
  private final VirtualFileListener myAdapted;
  private final VirtualFileSystem myFileSystem;

  public BulkVirtualFileListenerAdapter(@NotNull VirtualFileListener adapted) {
    this(adapted, null);
  }

  public BulkVirtualFileListenerAdapter(@NotNull VirtualFileListener adapted, @Nullable VirtualFileSystem fileSystem) {
    myAdapted = adapted;
    myFileSystem = fileSystem;
  }

  @Override
  public void before(@NotNull final List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      if (myFileSystem == null || myFileSystem == event.getFileSystem()) {
        fireBefore(myAdapted, event);
      }
    }
  }

  @Override
  public void after(@NotNull final List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      if (myFileSystem == null || myFileSystem == event.getFileSystem()) {
        fireAfter(myAdapted, event);
      }
    }
  }

  public static void fireAfter(@NotNull VirtualFileListener adapted, @NotNull VFileEvent event) {
    if (event instanceof VFileContentChangeEvent) {
      final VFileContentChangeEvent ce = (VFileContentChangeEvent)event;
      final VirtualFile file = ce.getFile();
      adapted.contentsChanged(
        new VirtualFileEvent(event.getRequestor(), file, file.getParent(), ce.getOldModificationStamp(), ce.getModificationStamp()));
    }
    else if (event instanceof VFileCopyEvent) {
      final VFileCopyEvent ce = (VFileCopyEvent)event;
      final VirtualFile original = ce.getFile();
      final VirtualFile copy = ce.getNewParent().findChild(ce.getNewChildName());
      if (copy != null) {
        adapted.fileCopied(new VirtualFileCopyEvent(event.getRequestor(), original, copy));
      }
    }
    else if (event instanceof VFileCreateEvent) {
      final VFileCreateEvent ce = (VFileCreateEvent)event;
      final VirtualFile newChild = ce.getFile();
      if (newChild != null) {
        adapted.fileCreated(new VirtualFileEvent(event.getRequestor(), newChild, ce.getParent(), 0, 0));
      }
    }
    else if (event instanceof VFileDeleteEvent) {
      final VFileDeleteEvent de = (VFileDeleteEvent)event;
      adapted.fileDeleted(new VirtualFileEvent(event.getRequestor(), de.getFile(), de.getFile().getParent(), 0, 0));
    }
    else if (event instanceof VFileMoveEvent) {
      final VFileMoveEvent me = (VFileMoveEvent)event;
      adapted.fileMoved(new VirtualFileMoveEvent(event.getRequestor(), me.getFile(), me.getOldParent(), me.getNewParent()));
    }
    else if (event instanceof VFilePropertyChangeEvent) {
      final VFilePropertyChangeEvent pce = (VFilePropertyChangeEvent)event;
      adapted.propertyChanged(
        new VirtualFilePropertyEvent(event.getRequestor(), pce.getFile(), pce.getPropertyName(), pce.getOldValue(), pce.getNewValue()));
    }
  }

  public static void fireBefore(@NotNull VirtualFileListener adapted, @NotNull VFileEvent event) {
    if (event instanceof VFileContentChangeEvent) {
      final VFileContentChangeEvent ce = (VFileContentChangeEvent)event;
      final VirtualFile file = ce.getFile();
      adapted.beforeContentsChange(
        new VirtualFileEvent(event.getRequestor(), file, file.getParent(), ce.getOldModificationStamp(), ce.getModificationStamp()));
    }
    else if (event instanceof VFileDeleteEvent) {
      final VFileDeleteEvent de = (VFileDeleteEvent)event;
      adapted.beforeFileDeletion(new VirtualFileEvent(event.getRequestor(), de.getFile(), de.getFile().getParent(), 0, 0));
    }
    else if (event instanceof VFileMoveEvent) {
      final VFileMoveEvent me = (VFileMoveEvent)event;
      adapted.beforeFileMovement(new VirtualFileMoveEvent(event.getRequestor(), me.getFile(), me.getOldParent(), me.getNewParent()));
    }
    else if (event instanceof VFilePropertyChangeEvent) {
      final VFilePropertyChangeEvent pce = (VFilePropertyChangeEvent)event;
      adapted.beforePropertyChange(
        new VirtualFilePropertyEvent(event.getRequestor(), pce.getFile(), pce.getPropertyName(), pce.getOldValue(), pce.getNewValue()));
    }
  }
}