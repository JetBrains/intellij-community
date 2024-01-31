// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.history.LocalHistory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.PingProgress;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiManager;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexImpl;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.ConcurrentBitSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import com.intellij.util.indexing.*;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@ApiStatus.Internal
public final class ChangedFilesCollector extends IndexedFilesListener {
  private static final Logger LOG = Logger.getInstance(ChangedFilesCollector.class);
  public static final boolean CLEAR_NON_INDEXABLE_FILE_DATA =
    SystemProperties.getBooleanProperty("idea.indexes.clear.non.indexable.file.data", true);

  private final IntObjectMap<VirtualFile> myFilesToUpdate =
    ConcurrentCollectionFactory.createConcurrentIntObjectMap();

  // files from myEventMerger and myFilesToUpdate
  private final List<Pair<Project, ConcurrentBitSet>> myDirtyFiles = ContainerUtil.createLockFreeCopyOnWriteList();
  private final ConcurrentBitSet myDirtyFilesWithoutProject = ConcurrentBitSet.create();

  private final AtomicInteger myProcessedEventIndex = new AtomicInteger();
  private final Phaser myWorkersFinishedSync = new Phaser() {
    @Override
    protected boolean onAdvance(int phase, int registeredParties) {
      return false;
    }
  };

  private final Executor
    myVfsEventsExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("FileBasedIndex Vfs Event Processor");
  private final AtomicInteger myScheduledVfsEventsWorkers = new AtomicInteger();
  private final FileBasedIndexImpl myFileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();

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

  public void scheduleForUpdate(@NotNull VirtualFile file) {
    int fileId = FileBasedIndex.getFileId(file);
    if (!(file instanceof DeletedVirtualFileStub)) {
      Set<Project> projects = myFileBasedIndex.getContainingProjects(file);
      if (projects.isEmpty()) {
        removeNonIndexableFileData(file, fileId);
        return;
      }
    }

    VfsEventsMerger.tryLog("ADD_TO_UPDATE", file);
    myFilesToUpdate.put(fileId, file);
  }

  public void removeScheduledFileFromUpdate(VirtualFile file) {
    int fileId = FileBasedIndex.getFileId(file);
    VirtualFile alreadyScheduledFile = myFilesToUpdate.get(fileId);
    if (!(alreadyScheduledFile instanceof DeletedVirtualFileStub)) {
      VfsEventsMerger.tryLog("PULL_OUT_FROM_UPDATE", file);
      myFilesToUpdate.remove(fileId);
      removeFromDirtyFiles(fileId);
    }
  }

  public void removeFileIdFromFilesScheduledForUpdate(int fileId) {
    myFilesToUpdate.remove(fileId);
    removeFromDirtyFiles(fileId);
  }

  private void removeFromDirtyFiles(int fileId) {
    myDirtyFilesWithoutProject.clear(fileId);
    for (Pair<Project, ConcurrentBitSet> pair : myDirtyFiles) {
      pair.second.clear(fileId);
    }
  }

  public boolean containsFileId(int fileId) {
    return myFilesToUpdate.containsKey(fileId);
  }

  public Iterator<@NotNull VirtualFile> getFilesToUpdate() {
    return myFilesToUpdate.values().iterator();
  }

  public Collection<VirtualFile> getAllFilesToUpdate() {
    ensureUpToDate();
    return myFilesToUpdate.isEmpty()
           ? Collections.emptyList()
           : Collections.unmodifiableCollection(myFilesToUpdate.values());
  }

  @NotNull
  public IntSet getDirtyFilesWithoutProject() {
    return toIntSet(myDirtyFilesWithoutProject);
  }

  public void clearFilesToUpdate() {
    myFilesToUpdate.clear();
    myDirtyFilesWithoutProject.clear();
    for (Pair<Project, ConcurrentBitSet> p : myDirtyFiles) {
      p.second.clear();
    }
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

    Set<Project> projects = myFileBasedIndex.getContainingProjects(fileOrDir);
    if (projects.isEmpty()) {
      myDirtyFilesWithoutProject.set(id);
      return;
    }
    for (Project project : projects) {
      Pair<Project, ConcurrentBitSet> projectDirtyFiles = getDirtyFilesWithoutProject(project);
      if (projectDirtyFiles != null) {
        projectDirtyFiles.second.set(id);
      }
      else {
        assert false : "Project (name: " + project.getName() + " hash: " + project.getLocationHash() + ") " +
                       "was not found in myDirtyFiles. " +
                       "Projects in myDirtyFiles: " + Strings.join(myDirtyFiles, p -> "(name: " + p.first.getName() + " hash: " + p.first.getLocationHash() + ") ", ", ");
      }
    }
  }

