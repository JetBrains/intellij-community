// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.google.common.collect.Iterators;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.startup.ServiceNotReadyException;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.EditorHighlighterCache;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.PingProgress;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.GentleFlusherBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.AsyncEventSupport;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiDocumentTransactionListener;
import com.intellij.psi.impl.cache.impl.id.PlatformIdTableBuilding;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.psi.stubs.SerializedStubTree;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.reference.SoftReference;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.gist.GistManager;
import com.intellij.util.indexing.FileIndexesValuesApplier.ApplicationMode;
import com.intellij.util.indexing.contentQueue.CachedFileContent;
import com.intellij.util.indexing.contentQueue.IndexUpdateRunner;
import com.intellij.util.indexing.dependencies.FileIndexingStamp;
import com.intellij.util.indexing.dependencies.IndexingRequestToken;
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService;
import com.intellij.util.indexing.diagnostic.BrokenIndexingDiagnostics;
import com.intellij.util.indexing.diagnostic.IndexStatisticGroup;
import com.intellij.util.indexing.diagnostic.StorageDiagnosticData;
import com.intellij.util.indexing.events.ChangedFilesCollector;
import com.intellij.util.indexing.events.DeletedVirtualFileStub;
import com.intellij.util.indexing.events.VfsEventsMerger;
import com.intellij.util.indexing.impl.MapReduceIndexMappingException;
import com.intellij.util.indexing.impl.storage.DefaultIndexStorageLayout;
import com.intellij.util.indexing.impl.storage.TransientFileContentIndex;
import com.intellij.util.indexing.projectFilter.FileAddStatus;
import com.intellij.util.indexing.projectFilter.IncrementalProjectIndexableFilesFilterHolder;
import com.intellij.util.indexing.projectFilter.ProjectIndexableFilesFilterHolder;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.StorageLockContext;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.SimpleMessageBusConnection;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.*;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.platform.diagnostic.telemetry.PlatformScopesKt.Indexes;
import static com.intellij.util.MathUtil.clamp;
import static com.intellij.util.indexing.IndexingFlag.cleanProcessingFlag;
import static com.intellij.util.indexing.IndexingFlag.cleanupProcessedFlag;
import static java.util.concurrent.TimeUnit.*;

public final class FileBasedIndexImpl extends FileBasedIndexEx {
  private static final ThreadLocal<VirtualFile> ourIndexedFile = new ThreadLocal<>();
  private static final ThreadLocal<IndexWritingFile> ourWritingIndexFile = new ThreadLocal<>();
  private static final boolean FORBID_LOOKUP_IN_NON_CANCELLABLE_SECTIONS =
    SystemProperties.getBooleanProperty("forbid.index.lookup.in.non.cancellable.section", false);

  @ApiStatus.Internal
  public static final Logger LOG = Logger.getInstance(FileBasedIndexImpl.class);

  private static final boolean USE_GENTLE_FLUSHER = SystemProperties.getBooleanProperty("indexes.flushing.use-gentle-flusher", true);
  /** How often, on average, flush each index to the disk */
  private static final long FLUSHING_PERIOD_MS = SECONDS.toMillis(FlushingDaemon.FLUSHING_PERIOD_IN_SECONDS);
  final CoroutineScope coroutineScope;

  private volatile RegisteredIndexes myRegisteredIndexes;
  private volatile @Nullable String myShutdownReason;
  private volatile long vfsCreationStamp;

  private final PerIndexDocumentVersionMap myLastIndexedDocStamps = new PerIndexDocumentVersionMap();

  private final ProjectIndexableFilesFilterHolder myIndexableFilesFilterHolder;

  // findExtensionOrFail is thread safe
  private final NotNullLazyValue<ChangedFilesCollector> myChangedFilesCollector =
    NotNullLazyValue.createValue(() -> AsyncEventSupport.EP_NAME.findExtensionOrFail(ChangedFilesCollector.class));

  private final List<Pair<IndexableFileSet, Project>> myIndexableSets = ContainerUtil.createLockFreeCopyOnWriteList();

  private final SimpleMessageBusConnection myConnection;
  private final FileDocumentManager myFileDocumentManager;

  private final Set<ID<?, ?>> myUpToDateIndicesForUnsavedOrTransactedDocuments = ConcurrentCollectionFactory.createConcurrentSet();
  private volatile SmartFMap<Document, PsiFile> myTransactionMap = SmartFMap.emptyMap();

  final boolean myIsUnitTestMode;

  private @Nullable Runnable myShutDownTask;
  private @Nullable AutoCloseable myFlushingTask;
  private @Nullable ScheduledFuture<?> myHealthCheckFuture;

  private final AtomicInteger myLocalModCount = new AtomicInteger();
  private final IntSet myStaleIds = new IntOpenHashSet();

  final Lock myReadLock;
  public final Lock myWriteLock;

  private IndexConfiguration getState() {
    return myRegisteredIndexes.getConfigurationState();
  }

  void dropRegisteredIndexes() {
    LOG.assertTrue(myFlushingTask == null);
    LOG.assertTrue(myUpToDateIndicesForUnsavedOrTransactedDocuments.isEmpty());
    LOG.assertTrue(myTransactionMap.isEmpty());

    myRegisteredIndexes = null;
  }

