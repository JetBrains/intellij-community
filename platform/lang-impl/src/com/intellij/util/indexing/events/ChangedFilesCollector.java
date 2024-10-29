// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events;

import com.intellij.history.LocalHistory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiManager;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexImpl;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppJavaExecutorUtil;
import com.intellij.util.concurrency.CoroutineDispatcherBackedExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.ui.UIUtil;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.TimeoutCancellationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@ApiStatus.Internal
public final class ChangedFilesCollector extends IndexedFilesListener {
  private static final Logger LOG = Logger.getInstance(ChangedFilesCollector.class);
  public static final boolean CLEAR_NON_INDEXABLE_FILE_DATA =
    SystemProperties.getBooleanProperty("idea.indexes.clear.non.indexable.file.data", true);

  private final DirtyFiles myDirtyFiles = new DirtyFiles();

  private final AtomicInteger myProcessedEventIndex = new AtomicInteger();
  private final Phaser myWorkersFinishedSync = new Phaser() {
    @Override
    protected boolean onAdvance(int phase, int registeredParties) {
      return false;
    }
  };

  private final CoroutineDispatcherBackedExecutor vfsEventsExecutor;
  private final AtomicInteger myScheduledVfsEventsWorkers = new AtomicInteger();
  private final FileBasedIndexImpl myFileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();

  ChangedFilesCollector(@NotNull CoroutineScope coroutineScope) {
    vfsEventsExecutor = AppJavaExecutorUtil.createBoundedTaskExecutor("FileBasedIndex Vfs Event Processor", coroutineScope);
  }

