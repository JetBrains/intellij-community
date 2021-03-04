// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.AtomicNullableLazyValue;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.CachedValueImpl;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;

abstract class IndexedFilesListener implements AsyncFileListener {
  @NotNull
  private final VfsEventsMerger myEventMerger = new VfsEventsMerger();

  @NotNull
  private final NullableLazyValue<VirtualFile> myConfig = AtomicNullableLazyValue.createValue(() -> {
    return LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(PathManager.getConfigPath()));
  });

  @NotNull
  private final NullableLazyValue<VirtualFile> myLog = AtomicNullableLazyValue.createValue(() -> {
    return LocalFileSystem.getInstance().findFileByIoFile(new File(PathManager.getLogPath()));
  });

  @NotNull
  private final CachedValue<Collection<VirtualFile>> myScratchesAndConsolesRoots =
    new CachedValueImpl<>(() -> new CachedValueProvider.Result<>(ScratchFileService.getAllRootPaths(),
                                                                 VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS));

  @NotNull
  VfsEventsMerger getEventMerger() {
    return myEventMerger;
  }

  protected void buildIndicesForFileRecursively(@NotNull final VirtualFile file, final boolean contentChange) {
    if (VfsEventsMerger.LOG != null) {
      VfsEventsMerger.LOG.info("Build indexes recursively for " + file + "; contentChange = " + contentChange);
    }
    if (file.isDirectory()) {
      final ContentIterator iterator = fileOrDir -> {
        myEventMerger.recordFileEvent(fileOrDir, contentChange);
        return true;
      };

      iterateIndexableFiles(file, iterator);
    }
    else {
      myEventMerger.recordFileEvent(file, contentChange);
    }
  }

  private boolean invalidateIndicesForFile(@NotNull VirtualFile file,
                                           boolean contentChange,
                                           boolean forceRebuildRequested,
                                           @NotNull VfsEventsMerger eventMerger) {
    if (isUnderConfigOrSystem(file)) {
      return false;
    }
    if (forceRebuildRequested) {
      file.putUserData(IndexingDataKeys.REBUILD_REQUESTED, Boolean.TRUE);
    }
    ProgressManager.checkCanceled();
    eventMerger.recordBeforeFileEvent(file, contentChange);
    return !file.isDirectory() || FileBasedIndexImpl.isMock(file) || ManagingFS.getInstance().wereChildrenAccessed(file);
  }

  protected abstract void iterateIndexableFiles(@NotNull VirtualFile file, @NotNull ContentIterator iterator);

  void invalidateIndicesRecursively(@NotNull VirtualFile file,
                                    boolean contentChange,
                                    boolean forceRebuildRequested,
                                    @NotNull VfsEventsMerger eventMerger) {
    if (VfsEventsMerger.LOG != null) {
      VfsEventsMerger.LOG.info("Invalidating indexes recursively for " + file + "; contentChange = " + contentChange + "; forceRebuildRequest = " + forceRebuildRequested);
    }
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        return invalidateIndicesForFile(file, contentChange, forceRebuildRequested, eventMerger);
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
        invalidateIndicesRecursively(((VFileContentChangeEvent)event).getFile(), true, false, tempMerger);
      }
      else if (event instanceof VFileDeleteEvent) {
        invalidateIndicesRecursively(((VFileDeleteEvent)event).getFile(), false, false, tempMerger);
      }
      else if (event instanceof VFilePropertyChangeEvent) {
        final VFilePropertyChangeEvent pce = (VFilePropertyChangeEvent)event;
        String propertyName = pce.getPropertyName();
        if (propertyName.equals(VirtualFile.PROP_NAME)) {
          // indexes may depend on file name
          // name change may lead to filetype change so the file might become not indexable
          // in general case have to 'unindex' the file and index it again if needed after the name has been changed
          invalidateIndicesRecursively(pce.getFile(), false, false, tempMerger);
        }
        else if (propertyName.equals(VirtualFile.PROP_ENCODING)) {
          invalidateIndicesRecursively(pce.getFile(), true, false, tempMerger);
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
}