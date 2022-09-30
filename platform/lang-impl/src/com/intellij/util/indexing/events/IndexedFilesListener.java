// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.events;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.IndexingFlag;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class IndexedFilesListener implements AsyncFileListener {
  @NotNull
  private final VfsEventsMerger myEventMerger;

  public IndexedFilesListener() {
    myEventMerger = new VfsEventsMerger();
  }

  public IndexedFilesListener(@NotNull VfsEventsMerger.VfsEventProcessor instantUpdateProcessor) {
    myEventMerger = new VfsEventsMerger(instantUpdateProcessor);
  }

  @NotNull
  public VfsEventsMerger getEventMerger() {
    return myEventMerger;
  }

  public void scheduleForIndexingRecursively(@NotNull VirtualFile file, boolean onlyContentDependent) {
    IndexingFlag.cleanProcessedFlagRecursively(file);
    if (file.isDirectory()) {
      final ContentIterator iterator = fileOrDir -> {
        myEventMerger.recordFileEvent(fileOrDir, onlyContentDependent);
        return true;
      };

      iterateIndexableFiles(file, iterator);
    }
    else {
      myEventMerger.recordFileEvent(file, onlyContentDependent);
    }
  }

  private static boolean collectFiles(@NotNull VirtualFile file, @NotNull Int2ObjectMap<VirtualFile> id2File) {
    ProgressManager.checkCanceled();
    if (file instanceof VirtualFileWithId) {
      id2File.put(((VirtualFileWithId)file).getId(), file);
    }
    return !file.isDirectory() || FileBasedIndexImpl.isMock(file) || ManagingFS.getInstance().wereChildrenAccessed(file);
  }

  protected abstract void iterateIndexableFiles(@NotNull VirtualFile file, @NotNull ContentIterator iterator);

  public void collectFilesRecursively(@NotNull VirtualFile file, @NotNull Int2ObjectMap<VirtualFile> id2File) {
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        return collectFiles(file, id2File);
      }

      @Override
      public Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile file) {
        return file instanceof NewVirtualFile ? ((NewVirtualFile)file).iterInDbChildren() : null;
      }
    });
  }

  @Override
  @NotNull
  public ChangeApplier prepareChange(@NotNull List<? extends @NotNull VFileEvent> events) {
    Int2ObjectMap<VirtualFile> deletedFiles = new Int2ObjectOpenHashMap<>();
    for (VFileEvent event : events) {
      if (event instanceof VFileDeleteEvent) {
        collectFilesRecursively(((VFileDeleteEvent)event).getFile(), deletedFiles);
      }
    }

    return new ChangeApplier() {
      @Override
      public void beforeVfsChange() {
        for (VirtualFile file : deletedFiles.values()) {
          myEventMerger.recordFileRemovedEvent(file);
        }
      }

      @Override
      public void afterVfsChange() {
        processAfterEvents(events);
      }
    };
  }

  private void processAfterEvents(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      VirtualFile fileToIndex = null;
      boolean onlyContentDependent = true;

      if (event instanceof VFileContentChangeEvent) {
        fileToIndex = event.getFile();
      }
      else if (event instanceof VFileCopyEvent) {
        final VFileCopyEvent ce = (VFileCopyEvent)event;
        final VirtualFile copy = ce.getNewParent().findChild(ce.getNewChildName());
        if (copy != null) {
          fileToIndex = copy;
          onlyContentDependent = false;
        }
      }
      else if (event instanceof VFileCreateEvent) {
        final VirtualFile newChild = event.getFile();
        if (newChild != null) {
          fileToIndex = newChild;
          onlyContentDependent = false;
        }
      }
      else if (event instanceof VFileMoveEvent) {
        fileToIndex = event.getFile();
        onlyContentDependent = false;
      }
      else if (event instanceof VFilePropertyChangeEvent) {
        final VFilePropertyChangeEvent pce = (VFilePropertyChangeEvent)event;
        String propertyName = pce.getPropertyName();
        if (propertyName.equals(VirtualFile.PROP_NAME)) {
          // indexes may depend on file name
          fileToIndex = pce.getFile();
          onlyContentDependent = false;
        }
        else if (propertyName.equals(VirtualFile.PROP_ENCODING)) {
          fileToIndex = pce.getFile();
        }
      }

      if (fileToIndex != null) {
        scheduleForIndexingRecursively(fileToIndex, onlyContentDependent);
      }
    }
  }
}