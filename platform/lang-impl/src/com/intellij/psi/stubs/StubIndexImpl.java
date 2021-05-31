// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.model.ModelBranchImpl;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CompactVirtualFileSet;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.impl.NullVirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.*;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.diagnostic.IndexAccessValidator;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.indexing.impl.storage.TransientFileContentIndex;
import com.intellij.util.indexing.impl.storage.VfsAwareMapIndexStorage;
import com.intellij.util.indexing.memory.InMemoryIndexStorage;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.VoidDataExternalizer;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.ObjectIterators;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.IntPredicate;

public final class StubIndexImpl extends StubIndexEx {
  static final Logger LOG = Logger.getInstance(StubIndexImpl.class);

  private static final class AsyncState {
    private final Map<StubIndexKey<?, ?>, UpdatableIndex<?, Void, FileContent>> myIndices = CollectionFactory.createSmallMemoryFootprintMap();
  }

  private final Map<StubIndexKey<?, ?>, CachedValue<Map<KeyAndFileId<?>, StubIdList>>> myCachedStubIds = FactoryMap.createMap(k -> {
    UpdatableIndex<Integer, SerializedStubTree, FileContent> index = getStubUpdatingIndex();
    ModificationTracker tracker = index::getModificationStamp;
    return new CachedValueImpl<>(() -> new CachedValueProvider.Result<>(new ConcurrentHashMap<>(), tracker));
  }, ConcurrentHashMap::new);

  private final StubProcessingHelper myStubProcessingHelper = new StubProcessingHelper();
  private final IndexAccessValidator myAccessValidator = new IndexAccessValidator();

  private final AtomicBoolean myForcedClean = new AtomicBoolean();
  private volatile CompletableFuture<AsyncState> myStateFuture;
  private volatile AsyncState myState;
  private volatile boolean myInitialized;