  @Override
  @NotNull
  public AsyncFileListener.ChangeApplier prepareChange(@NotNull List<? extends @NotNull VFileEvent> events) {
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

  private void removeNonIndexableFileData(@NotNull VirtualFile file, int fileId) {
    if (CLEAR_NON_INDEXABLE_FILE_DATA) {
      List<ID<?, ?>> extensions = getIndexedContentDependentExtensions(fileId);
      if (!extensions.isEmpty()) {
        myFileBasedIndex.removeDataFromIndicesForFile(fileId, file, "non_indexable_file");
      }
      IndexingFlag.cleanProcessingFlag(file);
    }
    else if (ApplicationManager.getApplication().isInternal() && !ApplicationManager.getApplication().isUnitTestMode()) {
      checkNotIndexedByContentBasedIndexes(file, fileId);
    }
  }

  public void addProject(@NotNull Project project) {
    myDirtyFiles.add(new Pair<>(project, ConcurrentBitSet.create()));
  }

  public void onProjectClosing(@NotNull Project project, long vfsCreationStamp) {
    persistProjectsDirtyFiles(project, vfsCreationStamp);
    myDirtyFiles.removeIf(p -> p.first.equals(project));
  }

  public void persistProjectsDirtyFiles(long vfsCreationStamp) {
    for (Pair<Project, ConcurrentBitSet> p : myDirtyFiles) {
      persistProjectsDirtyFiles(p.first, vfsCreationStamp);
    }
    // remove events from event merger, so they don't show up after FileBasedIndex is restarted using tumbler
    ReadAction.run(() -> {
      processFilesInReadAction(info -> {
        return true;
      });
    });
  }

  private void persistProjectsDirtyFiles(@NotNull Project project, long vfsCreationStamp) {
    Pair<Project, ConcurrentBitSet> p = getDirtyFilesWithoutProject(project);
    if (p == null) return;
    IntSet dirtyFileIds = toIntSet(p.second);
    PersistentDirtyFilesQueue.storeIndexingQueue(PersistentDirtyFilesQueue.getQueuesDir().resolve(p.first.getLocationHash()), dirtyFileIds, vfsCreationStamp);
  }

  @NotNull
  private static IntSet toIntSet(@NotNull ConcurrentBitSet dirtyFilesSet) {
    IntSet dirtyFileIds = new IntOpenHashSet();
    for (int fileId = 0; fileId < dirtyFilesSet.size(); fileId++) {
      if (dirtyFilesSet.get(fileId)) {
        PingProgress.interactWithEdtProgress();
        dirtyFileIds.add(fileId);
      }
    }
    return dirtyFileIds;
  }

  @Nullable
  private Pair<Project, ConcurrentBitSet> getDirtyFilesWithoutProject(@NotNull Project project) {
    return ContainerUtil.find(myDirtyFiles, p -> p.first.equals(project));
  }

  private static boolean memoryStorageCleaningNeeded(@NotNull VFileEvent event) {
    Object requestor = event.getRequestor();
    return requestor instanceof FileDocumentManager ||
           requestor instanceof PsiManager ||
           requestor == LocalHistory.VFS_EVENT_REQUESTOR;
  }

  public boolean isScheduledForUpdate(VirtualFile file) {
    return myFilesToUpdate.containsKey(FileBasedIndex.getFileId(file));
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
    if (getEventMerger().getApproximateChangesCount() >= 20 && myScheduledVfsEventsWorkers.compareAndSet(0, 1)) {
      if (DumbServiceImpl.isSynchronousTaskExecution()) {
        ensureUpToDate();
        return;
      }

      myVfsEventsExecutor.execute(() -> {
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
          if (info.isTransientStateChanged()) myFileBasedIndex.doTransientStateChangeForFile(fileId, file);
          if (info.isContentChanged()) myFileBasedIndex.scheduleFileForIndexing(fileId, file, true);
          if (info.isFileRemoved()) myFileBasedIndex.doInvalidateIndicesForFile(fileId, file);
          if (info.isFileAdded()) myFileBasedIndex.scheduleFileForIndexing(fileId, file, false);
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
          if (!myFilesToUpdate.containsKey(info.getFileId())) {
            removeFromDirtyFiles(info.getFileId()); // vfs event was processed by files was not scheduled for indexing
          }
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
      ReadAction.nonBlocking(() -> processFilesToUpdateInReadAction()).executeSynchronously();
    }
  }

  private void checkNotIndexedByContentBasedIndexes(@NotNull VirtualFile file, int fileId) {
    List<ID<?, ?>> contentDependentIndexes = getIndexedContentDependentExtensions(fileId);
    if (!contentDependentIndexes.isEmpty()) {
      LOG.error("indexes " + contentDependentIndexes + " will not be updated for file = " + file + ", id = " + fileId);
    }
  }

  private @NotNull List<ID<?, ?>> getIndexedContentDependentExtensions(int fileId) {
    List<ID<?, ?>> indexedStates = IndexingStamp.getNontrivialFileIndexedStates(fileId);
    RegisteredIndexes registeredIndexes = myFileBasedIndex.getRegisteredIndexes();
    List<ID<?, ?>> contentDependentIndexes;
    if (registeredIndexes == null) {
      Set<? extends ID<?, ?>> allContentDependentIndexes = FileBasedIndexExtension.EXTENSION_POINT_NAME.getExtensionList().stream()
        .filter(ex -> ex.dependsOnFileContent())
        .map(ex -> ex.getName())
        .collect(Collectors.toSet());
      contentDependentIndexes = ContainerUtil.filter(indexedStates, id -> !allContentDependentIndexes.contains(id));
    }
    else {
      contentDependentIndexes = ContainerUtil.filter(indexedStates, id -> {
        return registeredIndexes.isContentDependentIndex(id);
      });
    }
    return contentDependentIndexes;
  }

  @TestOnly
  public void waitForVfsEventsExecuted(long timeout, @NotNull TimeUnit unit) throws Exception {
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      ((BoundedTaskExecutor)myVfsEventsExecutor).waitAllTasksExecuted(timeout, unit);
      return;
    }
    long deadline = System.nanoTime() + unit.toNanos(timeout);
    while (System.nanoTime() < deadline) {
      try {
        ((BoundedTaskExecutor)myVfsEventsExecutor).waitAllTasksExecuted(100, TimeUnit.MILLISECONDS);
        return;
      }
      catch (TimeoutException e) {
        UIUtil.dispatchAllInvocationEvents();
      }
    }
  }
}
