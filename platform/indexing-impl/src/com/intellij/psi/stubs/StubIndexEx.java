// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.CachedValueImpl;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.UpdateData;
import com.intellij.util.indexing.impl.UpdateData.ForwardIndexUpdate;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.MeasurableIndexStore;
import com.intellij.util.io.VoidDataExternalizer;
import com.intellij.util.progress.CancellationUtil;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import static com.intellij.util.indexing.diagnostic.IndexLookupTimingsReporting.IndexOperationFusCollector.TRACE_OF_STUB_ENTRIES_LOOKUP;
import static com.intellij.util.indexing.diagnostic.IndexLookupTimingsReporting.IndexOperationFusCollector.lookupStubEntriesStarted;

@ApiStatus.Internal
public abstract class StubIndexEx extends StubIndex {
  static void initExtensions() {
    // initialize stub index keys
    for (StubIndexExtension<?, ?> extension : StubIndexExtension.EP_NAME.getExtensionList()) {
      extension.getKey();
    }
  }

  private final Map<StubIndexKey<?, ?>, CachedValue<Map<KeyAndFileId<?>, StubIdList>>> myCachedStubIds = FactoryMap.createMap(k -> {
    UpdatableIndex<Integer, SerializedStubTree, FileContent, ?> index = getStubUpdatingIndex();
    ModificationTracker tracker = index::getModificationStamp;
    return new CachedValueImpl<>(() -> new CachedValueProvider.Result<>(new ConcurrentHashMap<>(), tracker));
  }, ConcurrentHashMap::new);

  private final StubProcessingHelper myStubProcessingHelper = new StubProcessingHelper();

  @ApiStatus.Internal
  abstract void initializeStubIndexes();

  @ApiStatus.Internal
  public abstract void initializationFailed(@NotNull Throwable error);

  public <K> void updateIndex(@NotNull StubIndexKey<K, ?> stubIndexKey,
                              int fileId,
                              @NotNull Set<? extends K> oldKeys,
                              @NotNull Set<? extends K> newKeys) {
    ProgressManager.getInstance().executeNonCancelableSection(() -> {
      try {
        if (FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES) {
          getLogger().info("stub index '" + stubIndexKey + "' update: " + fileId +
                           " old = " + Arrays.toString(oldKeys.toArray()) +
                           " new  = " + Arrays.toString(newKeys.toArray()) +
                           " updated_id = " + System.identityHashCode(newKeys));
        }
        UpdatableIndex<K, Void, FileContent, ?> index = getIndex(stubIndexKey);
        if (index == null) return;

        index.updateWith(new UpdateData<>(
          fileId,
          index.getExtension().getName(),

          changedEntriesProcessor -> {

            boolean modified = false;
            for (K oldKey : oldKeys) {
              if (!newKeys.contains(oldKey)) {
                changedEntriesProcessor.removed(oldKey, fileId);
                if (!modified) modified = true;
              }
            }

            for (K newKey : newKeys) {
              if (!oldKeys.contains(newKey)) {
                changedEntriesProcessor.added(newKey, null, fileId);
                if (!modified) modified = true;
              }
            }

            if (FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES) {
              getLogger().info("keys iteration finished updated_id = " + System.identityHashCode(newKeys) + "; modified = " + modified);
            }

            return modified;
          },
          
          ForwardIndexUpdate.NOOP
        ));
      }
      catch (StorageException e) {
        getLogger().info(e);
        forceRebuild(e);
      }
    });
  }

  @ApiStatus.Experimental
  @ApiStatus.Internal
  public abstract @NotNull Logger getLogger();

