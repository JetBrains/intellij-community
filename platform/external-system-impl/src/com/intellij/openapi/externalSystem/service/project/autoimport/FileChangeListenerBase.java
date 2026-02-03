// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.autoimport;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class FileChangeListenerBase implements BulkFileListener {
  protected abstract boolean isRelevant(String path);

  protected abstract void updateFile(VirtualFile file, VFileEvent event);

  protected abstract void deleteFile(VirtualFile file, VFileEvent event);

  protected abstract void apply();

  @Override
  public void before(@NotNull List<? extends @NotNull VFileEvent> events) {
    for (VFileEvent each : events) {
      if (each instanceof VFileDeleteEvent) {
        deleteRecursively(each.getFile(), each);
      }
      else {
        if (!isRelevant(each.getPath())) continue;
        if (each instanceof VFilePropertyChangeEvent) {
          if (isRenamed(each)) {
            deleteRecursively(each.getFile(), each);
          }
        }
        else if (each instanceof VFileMoveEvent moveEvent) {
          String newPath = moveEvent.getNewParent().getPath() + "/" + moveEvent.getFile().getName();
          if (!isRelevant(newPath)) {
            deleteRecursively(moveEvent.getFile(), each);
          }
        }
      }
    }
  }

  private void deleteRecursively(VirtualFile f, final VFileEvent event) {
    VfsUtilCore.visitChildrenRecursively(f, new VirtualFileVisitor<Void>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile f) {
        if (isRelevant(f.getPath())) deleteFile(f, event);
        return true;
      }

      @Override
      public @Nullable Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile f) {
        return f.isDirectory() && f instanceof NewVirtualFile ? ((NewVirtualFile)f).iterInDbChildren() : null;
      }
    });
  }

  @Override
  public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
    for (VFileEvent each : events) {
      if (!isRelevant(each.getPath())) continue;

      if (each instanceof VFileCreateEvent createEvent) {
        VirtualFile newChild = createEvent.getParent().findChild(createEvent.getChildName());
        if (newChild != null) {
          updateFile(newChild, each);
        }
      }
      else if (each instanceof VFileCopyEvent copyEvent) {
        VirtualFile newChild = copyEvent.getNewParent().findChild(copyEvent.getNewChildName());
        if (newChild != null) {
          updateFile(newChild, each);
        }
      }
      else if (each instanceof VFileContentChangeEvent) {
        updateFile(each.getFile(), each);
      }
      else if (each instanceof VFilePropertyChangeEvent) {
        if (isRenamed(each)) {
          updateFile(each.getFile(), each);
        }
      }
      else if (each instanceof VFileMoveEvent) {
        updateFile(each.getFile(), each);
      }
    }
    apply();
  }

  private static boolean isRenamed(VFileEvent each) {
    return ((VFilePropertyChangeEvent)each).getPropertyName().equals(VirtualFile.PROP_NAME)
           && !Comparing.equal(((VFilePropertyChangeEvent)each).getOldValue(), ((VFilePropertyChangeEvent)each).getNewValue());
  }
}