  @Override
  protected void iterateIndexableFiles(@NotNull VirtualFile file, @NotNull ContentIterator iterator) {
    if (myFileBasedIndex.belongsToIndexableFiles(file)) {
      VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
        @Override
        public boolean visitFile(@NotNull VirtualFile file11) {
          if (!myFileBasedIndex.belongsToIndexableFiles(file11)) return false;
          iterator.processFile(file11);
          return true;
        }
      });
    }
  }

  public void clear() {
    myDirtyFiles.clear();
    ReadAction.run(() -> {
      processFilesInReadAction(info -> {
        return true;
      });
    });
  }

  @Override
  protected void recordFileEvent(@NotNull VirtualFile fileOrDir, boolean onlyContentDependent) {
    addToDirtyFiles(fileOrDir);
    super.recordFileEvent(fileOrDir, onlyContentDependent);
  }

  @Override
  protected void recordFileRemovedEvent(@NotNull VirtualFile file) {
    addToDirtyFiles(file);
    super.recordFileRemovedEvent(file);
  }

  private void addToDirtyFiles(@NotNull VirtualFile fileOrDir) {
    if (!(fileOrDir instanceof VirtualFileWithId fileOrDirWithId)) return;
    int id = fileOrDirWithId.getId();
    List<Project> projects = myFileBasedIndex.getIndexableFilesFilterHolder().findProjectsForFile(FileBasedIndex.getFileId(fileOrDir));
    myDirtyFiles.addFile(projects, id);
  }

  @Override
  public @NotNull AsyncFileListener.ChangeApplier prepareChange(@NotNull List<? extends @NotNull VFileEvent> events) {
    boolean shouldCleanup = ContainerUtil.exists(events, ChangedFilesCollector::memoryStorageCleaningNeeded);
    ChangeApplier superApplier = super.prepareChange(events);

    return new ChangeApplier() {
      @Override
      public void beforeVfsChange() {
        if (shouldCleanup) {
          myFileBasedIndex.cleanupMemoryStorage(false);
        }
        superApplier.beforeVfsChange();
      }

      @Override
      public void afterVfsChange() {
        superApplier.afterVfsChange();
        RegisteredIndexes registeredIndexes = myFileBasedIndex.getRegisteredIndexes();
        if (registeredIndexes != null && registeredIndexes.isInitialized()) ensureUpToDateAsync();
      }
    };
  }

  public @NotNull DirtyFiles getDirtyFiles() {
    return myDirtyFiles;
  }

  private static boolean memoryStorageCleaningNeeded(@NotNull VFileEvent event) {
    Object requestor = event.getRequestor();
    return requestor instanceof FileDocumentManager ||
           requestor instanceof PsiManager ||
           requestor == LocalHistory.VFS_EVENT_REQUESTOR;
  }

  public void ensureUpToDate() {
    if (!IndexUpToDateCheckIn.isUpToDateCheckEnabled()) {
      return;
    }
    //assert ApplicationManager.getApplication().isReadAccessAllowed() || ShutDownTracker.isShutdownHookRunning();
    myFileBasedIndex.waitUntilIndicesAreInitialized();

    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      processFilesToUpdateInReadAction();
    }
    else {
      processFilesInReadActionWithYieldingToWriteAction();
    }
  }

  public void ensureUpToDateAsync() {
    if (getEventMerger().getApproximateChangesCount() < 20 || !myScheduledVfsEventsWorkers.compareAndSet(0, 1)) {
      return;
    }

    if (DumbServiceImpl.isSynchronousTaskExecution()) {
      ensureUpToDate();
      return;
    }

    vfsEventsExecutor.execute(() -> {
      try {
        processFilesInReadActionWithYieldingToWriteAction();

        if (Registry.is("try.starting.dumb.mode.where.many.files.changed")) {
          for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            try {
              FileBasedIndexProjectHandler.scheduleReindexingInDumbMode(project);
            }
            catch (AlreadyDisposedException | ProcessCanceledException ignored) {
            }
            catch (Exception e) {
              LOG.error(e);
            }
          }
        }
      }
      finally {
        myScheduledVfsEventsWorkers.decrementAndGet();
      }
    });
  }

  public void processFilesToUpdateInReadAction() {
    processFilesInReadAction(new VfsEventsMerger.VfsEventProcessor() {
      private final StubIndexImpl.FileUpdateProcessor perFileElementTypeUpdateProcessor =
        ((StubIndexImpl)StubIndex.getInstance()).getPerFileElementTypeModificationTrackerUpdateProcessor();
      @Override
      public boolean process(VfsEventsMerger.@NotNull ChangeInfo info) {
        LOG.debug("Processing ", info);
        try {
          int fileId = info.getFileId();
          VirtualFile file = info.getFile();
          List<Project> dirtyQueueProjects = myDirtyFiles.getProjects(info.getFileId());
          if (info.isTransientStateChanged()) myFileBasedIndex.doTransientStateChangeForFile(fileId, file, dirtyQueueProjects);
          if (info.isContentChanged()) myFileBasedIndex.scheduleFileForIndexing(fileId, file, true, dirtyQueueProjects);
          if (info.isFileRemoved()) myFileBasedIndex.doInvalidateIndicesForFile(fileId, file, Collections.emptySet(), dirtyQueueProjects);
          if (info.isFileAdded()) myFileBasedIndex.scheduleFileForIndexing(fileId, file, false, dirtyQueueProjects);
          if (StubIndexImpl.PER_FILE_ELEMENT_TYPE_STUB_CHANGE_TRACKING_SOURCE ==
              StubIndexImpl.PerFileElementTypeStubChangeTrackingSource.ChangedFilesCollector) {
            perFileElementTypeUpdateProcessor.processUpdate(file);
          }
        }
        catch (Throwable t) {
          if (LOG.isDebugEnabled()) LOG.debug("Exception while processing " + info, t);
          throw t;
        }
        finally {
          myDirtyFiles.removeFile(info.getFileId());
        }
        return true;
      }

      @Override
      public void endBatch() {
        if (StubIndexImpl.PER_FILE_ELEMENT_TYPE_STUB_CHANGE_TRACKING_SOURCE ==
            StubIndexImpl.PerFileElementTypeStubChangeTrackingSource.ChangedFilesCollector) {
          perFileElementTypeUpdateProcessor.endUpdatesBatch();
        }
      }
    });
  }

  private void processFilesInReadAction(@NotNull VfsEventsMerger.VfsEventProcessor processor) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    int publishedEventIndex = getEventMerger().getPublishedEventIndex();
    int processedEventIndex = myProcessedEventIndex.get();
    if (processedEventIndex == publishedEventIndex) {
      return;
    }

    myWorkersFinishedSync.register();
    int phase = myWorkersFinishedSync.getPhase();
    try {
      myFileBasedIndex.waitUntilIndicesAreInitialized();
      getEventMerger().processChanges(new VfsEventsMerger.VfsEventProcessor() {
        @Override
        public boolean process(VfsEventsMerger.@NotNull ChangeInfo changeInfo) {
          return ConcurrencyUtil.withLock(myFileBasedIndex.myWriteLock, () -> {
            try {
              ProgressManager.getInstance().executeNonCancelableSection(() -> {
                processor.process(changeInfo);
              });
            }
            finally {
              IndexingStamp.flushCache(changeInfo.getFileId());
            }
            return true;
          });
        }
        @Override
        public void endBatch() {
          ConcurrencyUtil.withLock(myFileBasedIndex.myWriteLock, () -> {
            processor.endBatch();
          });
        }
      });
    }
    finally {
      myWorkersFinishedSync.arriveAndDeregister();
    }

    try {
      awaitWithCheckCancelled(myWorkersFinishedSync, phase);
    } catch (RejectedExecutionException | InterruptedException e) {
      LOG.warn(e);
      throw new ProcessCanceledException(e);
    }

    if (getEventMerger().getPublishedEventIndex() == publishedEventIndex) {
      myProcessedEventIndex.compareAndSet(processedEventIndex, publishedEventIndex);
    }
  }

  private static void awaitWithCheckCancelled(Phaser phaser, int phase) throws InterruptedException {
    while (true) {
      ProgressManager.checkCanceled();
      try {
        phaser.awaitAdvanceInterruptibly(phase, 100, TimeUnit.MILLISECONDS);
        break;
      }
      catch (TimeoutException ignored) {
      }
    }
  }

  private void processFilesInReadActionWithYieldingToWriteAction() {
    while (getEventMerger().hasChanges()) {
      ReadAction.nonBlocking((Callable<Void>)() -> {
        processFilesToUpdateInReadAction();
        return null;
      }).executeSynchronously();
    }
  }

  @TestOnly
  public void waitForVfsEventsExecuted(long timeout, @NotNull TimeUnit unit) {
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      vfsEventsExecutor.waitAllTasksExecuted(timeout, unit);
      return;
    }

    long deadline = System.nanoTime() + unit.toNanos(timeout);
    while (System.nanoTime() < deadline) {
      try {
        vfsEventsExecutor.waitAllTasksExecuted(100, TimeUnit.MILLISECONDS);
        return;
      }
      catch (TimeoutCancellationException e) {
        UIUtil.dispatchAllInvocationEvents();
      }
    }
  }
}