  @Override
  public <Key, Psi extends PsiElement> boolean processElements(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                               @NotNull Key key,
                                                               @NotNull Project project,
                                                               @Nullable GlobalSearchScope scope,
                                                               @Nullable IdFilter idFilter,
                                                               @NotNull Class<Psi> requiredClass,
                                                               @NotNull Processor<? super Psi> processor) {
    var trace = lookupStubEntriesStarted(indexKey)
      .withProject(project);

    try {
      boolean dumb = DumbService.isDumb(project);
      if (dumb) {
        if (project instanceof LightEditCompatible) return false;
        DumbModeAccessType accessType = FileBasedIndex.getInstance().getCurrentDumbModeAccessType();
        if (accessType == DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE &&
            Registry.is("ide.dumb.mode.check.awareness")) {
          throw new AssertionError("raw index data access is not available for StubIndex");
        }
      }

      Predicate<? super Psi> keyFilter = StubIndexKeyDescriptorCache.INSTANCE.getKeyPsiMatcher(indexKey, key);
      Processor<? super Psi> processorWithKeyFilter = keyFilter == null
                                                      ? processor
                                                      : o -> !keyFilter.test(o) || processor.process(o);

      PairProcessor<VirtualFile, StubIdList> stubProcessor = (file, list) -> {
        return myStubProcessingHelper.processStubsInFile(
          project, file, list, processorWithKeyFilter, scope, requiredClass,
          () -> "Looking for " + key + " in " + indexKey);
      };

      Iterator<VirtualFile> singleFileInScope = FileBasedIndexEx.extractSingleFileOrEmpty(scope);
      Iterator<VirtualFile> fileStream;
      boolean shouldHaveKeys;

      if (singleFileInScope != null) {
        if (!(singleFileInScope.hasNext())) return true;
        FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, scope);
        fileStream = singleFileInScope;
        trace.lookupResultSize(1);
        shouldHaveKeys = false;
      }
      else {
        IntSet fileIds = getContainingIds(indexKey, key, project, idFilter, scope);
        if (fileIds == null) {
          trace.lookupResultSize(0);
          return true;
        }
        else {
          trace.lookupResultSize(fileIds.size());
        }
        IntPredicate accessibleFileFilter = ((FileBasedIndexEx)FileBasedIndex.getInstance()).getAccessibleFileIdFilter(project);

        // already ensured up-to-date in getContainingIds() method
        IntIterator idIterator = fileIds.iterator();
        fileStream = StubIndexImplUtil.mapIdIterator(idIterator, accessibleFileFilter);
        shouldHaveKeys = true;
      }

      trace.stubTreesDeserializingStarted();

      try {
        while (fileStream.hasNext()) {
          VirtualFile file = fileStream.next();
          assert file != null;
          if (scope != null && !scope.contains(file)) {
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
          if (!stubProcessor.process(file, list)) {
            return false;
          }
        }
      }
      catch (RuntimeException e) {
        trace.lookupFailed();
        Throwable cause = FileBasedIndexEx.extractCauseToRebuildIndex(e);
        if (cause != null) {
          forceRebuild(cause);
        }
        else {
          throw e;
        }
      }
      return true;
    }
    catch (Throwable t) {
      trace.lookupFailed();
      throw t;
    }
    finally {
      tryFixIndexesForProblemFiles(indexKey, key, project);
      //Not using try-with-resources because in case of exceptions are thrown, .close() needs to be called _after_ catch,
      //  so .lookupFailed() is invoked on a not-yet-closed trace -- but TWR does the opposite: first close resources, then
      //  do all catch/finally blocks
      trace.close();
    }
  }

  @ApiStatus.Experimental
  @ApiStatus.Internal
  protected abstract <Key> UpdatableIndex<Key, Void, FileContent, ?> getIndex(@NotNull StubIndexKey<Key, ?> indexKey);

