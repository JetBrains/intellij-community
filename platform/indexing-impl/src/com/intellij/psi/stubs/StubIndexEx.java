// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.model.ModelBranchImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.CompactVirtualFileSet;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.*;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.diagnostic.IndexAccessValidator;
import com.intellij.util.indexing.impl.AbstractUpdateData;
import com.intellij.util.indexing.impl.KeyValueUpdateProcessor;
import com.intellij.util.indexing.impl.RemovedKeyProcessor;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.VoidDataExternalizer;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectIterators;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.function.IntPredicate;

@ApiStatus.Internal
public abstract class StubIndexEx extends StubIndex {
  static void initExtensions() {
    // initialize stub index keys
    for (StubIndexExtension<?, ?> extension : StubIndexExtension.EP_NAME.getExtensionList()) {
      extension.getKey();
    }
  }

  private final Map<StubIndexKey<?, ?>, CachedValue<Map<KeyAndFileId<?>, StubIdList>>> myCachedStubIds = FactoryMap.createMap(k -> {
    UpdatableIndex<Integer, SerializedStubTree, FileContent> index = getStubUpdatingIndex();
    ModificationTracker tracker = index::getModificationStamp;
    return new CachedValueImpl<>(() -> new CachedValueProvider.Result<>(new ConcurrentHashMap<>(), tracker));
  }, ConcurrentHashMap::new);

  private final StubProcessingHelper myStubProcessingHelper = new StubProcessingHelper();
  private final IndexAccessValidator myAccessValidator = new IndexAccessValidator();

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
        if (FileBasedIndexEx.DO_TRACE_STUB_INDEX_UPDATE) {
          getLogger().info("stub index '" + stubIndexKey + "' update: " + fileId +
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

            if (FileBasedIndexEx.DO_TRACE_STUB_INDEX_UPDATE) {
              getLogger().info("keys iteration finished updated_id = " + System.identityHashCode(newKeys) + "; modified = " + modified);
            }

            return modified;
          }
        });
      }
      catch (StorageException e) {
        getLogger().info(e);
        forceRebuild(e);
      }
    });
  }

  @ApiStatus.Experimental
  @ApiStatus.Internal
  @NotNull
  public abstract Logger getLogger();

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
      if (project instanceof LightEditCompatible) return false;
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

    Iterator<VirtualFile> singleFileInScope = extractSingleFile(scope);
    Iterator<VirtualFile> fileStream;
    boolean shouldHaveKeys;

    if (singleFileInScope != null) {
      if (!(singleFileInScope.hasNext())) return true;
      FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, scope);
      fileStream = singleFileInScope;
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
      final Throwable cause = FileBasedIndexEx.getCauseToRebuildIndex(e);
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

  @ApiStatus.Experimental
  @ApiStatus.Internal
  protected abstract <Key> UpdatableIndex<Key, Void, FileContent> getIndex(@NotNull StubIndexKey<Key, ?> indexKey);

  // Self repair for IDEA-181227, caused by (yet) unknown file event processing problem in indices
  // FileBasedIndex.requestReindex doesn't handle the situation properly because update requires old data that was lost
  private <Key> void wipeProblematicFileIdsForParticularKeyAndStubIndex(@NotNull StubIndexKey<Key, ?> indexKey,
                                                                        @NotNull Key key) {
    Set<VirtualFile> filesWithProblems = myStubProcessingHelper.takeAccumulatedFilesWithIndexProblems();

    if (filesWithProblems != null) {
      getLogger().info("data for " + indexKey.getName() + " will be wiped for a some files because of internal stub processing error");
      ((FileBasedIndexEx)FileBasedIndex.getInstance()).runCleanupAction(() -> {
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

    if (idFilter == null) {
      idFilter = fileBasedIndexEx.extractIdFilter(scope, scope.getProject());
    }

    try {
      @Nullable IdFilter finalIdFilter = idFilter;
      return myAccessValidator.validate(StubUpdatingIndex.INDEX_ID, ()->FileBasedIndexEx.disableUpToDateCheckIn(()->
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
  public @NotNull <Key> Iterator<VirtualFile> getContainingFilesIterator(@NotNull StubIndexKey<Key, ?> indexKey,
                                                                         @NotNull Key dataKey,
                                                                         @NotNull Project project,
                                                                         @NotNull GlobalSearchScope scope) {
    IntSet result = getContainingIds(indexKey, dataKey, project, null, scope);
    Set<VirtualFile> fileSet = new CompactVirtualFileSet(result == null ? ArrayUtil.EMPTY_INT_ARRAY : result.toIntArray()).freeze();
    return fileSet.stream().filter(scope::contains).iterator();
  }

  @Override
  public <Key> int getMaxContainingFileCount(@NotNull StubIndexKey<Key, ?> indexKey,
                                             @NotNull Key dataKey,
                                             @NotNull Project project,
                                             @NotNull GlobalSearchScope scope) {
    IntSet result = getContainingIds(indexKey, dataKey, project, null, scope);
    return result == null ? 0 : result.size();
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

    IdFilter finalIdFilter = idFilter != null
                             ? idFilter
                             : ((FileBasedIndexEx)FileBasedIndex.getInstance()).extractIdFilter(scope, project);

    UpdatableIndex<Integer, SerializedStubTree, FileContent> stubUpdatingIndex = fileBasedIndex.getIndex(stubUpdatingIndexId);

    try {
      IntSet result = new IntLinkedOpenHashSet(); // workaround duplicates keys
      myAccessValidator.validate(stubUpdatingIndexId, ()-> {
        // disable up-to-date check to avoid locks on attempt to acquire index write lock while holding at the same time the readLock for this index
        //noinspection Convert2Lambda (workaround for JBR crash, JBR-2349),Convert2Diamond
        return FileBasedIndexEx.disableUpToDateCheckIn(() -> ConcurrencyUtil.withLock(stubUpdatingIndex.getLock().readLock(), () ->
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
      final Throwable cause = FileBasedIndexEx.getCauseToRebuildIndex(e);
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
  void setDataBufferingEnabled(final boolean enabled) { }

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
  static UpdatableIndex<Integer, SerializedStubTree, FileContent> getStubUpdatingIndex() {
    return ((FileBasedIndexEx)FileBasedIndex.getInstance()).getIndex(StubUpdatingIndex.INDEX_ID);
  }

  @SuppressWarnings("unchecked")
  private static @Nullable Iterator<VirtualFile> extractSingleFile(@Nullable GlobalSearchScope scope) {
    if (!(scope instanceof Iterable)) {
      return null;
    }
    Iterable<VirtualFile> scopeAsFileIterable = (Iterable<VirtualFile>)scope;
    Iterator<VirtualFile> result = null;
    for (VirtualFile file : scopeAsFileIterable) {
      if (result == null) {
        result = file != null ? ObjectIterators.singleton(file) : ObjectIterators.emptyIterator();
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
