// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.events;

import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.CachedValueImpl;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.IndexingDataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public abstract class IndexedFilesListener implements AsyncFileListener {
  public enum InvalidationCause {
    REMOVED,
    CHANGED,
    RENAMED,
    FORCE_REBUILD
  }

  @NotNull
  private final VfsEventsMerger myEventMerger = new VfsEventsMerger();

  @NotNull
  private final CachedValue<VirtualFile> myConfig = new CachedValueImpl<>(() -> {
    return new CachedValueProvider.Result<>(findVFileByPath(PathManager.getConfigPath()),
                                            VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS);
  });

  @NotNull
  private final CachedValue<VirtualFile> myLog = new CachedValueImpl<>(() -> {
    return new CachedValueProvider.Result<>(findVFileByPath(PathManager.getLogPath()),
                                            VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS);
  });

  @NotNull
  private final CachedValue<Collection<VirtualFile>> myScratchesAndConsolesRoots =
    new CachedValueImpl<>(() -> new CachedValueProvider.Result<>(ScratchFileService.getAllRootPaths(),
                                                                 VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS));

  @NotNull
  public VfsEventsMerger getEventMerger() {
    return myEventMerger;
  }

  protected void buildIndicesForFileRecursively(@NotNull final VirtualFile file, final boolean contentChange) {
    if (file.isDirectory()) {
      final ContentIterator iterator = fileOrDir -> {
        myEventMerger.recordFileEvent(fileOrDir, contentChange);
        if (VfsEventsMerger.LOG != null) {
          VfsEventsMerger.LOG.info("Build indexes for " + file + "; contentChange = " + contentChange);
        }
        return true;
      };

      iterateIndexableFiles(file, iterator);
    }
    else {
      myEventMerger.recordFileEvent(file, contentChange);
    }
  }

  private boolean invalidateIndicesForFile(@NotNull VirtualFile file,
                                           @NotNull InvalidationCause cause,
                                           @NotNull VfsEventsMerger eventMerger) {
    if (cause != InvalidationCause.REMOVED && isUnderConfigOrSystem(file)) {
      // wipe eagerly indexes if they're built for system/config files anyway.
      return false;
    }
    if (cause == InvalidationCause.FORCE_REBUILD) {
      file.putUserData(IndexingDataKeys.REBUILD_REQUESTED, Boolean.TRUE);
    }
    ProgressManager.checkCanceled();
    eventMerger.recordBeforeFileEvent(file, cause != InvalidationCause.REMOVED && cause != InvalidationCause.RENAMED);
    if (VfsEventsMerger.LOG != null) {
      VfsEventsMerger.LOG.info("Invalidating indexes for " + file + "; cause = " + cause);
    }
    return !file.isDirectory() || FileBasedIndexImpl.isMock(file) || ManagingFS.getInstance().wereChildrenAccessed(file);
  }

  protected abstract void iterateIndexableFiles(@NotNull VirtualFile file, @NotNull ContentIterator iterator);

  public void invalidateIndicesRecursively(@NotNull VirtualFile file,
                                           @NotNull InvalidationCause cause,
                                           @NotNull VfsEventsMerger eventMerger) {
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        return invalidateIndicesForFile(file, cause, eventMerger);
      }

      @Override
      public Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile file) {
        return file instanceof NewVirtualFile ? ((NewVirtualFile)file).iterInDbChildren() : null;
      }
    });
  }

  @Override
  @NotNull
  public ChangeApplier prepareChange(@NotNull List<? extends VFileEvent> events) {
    VfsEventsMerger tempMerger = new VfsEventsMerger();
    for (VFileEvent event : events) {
      if (event instanceof VFileContentChangeEvent) {
        invalidateIndicesRecursively(((VFileContentChangeEvent)event).getFile(), InvalidationCause.CHANGED, tempMerger);
      }
      else if (event instanceof VFileDeleteEvent) {
        invalidateIndicesRecursively(((VFileDeleteEvent)event).getFile(), InvalidationCause.REMOVED, tempMerger);
      }
      else if (event instanceof VFilePropertyChangeEvent) {
        final VFilePropertyChangeEvent pce = (VFilePropertyChangeEvent)event;
        String propertyName = pce.getPropertyName();
        if (propertyName.equals(VirtualFile.PROP_NAME)) {
          // indexes may depend on file name
          // name change may lead to filetype change so the file might become not indexable
          // in general case have to 'unindex' the file and index it again if needed after the name has been changed
          // TODO revise this code, looks suspicious
          invalidateIndicesRecursively(pce.getFile(), InvalidationCause.RENAMED, tempMerger);
        }
        else if (propertyName.equals(VirtualFile.PROP_ENCODING)) {
          invalidateIndicesRecursively(pce.getFile(), InvalidationCause.CHANGED, tempMerger);
        }
      }
    }
    return new ChangeApplier() {
      @Override
      public void beforeVfsChange() {
        myEventMerger.applyMergedEvents(tempMerger);
      }

      @Override
      public void afterVfsChange() {
        processAfterEvents(events);
      }
    };
  }

  private void processAfterEvents(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      if (event instanceof VFileContentChangeEvent) {
        buildIndicesForFileRecursively(((VFileContentChangeEvent)event).getFile(), true);
      }
      else if (event instanceof VFileCopyEvent) {
        final VFileCopyEvent ce = (VFileCopyEvent)event;
        final VirtualFile copy = ce.getNewParent().findChild(ce.getNewChildName());
        if (copy != null) {
          buildIndicesForFileRecursively(copy, false);
        }
      }
      else if (event instanceof VFileCreateEvent) {
        final VirtualFile newChild = event.getFile();
        if (newChild != null) {
          buildIndicesForFileRecursively(newChild, false);
        }
      }
      else if (event instanceof VFileMoveEvent) {
        buildIndicesForFileRecursively(((VFileMoveEvent)event).getFile(), false);
      }
      else if (event instanceof VFilePropertyChangeEvent) {
        final VFilePropertyChangeEvent pce = (VFilePropertyChangeEvent)event;
        String propertyName = pce.getPropertyName();
        if (propertyName.equals(VirtualFile.PROP_NAME)) {
          // indexes may depend on file name
          buildIndicesForFileRecursively(pce.getFile(), false);
        }
        else if (propertyName.equals(VirtualFile.PROP_ENCODING)) {
          buildIndicesForFileRecursively(pce.getFile(), true);
        }
      }
    }
  }

  /**
   * There's no sense to index files under config and system directories.
   * But, actually, config directory contains scratches and consoles as well: these files we must index.
   */
  private boolean isUnderConfigOrSystem(@NotNull VirtualFile file) {
    // check log files
    VirtualFile logValue = myLog.getValue();
    if (logValue != null && VfsUtilCore.isAncestor(logValue, file, false)) return true;

    // check scratches & consoles first because they are placed under config
    for (VirtualFile root : myScratchesAndConsolesRoots.getValue()) {
      if (root != null && VfsUtilCore.isAncestor(root, file, false)) {
        return false;
      }
    }

    // check other config files
    VirtualFile configValue = myConfig.getValue();
    if (configValue != null && VfsUtilCore.isAncestor(configValue, file, false)) return true;

    return false;
  }

  @Nullable
  private static VirtualFile findVFileByPath(@NotNull String path) {
    return LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(path));
  }
}