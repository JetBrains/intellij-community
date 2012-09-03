/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BulkVirtualFileListenerAdapter implements BulkFileListener {
  private final VirtualFileListener myAdapted;

  public BulkVirtualFileListenerAdapter(final VirtualFileListener adapted) {
    myAdapted = adapted;
  }

  @Override
  public void before(@NotNull final List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      fireBefore(event);
    }
  }

  @Override
  public void after(@NotNull final List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      fireAfter(event);
    }
  }

  private void fireAfter(final VFileEvent event) {
    if (event instanceof VFileContentChangeEvent) {
      final VFileContentChangeEvent ce = (VFileContentChangeEvent)event;
      final VirtualFile file = ce.getFile();
      myAdapted.contentsChanged(
        new VirtualFileEvent(event.getRequestor(), file, file.getParent(), ce.getOldModificationStamp(), ce.getModificationStamp()));
    }
    else if (event instanceof VFileCopyEvent) {
      final VFileCopyEvent ce = (VFileCopyEvent)event;
      final VirtualFile original = ce.getFile();
      final VirtualFile copy = ce.getNewParent().findChild(ce.getNewChildName());
      if (original != null && copy != null) {
        myAdapted.fileCopied(new VirtualFileCopyEvent(event.getRequestor(), original, copy));
      }
    }
    else if (event instanceof VFileCreateEvent) {
      final VFileCreateEvent ce = (VFileCreateEvent)event;
      final VirtualFile newChild = ce.getFile();
      if (newChild != null) {
        myAdapted.fileCreated(new VirtualFileEvent(event.getRequestor(), newChild, ce.getChildName(), ce.getParent()));
      }
    }
    else if (event instanceof VFileDeleteEvent) {
      final VFileDeleteEvent de = (VFileDeleteEvent)event;
      myAdapted.fileDeleted(new VirtualFileEvent(event.getRequestor(), de.getFile(), de.getFile().getParent(), 0, 0));
    }
    else if (event instanceof VFileMoveEvent) {
      final VFileMoveEvent me = (VFileMoveEvent)event;
      myAdapted.fileMoved(new VirtualFileMoveEvent(event.getRequestor(), me.getFile(), me.getOldParent(), me.getNewParent()));
    }
    else if (event instanceof VFilePropertyChangeEvent) {
      final VFilePropertyChangeEvent pce = (VFilePropertyChangeEvent)event;
      myAdapted.propertyChanged(
        new VirtualFilePropertyEvent(event.getRequestor(), pce.getFile(), pce.getPropertyName(), pce.getOldValue(), pce.getNewValue()));
    }
  }

  private void fireBefore(final VFileEvent event) {
    if (event instanceof VFileContentChangeEvent) {
      final VFileContentChangeEvent ce = (VFileContentChangeEvent)event;
      final VirtualFile file = ce.getFile();
      myAdapted.beforeContentsChange(
        new VirtualFileEvent(event.getRequestor(), file, file.getParent(), ce.getOldModificationStamp(), ce.getModificationStamp()));
    }
    else if (event instanceof VFileDeleteEvent) {
      final VFileDeleteEvent de = (VFileDeleteEvent)event;
      myAdapted.beforeFileDeletion(new VirtualFileEvent(event.getRequestor(), de.getFile(), de.getFile().getParent(), 0, 0));
    }
    else if (event instanceof VFileMoveEvent) {
      final VFileMoveEvent me = (VFileMoveEvent)event;
      myAdapted.beforeFileMovement(new VirtualFileMoveEvent(event.getRequestor(), me.getFile(), me.getOldParent(), me.getNewParent()));
    }
    else if (event instanceof VFilePropertyChangeEvent) {
      final VFilePropertyChangeEvent pce = (VFilePropertyChangeEvent)event;
      myAdapted.beforePropertyChange(
        new VirtualFilePropertyEvent(event.getRequestor(), pce.getFile(), pce.getPropertyName(), pce.getOldValue(), pce.getNewValue()));
    }
  }
}