  public StubIndexImpl() {
    StubIndexExtension.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionRemoved(@NotNull StubIndexExtension<?, ?> extension, @NotNull PluginDescriptor pluginDescriptor) {
        ID.unloadId(extension.getKey());
      }
    }, null);
  }

  private AsyncState getAsyncState() {
    AsyncState state = myState; // memory barrier
    if (state == null) {
      if (myStateFuture == null) {
        ((FileBasedIndexImpl)FileBasedIndex.getInstance()).waitUntilIndicesAreInitialized();
      }
      myState = state = ProgressIndicatorUtils.awaitWithCheckCanceled(myStateFuture);
    }
    return state;
  }

  @ApiStatus.Internal
  @TestOnly
  public void waitUntilStubIndexedInitialized() {
    getAsyncState();
  }

  public void initializationFailed(@NotNull Throwable error) {
    myStateFuture = new CompletableFuture<>();
    myStateFuture.completeExceptionally(error);
  }

  public static @NotNull <K> FileBasedIndexExtension<K, Void> wrapStubIndexExtension(StubIndexExtension<K, ?> extension) {
    return new FileBasedIndexExtension<>() {
      @Override
      public @NotNull ID<K, Void> getName() {
        @SuppressWarnings("unchecked") ID<K, Void> key = (ID<K, Void>)extension.getKey();
        return key;
      }

      @Override
      public @NotNull FileBasedIndex.InputFilter getInputFilter() {
        return f -> {
          throw new UnsupportedOperationException();
        };
      }

      @Override
      public boolean dependsOnFileContent() {
        return true;
      }

      @Override
      public boolean needsForwardIndexWhenSharing() {
        return false;
      }

      @Override
      public @NotNull DataIndexer<K, Void, FileContent> getIndexer() {
        return i -> {
          throw new AssertionError();
        };
      }

      @Override
      public @NotNull KeyDescriptor<K> getKeyDescriptor() {
        return extension.getKeyDescriptor();
      }

      @Override
      public @NotNull DataExternalizer<Void> getValueExternalizer() {
        return VoidDataExternalizer.INSTANCE;
      }

      @Override
      public int getVersion() {
        return extension.getVersion();
      }

      @Override
      public boolean traceKeyHashToVirtualFileMapping() {
        return extension instanceof StringStubIndexExtension && ((StringStubIndexExtension<?>)extension).traceKeyHashToVirtualFileMapping();
      }
    };
  }

  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  private static <K> void registerIndexer(final @NotNull StubIndexExtension<K, ?> extension, final boolean forceClean,
                                          @NotNull AsyncState state, @NotNull IndexVersionRegistrationSink registrationResultSink)
    throws IOException {
    final StubIndexKey<K, ?> indexKey = extension.getKey();
    final int version = extension.getVersion();
    FileBasedIndexExtension<K, Void> wrappedExtension = wrapStubIndexExtension(extension);

    Path indexRootDir = IndexInfrastructure.getIndexRootDir(indexKey);
    IndexVersion.IndexVersionDiff versionDiff = forceClean
                                                 ? new IndexVersion.IndexVersionDiff.InitialBuild(version)
                                                 : IndexVersion.versionDiffers(indexKey, version);

    registrationResultSink.setIndexVersionDiff(indexKey, versionDiff);
    if (versionDiff != IndexVersion.IndexVersionDiff.UP_TO_DATE) {
      Path versionFile = IndexInfrastructure.getVersionFile(indexKey);
      boolean versionFileExisted = Files.exists(versionFile);
      final String[] children = indexRootDir.toFile().list();
      // rebuild only if there exists what to rebuild
      boolean indexRootHasChildren = children != null && children.length > 0;
      boolean needRebuild = !forceClean && (versionFileExisted || indexRootHasChildren);

      if (indexRootHasChildren) {
        FileUtil.deleteWithRenaming(indexRootDir.toFile());
      }
      IndexVersion.rewriteVersion(indexKey, version); // todo snapshots indices

      try {
        if (needRebuild) {
          for (FileBasedIndexInfrastructureExtension ex : FileBasedIndexInfrastructureExtension.EP_NAME.getExtensionList()) {
            ex.onStubIndexVersionChanged(indexKey);
          }
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    UpdatableIndex<Integer, SerializedStubTree, FileContent> stubUpdatingIndex = getStubUpdatingIndex();
    ReadWriteLock lock = stubUpdatingIndex.getLock();

    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        UpdatableIndex<K, Void, FileContent> index = new TransientFileContentIndex<>(wrappedExtension,
                                                                                     new StubIndexStorageLayout<>(wrappedExtension, indexKey),
                                                                                     lock);

        for (FileBasedIndexInfrastructureExtension infrastructureExtension : FileBasedIndexInfrastructureExtension.EP_NAME.getExtensionList()) {
          UpdatableIndex<K, Void, FileContent> intermediateIndex = infrastructureExtension.combineIndex(wrappedExtension, index);
          if (intermediateIndex != null) {
            index = intermediateIndex;
          }
        }

        synchronized (state) {
          state.myIndices.put(indexKey, index);
        }
        break;
      }
      catch (IOException e) {
        registrationResultSink.setIndexVersionDiff(indexKey, new IndexVersion.IndexVersionDiff.CorruptedRebuild(version));
        onExceptionInstantiatingIndex(indexKey, version, indexRootDir, e);
      }
      catch (RuntimeException e) {
        Throwable cause = FileBasedIndexImpl.getCauseToRebuildIndex(e);
        if (cause == null) {
          throw e;
        }
        onExceptionInstantiatingIndex(indexKey, version, indexRootDir, e);
      }
    }
  }

  private static <K> void onExceptionInstantiatingIndex(@NotNull StubIndexKey<K, ?> indexKey,
                                                        int version,
                                                        @NotNull Path indexRootDir,
                                                        @NotNull Exception e) throws IOException {
    LOG.info(e);
    FileUtil.deleteWithRenaming(indexRootDir.toFile());
    IndexVersion.rewriteVersion(indexKey, version); // todo snapshots indices
  }

  public long getIndexModificationStamp(@NotNull StubIndexKey<?, ?> indexId, @NotNull Project project) {
    UpdatableIndex<?, Void, FileContent> index = getAsyncState().myIndices.get(indexId);
    if (index != null) {
      FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, GlobalSearchScope.allScope(project));
      return index.getModificationStamp();
    }
    return -1;
  }

  public void flush() throws StorageException {
    if (!myInitialized) {
      return;
    }
    for (UpdatableIndex<?, Void, FileContent> index : getAsyncState().myIndices.values()) {
      index.flush();
    }
  }


  @Override
  public <Key, Psi extends PsiElement> boolean processElements(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                               @NotNull Key key,
                                                               @NotNull Project project,
                                                               @Nullable GlobalSearchScope scope,
                                                               @Nullable IdFilter idFilter,
                                                               @NotNull Class<Psi> requiredClass,
                                                               @NotNull Processor<? super Psi> processor) {
    boolean dumb = DumbService.isDumb(project);
    if (dumb) {
      if (LightEdit.owns(project)) return false;
      DumbModeAccessType accessType = FileBasedIndex.getInstance().getCurrentDumbModeAccessType();
      if (accessType == DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE) {
        throw new AssertionError("raw index data access is not available for StubIndex");
      }
    }

    PairProcessor<VirtualFile, StubIdList> stubProcessor = (file, list) ->
      myStubProcessingHelper.processStubsInFile(project, file, list, processor, scope, requiredClass);

    if (!ModelBranchImpl.processModifiedFilesInScope(scope != null ? scope : GlobalSearchScope.everythingScope(project),
                                                     file -> processInMemoryStubs(indexKey, key, project, stubProcessor, file))) {
      return false;
    }

    VirtualFile singleFileInScope = extractSingleFile(scope);
    Iterator<VirtualFile> fileStream;
    boolean shouldHaveKeys;

    if (singleFileInScope != null) {
      if (!(singleFileInScope instanceof VirtualFileWithId)) return true;
      FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, scope);
      fileStream = ObjectIterators.singleton(singleFileInScope);
      shouldHaveKeys = false;
    }
    else {
      IntSet ids = getContainingIds(indexKey, key, project, idFilter, scope);
      if (ids == null) return true;
      IntPredicate accessibleFileFilter = ((FileBasedIndexEx)FileBasedIndex.getInstance()).getAccessibleFileIdFilter(project);
      // already ensured up-to-date in getContainingIds() method
      IntIterator idIterator = ids.iterator();
      fileStream = StubIndexImplUtil.mapIdIterator(idIterator, accessibleFileFilter);
      shouldHaveKeys = true;
    }

    try {
      while (fileStream.hasNext()) {
        VirtualFile file = fileStream.next();
        assert file != null;

        List<VirtualFile> filesInScope = scope != null ? FileBasedIndexEx.filesInScopeWithBranches(scope, file) : Collections.singletonList(file);
        if (filesInScope.isEmpty()) {
          continue;
        }

        int id = ((VirtualFileWithId)file).getId();
        StubIdList list = myCachedStubIds.get(indexKey).getValue().computeIfAbsent(new KeyAndFileId<>(key, id), __ ->
          myStubProcessingHelper.retrieveStubIdList(indexKey, key, file, project, shouldHaveKeys)
        );
        if (list == null) {
          // stub index inconsistency
          continue;
        }
        for (VirtualFile eachFile : filesInScope) {
          if (!stubProcessor.process(eachFile, list)) {
            return false;
          }
        }
      }
    }
    catch (RuntimeException e) {
      final Throwable cause = FileBasedIndexImpl.getCauseToRebuildIndex(e);
      if (cause != null) {
        forceRebuild(cause);
      }
      else {
        throw e;
      }
    } finally {
      wipeProblematicFileIdsForParticularKeyAndStubIndex(indexKey, key);
    }
    return true;
  }

  private static <Key, Psi extends PsiElement> boolean processInMemoryStubs(StubIndexKey<Key, Psi> indexKey,
                                                                            Key key,
                                                                            Project project,
                                                                            PairProcessor<? super VirtualFile, ? super StubIdList> stubProcessor,
                                                                            VirtualFile file) {
    Map<Integer, SerializedStubTree> data = FileBasedIndex.getInstance().getFileData(StubUpdatingIndex.INDEX_ID, file, project);
    if (data.size() == 1) {
      try {
        StubIdList list = data.values().iterator().next().restoreIndexedStubs(indexKey, key);
        if (list != null) {
          return stubProcessor.process(file, list);
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return true;
  }

  @SuppressWarnings("unchecked")
  private <Key> UpdatableIndex<Key, Void, FileContent> getIndex(@NotNull StubIndexKey<Key, ?> indexKey) {
    return (UpdatableIndex<Key, Void, FileContent>)getAsyncState().myIndices.get(indexKey);
  }

  // Self repair for IDEA-181227, caused by (yet) unknown file event processing problem in indices
  // FileBasedIndex.requestReindex doesn't handle the situation properly because update requires old data that was lost
  private <Key> void wipeProblematicFileIdsForParticularKeyAndStubIndex(@NotNull StubIndexKey<Key, ?> indexKey,
                                                                        @NotNull Key key) {
    Set<VirtualFile> filesWithProblems = myStubProcessingHelper.takeAccumulatedFilesWithIndexProblems();

    if (filesWithProblems != null) {
      LOG.info("data for " + indexKey.getName() + " will be wiped for a some files because of internal stub processing error");
      ((FileBasedIndexImpl)FileBasedIndex.getInstance()).runCleanupAction(() -> {
        Lock writeLock = getIndex(indexKey).getLock().writeLock();
        boolean locked = writeLock.tryLock();
        if (!locked) return; // nested indices invocation, can not cleanup without deadlock
        try {
          for (VirtualFile file : filesWithProblems) {
            updateIndex(indexKey,
                        FileBasedIndex.getFileId(file),
                        Collections.singleton(key),
                        Collections.emptySet());
          }
        }
        finally {
          writeLock.unlock();
        }
      });
    }
  }

  @Override
  public void forceRebuild(@NotNull Throwable e) {
    FileBasedIndex.getInstance().scheduleRebuild(StubUpdatingIndex.INDEX_ID, e);
  }

  private static void requestRebuild() {
    FileBasedIndex.getInstance().requestRebuild(StubUpdatingIndex.INDEX_ID);
  }

  @Override
  public @NotNull <K> Collection<K> getAllKeys(@SuppressWarnings("BoundedWildcard") @NotNull StubIndexKey<K, ?> indexKey, @NotNull Project project) {
    Set<K> allKeys = new HashSet<>();
    processAllKeys(indexKey, project, Processors.cancelableCollectProcessor(allKeys));
    return allKeys;
  }

  @Override
  public <K> boolean processAllKeys(@NotNull StubIndexKey<K, ?> indexKey,
                                    @NotNull Processor<? super K> processor,
                                    @NotNull GlobalSearchScope scope,
                                    @Nullable IdFilter idFilter) {
    final UpdatableIndex<K, Void, FileContent> index = getIndex(indexKey); // wait for initialization to finish
    FileBasedIndexEx fileBasedIndexEx = (FileBasedIndexEx)FileBasedIndex.getInstance();
    if (index == null ||
        !fileBasedIndexEx.ensureUpToDate(StubUpdatingIndex.INDEX_ID, scope.getProject(), scope, null)) {
      return true;
    }

    //if (idFilter == null) {
    //  idFilter = fileBasedIndexEx.projectIndexableFiles(scope.getProject());
    //}

    try {
      @Nullable IdFilter finalIdFilter = idFilter;
      return myAccessValidator.validate(StubUpdatingIndex.INDEX_ID, ()->FileBasedIndexImpl.disableUpToDateCheckIn(()->
        index.processAllKeys(processor, scope, finalIdFilter)));
    }
    catch (StorageException e) {
      forceRebuild(e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException || cause instanceof StorageException) {
        forceRebuild(e);
      }
      throw e;
    }
    return true;
  }

  @Override
  public @NotNull <Key> IdIterator getContainingIds(@NotNull StubIndexKey<Key, ?> indexKey,
                                                    @NotNull Key dataKey,
                                                    final @NotNull Project project,
                                                    final @Nullable GlobalSearchScope scope) {
    IntSet result = getContainingIds(indexKey, dataKey, project, null, scope);
    if (result == null) return IdIterator.EMPTY;
    return new IdIterator() {
      final IntIterator iterator = result.iterator();

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public int next() {
        return iterator.nextInt();
      }

      @Override
      public int size() {
        return result.size();
      }
    };
  }

  @Override
  public @NotNull <Key> Set<VirtualFile> getContainingFiles(@NotNull StubIndexKey<Key, ?> indexKey,
                                                            @NotNull Key dataKey,
                                                            @NotNull Project project,
                                                            @NotNull GlobalSearchScope scope) {
    IntSet result = getContainingIds(indexKey, dataKey, project, null, scope);
    CompactVirtualFileSet fileSet = new CompactVirtualFileSet(result == null ? IntSets.emptySet() : result);
    fileSet.freeze();
    return fileSet;
  }

  private @Nullable <Key> IntSet getContainingIds(@NotNull StubIndexKey<Key, ?> indexKey,
                                                  @NotNull Key dataKey,
                                                  final @NotNull Project project,
                                                  @Nullable IdFilter idFilter,
                                                  final @Nullable GlobalSearchScope scope) {
    final FileBasedIndexEx fileBasedIndex = (FileBasedIndexEx)FileBasedIndex.getInstance();
    ID<Integer, SerializedStubTree> stubUpdatingIndexId = StubUpdatingIndex.INDEX_ID;
    final UpdatableIndex<Key, Void, FileContent> index = getIndex(indexKey);   // wait for initialization to finish
    if (index == null || !fileBasedIndex.ensureUpToDate(stubUpdatingIndexId, project, scope, null)) return null;

    IdFilter finalIdFilter = idFilter != null ? idFilter : ((FileBasedIndexEx)FileBasedIndex.getInstance()).projectIndexableFiles(project);

    UpdatableIndex<Integer, SerializedStubTree, FileContent> stubUpdatingIndex = fileBasedIndex.getIndex(stubUpdatingIndexId);

    try {
      IntSet result = new IntLinkedOpenHashSet(); // workaround duplicates keys
      myAccessValidator.validate(stubUpdatingIndexId, ()-> {
        // disable up-to-date check to avoid locks on attempt to acquire index write lock while holding at the same time the readLock for this index
        //noinspection Convert2Lambda (workaround for JBR crash, JBR-2349),Convert2Diamond
        return FileBasedIndexImpl.disableUpToDateCheckIn(() -> ConcurrencyUtil.withLock(stubUpdatingIndex.getLock().readLock(), () ->
          index.getData(dataKey).forEach(new ValueContainer.ContainerAction<>() {
            @Override
            public boolean perform(int id, Void value) {
              if (finalIdFilter == null || finalIdFilter.containsFileId(id)) {
                result.add(id);
              }
              return true;
            }
          })
        ));
      });
      return result;
    }
    catch (StorageException e) {
      forceRebuild(e);
    }
    catch (RuntimeException e) {
      final Throwable cause = FileBasedIndexImpl.getCauseToRebuildIndex(e);
      if (cause != null) {
        forceRebuild(cause);
      }
      else {
        throw e;
      }
    }

    return null;
  }

  void initializeStubIndexes() {
    assert !myInitialized;

    // might be called on the same thread twice if initialization has been failed
    if (myStateFuture == null) {
      // ensure that FileBasedIndex task "FileIndexDataInitialization" submitted first
      FileBasedIndex.getInstance();

      myStateFuture = new CompletableFuture<>();
      Future<AsyncState> future = IndexDataInitializer.submitGenesisTask(new StubIndexInitialization());

      if (!IndexDataInitializer.ourDoAsyncIndicesInitialization) {
        try {
          future.get();
        }
        catch (Throwable t) {
          LOG.error(t);
        }
      }
    }
  }

  public void dispose() {
    try {
      for (UpdatableIndex<?, ?, ?> index : getAsyncState().myIndices.values()) {
        index.dispose();
      }
    } finally {
      clearState();
    }
  }

  private void clearState() {
    StubIndexKeyDescriptorCache.INSTANCE.clear();
    ((SerializationManagerImpl)SerializationManagerEx.getInstanceEx()).dropSerializerData();
    myCachedStubIds.clear();
    myStateFuture = null;
    myState = null;
    myInitialized = false;
    LOG.info("StubIndexExtension-s were unloaded");
  }

  void setDataBufferingEnabled(final boolean enabled) {
    for (UpdatableIndex<?, ?, ?> index : getAsyncState().myIndices.values()) {
      index.setBufferingEnabled(enabled);
    }
  }

  void cleanupMemoryStorage() {
    UpdatableIndex<Integer, SerializedStubTree, FileContent> stubUpdatingIndex = getStubUpdatingIndex();
    stubUpdatingIndex.getLock().writeLock().lock();

    try {
      for (UpdatableIndex<?, ?, ?> index : getAsyncState().myIndices.values()) {
        index.cleanupMemoryStorage();
      }
    }
    finally {
      stubUpdatingIndex.getLock().writeLock().unlock();
    }
  }

  void clearAllIndices() {
    if (!myInitialized) {
      myForcedClean.set(true);
      return;
    }
    for (UpdatableIndex<?, ?, ?> index : getAsyncState().myIndices.values()) {
      try {
        index.clear();
      }
      catch (StorageException e) {
        LOG.error(e);
        throw new RuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  <K> void removeTransientDataForFile(@NotNull StubIndexKey<K, ?> key, int inputId, Map<K, StubIdList> keys) {
    UpdatableIndex<Object, Void, FileContent> index = (UpdatableIndex)getIndex(key);
    index.removeTransientDataForKeys(inputId, new MapInputDataDiffBuilder(inputId, keys));
  }

  public <K> void updateIndex(@NotNull StubIndexKey<K, ?> stubIndexKey,
                              int fileId,
                              @NotNull Set<? extends K> oldKeys,
                              @NotNull Set<? extends K> newKeys) {
    ProgressManager.getInstance().executeNonCancelableSection(() -> {
      try {
        if (FileBasedIndexImpl.DO_TRACE_STUB_INDEX_UPDATE) {
          LOG.info("stub index '" + stubIndexKey + "' update: " + fileId +
                   " old = " + Arrays.toString(oldKeys.toArray()) +
                   " new  = " + Arrays.toString(newKeys.toArray()) +
                   " updated_id = " + System.identityHashCode(newKeys));
        }
        final UpdatableIndex<K, Void, FileContent> index = getIndex(stubIndexKey);
        if (index == null) return;
        index.updateWithMap(new AbstractUpdateData<>(fileId) {
          @Override
          protected boolean iterateKeys(@NotNull KeyValueUpdateProcessor<? super K, ? super Void> addProcessor,
                                        @NotNull KeyValueUpdateProcessor<? super K, ? super Void> updateProcessor,
                                        @NotNull RemovedKeyProcessor<? super K> removeProcessor) throws StorageException {
            boolean modified = false;

            for (K oldKey : oldKeys) {
              if (!newKeys.contains(oldKey)) {
                removeProcessor.process(oldKey, fileId);
                if (!modified) modified = true;
              }
            }

            for (K oldKey : newKeys) {
              if (!oldKeys.contains(oldKey)) {
                addProcessor.process(oldKey, null, fileId);
                if (!modified) modified = true;
              }
            }

            if (FileBasedIndexImpl.DO_TRACE_STUB_INDEX_UPDATE) {
              LOG.info("keys iteration finished updated_id = " + System.identityHashCode(newKeys) + "; modified = " + modified);
            }

            return modified;
          }
        });
      }
      catch (StorageException e) {
        LOG.info(e);
        requestRebuild();
      }
    });

  }

  private static class StubIndexStorageLayout<K> implements VfsAwareIndexStorageLayout<K, Void> {
    private final FileBasedIndexExtension<K, Void> myWrappedExtension;
    private final StubIndexKey<K, ?> myIndexKey;

    private StubIndexStorageLayout(FileBasedIndexExtension<K, Void> wrappedExtension, StubIndexKey<K, ?> indexKey) {
      myWrappedExtension = wrappedExtension;
      myIndexKey = indexKey;
    }

    @Override
    public @NotNull IndexStorage<K, Void> openIndexStorage() throws IOException {
      if (FileBasedIndex.USE_IN_MEMORY_INDEX) {
        return new InMemoryIndexStorage<>(myWrappedExtension.getKeyDescriptor());
      }

      Path storageFile = IndexInfrastructure.getStorageFile(myIndexKey);
      try {
        return new VfsAwareMapIndexStorage<>(
          storageFile,
          myWrappedExtension.getKeyDescriptor(),
          myWrappedExtension.getValueExternalizer(),
          myWrappedExtension.getCacheSize(),
          myWrappedExtension.keyIsUniqueForIndexedFile(),
          myWrappedExtension.traceKeyHashToVirtualFileMapping()
        );
      }
      catch (IOException e) {
        IOUtil.deleteAllFilesStartingWith(storageFile);
        throw e;
      }
    }

    @Override
    public void clearIndexData() {
      throw new UnsupportedOperationException();
    }
  }

  private final class StubIndexInitialization extends IndexDataInitializer<AsyncState> {
    private final AsyncState state = new AsyncState();
    private final IndexVersionRegistrationSink indicesRegistrationSink = new IndexVersionRegistrationSink();

    @Override
    protected @NotNull AsyncState finish() {
      indicesRegistrationSink.logChangedAndFullyBuiltIndices(LOG, "Following stub indices will be updated:",
                                                             "Following stub indices will be built:");

      if (indicesRegistrationSink.hasChangedIndexes()) {
        final Throwable e = new Throwable(indicesRegistrationSink.changedIndices());
        // avoid direct forceRebuild as it produces dependency cycle (IDEA-105485)
        AppUIExecutor.onWriteThread(ModalityState.NON_MODAL).later().submit(() -> forceRebuild(e));
      }

      myInitialized = true;
      myStateFuture.complete(state);
      return state;
    }

    @NotNull
    @Override
    protected Collection<ThrowableRunnable<?>> prepareTasks() {
      Iterator<StubIndexExtension<?, ?>> extensionsIterator;
      if (IndexInfrastructure.hasIndices()) {
        extensionsIterator = StubIndexExtension.EP_NAME.getIterable().iterator();
      }
      else {
        extensionsIterator = Collections.emptyIterator();
      }

      boolean forceClean = Boolean.TRUE == myForcedClean.getAndSet(false);
      List<ThrowableRunnable<?>> tasks = new ArrayList<>();
      while (extensionsIterator.hasNext()) {
        StubIndexExtension<?, ?> extension = extensionsIterator.next();
        if (extension == null) {
          break;
        }
        // initialize stub index keys
        extension.getKey();

        tasks.add(() -> registerIndexer(extension, forceClean, state, indicesRegistrationSink));
      }
      return tasks;
    }

    @NotNull
    @Override
    protected String getInitializationFinishedMessage(AsyncState initializationResult) {
      return "Initialized stub indexes: " + initializationResult.myIndices.keySet() + ".";
    }
  }

  static UpdatableIndex<Integer, SerializedStubTree, FileContent> getStubUpdatingIndex() {
    return ((FileBasedIndexEx)FileBasedIndex.getInstance()).getIndex(StubUpdatingIndex.INDEX_ID);
  }

  @SuppressWarnings("unchecked")
  private static @Nullable VirtualFile extractSingleFile(@Nullable GlobalSearchScope scope) {
    if (!(scope instanceof Iterable)) {
      return null;
    }
    Iterable<VirtualFile> scopeAsFileIterable = (Iterable<VirtualFile>)scope;
    VirtualFile result = null;
    for (VirtualFile file : scopeAsFileIterable) {
      if (result == null) {
        result = ObjectUtils.notNull(file, NullVirtualFile.INSTANCE);
      }
      else {
        return null;
      }
    }
    return result;
  }

  private static final class KeyAndFileId<K> {
    @NotNull
    private final K key;
    private final int fileId;

    private KeyAndFileId(@NotNull K key, int fileId) {
      this.key = key;
      this.fileId = fileId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      KeyAndFileId<?> key1 = (KeyAndFileId<?>)o;
      return fileId == key1.fileId && Objects.equals(key, key1.key);
    }

    @Override
    public int hashCode() {
      return Objects.hash(key, fileId);
    }
  }

  @TestOnly
  public boolean areAllProblemsProcessedInTheCurrentThread() {
    return myStubProcessingHelper.areAllProblemsProcessedInTheCurrentThread();
  }
}