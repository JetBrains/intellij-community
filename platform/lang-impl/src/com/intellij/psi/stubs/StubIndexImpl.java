// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.google.common.collect.Iterators;
import com.google.common.util.concurrent.Futures;
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
import com.intellij.util.io.IOUtil;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
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
      if (ProgressManager.getInstance().isInNonCancelableSection()) {
        try {
          state = Futures.getUnchecked(myStateFuture);
        }
        catch (Exception e) {
          FileBasedIndexImpl.LOG.error(e);
        }
      }
      else {
        state = ProgressIndicatorUtils.awaitWithCheckCanceled(myStateFuture);
      }
      myState = state;
    }
    return state;
  }

  @ApiStatus.Internal
  @TestOnly
  public void waitUntilStubIndexedInitialized() {
    getAsyncState();
  }

  @Override
  public void initializationFailed(@NotNull Throwable error) {
    myStateFuture = new CompletableFuture<>();
    myStateFuture.completeExceptionally(error);
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
        Throwable cause = FileBasedIndexEx.getCauseToRebuildIndex(e);
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

  @ApiStatus.Internal
  @Override
  @SuppressWarnings("unchecked")
  protected <Key> UpdatableIndex<Key, Void, FileContent> getIndex(@NotNull StubIndexKey<Key, ?> indexKey) {
    return (UpdatableIndex<Key, Void, FileContent>)getAsyncState().myIndices.get(indexKey);
  }

  @Override
  public void forceRebuild(@NotNull Throwable e) {
    FileBasedIndex.getInstance().scheduleRebuild(StubUpdatingIndex.INDEX_ID, e);
  }

  @Override
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

  @Override
  protected void clearState() {
    super.clearState();
    myStateFuture = null;
    myState = null;
    myInitialized = false;
    LOG.info("StubIndexExtension-s were unloaded");
  }

  @Override
  void setDataBufferingEnabled(final boolean enabled) {
    AsyncState state = ProgressManager.getInstance().computeInNonCancelableSection(this::getAsyncState);
    for (UpdatableIndex<?, ?, ?> index : state.myIndices.values()) {
      index.setBufferingEnabled(enabled);
    }
  }

  @Override
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

  @Override
  public @NotNull Logger getLogger() {
    return LOG;
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
          myWrappedExtension.traceKeyHashToVirtualFileMapping(),
          myWrappedExtension.enableWal()
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
}