  // Self repair for IDEA-181227, caused by (yet) unknown file event processing problem in indices
  // FileBasedIndex.requestReindex doesn't handle the situation properly because update requires old data that was lost
  private <Key> void tryFixIndexesForProblemFiles(@NotNull StubIndexKey<Key, ?> indexKey, @NotNull Key key, @NotNull Project project) {
    Set<VirtualFile> filesWithProblems = myStubProcessingHelper.takeAccumulatedFilesWithIndexProblems();

    if (filesWithProblems != null) {
      List<String> fileNames = ContainerUtil.map(filesWithProblems, f -> f.getName());
      String fileNamesStr = StringUtil.first(StringUtil.join(fileNames, ","), 300, true);
      getLogger().info("Data for " + fileNamesStr + " will be re-indexes because of internal stub processing error. Recomputing index request");

      // clear possibly inconsistent key
      ((FileBasedIndexEx)FileBasedIndex.getInstance()).runCleanupAction(() -> {
        UpdatableIndex<Integer, SerializedStubTree, FileContent, ?> index = getStubUpdatingIndex();

        for (VirtualFile file : filesWithProblems) {
          int fileId = FileBasedIndex.getFileId(file);
          index.mapInputAndPrepareUpdate(fileId, null).update();
        }

        Lock writeLock = getIndex(indexKey).getLock().writeLock();
        writeLock.lock();
        try {
          for (VirtualFile file : filesWithProblems) {
            int fileId = FileBasedIndex.getFileId(file);
            updateIndex(indexKey,
                        fileId,
                        Collections.singleton(key),
                        Collections.emptySet());
          }
        }
        finally {
          writeLock.unlock();
        }

        index.cleanupMemoryStorage();
      });

      // schedule indexes to rebuild
      for (VirtualFile file: filesWithProblems) {
        FileBasedIndex.getInstance().requestReindex(file);
      }

      // drop caches
      ApplicationManager.getApplication().invokeLater(() -> WriteAction.run(() -> {
        PsiManager psiManager = PsiManager.getInstance(project);
        psiManager.dropPsiCaches();
        psiManager.dropResolveCaches();
      }), project.getDisposed());
    }
  }

  @Override
  public @NotNull <K> Collection<K> getAllKeys(@SuppressWarnings("BoundedWildcard") @NotNull StubIndexKey<K, ?> indexKey,
                                               @NotNull Project project) {
    Set<K> allKeys = new HashSet<>();
    processAllKeys(indexKey, project, Processors.cancelableCollectProcessor(allKeys));
    return allKeys;
  }

