// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileCopyEvent;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
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
  public void before(final @NotNull List<? extends @NotNull VFileEvent> events) {
    for (VFileEvent event : events) {
      if (myFileSystem == null || myFileSystem == event.getFileSystem()) {
        fireBefore(myAdapted, event);
      }
    }
  }

  @Override
  public void after(final @NotNull List<? extends @NotNull VFileEvent> events) {
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
        new VirtualFileEvent(event.getRequestor(), file, event.getPath(), file.getParent(), ce.getOldModificationStamp(), ce.getModificationStamp()));
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
        adapted.fileCreated(new VirtualFileEvent(event.getRequestor(), newChild, event.getPath(), ce.getParent(), 0, 0));
      }
    }
    else if (event instanceof VFileDeleteEvent) {
      final VFileDeleteEvent de = (VFileDeleteEvent)event;
      adapted.fileDeleted(new VirtualFileEvent(event.getRequestor(), de.getFile(),  de.getPath(), de.getFile().getParent(), 0, 0));
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
        new VirtualFileEvent(event.getRequestor(), file, event.getPath(), file.getParent(), ce.getOldModificationStamp(), ce.getModificationStamp()));
    }
    else if (event instanceof VFileDeleteEvent) {
      final VFileDeleteEvent de = (VFileDeleteEvent)event;
      adapted.beforeFileDeletion(new VirtualFileEvent(event.getRequestor(), de.getFile(), de.getPath(), de.getFile().getParent(), 0, 0));
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