  public FileBasedIndexImpl(@NotNull CoroutineScope coroutineScope) {
    this.coroutineScope = coroutineScope;
    ReadWriteLock lock = new ReentrantReadWriteLock();
    myReadLock = lock.readLock();
    myWriteLock = lock.writeLock();

    myFileDocumentManager = FileDocumentManager.getInstance();
    myIsUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();

    MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
    SimpleMessageBusConnection connection = messageBus.simpleConnect();

    connection.subscribe(PsiDocumentTransactionListener.TOPIC, new PsiDocumentTransactionListener() {
      @Override
      public void transactionStarted(@NotNull final Document doc, @NotNull final PsiFile file) {
        myTransactionMap = myTransactionMap.plus(doc, file);
        clearUpToDateIndexesForUnsavedOrTransactedDocs();
      }

      @Override
      public void transactionCompleted(@NotNull final Document doc, @NotNull final PsiFile file) {
        myTransactionMap = myTransactionMap.minus(doc);
      }
    });

    connection.subscribe(FileTypeManager.TOPIC, new FileBasedIndexFileTypeListener());

    connection.subscribe(FileDocumentManagerListener.TOPIC, new FileDocumentManagerListener() {
      @Override
      public void fileContentReloaded(@NotNull VirtualFile file, @NotNull Document document) {
        cleanupMemoryStorage(true);
      }

      @Override
      public void unsavedDocumentsDropped() {
        cleanupMemoryStorage(false);
      }
    });

    connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appWillBeClosed(boolean isRestart) {
        if (myRegisteredIndexes != null && !myRegisteredIndexes.areIndexesReady()) {
          new Task.Modal(null, IndexingBundle.message("indexes.preparing.to.shutdown.message"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              myRegisteredIndexes.waitUntilAllIndicesAreInitialized();
            }
          }.queue();
        }
      }
    });

    myConnection = connection;

    FileBasedIndexExtension.EXTENSION_POINT_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionRemoved(@NotNull FileBasedIndexExtension<?, ?> extension, @NotNull PluginDescriptor pluginDescriptor) {
        ID.unloadId(extension.getName());
      }
    }, null);

    myIndexableFilesFilterHolder = new IncrementalProjectIndexableFilesFilterHolder();

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      StorageDiagnosticData.startPeriodicDumping();
    }
  }

  void scheduleFullIndexesRescan(@NotNull String reason) {
    cleanupProcessedFlag(reason);
    scheduleIndexRescanningForAllProjects(reason);
  }

  @VisibleForTesting
  void doClearIndices(@NotNull Predicate<? super ID<?, ?>> filter) {
    try {
      waitUntilIndicesAreInitialized();
    }
    catch (ProcessCanceledException e) {
      // will be rebuilt on re-scan
      return;
    }
    IndexingStamp.flushCaches();

    List<ID<?, ?>> clearedIndexes = new ArrayList<>();
    List<ID<?, ?>> survivedIndexes = new ArrayList<>();
    for (ID<?, ?> indexId : getState().getIndexIDs()) {
      if (filter.test(indexId)) {
        try {
          clearIndex(indexId);
        }
        catch (StorageException e) {
          LOG.info(e);
        }
        catch (Exception e) {
          LOG.error(e);
        }
        clearedIndexes.add(indexId);
      }
      else {
        survivedIndexes.add(indexId);
      }
    }

    LOG.info("indexes cleared: " + clearedIndexes.stream().map(id -> id.getName()).collect(Collectors.joining(", ")) + "\n" +
             "survived indexes: " + survivedIndexes.stream().map(id -> id.getName()).collect(Collectors.joining(", ")));
  }

  @Override
  public void registerProjectFileSets(@NotNull Project project) {
    registerIndexableSet(new IndexableFileSet() {
      @Override
      public boolean isInSet(@NotNull VirtualFile file) {
        return IndexableFilesIndex.getInstance(project).shouldBeIndexed(file);
      }
    }, project);
  }

  @Override
  public void removeProjectFileSets(@NotNull Project project) {
    myIndexableSets.removeIf(p -> p.second.equals(project));
  }

  boolean processChangedFiles(@NotNull Project project, @NotNull Processor<? super VirtualFile> processor) {
    // can be performance critical, better to use cycle instead of streams
    // avoid missing files when events are processed concurrently
    Iterator<VirtualFile> iterator = Iterators.concat(
      getChangedFilesCollector().getEventMerger().getChangedFiles(),
      getChangedFilesCollector().getFilesToUpdate()
    );

    HashSet<VirtualFile> checkedFiles = new HashSet<>();
    Predicate<VirtualFile> filterPredicate = filesToBeIndexedForProjectCondition(project);

    while (iterator.hasNext()) {
      VirtualFile virtualFile = iterator.next();
      if (filterPredicate.test(virtualFile) && !checkedFiles.contains(virtualFile)) {
        checkedFiles.add(virtualFile);
        if (!processor.process(virtualFile)) return false;
      }
    }

    return true;
  }

  public RegisteredIndexes getRegisteredIndexes() {
    return myRegisteredIndexes;
  }

  void setUpShutDownTask() {
    myShutDownTask = new MyShutDownTask(true);
    ShutDownTracker.getInstance().registerCacheShutdownTask(myShutDownTask);
  }

  void addStaleIds(@NotNull IntSet staleIds) {
    synchronized (myStaleIds) {
      myStaleIds.addAll(staleIds);
    }
  }

  void setUpHealthCheck() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      myHealthCheckFuture = AppExecutorUtil
        .getAppScheduledExecutorService()
        .scheduleWithFixedDelay(ConcurrencyUtil.underThreadNameRunnable("Index Healthcheck", () -> {
          myIndexableFilesFilterHolder.runHealthCheck();
        }), 5, 5, MINUTES);
    }
  }

  @Override
  public void requestRebuild(@NotNull final ID<?, ?> indexId, final @NotNull Throwable throwable) {
    IndexStatisticGroup.reportIndexRebuild(indexId, throwable, false);
    LOG.info("Requesting index rebuild for: " + indexId.getName(), throwable);
    if (FileBasedIndexScanUtil.isManuallyManaged(indexId)) {
      advanceIndexVersion(indexId);
    }
    else if (!myRegisteredIndexes.isExtensionsDataLoaded()) {
      IndexDataInitializer.submitGenesisTask(coroutineScope, () -> {
        // should be always true here since the genesis pool is sequential
        waitUntilIndicesAreInitialized();
        doRequestRebuild(indexId, throwable);
        return null;
      });
    }
    else {
      doRequestRebuild(indexId, throwable);
    }
  }

  public static boolean isProjectOrWorkspaceFile(@NotNull VirtualFile file, @Nullable FileType fileType) {
    return ProjectCoreUtil.isProjectOrWorkspaceFile(file, fileType);
  }

  @Override
  public void requestReindex(@NotNull VirtualFile file) {
    requestReindex(file, true);
  }

  @ApiStatus.Internal
  public void requestReindex(@NotNull VirtualFile file, boolean forceRebuild) {
    GistManager.getInstance().invalidateData(file);
    VfsEventsMerger.tryLog("explicit_request_reindex", file);
    // todo: this is the same vfs event handling sequence that is produces after events of FileContentUtilCore.reparseFiles
    // but it is more costly than current code, see IDEA-192192
    //myChangedFilesCollector.invalidateIndicesRecursively(file, false);
    //myChangedFilesCollector.buildIndicesForFileRecursively(file, false);
    ChangedFilesCollector changedFilesCollector = getChangedFilesCollector();
    if (forceRebuild) {
      file.putUserData(IndexingDataKeys.REBUILD_REQUESTED, Boolean.TRUE);
      IndexingFlag.cleanProcessedFlagRecursively(file);
    }
    changedFilesCollector.scheduleForIndexingRecursively(file, true);
    if (myRegisteredIndexes.isInitialized()) {
      changedFilesCollector.ensureUpToDateAsync();
    }
  }

  @Override
  public synchronized void loadIndexes() {
    if (myRegisteredIndexes == null) {
      super.loadIndexes();

      LOG.assertTrue(myRegisteredIndexes == null);
      myStorageBufferingHandler.resetState();
      myRegisteredIndexes = new RegisteredIndexes(myFileDocumentManager, this);
      myShutdownReason = null;

      // capture VFS creation time. It will be used to identify VFS epoch for dirty files queue.
      // at the moment when we write the queue, VFS might have already been disposed via shutdown hook (in the case on emergency shutdown)
      vfsCreationStamp = ManagingFS.getInstance().getCreationTimestamp();
    }
  }

  @Override
  public void resetHints() {
    myRegisteredIndexes.resetHints();
  }

  @Override
  public void waitUntilIndicesAreInitialized() {
    if (myRegisteredIndexes == null) {
      // interrupt all calculation while plugin reload
      throw new ServiceNotReadyException();
    }
    myRegisteredIndexes.waitUntilIndicesAreInitialized();
  }

  static <K, V> void registerIndexer(@NotNull final FileBasedIndexExtension<K, V> extension,
                                     @NotNull IndexConfiguration state,
                                     @NotNull IndexVersionRegistrationSink versionRegistrationStatusSink,
                                     @NotNull IntSet staleInputIdSink,
                                     @NotNull IntSet dirtyFiles) throws Exception {
    ID<K, V> name = extension.getName();
    int version = getIndexExtensionVersion(extension);

    IndexVersion.IndexVersionDiff diff = IndexVersion.versionDiffers(name, version);
    versionRegistrationStatusSink.setIndexVersionDiff(name, diff);
    if (diff != IndexVersion.IndexVersionDiff.UP_TO_DATE) {
      deleteIndexFiles(extension);
      IndexVersion.rewriteVersion(name, version);

      try {
        for (FileBasedIndexInfrastructureExtension ex : FileBasedIndexInfrastructureExtension.EP_NAME.getExtensionList()) {
          ex.onFileBasedIndexVersionChanged(name);
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    initIndexStorage(extension, version, state, versionRegistrationStatusSink, staleInputIdSink, dirtyFiles);
  }

  private static <K, V> void deleteIndexFiles(@NotNull final FileBasedIndexExtension<K, V> extension) throws IOException {
    ID<K, V> name = extension.getName();
    var persistentIndexRootDir = IndexInfrastructure.getPersistentIndexRootDir(name);
    if (Files.exists(persistentIndexRootDir)) {
      if (!FileUtil.deleteWithRenaming(persistentIndexRootDir)) {
        LOG.warn("failed to delete persistent index files at " + persistentIndexRootDir);
      }
    }
    var indexRootDir = IndexInfrastructure.getIndexRootDir(name);
    if (Files.exists(indexRootDir)) {
      if (!FileUtil.deleteWithRenaming(indexRootDir)) {
        LOG.warn("failed to delete index files at " + indexRootDir);
      }
    }
  }

  private static <K, V> void initIndexStorage(@NotNull FileBasedIndexExtension<K, V> extension,
                                              int version,
                                              @NotNull IndexConfiguration state,
                                              @NotNull IndexVersionRegistrationSink registrationStatusSink,
                                              @NotNull IntSet staleInputIdSink,
                                              @NotNull IntSet dirtyFiles)
    throws Exception {
    ID<K, V> name = extension.getName();
    InputFilter inputFilter = extension.getInputFilter();

    UpdatableIndex<K, V, FileContent, ?> index = null;

    int attemptCount = 2;
    for (int attempt = 0; attempt < attemptCount; attempt++) {
      try {
        VfsAwareIndexStorageLayout<K, V> layout = DefaultIndexStorageLayout.getLayout(extension);
        index = createIndex(extension, layout);

        for (FileBasedIndexInfrastructureExtension infrastructureExtension : FileBasedIndexInfrastructureExtension.EP_NAME.getExtensionList()) {
          UpdatableIndex<K, V, FileContent, ?> intermediateIndex = infrastructureExtension.combineIndex(extension, index);
          if (intermediateIndex != null) {
            index = intermediateIndex;
          }
        }

        state.registerIndex(name,
                            index,
                            inputFilter,
                            version + GlobalIndexFilter.getFiltersVersion(name));
        break;
      }
      catch (Exception e) {
        boolean lastAttempt = attempt == attemptCount - 1;

        try {
          VfsAwareIndexStorageLayout<K, V> layout = DefaultIndexStorageLayout.getLayout(extension);
          layout.clearIndexData();
        }
        catch (Exception layoutEx) {
          LOG.error(layoutEx);
        }

        for (FileBasedIndexInfrastructureExtension ext : FileBasedIndexInfrastructureExtension.EP_NAME.getExtensionList()) {
          try {
            ext.resetPersistentState(name);
          }
          catch (Exception extEx) {
            LOG.error(extEx);
          }
        }

        registrationStatusSink.setIndexVersionDiff(name, new IndexVersion.IndexVersionDiff.CorruptedRebuild(version));
        IndexVersion.rewriteVersion(name, version);
        IndexStatisticGroup.reportIndexRebuild(name, e, true);

        if (lastAttempt) {
          state.registerIndexInitializationProblem(name, e);
          if (extension instanceof CustomImplementationFileBasedIndexExtension) {
            ((CustomImplementationFileBasedIndexExtension<?, ?>)extension).handleInitializationError(e);
          }
          throw e;
        }
        else if (ApplicationManager.getApplication().isUnitTestMode()) {
          LOG.error(e);
        }
        else {
          String message = "Attempt #" + attemptCount + " to initialize index has failed for " + extension.getName();
          //noinspection InstanceofCatchParameter
          if (e instanceof CorruptedException) {
            LOG.warn(message + " because storage corrupted");
          }
          else {
            LOG.warn(message, e);
          }
        }
      }
    }

    try {
      if (StubUpdatingIndex.INDEX_ID.equals(extension.getName()) && index != null) {
        staleInputIdSink.addAll(StaleIndexesChecker.checkIndexForStaleRecords(index, dirtyFiles, true));
      }
    }
    catch (Exception e) {
      LOG.error("Exception while checking for stale records", e);
    }
  }

  @NotNull
  private static <K, V> UpdatableIndex<K, V, FileContent, ?> createIndex(@NotNull FileBasedIndexExtension<K, V> extension,
                                                                         @NotNull VfsAwareIndexStorageLayout<K, V> layout)
    throws StorageException, IOException {
    if (FileBasedIndexExtension.USE_VFS_FOR_FILENAME_INDEX && extension.getName() == FilenameIndex.NAME) {
      return new EmptyIndex<>(extension);
    }
    else if (extension instanceof CustomImplementationFileBasedIndexExtension) {
      @SuppressWarnings("unchecked") UpdatableIndex<K, V, FileContent, ?> index =
        ((CustomImplementationFileBasedIndexExtension<K, V>)extension).createIndexImplementation(extension, layout);
      return index;
    }
    else {
      return TransientFileContentIndex.createIndex(extension, layout);
    }
  }

  void performShutdown(boolean keepConnection, @NotNull String reason) {
    myShutdownReason = keepConnection ? reason : null;
    RegisteredIndexes registeredIndexes = myRegisteredIndexes;
    if (registeredIndexes == null || !registeredIndexes.performShutdown()) {
      return; // already shut down
    }

    ProgressManager.getInstance().executeNonCancelableSection(() -> {
      registeredIndexes.waitUntilAllIndicesAreInitialized();
    });
    try {
      if (myShutDownTask != null) {
        ShutDownTracker.getInstance().unregisterShutdownTask(myShutDownTask);
      }
      if (myFlushingTask != null) {
        try {
          myFlushingTask.close();
        }
        catch (Exception e) {
          LOG.error("Error cancelling flushing task", e);
        }
        myFlushingTask = null;
      }
      if (myHealthCheckFuture != null) {
        myHealthCheckFuture.cancel(false);
        myHealthCheckFuture = null;
      }
    }
    finally {
      long ms = System.currentTimeMillis();
      LOG.info("Index dispose started");
      try {
        PersistentIndicesConfiguration.saveConfiguration();

        IntSet dirtyFileIds = new IntOpenHashSet();
        for (VirtualFile file : getChangedFilesCollector().getAllPossibleFilesToUpdate()) {
          PingProgress.interactWithEdtProgress();
          int fileId = getFileId(file);
          dirtyFileIds.add(fileId);
        }
        synchronized (myStaleIds) {
          dirtyFileIds.addAll(myStaleIds);
          myStaleIds.clear();
        }
        PersistentDirtyFilesQueue.storeIndexingQueue(PersistentDirtyFilesQueue.getQueueFile(), dirtyFileIds, vfsCreationStamp);
        getChangedFilesCollector().clearFilesToUpdate();
        vfsCreationStamp = 0;

        // TODO-ank: Should we catch and ignore CancellationException here to allow other lines to execute?
        IndexingStamp.close();
        IndexingFlag.unlockAllFiles(); // TODO-ank: IndexingFlag should also be closed, because indexes might be cleared (IDEA-336540)
        // TODO-ank: review all the remaining usages of fast file attributes (IDEA-336540)

        if (myIsUnitTestMode) {
          UpdatableIndex<Integer, SerializedStubTree, FileContent, ?> index = getState().getIndex(StubUpdatingIndex.INDEX_ID);
          if (index != null) {
            StaleIndexesChecker.checkIndexForStaleRecords(index, dirtyFileIds, false);
          }
        }

        List<ThrowableRunnable<?>> indexDisposeTasks = new ArrayList<>();
        IndexConfiguration state = getState();
        for (ID<?, ?> indexId : state.getIndexIDs()) {
          PingProgress.interactWithEdtProgress();
          indexDisposeTasks.add(() -> {
            try {
              UpdatableIndex<?, ?, FileContent, ?> index = getIndex(indexId);
              if (!RebuildStatus.isOk(indexId)) {
                clearIndex(indexId); // if the index was scheduled for rebuild, only clean it
              }
              index.dispose();
            }
            catch (Throwable throwable) {
              LOG.info("Problem disposing " + indexId, throwable);
            }
          });
        }

        IndexDataInitializer.runParallelTasks(indexDisposeTasks, false);
        FileBasedIndexInfrastructureExtension.EP_NAME.getExtensionList().forEach(ex -> ex.shutdown());
        if (!keepConnection) {
          myConnection.disconnect();
        }
        //CorruptionMarker.markIndexesAsClosed();
      }
      catch (Throwable e) {
        LOG.error("Problems during index shutdown", e);
      }
      finally {
        IndexVersion.clearCachedIndexVersions();
      }
      LOG.info("Index dispose completed in " + (System.currentTimeMillis() - ms) + "ms.");
    }
  }

  public void removeDataFromIndicesForFile(int fileId, @NotNull VirtualFile file, @NotNull String cause) {
    VfsEventsMerger.tryLog("REMOVE", file, () -> {
      return "cause=" + cause;
    });

    VirtualFile originalFile = file instanceof DeletedVirtualFileStub ? ((DeletedVirtualFileStub)file).getOriginalFile() : file;
    final List<ID<?, ?>> states = IndexingStamp.getNontrivialFileIndexedStates(fileId);

    cleanProcessingFlag(fileId);
    if (!states.isEmpty()) {
      ProgressManager.getInstance().executeNonCancelableSection(() -> removeFileDataFromIndices(states, fileId, originalFile));
    }
    boolean isValid =
      file instanceof DeletedVirtualFileStub ? ((DeletedVirtualFileStub)file).isOriginalValid() : file.isValid();
    if (!isValid) {
      getIndexableFilesFilterHolder().removeFile(fileId);
    }
  }

  public void removeFileDataFromIndices(@NotNull Collection<? extends ID<?, ?>> indexIds, int fileId, @Nullable VirtualFile file) {
    assert ProgressManager.getInstance().isInNonCancelableSection();
    try {
      // document diff can depend on the previous value that will be removed
      removeTransientFileDataFromIndices(indexIds, fileId, file);
      cleanProcessingFlag(fileId);
      Throwable unexpectedError = null;
      for (ID<?, ?> indexId : indexIds) {
        try {
          removeSingleIndexValue(indexId, fileId);
        }
        catch (Throwable e) {
          LOG.info(e);
          if (unexpectedError == null) {
            unexpectedError = e;
          }
        }
      }

      if (unexpectedError != null) {
        LOG.error(unexpectedError);
      }
    }
    finally {
      IndexingStamp.flushCache(fileId);
    }
  }

  private void removeTransientFileDataFromIndices(@NotNull Collection<? extends ID<?, ?>> indices,
                                                  int inputId,
                                                  @Nullable VirtualFile file) {
    for (ID<?, ?> indexId : indices) {
      getIndex(indexId).removeTransientDataForFile(inputId);
    }

    Document document = file == null ? null : myFileDocumentManager.getCachedDocument(file);
    if (document != null) {
      myLastIndexedDocStamps.clearForDocument(document);
      document.putUserData(ourFileContentKey, null);
    }

    clearUpToDateIndexesForUnsavedOrTransactedDocs();
  }

  private final ThreadLocal<Boolean> myReentrancyGuard = ThreadLocal.withInitial(() -> Boolean.FALSE);

  @Override
  public <K> boolean ensureUpToDate(@NotNull final ID<K, ?> indexId,
                                    @Nullable Project project,
                                    @Nullable GlobalSearchScope filter,
                                    @Nullable VirtualFile restrictedFile) {
    String shutdownReason = myShutdownReason;
    if (shutdownReason != null) {
      LOG.info("FileBasedIndex is currently shutdown because: " + shutdownReason);
      return false;
    }
    if (FORBID_LOOKUP_IN_NON_CANCELLABLE_SECTIONS && ProgressManager.getInstance().isInNonCancelableSection()) {
      LOG.error("Indexes should not be accessed in non-cancellable section");
    }

    ProgressManager.checkCanceled();
    SlowOperations.assertSlowOperationsAreAllowed();
    getChangedFilesCollector().ensureUpToDate();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    NoAccessDuringPsiEvents.checkCallContext(indexId);

    if (!needsFileContentLoading(indexId)) {
      return true; //indexed eagerly in foreground while building an unindexed file list
    }
    if (filter == GlobalSearchScope.EMPTY_SCOPE ||
        filter instanceof DelegatingGlobalSearchScope && ((DelegatingGlobalSearchScope)filter).unwrap() == GlobalSearchScope.EMPTY_SCOPE) {
      return false;
    }
    if (project == null) {
      LOG.warn("Please provide a GlobalSearchScope with specified project. Otherwise it might lead to performance problems!",
               new Exception());
    }
    if (project != null && project.isDefault()) {
      LOG.error("FileBasedIndex should not receive default project");
    }
    if (FileBasedIndexScanUtil.isManuallyManaged(indexId)) {
      return true;
    }
    if (ActionUtil.isDumbMode(project) && getCurrentDumbModeAccessType_NoDumbChecks() == null) {
      handleDumbMode(project);
    }

    if (myReentrancyGuard.get().booleanValue()) {
      //assert false : "ensureUpToDate() is not reentrant!";
      return true;
    }
    myReentrancyGuard.set(Boolean.TRUE);

    try {
      if (IndexUpToDateCheckIn.isUpToDateCheckEnabled()) {
        try {
          if (!RebuildStatus.isOk(indexId)) {
            if (getCurrentDumbModeAccessType_NoDumbChecks() == null) {
              throw new ServiceNotReadyException("index " + indexId + " has status " + RebuildStatus.getStatus(indexId));
            }
            return false;
          }
          if (!ActionUtil.isDumbMode(project) || getCurrentDumbModeAccessType_NoDumbChecks() == null) {
            forceUpdate(project, filter, restrictedFile);
          }
          indexUnsavedDocuments(indexId, project, filter, restrictedFile);
        }
        catch (RuntimeException e) {
          final Throwable cause = e.getCause();
          if (cause instanceof StorageException || cause instanceof IOException) {
            requestRebuild(indexId, e);
          }
          else {
            throw e;
          }
        }
      }
    }
    finally {
      myReentrancyGuard.set(Boolean.FALSE);
    }
    return true;
  }

  @Override
  public <K> boolean processAllKeys(@NotNull ID<K, ?> indexId,
                                    @NotNull Processor<? super K> processor,
                                    @NotNull GlobalSearchScope scope,
                                    @Nullable IdFilter idFilter) {
    Boolean scanResult = FileBasedIndexScanUtil.processAllKeys(indexId, processor, scope, idFilter);
    if (scanResult != null) return scanResult;
    return super.processAllKeys(indexId, processor, scope, idFilter);
  }

  private static void handleDumbMode(@Nullable Project project) throws IndexNotReadyException {
    ProgressManager.checkCanceled();
    throw IndexNotReadyException.create(project == null ? null : DumbServiceImpl.getInstance(project).getDumbModeStartTrace());
  }


  @TestOnly
  public void cleanupForNextTest() {
    getChangedFilesCollector().ensureUpToDate();

    myTransactionMap = SmartFMap.emptyMap();
    for (ID<?, ?> indexId : getState().getIndexIDs()) {
      final UpdatableIndex<?, ?, FileContent, ?> index = getIndex(indexId);
      index.cleanupForNextTest();
    }
  }

  @ApiStatus.Internal
  public ChangedFilesCollector getChangedFilesCollector() {
    return myChangedFilesCollector.getValue();
  }

  void filesUpdateStarted(Project project, boolean isFullUpdate) {
    if (isFullUpdate) {
      myIndexableFilesFilterHolder.entireProjectUpdateStarted(project);
    }
    ensureStaleIdsDeleted();
    getChangedFilesCollector().ensureUpToDate();
  }

  void ensureStaleIdsDeleted() {
    loadIndexes();
    waitUntilIndicesAreInitialized();
    synchronized (myStaleIds) {
      if (myStaleIds.isEmpty()) return;
      try {
        StaleIndexesChecker.clearStaleIndexes(myStaleIds);
      }
      catch (Exception e) {
        LOG.error(e);
      }
      finally {
        myStaleIds.clear();
      }
    }
  }

  void filesUpdateFinished(@NotNull Project project) {
    myIndexableFilesFilterHolder.entireProjectUpdateFinished(project);
  }

  @Override
  @Nullable
  public IdFilter projectIndexableFiles(@Nullable Project project) {
    if (project == null || project.isDefault()) return null;
    return myIndexableFilesFilterHolder.getProjectIndexableFiles(project);
  }

  public @NotNull ProjectIndexableFilesFilterHolder getIndexableFilesFilterHolder() {
    return myIndexableFilesFilterHolder;
  }

  private static void scheduleIndexRescanningForAllProjects(@NotNull String reason) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      new UnindexedFilesScanner(project, reason).queue();
    }
  }

  void clearIndicesIfNecessary() {
    waitUntilIndicesAreInitialized();
    for (ID<?, ?> indexId : getState().getIndexIDs()) {
      try {
        RebuildStatus.clearIndexIfNecessary(indexId, () -> clearIndex(indexId));
      }
      catch (StorageException e) {
        LOG.error(e);
        requestRebuild(indexId, e);
      }
    }
  }

  void clearIndex(@NotNull ID<?, ?> indexId) throws StorageException {
    if (IOUtil.isSharedCachesEnabled()) {
      IOUtil.OVERRIDE_BYTE_BUFFERS_USE_NATIVE_BYTE_ORDER_PROP.set(false);
    }
    try {
      advanceIndexVersion(indexId);
      getIndex(indexId).clear();
    }
    finally {
      IOUtil.OVERRIDE_BYTE_BUFFERS_USE_NATIVE_BYTE_ORDER_PROP.remove();
    }
  }

  private void advanceIndexVersion(ID<?, ?> indexId) {
    try {
      IndexVersion.rewriteVersion(indexId, myRegisteredIndexes.getState().getIndexVersion(indexId));
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @NotNull
  private Set<Document> getTransactedDocuments() {
    return myTransactionMap.keySet();
  }

  private void indexUnsavedDocuments(@NotNull final ID<?, ?> indexId,
                                     @Nullable("All projects") Project project,
                                     @Nullable GlobalSearchScope filter,
                                     @Nullable VirtualFile restrictedFile) {
    if (myUpToDateIndicesForUnsavedOrTransactedDocuments.contains(indexId)) {
      return; // no need to index unsaved docs        // todo: we only index files for a project, but this service is app-wide
    }

    Document[] unsavedDocuments = myFileDocumentManager.getUnsavedDocuments();
    Set<Document> transactedDocuments = getTransactedDocuments();
    Document[] uncommittedDocuments = project == null ? Document.EMPTY_ARRAY :
                                      PsiDocumentManager.getInstance(project).getUncommittedDocuments();

    if (unsavedDocuments.length == 0 && uncommittedDocuments.length == 0 && transactedDocuments.isEmpty()) return;

    final Set<Document> documents = new HashSet<>();
    Collections.addAll(documents, unsavedDocuments);
    documents.addAll(transactedDocuments);
    Collections.addAll(documents, uncommittedDocuments);

    LOG.assertTrue(project == null || filter == null || filter.getProject() == null || project.equals(filter.getProject()),
                   "filter should filter files in provided project. ref: 50cf572587cf");
    Collection<Document> documentsToProcessForProject = project == null ? documents :
                                                        ContainerUtil.filter(documents,
                                                                             document -> belongsToScope(
                                                                               myFileDocumentManager.getFile(document), restrictedFile,
                                                                               GlobalSearchScope.everythingScope(project)));

    if (!documentsToProcessForProject.isEmpty()) {
      UpdateTask<Document> task = myRegisteredIndexes.getUnsavedDataUpdateTask(indexId);
      assert task != null : "Task for unsaved data indexing was not initialized for index " + indexId;

      if (myStorageBufferingHandler.runUpdate(true, () -> task.processAll(documentsToProcessForProject, project)) &&
          documentsToProcessForProject.size() == documents.size() &&
          !hasActiveTransactions()
      ) {
        myUpToDateIndicesForUnsavedOrTransactedDocuments.add(indexId);
      }
    }
  }

  private boolean hasActiveTransactions() {
    return !myTransactionMap.isEmpty();
  }


  private static final Key<WeakReference<Pair<FileContentImpl, Long>>> ourFileContentKey = Key.create("unsaved.document.index.content");

  // returns false if doc was not indexed because it is already up-to-date
  // return true if the document was indexed
  // caller is responsible to ensure no concurrent same document processing
  void indexUnsavedDocument(@NotNull final Document document,
                            @NotNull final ID<?, ?> requestedIndexId,
                            @NotNull Project project,
                            @NotNull final VirtualFile vFile) {
    PsiFile dominantContentFile = findLatestKnownPsiForUncomittedDocument(document, project);

    DocumentContent content = findLatestContent(document, dominantContentFile);

    long currentDocStamp = PsiDocumentManager.getInstance(project).getLastCommittedStamp(document);

    long previousDocStamp = myLastIndexedDocStamps.get(document, requestedIndexId);
    if (previousDocStamp == currentDocStamp) return;

    final CharSequence contentText = content.getText();
    FileTypeManagerEx.getInstanceEx().freezeFileTypeTemporarilyIn(vFile, () -> {
      IndexedFileImpl indexedFile = new IndexedFileImpl(vFile, project);
      if (getRequiredIndexes(indexedFile).contains(requestedIndexId)) {
        int inputId = getFileId(vFile);

        if (!isTooLarge(vFile, (long)contentText.length())) {
          FileContentImpl newFc = getUnsavedDocContent(document, project, vFile, currentDocStamp, contentText);
          tuneFileContent(document, dominantContentFile, content, newFc);

          markFileIndexed(vFile, newFc);
          try {
            updateIndexInNonCancellableSection(requestedIndexId, inputId, newFc);
          }
          finally {
            unmarkBeingIndexed();
            cleanFileContent(newFc, dominantContentFile);
          }
        }
        else { // effectively wipe the data from the indices
          updateIndexInNonCancellableSection(requestedIndexId, inputId, null);
        }
      }

      long previousState = myLastIndexedDocStamps.set(document, requestedIndexId, currentDocStamp);
      assert previousState == previousDocStamp;
    });
  }

  @ApiStatus.Internal
  @NotNull
  public static DocumentContent findLatestContent(@NotNull Document document, @Nullable PsiFile dominantContentFile) {
    return dominantContentFile != null && dominantContentFile.getViewProvider().getModificationStamp() > document.getModificationStamp()
           ? new PsiContent(document, dominantContentFile)
           : new AuthenticContent(document);
  }

  private void updateIndexInNonCancellableSection(@NotNull ID<?, ?> requestedIndexId, int inputId, FileContentImpl newFc) {
    Computable<Boolean> update = getIndex(requestedIndexId).mapInputAndPrepareUpdate(inputId, newFc);
    ProgressManager.getInstance().executeNonCancelableSection(update::compute);
  }

  private static void tuneFileContent(@NotNull Document document,
                                      PsiFile dominantContentFile,
                                      DocumentContent content,
                                      FileContentImpl newFc) {
    initFileContent(newFc, dominantContentFile);
    newFc.ensureThreadSafeLighterAST();
    if (content instanceof AuthenticContent) {
      newFc.putUserData(PlatformIdTableBuilding.EDITOR_HIGHLIGHTER,
                        EditorHighlighterCache.getEditorHighlighterForCachesBuilding(document));
    }
  }

  private static @NotNull FileContentImpl getUnsavedDocContent(@NotNull Document document,
                                                               @NotNull Project project,
                                                               @NotNull VirtualFile vFile,
                                                               long currentDocStamp,
                                                               @NotNull CharSequence contentText) {
    // Reasonably attempt to use the same file content when calculating indices
    // as we can evaluate them several at once and store in file content
    WeakReference<Pair<FileContentImpl, Long>> previousContentAndStampRef = document.getUserData(ourFileContentKey);
    Pair<FileContentImpl, Long> previousContentAndStamp = SoftReference.dereference(previousContentAndStampRef);

    if (previousContentAndStamp != null && currentDocStamp == previousContentAndStamp.getSecond()) {
      FileContentImpl existingFC = previousContentAndStamp.getFirst();
      if (project.equals(existingFC.getProject())) {
        return existingFC;
      }
    }

    FileContentImpl newFc = (FileContentImpl)FileContentImpl.createByText(vFile, contentText, project);
    document.putUserData(ourFileContentKey, new WeakReference<>(Pair.create(newFc, currentDocStamp)));
    return newFc;
  }

  @Override
  public @NotNull <K, V> Collection<VirtualFile> getContainingFiles(@NotNull ID<K, V> indexId,
                                                                    @NotNull K dataKey,
                                                                    @NotNull GlobalSearchScope filter) {
    Collection<VirtualFile> scanResult = FileBasedIndexScanUtil.getContainingFiles(indexId, dataKey, filter);
    if (scanResult != null) return scanResult;
    return super.getContainingFiles(indexId, dataKey, filter);
  }

  @Override
  protected <K, V> boolean processValuesInOneFile(@NotNull ID<K, V> indexId,
                                                  @NotNull K dataKey,
                                                  @NotNull VirtualFile restrictToFile,
                                                  @NotNull GlobalSearchScope scope,
                                                  @NotNull ValueProcessor<? super V> processor) {
    Boolean scanResult = FileBasedIndexScanUtil.processValuesInOneFile(indexId, dataKey, restrictToFile, scope, processor);
    if (scanResult != null) return scanResult;
    return super.processValuesInOneFile(indexId, dataKey, restrictToFile, scope, processor);
  }

  @Override
  protected <K, V> boolean processValuesInScope(@NotNull ID<K, V> indexId,
                                                @NotNull K dataKey,
                                                boolean ensureValueProcessedOnce,
                                                @NotNull GlobalSearchScope scope,
                                                @Nullable IdFilter idFilter,
                                                @NotNull ValueProcessor<? super V> processor) {
    //There is (optional) alternative implementation for few indexes:
    Boolean scanResult =
      FileBasedIndexScanUtil.processValuesInScope(indexId, dataKey, ensureValueProcessedOnce, scope, idFilter, processor);
    if (scanResult != null) return scanResult;
    return super.processValuesInScope(indexId, dataKey, ensureValueProcessedOnce, scope, idFilter, processor);
  }

  @Override
  public <K, V> boolean processFilesContainingAllKeys(@NotNull ID<K, V> indexId,
                                                      @NotNull Collection<? extends K> dataKeys,
                                                      @NotNull GlobalSearchScope filter,
                                                      @Nullable Condition<? super V> valueChecker,
                                                      @NotNull Processor<? super VirtualFile> processor) {
    Boolean scanResult = FileBasedIndexScanUtil.processFilesContainingAllKeys(indexId, dataKeys, filter, valueChecker, processor);
    if (scanResult != null) return scanResult;
    return super.processFilesContainingAllKeys(indexId, dataKeys, filter, valueChecker, processor);
  }

  @Override
  public <K, V> boolean processFilesContainingAnyKey(@NotNull ID<K, V> indexId,
                                                     @NotNull Collection<? extends K> dataKeys,
                                                     @NotNull GlobalSearchScope filter,
                                                     @Nullable IdFilter idFilter,
                                                     @Nullable Condition<? super V> valueChecker,
                                                     @NotNull Processor<? super VirtualFile> processor) {
    IdFilter idFilterAdjusted = idFilter != null ? idFilter : extractIdFilter(filter, filter.getProject());
    Boolean scanResult =
      FileBasedIndexScanUtil.processFilesContainingAnyKey(indexId, dataKeys, filter, idFilterAdjusted, valueChecker, processor);
    if (scanResult != null) return scanResult;
    return super.processFilesContainingAnyKey(indexId, dataKeys, filter, idFilterAdjusted, valueChecker, processor);
  }

  @Override
  public boolean processFilesContainingAllKeys(@NotNull Collection<? extends AllKeysQuery<?, ?>> queries,
                                               @NotNull GlobalSearchScope filter,
                                               @NotNull Processor<? super VirtualFile> processor) {
    Boolean scanResult = FileBasedIndexScanUtil.processFilesContainingAllKeys(queries, filter, processor);
    if (scanResult != null) return scanResult;
    return super.processFilesContainingAllKeys(queries, filter, processor);
  }

  @Override
  public @Nullable VirtualFile findFileById(int id) {
    return PersistentFS.getInstance().findFileById(id);
  }

  @Override
  public @NotNull Logger getLogger() {
    return LOG;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @NotNull
  private <K, V> Map<K, V> getInMemoryData(@NotNull ID<K, V> id, @NotNull VirtualFile virtualFile, @NotNull Project project) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
    if (psiFile != null) {
      Map<ID, Map> indexValues = CachedValuesManager.getCachedValue(psiFile, () -> {
        try {
          FileContentImpl fc = psiFile instanceof PsiBinaryFile ? (FileContentImpl)FileContentImpl.createByFile(virtualFile, project)
                                                                : (FileContentImpl)FileContentImpl.createByText(virtualFile,
                                                                                                                psiFile.getViewProvider()
                                                                                                                  .getContents(), project);
          initFileContent(fc, psiFile);
          Map<ID, Map> result = FactoryMap.create(key -> getIndex(key).getExtension().getIndexer().map(fc));
          return CachedValueProvider.Result.createSingleDependency(result, psiFile);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
      return indexValues.get(id);
    }
    return Collections.emptyMap();
  }


  private final StorageBufferingHandler myStorageBufferingHandler = new StorageBufferingHandler() {
    @NotNull
    @Override
    protected Stream<UpdatableIndex<?, ?, ?, ?>> getIndexes() {
      IndexConfiguration state = getState();
      return state.getIndexIDs().stream().map(id -> getIndex(id));
    }
  };

  @ApiStatus.Internal
  @Override
  public void runCleanupAction(@NotNull Runnable cleanupAction) {
    Computable<Boolean> updateComputable = () -> {
      ProgressManager.getInstance().executeNonCancelableSection(cleanupAction);
      return true;
    };
    runUpdateForPersistentData(updateComputable);
    myStorageBufferingHandler.runUpdate(true, updateComputable);
  }

  public void cleanupMemoryStorage(boolean skipContentDependentIndexes) {
    myLastIndexedDocStamps.clear();
    if (myRegisteredIndexes == null) {
      // unsaved doc is dropped while plugin load/unload-ing
      return;
    }
    IndexConfiguration state = myRegisteredIndexes.getState();
    if (state == null) {
      // avoid waiting for the end of indices initialization (IDEA-173382)
      // in memory content will appear on indexing (in read action) and here is event dispatch (write context)
      return;
    }
    for (ID<?, ?> indexId : state.getIndexIDs()) {
      if (skipContentDependentIndexes && myRegisteredIndexes.isContentDependentIndex(indexId)) continue;
      UpdatableIndex<?, ?, FileContent, ?> index = getIndex(indexId);
      index.cleanupMemoryStorage();
    }
  }

  static final class MyShutDownTask implements Runnable {
    private final boolean myTermination;

    MyShutDownTask(boolean termination) { myTermination = termination; }

    @Override
    public void run() {
      Application app = ApplicationManager.getApplication();
      if (app == null) {
        return;
      }

      try {
        FileBasedIndex fileBasedIndex = app.getServiceIfCreated(FileBasedIndex.class);
        if (fileBasedIndex instanceof FileBasedIndexImpl fileBasedIndexImpl) {
          fileBasedIndexImpl.performShutdown(false, "IDE shutdown");
        }
      }
      finally {
        if (!myTermination && !app.isUnitTestMode()) {
          StorageDiagnosticData.dumpOnShutdown();
        }
      }
    }
  }

  private void doRequestRebuild(@NotNull ID<?, ?> indexId, Throwable throwable) {
    cleanupProcessedFlag("Rebuild requested for index " + indexId);
    if (!myRegisteredIndexes.isExtensionsDataLoaded()) reportUnexpectedAsyncInitState();

    if (RebuildStatus.requestRebuild(indexId)) {
      String message = "Rebuild requested for index " + indexId;
      Application app = ApplicationManager.getApplication();
      if (myIsUnitTestMode && app.isReadAccessAllowed() && !app.isDispatchThread()) {
        // Shouldn't happen in tests in general; so fail early with the exception that caused the index to be rebuilt.
        // Otherwise, reindexing will fail anyway later, but with a much more cryptic assertion
        LOG.error(message, throwable);
      }
      else {
        LOG.info(message, throwable);
      }

      cleanupProcessedFlag(message);

      if (!myRegisteredIndexes.isInitialized()) return;
      advanceIndexVersion(indexId);

      Runnable rebuildRunnable = () -> scheduleIndexRescanningForAllProjects(message);

      // we do invoke later since we can have read lock acquired
      AppUIExecutor.onWriteThread().later().expireWith(app).submit(rebuildRunnable);
    }
  }

  private static void reportUnexpectedAsyncInitState() {
    LOG.error("Unexpected async indices initialization problem");
  }

  @NotNull
  @Override
  public <K, V> UpdatableIndex<K, V, FileContent, ?> getIndex(ID<K, V> indexId) {
    UpdatableIndex<K, V, FileContent, ?> index = getState().getIndex(indexId);
    if (index == null) {
      Throwable initializationProblem = getState().getInitializationProblem(indexId);
      String message = "Index is not created for `" + indexId.getName() + "`";
      throw initializationProblem != null
            ? new IllegalStateException(message, initializationProblem)
            : new IllegalStateException(message);
    }
    return index;
  }

  @NotNull
  Collection<VirtualFile> getFilesToUpdate(final Project project) {
    return ContainerUtil.filter(getChangedFilesCollector().getAllFilesToUpdate(), filesToBeIndexedForProjectCondition(project)::test);
  }

  @NotNull
  private Predicate<VirtualFile> filesToBeIndexedForProjectCondition(Project project) {
    return virtualFile -> {
      if (!virtualFile.isValid()) {
        return true;
      }

      for (Pair<IndexableFileSet, Project> set : myIndexableSets) {
        final Project proj = set.second;
        if (proj != null && !proj.equals(project)) {
          continue; // skip this set as associated with a different project
        }
        if (ReadAction.compute(() -> set.first.isInSet(virtualFile))) {
          return true;
        }
      }
      return false;
    };
  }

  public boolean isFileUpToDate(VirtualFile file) {
    return file instanceof VirtualFileWithId && !getChangedFilesCollector().isScheduledForUpdate(file);
  }

  // caller is responsible to ensure no concurrent same document processing
  private void processRefreshedFile(@Nullable Project project, @NotNull final CachedFileContent fileContent,
                                    @NotNull FileIndexingStamp indexingStamp) {
    // ProcessCanceledException will cause re-adding the file to the processing list
    final VirtualFile file = fileContent.getVirtualFile();
    if (getChangedFilesCollector().isScheduledForUpdate(file)) {
      try {
        indexFileContent(project, fileContent, null, indexingStamp).apply(file, null, true);
      }
      finally {
        IndexingStamp.flushCache(getFileId(file));
        IndexingFlag.unlockFile(file);
      }
    }
  }

  private boolean isPendingDeletionFileAppearedInIndexableFilter(int fileId, @NotNull VirtualFile file) {
    if (file instanceof DeletedVirtualFileStub deletedFileStub) {
      if (deletedFileStub.isOriginalValid() &&
          ensureFileBelongsToIndexableFilter(fileId, deletedFileStub.getOriginalFile()) != FileAddStatus.SKIPPED) {
        return true;
      }
    }
    return false;
  }

  @ApiStatus.Internal
  @NotNull
  public FileIndexesValuesApplier indexFileContent(@Nullable Project project,
                                                   @NotNull CachedFileContent content,
                                                   @Nullable FileType cachedFileType,
                                                   @NotNull FileIndexingStamp indexingStamp) {
    ProgressManager.checkCanceled();
    VirtualFile file = content.getVirtualFile();
    final int fileId = getFileId(file);

    ApplicationMode applicationMode = getIndexApplicationMode();
    boolean isValid = file.isValid();
    // If the file was scheduled for update due to vfs events, then it is present in myFilesToUpdate.
    // In this case, we consider that current indexing (out of roots backed CacheUpdater) will cover its content
    if (file.isValid() && content.getTimeStamp() != file.getTimeStamp()) {
      content = new CachedFileContent(file);
    }

    if (isPendingDeletionFileAppearedInIndexableFilter(fileId, file)) {
      file = ((DeletedVirtualFileStub)file).getOriginalFile();
      assert file != null;
      content = new CachedFileContent(file);
      isValid = file.isValid();
      dropNontrivialIndexedStates(fileId);
      cachedFileType = file.getFileType();
    }

    FileIndexesValuesApplier applier;
    if (!isValid || isTooLarge(file)) {
      ProgressManager.checkCanceled();
      applier = new FileIndexesValuesApplier(this, fileId, file, indexingStamp, Collections.emptyList(), Collections.emptyList(),
                                             true, true, applicationMode,
                                             cachedFileType == null ? file.getFileType() : cachedFileType, false);
    }
    else {
      applier = doIndexFileContent(project, content, cachedFileType, applicationMode, indexingStamp);
    }
    return applier;
  }

  @NotNull
  private FileIndexesValuesApplier doIndexFileContent(@Nullable Project project,
                                                      @NotNull CachedFileContent content,
                                                      @Nullable FileType cachedFileType,
                                                      @NotNull ApplicationMode applicationMode,
                                                      FileIndexingStamp indexingStamp) {
    ProgressManager.checkCanceled();
    final VirtualFile file = content.getVirtualFile();
    Ref<Boolean> setIndexedStatus = Ref.create(Boolean.TRUE);
    Ref<FileType> fileTypeRef = Ref.create();

    //todo check file still from project
    int inputId = getFileId(file);
    Project guessedProject = project != null ? project : findProjectForFileId(inputId);
    IndexedFileImpl indexedFile = new IndexedFileImpl(file, guessedProject);

    List<SingleIndexValueApplier<?>> appliers = new ArrayList<>();
    List<SingleIndexValueRemover> removers = new ArrayList<>();

    FileTypeManagerEx fileTypeManagerEx = FileTypeManagerEx.getInstanceEx();
    if (fileTypeManagerEx instanceof FileTypeManagerImpl) {
      ((FileTypeManagerImpl)fileTypeManagerEx).freezeFileTypeTemporarilyWithProvidedValueIn(file, cachedFileType, () -> {
        ProgressManager.checkCanceled();
        FileContentImpl fc = null;

        Set<ID<?, ?>> currentIndexedStates = getAppliedIndexes(inputId);
        List<ID<?, ?>> requiredIndexes = getRequiredIndexes(indexedFile);
        for (ID<?, ?> indexId : requiredIndexes) {
          currentIndexedStates.remove(indexId);
          if (FileBasedIndexScanUtil.isManuallyManaged(indexId)) continue;
          ProgressManager.checkCanceled();

          if (fc == null) {
            fc = (FileContentImpl)FileContentImpl.createByContent(file, () -> content.getBytesOrEmpty(), guessedProject);
            fc.setSubstituteFileType(indexedFile.getFileType());
            ProgressManager.checkCanceled();

            fileTypeRef.set(fc.getFileType());

            ProgressManager.checkCanceled();
          }

          boolean update;
          boolean acceptedAndRequired = getIndexingState(fc, indexId).updateRequired();
          if (acceptedAndRequired) {
            update = RebuildStatus.isOk(indexId);
            if (!update) {
              setIndexedStatus.set(Boolean.FALSE);
            }
          }
          else {
            update = false;
          }

          if (!update && doTraceStubUpdates(indexId)) {
            String reason;
            if (acceptedAndRequired) {
              reason = "index is required to rebuild, and indexing does not update such";
            }
            else {
              reason = "update is not required";
            }

            LOG.info("index " + indexId + " should not be updated for " + fc.getFileName() + " because " + reason);
          }

          if (update) {
            ProgressManager.checkCanceled();
            SingleIndexValueApplier<?> singleIndexValueApplier = createSingleIndexValueApplier(indexId, file, inputId, fc);
            if (singleIndexValueApplier == null) {
              setIndexedStatus.set(Boolean.FALSE);
            }
            else {
              appliers.add(singleIndexValueApplier);
            }
          }
        }

        for (ID<?, ?> indexId : currentIndexedStates) {
          ProgressManager.checkCanceled();
          SingleIndexValueRemover remover = createSingleIndexRemover(indexId, file, fc, inputId, applicationMode);
          if (remover == null) {
            setIndexedStatus.set(Boolean.FALSE);
          }
          else {
            removers.add(remover);
          }
        }

        fileTypeRef.set(fc != null ? fc.getFileType() : file.getFileType());
      });
    }

    file.putUserData(IndexingDataKeys.REBUILD_REQUESTED, null);
    return new FileIndexesValuesApplier(this,
                                        inputId, file, indexingStamp, appliers, removers, false, setIndexedStatus.get(), applicationMode,
                                        fileTypeRef.get(), doTraceSharedIndexUpdates()
    );
  }

  @NotNull
  Set<ID<?, ?>> getAppliedIndexes(int inputId) {
    return new HashSet<>(IndexingStamp.getNontrivialFileIndexedStates(inputId));
  }

  @NotNull
  List<ID<?, ?>> getRequiredIndexes(@NotNull IndexedFile indexedFile) {
    if (!myRegisteredIndexes.isInitialized()) {
      // 1. early vfs event that needs invalidation
      // 2. pushers that do synchronous indexing for contentless indices
      waitUntilIndicesAreInitialized();
    }
    return myRegisteredIndexes.getRequiredIndexes(indexedFile);
  }

  private static void cleanFileContent(FileContentImpl fc, PsiFile psiFile) {
    if (fc == null) return;
    if (psiFile != null) psiFile.putUserData(PsiFileImpl.BUILDING_STUB, null);
    fc.putUserData(IndexingDataKeys.PSI_FILE, null);
  }

  private static void initFileContent(@NotNull FileContentImpl fc, PsiFile psiFile) {
    if (psiFile != null) {
      psiFile.putUserData(PsiFileImpl.BUILDING_STUB, true);
      fc.putUserData(IndexingDataKeys.PSI_FILE, psiFile);
    }
  }

  @NonNls
  static String getFileInfoLogString(int inputId, @Nullable VirtualFile file, @Nullable FileContent currentFC) {
    if (file == null && currentFC == null) {
      return String.valueOf(inputId);
    }
    String fileName = currentFC != null ? currentFC.getFileName() : file.getName();
    return fileName + "(id=" + inputId + ")";
  }

  void requestIndexRebuildOnException(RuntimeException exception, ID<?, ?> indexId) {
    Throwable causeToRebuildIndex = getCauseToRebuildIndex(exception);
    if (causeToRebuildIndex != null) {
      requestRebuild(indexId, causeToRebuildIndex);
    }
    else {
      throw exception;
    }
  }

  void updateSingleIndex(@NotNull ID<?, ?> indexId,
                         @NotNull VirtualFile file,
                         int inputId,
                         @NotNull FileContent currentFC) {
    SingleIndexValueApplier<?> applier = createSingleIndexValueApplier(indexId, file, inputId, currentFC);
    if (applier != null) {
      applier.apply();
    }
  }

  @ApiStatus.Internal
  @Nullable("null in case index update is not needed") <FileIndexMetaData> SingleIndexValueApplier<FileIndexMetaData> createSingleIndexValueApplier(
    @NotNull ID<?, ?> indexId,
    @NotNull VirtualFile file,
    int inputId,
    @NotNull FileContent currentFC) {
    if (doTraceStubUpdates(indexId)) {
      LOG.info("index " + indexId + " update requested for " + getFileInfoLogString(inputId, file, currentFC));
    }
    if (!myRegisteredIndexes.isExtensionsDataLoaded()) reportUnexpectedAsyncInitState();
    if (!RebuildStatus.isOk(indexId) && !myIsUnitTestMode) {
      return null; // the index is scheduled for rebuild, no need to update
    }
    increaseLocalModCount();

    //noinspection unchecked
    UpdatableIndex<?, ?, FileContent, FileIndexMetaData> index = (UpdatableIndex<?, ?, FileContent, FileIndexMetaData>)getIndex(indexId);

    ensureFileBelongsToIndexableFilter(inputId, file);

    if (currentFC instanceof FileContentImpl &&
        FileBasedIndex.ourSnapshotMappingsEnabled &&
        (((FileBasedIndexExtension<?, ?>)index.getExtension()).hasSnapshotMapping() ||
         ((FileBasedIndexExtension<?, ?>)index.getExtension()).canBeShared())) {
      // Optimization: initialize indexed file hash eagerly. The hash is calculated by raw content bytes.
      // If we pass the currentFC to an indexer that calls "FileContentImpl.getContentAsText",
      // the raw bytes will be converted to text and assigned to null.
      // Then, to compute the hash, the reverse conversion will be necessary.
      // To avoid this extra conversion, let's initialize the hash eagerly.
      IndexedHashesSupport.getOrInitIndexedHash((FileContentImpl)currentFC);
    }

    markFileIndexed(file, currentFC);
    try {
      Supplier<Boolean> storageUpdate;
      long evaluatingIndexValueApplierTime = System.nanoTime();
      FileIndexMetaData fileIndexMetaData = index.getFileIndexMetaData(currentFC);
      try {
        storageUpdate = index.mapInputAndPrepareUpdate(inputId, currentFC);
      }
      catch (MapReduceIndexMappingException e) {
        index.setIndexedStateForFile(inputId, currentFC, false);
        BrokenIndexingDiagnostics.INSTANCE.getExceptionListener().onFileIndexMappingFailed(
          inputId,
          currentFC.getFile(),
          currentFC.getFileType(),
          indexId,
          e
        );
        return null;
      }
      finally {
        evaluatingIndexValueApplierTime = System.nanoTime() - evaluatingIndexValueApplierTime;
      }

      return new SingleIndexValueApplier<>(
        this,
        indexId,
        inputId,
        fileIndexMetaData,
        storageUpdate,
        file,
        currentFC,
        evaluatingIndexValueApplierTime
      );
    }
    catch (RuntimeException exception) {
      requestIndexRebuildOnException(exception, indexId);
      return null;
    }
    finally {
      unmarkBeingIndexed();
    }
  }

  void increaseLocalModCount() {
    myLocalModCount.incrementAndGet();
  }

  private void removeSingleIndexValue(@NotNull ID<?, ?> indexId, int inputId) {
    SingleIndexValueRemover remover = createSingleIndexRemover(indexId, null, null, inputId, getIndexApplicationMode());
    if (remover != null) {
      remover.remove();
    }
  }

  @ApiStatus.Internal
  @Nullable("null in case index value removal is not necessary")
  SingleIndexValueRemover createSingleIndexRemover(@NotNull ID<?, ?> indexId,
                                                   @Nullable VirtualFile file,
                                                   @Nullable FileContent fileContent,
                                                   int inputId,
                                                   @NotNull ApplicationMode applicationMode) {
    if (doTraceStubUpdates(indexId)) {
      LOG.info("index " + indexId + " deletion requested for " + getFileInfoLogString(inputId, file, fileContent));
    }
    if (!myRegisteredIndexes.isExtensionsDataLoaded()) reportUnexpectedAsyncInitState();
    if (!RebuildStatus.isOk(indexId) && !myIsUnitTestMode) {
      return null; // the index is scheduled for rebuild, no need to update
    }
    return new SingleIndexValueRemover(this, indexId, file, fileContent, inputId, applicationMode);
  }

  boolean runUpdateForPersistentData(Supplier<Boolean> storageUpdate) {
    return myStorageBufferingHandler.runUpdate(false, () -> {
      return ProgressManager.getInstance().computeInNonCancelableSection(() -> storageUpdate.get());
    });
  }

  public static void markFileIndexed(@Nullable VirtualFile file,
                                     @Nullable FileContent fc) {
    // TODO restore original assertion
    if (fc != null && ourIndexedFile.get() != null) {
      throw new AssertionError("Reentrant indexing");
    }
    ourIndexedFile.set(file);
  }

  public static void unmarkBeingIndexed() {
    ourIndexedFile.remove();
  }

  @Override
  public VirtualFile getFileBeingCurrentlyIndexed() {
    return ourIndexedFile.get();
  }

  static void markFileWritingIndexes(int fileId) {
    if (/*filePath != null &&*/ ourWritingIndexFile.get() != null) {
      throw new AssertionError("Reentrant writing indices");
    }
    ourWritingIndexFile.set(new IndexWritingFile(fileId));
  }

  static void unmarkWritingIndexes() {
    ourWritingIndexFile.remove();
  }

  @Override
  public @Nullable IndexWritingFile getFileWritingCurrentlyIndexes() {
    return ourWritingIndexFile.get();
  }

  private final class VirtualFileUpdateTask extends UpdateTask<VirtualFile> {
    @Override
    void doProcess(VirtualFile item, Project project) {
      // snapshot at the beginning: if file changes while being processed, we can detect this on the following scanning
      IndexingRequestToken indexingRequest = project.getService(ProjectIndexingDependenciesService.class).getLatestIndexingRequestToken();
      var stamp = indexingRequest.getFileIndexingStamp(item);
      processRefreshedFile(project, new CachedFileContent(item), stamp);
    }
  }

  private final VirtualFileUpdateTask myForceUpdateTask = new VirtualFileUpdateTask();

  private void forceUpdate(@Nullable Project project, @Nullable final GlobalSearchScope filter, @Nullable final VirtualFile restrictedTo) {
    Collection<VirtualFile> allFilesToUpdate = getChangedFilesCollector().getAllFilesToUpdate();

    if (!allFilesToUpdate.isEmpty()) {
      boolean includeFilesFromOtherProjects = restrictedTo == null && project == null;
      List<VirtualFile> virtualFilesToBeUpdatedForProject = ContainerUtil.filter(
        allFilesToUpdate,
        new ProjectFilesCondition(projectIndexableFiles(project), filter, restrictedTo, includeFilesFromOtherProjects)
      );

      if (!virtualFilesToBeUpdatedForProject.isEmpty()) {
        myForceUpdateTask.processAll(virtualFilesToBeUpdatedForProject, project);
      }
    }
  }

  public boolean needsFileContentLoading(@NotNull ID<?, ?> indexId) {
    return myRegisteredIndexes.isContentDependentIndex(indexId);
  }

  public @NotNull Set<Project> getContainingProjects(@NotNull VirtualFile file) {
    Project project = ProjectCoreUtil.theOnlyOpenProject();
    if (project != null) {
      return belongsToIndexableFiles(file) ? Collections.singleton(project) : Collections.emptySet();
    }
    else {
      Set<Project> projects = null;
      for (Pair<IndexableFileSet, Project> set : myIndexableSets) {
        if ((projects == null || !projects.contains(set.second)) && set.first.isInSet(file)) {
          if (projects == null) {
            projects = new SmartHashSet<>();
          }
          projects.add(set.second);
        }
      }
      return ContainerUtil.notNullize(projects);
    }
  }

  public boolean belongsToProjectIndexableFiles(@NotNull VirtualFile file, @NotNull Project project) {
    return ContainerUtil.find(myIndexableSets, pair -> pair.second.equals(project) && pair.first.isInSet(file)) != null;
  }

  public boolean belongsToIndexableFiles(@NotNull VirtualFile file) {
    return ContainerUtil.find(myIndexableSets, pair -> pair.first.isInSet(file)) != null;
  }

  @ApiStatus.Internal
  public void dropNontrivialIndexedStates(int inputId) {
    for (ID<?, ?> id : IndexingStamp.getNontrivialFileIndexedStates(inputId)) {
      dropNontrivialIndexedStates(inputId, id);
    }
  }

  @ApiStatus.Internal
  public void dropNontrivialIndexedStates(int inputId, ID<?, ?> indexId) {
    UpdatableIndex<?, ?, FileContent, ?> index = getIndex(indexId);
    index.invalidateIndexedStateForFile(inputId);
  }

  public void doTransientStateChangeForFile(int fileId, @NotNull VirtualFile file) {
    clearUpToDateIndexesForUnsavedOrTransactedDocs();

    Document document = myFileDocumentManager.getCachedDocument(file);
    if (document != null && myFileDocumentManager.isDocumentUnsaved(document)) {   // will be reindexed in indexUnsavedDocuments
      myLastIndexedDocStamps.clearForDocument(document); // Q: non psi indices
      document.putUserData(ourFileContentKey, null);

      return;
    }

    Collection<ID<?, ?>> contentDependentIndexes = ContainerUtil.intersection(IndexingStamp.getNontrivialFileIndexedStates(fileId),
                                                                              myRegisteredIndexes.getRequiringContentIndices());
    removeTransientFileDataFromIndices(contentDependentIndexes, fileId, file);
    for (ID<?, ?> candidate : contentDependentIndexes) {
      getIndex(candidate).invalidateIndexedStateForFile(fileId);
    }
    IndexingStamp.flushCache(fileId);

    getChangedFilesCollector().scheduleForUpdate(file);
  }

  public void doInvalidateIndicesForFile(int fileId, @NotNull VirtualFile file) {
    IndexingFlag.cleanProcessedFlagRecursively(file);

    List<ID<?, ?>> nontrivialFileIndexedStates = IndexingStamp.getNontrivialFileIndexedStates(fileId);

    // transient index value can depend on disk value because former is diff to latter
    // it doesn't matter content froze or not: indices might depend on file name too
    removeTransientFileDataFromIndices(nontrivialFileIndexedStates, fileId, file);

    // The file was removed
    for (ID<?, ?> indexId : nontrivialFileIndexedStates) {
      if (!myRegisteredIndexes.isContentDependentIndex(indexId)) {
        removeSingleIndexValue(indexId, fileId);
      }
    }
    if (!file.isDirectory()) {
      // its data should be (lazily) wiped for every index
      getChangedFilesCollector().scheduleForUpdate(new DeletedVirtualFileStub((VirtualFileWithId)file));
    }
    else {
      getChangedFilesCollector().removeScheduledFileFromUpdate(file); // no need to update it anymore
    }
  }

  public void scheduleFileForIndexing(int fileId, @NotNull VirtualFile file, boolean contentChange) {
    if (ensureFileBelongsToIndexableFilter(fileId, file) == FileAddStatus.SKIPPED) {
      doInvalidateIndicesForFile(fileId, file);
      return;
    }

    Project projectForFile = findProjectForFileId(fileId);
    if (projectForFile == null) {
      LOG.error("ensureFileBelongsToIndexableFilter returned ADDED or PRESENT, but findProjectForFileId returned null for file: " + file);
      doInvalidateIndicesForFile(fileId, file);
      return;
    }

    var indexingRequest = projectForFile.getService(ProjectIndexingDependenciesService.class).getLatestIndexingRequestToken();
    var indexingStamp = indexingRequest.getFileIndexingStamp(file);

    List<ID<?, ?>> nontrivialFileIndexedStates = IndexingStamp.getNontrivialFileIndexedStates(fileId);

    // transient index value can depend on disk value because former is diff to latter
    // it doesn't matter content froze or not: indices might depend on file name too
    removeTransientFileDataFromIndices(nontrivialFileIndexedStates, fileId, file);

    boolean isRegularFile = !file.isDirectory();
    IndexedFileImpl indexedFile = new IndexedFileImpl(file, projectForFile);

    // Apply index contentless indexes in-place
    // For 'normal indices' schedule the file for update and reset stamps for all affected indices (there
    // can be a client that used indices between before and after events, in such case indices are up-to-date due to force update
    // with old content)
    if (!file.isValid() || (isRegularFile && isTooLarge(file))) {
      // large file might be scheduled for update in before event when its size was not large
      getChangedFilesCollector().scheduleForUpdate(new DeletedVirtualFileStub((VirtualFileWithId)file));
    }
    else {
      FileTypeManagerEx.getInstanceEx().freezeFileTypeTemporarilyIn(file, () -> {
        List<ID<?, ?>> candidates = getRequiredIndexes(indexedFile);
        // TODO-ank: delete not needed indexed data now? (will be deleted during indexing)

        boolean scheduleForUpdate = false;
        FileContent fileContent = null;

        for (int i = 0, size = candidates.size(); i < size; ++i) {
          final ID<?, ?> indexId = candidates.get(i);
          if (needsFileContentLoading(indexId)) {
            getIndex(indexId).invalidateIndexedStateForFile(fileId);
            scheduleForUpdate = true;
          }
          else if (!contentChange || indexId == FileTypeIndex.NAME){
            // TODO-ank: quite a strange condition. Mostly to preserve old behavior
            //  and please the test com.intellij.util.indexing.RequestedToRebuildIndexTest
            if (fileContent == null) {
              fileContent = new IndexedFileWrapper(indexedFile);
            }
            updateSingleIndex(indexId, file, fileId, fileContent);
          }
        }

        if (scheduleForUpdate) {
          IndexingStamp.flushCache(fileId);
          getChangedFilesCollector().scheduleForUpdate(file);
        }
        else {
          IndexingFlag.setFileIndexed(file, indexingStamp);
        }
      });
    }
  }

  @Nullable
  public Project findProjectForFileId(int fileId) {
    return myIndexableFilesFilterHolder.findProjectForFile(fileId);
  }

  @NotNull
  private FileAddStatus ensureFileBelongsToIndexableFilter(int fileId, @NotNull VirtualFile file) {
    return myIndexableFilesFilterHolder.addFileId(fileId, () -> getContainingProjects(file));
  }

  @NotNull
  Collection<ID<?, ?>> getContentLessIndexes(boolean isDirectory) {
    return isDirectory ? myRegisteredIndexes.getIndicesForDirectories() : myRegisteredIndexes.getNotRequiringContentIndices();
  }

  @NotNull
  public Collection<ID<?, ?>> getContentDependentIndexes() {
    return myRegisteredIndexes.getRequiringContentIndices();
  }

  void clearUpToDateIndexesForUnsavedOrTransactedDocs() {
    if (!myUpToDateIndicesForUnsavedOrTransactedDocuments.isEmpty()) {
      myUpToDateIndicesForUnsavedOrTransactedDocuments.clear();
    }
  }

  @NotNull
  FileIndexingState getIndexingState(@NotNull IndexedFile file, @NotNull ID<?, ?> indexId) {
    VirtualFile virtualFile = file.getFile();
    if (isMock(virtualFile)) return FileIndexingState.NOT_INDEXED;
    return getIndex(indexId).getIndexingStateForFile(((NewVirtualFile)virtualFile).getId(), file);
  }

  public static boolean isMock(final VirtualFile file) {
    return !(file instanceof NewVirtualFile);
  }

  public boolean isTooLarge(@NotNull VirtualFile file) {
    return isTooLarge(file, null);
  }

  public boolean isTooLarge(@NotNull VirtualFile file,
                            @Nullable("if content size should be retrieved from a file") Long contentSize) {
    return isTooLarge(file, contentSize, myRegisteredIndexes.getNoLimitCheckFileTypes());
  }

  public void registerIndexableSet(@NotNull IndexableFileSet set, @NotNull Project project) {
    myIndexableSets.add(Pair.create(set, project));
  }

  public void removeIndexableSet(@NotNull IndexableFileSet set) {
    if (!myIndexableSets.removeIf(p -> p.first == set)) return;

    ChangedFilesCollector changedFilesCollector = getChangedFilesCollector();
    for (VirtualFile file : changedFilesCollector.getAllFilesToUpdate()) {
      final int fileId = getFileId(file);
      if (!file.isValid()) {
        removeDataFromIndicesForFile(fileId, file, "invalid_file");
        changedFilesCollector.removeFileIdFromFilesScheduledForUpdate(fileId);
      }
      else if (!belongsToIndexableFiles(file)) {
        if (ChangedFilesCollector.CLEAR_NON_INDEXABLE_FILE_DATA) {
          removeDataFromIndicesForFile(fileId, file, "non_indexable_file");
        }
        changedFilesCollector.removeFileIdFromFilesScheduledForUpdate(fileId);
      }
    }

    IndexingStamp.flushCaches();
  }

  @Override
  public VirtualFile findFileById(Project project, int id) {
    return ManagingFS.getInstance().findFileById(id);
  }

  @Nullable
  private static PsiFile findLatestKnownPsiForUncomittedDocument(@NotNull Document doc, @NotNull Project project) {
    return PsiDocumentManager.getInstance(project).getCachedPsiFile(doc);
  }

  void setUpFlusher() {
    final ScheduledExecutorService scheduler = AppExecutorUtil.getAppScheduledExecutorService();
    if (USE_GENTLE_FLUSHER) {
      myFlushingTask = new GentleIndexFlusher(scheduler);
      LOG.info("Using nice flusher for indexes");
    }
    else {
      myFlushingTask = new SimpleFlusher(scheduler);
      LOG.info("Using simple flusher for indexes");
    }
  }

  @Override
  public void invalidateCaches() {
    CorruptionMarker.requestInvalidation();
  }

  @Override
  @ApiStatus.Internal
  @NotNull
  public IntPredicate getAccessibleFileIdFilter(@Nullable Project project) {
    boolean dumb = ActionUtil.isDumbMode(project);
    if (!dumb) return f -> true;

    if (DumbServiceImpl.ALWAYS_SMART && project != null && UnindexedFilesUpdater.isIndexUpdateInProgress(project)) {
      return f -> true;
    }

    DumbModeAccessType dumbModeAccessType = getCurrentDumbModeAccessType();
    if (dumbModeAccessType == null) {
      //throw new IllegalStateException("index access is not allowed in dumb mode");
      return __ -> true;
    }

    if (dumbModeAccessType == DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE) return f -> true;

    assert dumbModeAccessType == DumbModeAccessType.RELIABLE_DATA_ONLY;
    return fileId -> !getChangedFilesCollector().containsFileId(fileId);
  }

  @Override
  public @Nullable IdFilter extractIdFilter(@Nullable GlobalSearchScope scope, @Nullable Project project) {
    if (scope == null) return projectIndexableFiles(project);
    IdFilter filter = extractFileEnumeration(scope);
    if (filter != null) return filter;
    return projectIndexableFiles(ObjectUtils.chooseNotNull(project, scope.getProject()));
  }

  @ApiStatus.Internal
  public void flushIndexes() {
    for (ID<?, ?> id : getRegisteredIndexes().getState().getIndexIDs()) {
      try {
        getIndex(id).flush();
      }
      catch (StorageException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @ApiStatus.Internal
  static <K, V> int getIndexExtensionVersion(@NotNull FileBasedIndexExtension<K, V> extension) {
    return extension.getVersion();
  }

  @Nullable
  private IdFilter extractFileEnumeration(@NotNull GlobalSearchScope scope) {
    VirtualFileEnumeration hint = VirtualFileEnumeration.extract(scope);
    if (hint != null) {
      return new IdFilter() {
        @Override
        public boolean containsFileId(int id) {
          return hint.contains(id);
        }

        @Override
        public String toString() {
          return "IdFilter of " + scope;
        }
      };
    }
    Project project = scope.getProject();
    if (project == null) return null;
    // todo support project only content scope
    return projectIndexableFiles(project);
  }

  @NotNull
  private static ApplicationMode getIndexApplicationMode() {
    return ourWritingIndexValuesSeparatedFromCounting;
  }

  @NotNull
  static ApplicationMode getContentIndependentIndexesApplicationMode() {
    return ApplicationMode.SameThreadOutsideReadLock;
  }

  static void setupWritingIndexValuesSeparatedFromCounting() {
    ourWritingIndexValuesSeparatedFromCounting =
      IndexUpdateRunner.WRITE_INDEXES_ON_SEPARATE_THREAD ? ApplicationMode.AnotherThread : ApplicationMode.SameThreadOutsideReadLock;
  }

  private static volatile ApplicationMode ourWritingIndexValuesSeparatedFromCounting;

  // ==== Flushers implementations: =====

  //We're trying to guess when and how to flush indexes so that this flush is the least intrusive for others, who
  //  also want an access index, or VFS. Current indexes are protected by a global lock, and VFS mostly protected
  //  by StorageLockContext's global lock also, so indexes flush could create freeze whole app very easily,
  //  especially if there is a lot of data to flush. Here we try to reduce the probability of a long freezes by
  //  looking for the signs of intensive indexes/VFS use, and postponing index flush if such signs are present.


  /**
   * Legacy flushing implementation: do some basic precautions against contention. Wait for a period without modifications,
   * use .tryLock() to avoid competing with other threads
   */
  private final class SimpleFlusher implements Runnable, AutoCloseable {

    private int lastModCount;
    private final Future<?> scheduledFuture;


    private SimpleFlusher(final @NotNull ScheduledExecutorService scheduler) {
      this.scheduledFuture = scheduler.scheduleWithFixedDelay(this, FLUSHING_PERIOD_MS, FLUSHING_PERIOD_MS, MILLISECONDS);
    }

    @Override
    public void run() {
      final int currentModCount = myLocalModCount.get();
      try {
        flushAllIndices(lastModCount);
      }
      finally {
        lastModCount = currentModCount;
      }
    }

    private void flushAllIndices(final int modCount) {
      if (betterToInterruptFlushingEarly(modCount)) {
        return;
      }

      IndexingStamp.flushCaches();

      final int maxAttemptsPerIndex = 2;
      final int maxSleepPerAttemptMs = 16;

      IndexConfiguration state = getState();
      int interferencesWithOtherThreads = 0;
      for (ID<?, ?> indexId : state.getIndexIDs()) {
        if (betterToInterruptFlushingEarly(modCount)) {
          return; // do not interfere with 'main' jobs
        }
        try {
          final UpdatableIndex<?, ?, FileContent, ?> index = state.getIndex(indexId);
          if (index != null) {
            //RC: regular flush should not interfere with other, (likely) more response-time-critical
            //    jobs. We can't guarantee the total absence of interference, though -- instead we're
            //    trying to be just 'nice to others' here.
            //    I.e. do .yield() after each flush, so somebody who waits for index access -- has its
            //    chance. We also use .tryLock() as a way to feel interference (readLock.tryLock fails
            //    -> somebody else acquired write lock), and back off a bit, giving 'another job' a chance
            //    to finish.
            //    (See e.g., IDEA-244174 for what could go wrong otherwise)

            for (int attempt = 0; attempt < maxAttemptsPerIndex; attempt++) {
              final ReadWriteLock rwLock = index.getLock();
              final Lock indexReadLock = rwLock.readLock();
              final boolean lockSucceeded = indexReadLock.tryLock();
              if (lockSucceeded) {
                try {
                  index.flush();
                }
                finally {
                  indexReadLock.unlock();
                }
                interferencesWithOtherThreads--;
                break;
              }
              else {
                interferencesWithOtherThreads++;
              }

              // linear backoff based on how many times we contended with others:
              final int toWaitMs = clamp(interferencesWithOtherThreads, 0, maxSleepPerAttemptMs);
              if (toWaitMs == 0) {
                Thread.yield();
              }
              else {
                Thread.sleep(toWaitMs);
              }
            }
          }
        }
        catch (Throwable e) {
          requestRebuild(indexId, e);
        }
      }
    }

    private boolean betterToInterruptFlushingEarly(final int modCount) {
      //RC: Basically, we're trying to flush 'if idle': i.e., we don't want to
      //    issue a flush if somebody actively writes to indexes because flush
      //    will slow them down, if not stall them -- and (regular) flush is
      //    less important than e.g., a current UI task.
      //    So we issue a flush only if there _were no updates_ in indexes
      //    since the last invocation of this method:
      return HeavyProcessLatch.INSTANCE.isRunning() || modCount != myLocalModCount.get();
    }

    @Override
    public void close() {
      scheduledFuture.cancel(false);
    }
  }

  /**
   * Try to reduce contention by looking on the signs of interference/contention -- like Lock.getQueueLength(),
   * and fail of .tryLock(). Introduce a limit on how many such signs are OK during a single attempt to flush
   * indexes -- 'contention quota'. Attempt to flush indexes continues until there are less total signs of
   * contention than the quota allows. After quota is fully spent -> flush is interrupted, and the next flush
   * attempt is re-scheduled in a short period, and with contention quota doubled. If quota is more than enough
   * to flush everything -- i.e., there is unspent quota -- then the next attempt is scheduled in a regular
   * interval, and the contention quota is slightly decreased for the next attempt.
   * More details in a {@link GentleFlusherBase} javadocs
   */
  private final class GentleIndexFlusher extends GentleFlusherBase {
    private static final int MIN_CONTENTION_QUOTA = 2;
    private static final int INITIAL_CONTENTION_QUOTA = 16;
    private static final int MAX_CONTENTION_QUOTA = 64;


    private int lastModCount;
    private final Map<ID<?, ?>, IndexFlushingState> flushingStates = new HashMap<>();

    //=====================

    private GentleIndexFlusher(final @NotNull ScheduledExecutorService scheduler) {
      super("IndexesFlusher",
            scheduler, FLUSHING_PERIOD_MS,
            MIN_CONTENTION_QUOTA, MAX_CONTENTION_QUOTA, INITIAL_CONTENTION_QUOTA,
            TelemetryManager.getInstance().getMeter(Indexes)
      );
    }

    @Override
    protected FlushResult flushAsMuchAsPossibleWithinQuota(final /*InOut*/ IntRef contentionQuota) {
      //TODO RC: check if there _any_ index to flush -- otherwise no need to flush IndexingStamp either
      IndexingStamp.flushCaches();

      final IndexConfiguration indexes = getState();

      FlushResult overallResult = FlushResult.NOTHING_TO_FLUSH_NOW;
      for (ID<?, ?> indexId : indexes.getIndexIDs()) {
        final IndexFlushingState indexFlushingState = flushingStates.computeIfAbsent(indexId, IndexFlushingState::new);
        final FlushResult indexFlushResult = indexFlushingState.tryFlushIfNeeded(
          indexes,
          contentionQuota,
          flushingPeriodMs
        );
        overallResult = overallResult.and(indexFlushResult);
        if (LOG.isTraceEnabled()) {
          LOG.trace("\t" + indexFlushingState + " " + indexFlushResult);
        }

        final int contentionQuotaRemains = contentionQuota.get();
        if (contentionQuotaRemains <= 0) {
          contentionQuota.set(contentionQuotaRemains);
          return FlushResult.HAS_MORE_TO_FLUSH;
        }
      }

      return overallResult;
    }

    @Override
    public boolean hasSomethingToFlush() {
      if (IndexingStamp.isDirty()) return true;

      IndexConfiguration indexes = getState();
      for (ID<?, ?> indexId : indexes.getIndexIDs()) {
        UpdatableIndex<?, ?, FileContent, ?> index = indexes.getIndex(indexId);
        if (index != null && index.isDirty()) {
          return true;
        }
      }
      return false;
    }

    @Override
    protected boolean betterPostponeFlushNow() {
      //RC: Basically, we're trying to flush 'if idle': i.e., we don't want to
      //    issue a flush if somebody actively writes to indexes because flush
      //    will slow them down, if not stall them -- and (regular) flush is
      //    less important than e.g., a current UI task.
      //    So we issue a flush only if there _were no updates_ in indexes
      //    since the last invocation of this method:
      final int currentModCount = myLocalModCount.get();
      if (lastModCount != currentModCount) {
        lastModCount = currentModCount;
        return true;
      }
      return false;
    }

    private static int threadsCompetingForLock(final ReadWriteLock lock) {
      if (!(lock instanceof ReentrantReadWriteLock)) {
        throw new IllegalStateException("index.lock (" + lock + ") is not ReentrantReadWriteLock -- can't sample queue length");
      }
      //RC: worth to add StorageLockContext.defaultContextLock().getQueueLength() into
      //    the equation: if storages are intensively used outside of indexes, it is better to
      //    keep hands off the indexes flush also -- since the index flush will also compete for the
      //    storage lock
      final int storageLockQueueLength = StorageLockContext.defaultContextLock().getQueueLength();
      return ((ReentrantReadWriteLock)lock).getQueueLength() + storageLockQueueLength;
    }

    private final class IndexFlushingState {
      private final ID<?, ?> indexId;
      private long lastFlushedMs = -1;

      private IndexFlushingState(final @NotNull ID<?, ?> indexId) {
        this.indexId = indexId;
      }

      public FlushResult tryFlushIfNeeded(final @NotNull IndexConfiguration indexes,
                                          final @NotNull /*InOut*/ IntRef contentionQuota,
                                          final long flushingPeriodMs) {
        if (System.currentTimeMillis() - lastFlushedMs < flushingPeriodMs) {
          //no need for another flush yet:
          return FlushResult.NOTHING_TO_FLUSH_NOW;
        }
        final UpdatableIndex<?, ?, FileContent, ?> index = indexes.getIndex(indexId);
        if (index == null) {
          //did nothing -> spent no quota:
          return FlushResult.NOTHING_TO_FLUSH_NOW;
        }

        int unspentContentionQuota = contentionQuota.get();
        final ReadWriteLock indexProtectingLock = index.getLock();
        final Lock indexReadLock = indexProtectingLock.readLock();

        final boolean lockSucceeded = indexReadLock.tryLock();
        if (lockSucceeded) {
          try {
            try {
              index.flush();
              lastFlushedMs = System.currentTimeMillis();
            }
            catch (Throwable e) {
              requestRebuild(indexId, e);
            }

            unspentContentionQuota -= threadsCompetingForLock(indexProtectingLock);
            contentionQuota.set(unspentContentionQuota);

            return FlushResult.FLUSHED_ALL;
          }
          finally {
            indexReadLock.unlock();
          }
        }

        //+1 because of the thread currently holding lock (causing .tryLock to fail)
        final int competingThreads = threadsCompetingForLock(indexProtectingLock) + 1;
        unspentContentionQuota -= competingThreads;

        contentionQuota.set(unspentContentionQuota);
        return FlushResult.HAS_MORE_TO_FLUSH;
      }

      @Override
      public String toString() {
        return "IndexFlushingState[" + indexId + "][lastFlushed: " + lastFlushedMs + ']';
      }
    }
  }
}