  @Override
  public <K> boolean processAllKeys(@NotNull StubIndexKey<K, ?> indexKey,
                                    @NotNull Processor<? super K> processor,
                                    @NotNull GlobalSearchScope scope,
                                    @Nullable IdFilter idFilter) {
    final UpdatableIndex<K, Void, FileContent, ?> index = getIndex(indexKey); // wait for initialization to finish
    FileBasedIndexEx fileBasedIndexEx = (FileBasedIndexEx)FileBasedIndex.getInstance();
    if (index == null ||
        !fileBasedIndexEx.ensureUpToDate(StubUpdatingIndex.INDEX_ID, scope.getProject(), scope, null)) {
      return true;
    }

    if (idFilter == null) {
      idFilter = fileBasedIndexEx.extractIdFilter(scope, scope.getProject());
    }

    try {
      @Nullable IdFilter finalIdFilter = idFilter;
      return FileBasedIndexEx.disableUpToDateCheckIn(() -> index.processAllKeys(processor, scope, finalIdFilter));
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
  public @NotNull <Key> Iterator<VirtualFile> getContainingFilesIterator(@NotNull StubIndexKey<Key, ?> indexKey,
                                                                         @NotNull Key dataKey,
                                                                         @NotNull Project project,
                                                                         @NotNull GlobalSearchScope scope) {
    IntSet result = getContainingIds(indexKey, dataKey, project, null, scope);
    return FileBasedIndexEx.createLazyFileIterator(result, scope);
  }

  @Override
  public <Key> int getMaxContainingFileCount(@NotNull StubIndexKey<Key, ?> indexKey,
                                             @NotNull Key dataKey,
                                             @NotNull Project project,
                                             @NotNull GlobalSearchScope scope) {
    IntSet result = getContainingIds(indexKey, dataKey, project, null, scope);
    return result == null ? 0 : result.size();
  }

  /**
   * @return set of fileId of files containing lookup key (dataKey)
   */
  private @Nullable <Key> IntSet getContainingIds(@NotNull StubIndexKey<Key, ?> indexKey,
                                                  @NotNull Key dataKey,
                                                  @NotNull Project project,
                                                  @Nullable IdFilter idFilter,
                                                  @Nullable GlobalSearchScope scope) {
    var trace = TRACE_OF_STUB_ENTRIES_LOOKUP.get();
    FileBasedIndexEx fileBasedIndex = (FileBasedIndexEx)FileBasedIndex.getInstance();
    ID<Integer, SerializedStubTree> stubUpdatingIndexId = StubUpdatingIndex.INDEX_ID;
    UpdatableIndex<Key, Void, FileContent, ?> index = getIndex(indexKey);   // wait for initialization to finish
    if (index == null || !fileBasedIndex.ensureUpToDate(stubUpdatingIndexId, project, scope, null)) return null;

    trace.indexValidationFinished();

    IdFilter finalIdFilter = idFilter != null
                             ? idFilter
                             : ((FileBasedIndexEx)FileBasedIndex.getInstance()).extractIdFilter(scope, project);

    UpdatableIndex<Integer, SerializedStubTree, FileContent, ?> stubUpdatingIndex = fileBasedIndex.getIndex(stubUpdatingIndexId);

    try {
      // workaround duplicates keys
      var action = new ValueContainer.ContainerAction<Void>() {
        IntSet result = null;

        @Override
        public boolean perform(int id, Void value) {
          if (finalIdFilter == null || finalIdFilter.containsFileId(id)) {
            if (result == null) {
              result = new IntLinkedOpenHashSet();
            }
            result.add(id);
          }
          return true;
        }
      };
      trace.totalKeysIndexed(MeasurableIndexStore.keysCountApproximatelyIfPossible(index));
      // disable up-to-date check to avoid locks on an attempt to acquire index write lock
      // while holding at the same time the readLock for this index
      FileBasedIndexEx.disableUpToDateCheckIn(() -> {
        Lock lock = stubUpdatingIndex.getLock().readLock();
        CancellationUtil.lockMaybeCancellable(lock);
        try {
          return index.getData(dataKey).forEach(action);
        }
        finally {
          lock.unlock();
        }
      });
      return action.result == null ? IntSets.EMPTY_SET : action.result;
    }
    catch (StorageException e) {
      trace.lookupFailed();
      forceRebuild(e);
    }
    catch (RuntimeException e) {
      trace.lookupFailed();
      Throwable cause = FileBasedIndexEx.extractCauseToRebuildIndex(e);
      if (cause != null) {
        forceRebuild(cause);
      }
      else {
        throw e;
      }
    }

    return null;
  }

  @ApiStatus.Internal
  protected void clearState() {
    StubIndexKeyDescriptorCache.INSTANCE.clear();
    ((SerializationManagerImpl)SerializationManagerEx.getInstanceEx()).dropSerializerData();
    myCachedStubIds.clear();
  }

  @ApiStatus.Internal
  void setDataBufferingEnabled(boolean enabled) { }

  @ApiStatus.Internal
  void cleanupMemoryStorage() { }

  @ApiStatus.Internal
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

  @ApiStatus.Internal
  static UpdatableIndex<Integer, SerializedStubTree, FileContent, ?> getStubUpdatingIndex() {
    return ((FileBasedIndexEx)FileBasedIndex.getInstance()).getIndex(StubUpdatingIndex.INDEX_ID);
  }

  private record KeyAndFileId<K>(@NotNull K key, int fileId) {
  }

  @TestOnly
  public boolean areAllProblemsProcessedInTheCurrentThread() {
    return myStubProcessingHelper.areAllProblemsProcessedInTheCurrentThread();
  }

  @ApiStatus.Internal
  public void cleanCaches() {
    myCachedStubIds.clear();
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  public interface FileUpdateProcessor {
    void processUpdate(@NotNull VirtualFile file);
    default void endUpdatesBatch() {}
  }
  @ApiStatus.Internal
  @ApiStatus.Experimental
  public abstract @NotNull FileUpdateProcessor getPerFileElementTypeModificationTrackerUpdateProcessor();
}
