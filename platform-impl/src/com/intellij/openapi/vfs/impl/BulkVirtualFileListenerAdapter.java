/*
 * @author max
 */
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;

import java.util.List;

public class BulkVirtualFileListenerAdapter implements BulkFileListener {
  private final VirtualFileListener myAdaptee;

  public BulkVirtualFileListenerAdapter(final VirtualFileListener adaptee) {
    myAdaptee = adaptee;
  }

  public void before(final List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      fireBefore(event);
    }
  }

  public void after(final List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      fireAfter(event);
    }
  }

  private void fireAfter(final VFileEvent event) {
    if (event instanceof VFileContentChangeEvent) {
      final VFileContentChangeEvent ce = (VFileContentChangeEvent)event;
      final VirtualFile file = ce.getFile();
      myAdaptee
        .contentsChanged(new VirtualFileEvent(event.getRequestor(), file, file.getParent(), ce.getOldModificationStamp(), ce.getModificationStamp()));
    }
    else if (event instanceof VFileCopyEvent) {
      final VFileCopyEvent ce = (VFileCopyEvent)event;
      myAdaptee
        .fileCopied(new VirtualFileCopyEvent(event.getRequestor(), ce.getFile(), ce.getNewParent().findChild(ce.getNewChildName())));
    }
    else if (event instanceof VFileCreateEvent) {
      final VFileCreateEvent ce = (VFileCreateEvent)event;
      final VirtualFile newChild = ce.getParent().findChild(ce.getChildName());
      if (newChild != null) {
        myAdaptee.fileCreated(
        new VirtualFileEvent(event.getRequestor(), newChild, ce.getChildName(), ce.getParent()));
      }
    }
    else if (event instanceof VFileDeleteEvent) {
      final VFileDeleteEvent de = (VFileDeleteEvent)event;
      myAdaptee
        .fileDeleted(new VirtualFileEvent(event.getRequestor(), de.getFile(), de.getFile().getParent(), 0, 0));
    }
    else if (event instanceof VFileMoveEvent) {
      final VFileMoveEvent me = (VFileMoveEvent)event;
      myAdaptee.fileMoved(new VirtualFileMoveEvent(event.getRequestor(), me.getFile(), me.getOldParent(), me.getNewParent()));
    }
    else if (event instanceof VFilePropertyChangeEvent) {
      final VFilePropertyChangeEvent pce = (VFilePropertyChangeEvent)event;
      myAdaptee.propertyChanged(
        new VirtualFilePropertyEvent(event.getRequestor(), pce.getFile(), pce.getPropertyName(), pce.getOldValue(), pce.getNewValue()));
    }
  }

  private void fireBefore(final VFileEvent event) {
    if (event instanceof VFileContentChangeEvent) {
      final VFileContentChangeEvent ce = (VFileContentChangeEvent)event;
      final VirtualFile file = ce.getFile();
      myAdaptee
        .beforeContentsChange(new VirtualFileEvent(event.getRequestor(), file, file.getParent(), ce.getOldModificationStamp(), ce.getModificationStamp()));
    }
    else if (event instanceof VFileDeleteEvent) {
      final VFileDeleteEvent de = (VFileDeleteEvent)event;
      myAdaptee
        .beforeFileDeletion(new VirtualFileEvent(event.getRequestor(), de.getFile(), de.getFile().getParent(), 0, 0));
    }
    else if (event instanceof VFileMoveEvent) {
      final VFileMoveEvent me = (VFileMoveEvent)event;
      myAdaptee.beforeFileMovement(new VirtualFileMoveEvent(event.getRequestor(), me.getFile(), me.getOldParent(), me.getNewParent()));
    }
    else if (event instanceof VFilePropertyChangeEvent) {
      final VFilePropertyChangeEvent pce = (VFilePropertyChangeEvent)event;
      myAdaptee.beforePropertyChange(
        new VirtualFilePropertyEvent(event.getRequestor(), pce.getFile(), pce.getPropertyName(), pce.getOldValue(), pce.getNewValue()));
    }
  }
}