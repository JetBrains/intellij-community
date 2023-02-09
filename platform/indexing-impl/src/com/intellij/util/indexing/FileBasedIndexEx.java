// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.model.ModelBranch;
import com.intellij.model.ModelBranchImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CompactVirtualFileSet;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.indexing.impl.InvertedIndexValueIterator;
import com.intellij.util.indexing.impl.MapReduceIndexMappingException;
import com.intellij.util.indexing.roots.IndexableFilesContributor;
import com.intellij.util.indexing.roots.IndexableFilesDeduplicateFilter;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.snapshot.SnapshotInputMappingException;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.ObjectIterators;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

import static com.intellij.util.indexing.diagnostic.IndexOperationFUS.IndexOperationFusCollector.*;
import static com.intellij.util.io.MeasurableIndexStore.keysCountApproximatelyIfPossible;

@ApiStatus.Internal
public abstract class FileBasedIndexEx extends FileBasedIndex {
  public static final boolean DO_TRACE_STUB_INDEX_UPDATE = Boolean.getBoolean("idea.trace.stub.index.update");
  @SuppressWarnings("SSBasedInspection")
  private static final ThreadLocal<Stack<DumbModeAccessType>> ourDumbModeAccessTypeStack =
    ThreadLocal.withInitial(() -> new com.intellij.util.containers.Stack<>());
  private static final RecursionGuard<Object> ourIgnoranceGuard = RecursionManager.createGuard("ignoreDumbMode");
  private volatile boolean myTraceIndexUpdates;
  private volatile boolean myTraceStubIndexUpdates;
  private volatile boolean myTraceSharedIndexUpdates;

  @ApiStatus.Internal
  boolean doTraceIndexUpdates() {
    return myTraceIndexUpdates;
  }

  @ApiStatus.Internal
  public boolean doTraceStubUpdates(@NotNull ID<?, ?> indexId) {
    return myTraceStubIndexUpdates && indexId.equals(StubUpdatingIndex.INDEX_ID);
  }

  @ApiStatus.Internal
  boolean doTraceSharedIndexUpdates() {
    return myTraceSharedIndexUpdates;
  }

  @ApiStatus.Internal
  public void loadIndexes() {
    myTraceIndexUpdates = SystemProperties.getBooleanProperty("trace.file.based.index.update", false);
    myTraceStubIndexUpdates = SystemProperties.getBooleanProperty("trace.stub.index.update", false);
    myTraceSharedIndexUpdates = SystemProperties.getBooleanProperty("trace.shared.index.update", false);
  }

  @ApiStatus.Internal
  @NotNull
  public abstract IntPredicate getAccessibleFileIdFilter(@Nullable Project project);

  @Nullable
  @ApiStatus.Internal
  public abstract IdFilter extractIdFilter(@Nullable GlobalSearchScope scope,
                                           @Nullable Project project);

  @Nullable
  @ApiStatus.Internal
  public abstract IdFilter projectIndexableFiles(@Nullable Project project);

  @NotNull
  @ApiStatus.Internal
  public abstract <K, V> UpdatableIndex<K, V, FileContent, ?> getIndex(ID<K, V> indexId);

  @ApiStatus.Internal
  public abstract void waitUntilIndicesAreInitialized();

  /**
   * @return true if index can be processed after it or
   * false if no need to process it because, for example, scope is empty or index is going to rebuild.
   */
  @ApiStatus.Internal
  public abstract <K> boolean ensureUpToDate(@NotNull final ID<K, ?> indexId,
                                             @Nullable Project project,
                                             @Nullable GlobalSearchScope filter,
                                             @Nullable VirtualFile restrictedFile);

  @Override
  @NotNull
  public <K, V> List<V> getValues(@NotNull final ID<K, V> indexId, @NotNull K dataKey, @NotNull final GlobalSearchScope filter) {
    @Nullable Iterator<VirtualFile> restrictToFileIt = extractSingleFileOrEmpty(filter);

    final List<V> values = new SmartList<>();
    ValueProcessor<V> processor = (file, value) -> {
      values.add(value);
      return true;
    };
    if (restrictToFileIt != null) {
      VirtualFile restrictToFile = restrictToFileIt.hasNext() ? restrictToFileIt.next() : null;
      if (restrictToFile == null) return Collections.emptyList();
      processValuesInOneFile(indexId, dataKey, restrictToFile, filter, processor);
    }
    else {
      processValuesInScope(indexId, dataKey, true, filter, null, processor);
    }
    return values;
  }

  @Override
  @NotNull
  public <K> Collection<K> getAllKeys(@NotNull final ID<K, ?> indexId, @NotNull Project project) {
    Set<K> allKeys = new HashSet<>();
    processAllKeys(indexId, Processors.cancelableCollectProcessor(allKeys), project);
    return allKeys;
  }

  @Override
  public <K> boolean processAllKeys(@NotNull ID<K, ?> indexId, @NotNull Processor<? super K> processor, @Nullable Project project) {
    return processAllKeys(indexId, processor, project == null ? new EverythingGlobalScope() : GlobalSearchScope.everythingScope(project),
                          null);
  }

  @Override
  public <K> boolean processAllKeys(@NotNull ID<K, ?> indexId,
                                    @NotNull Processor<? super K> processor,
                                    @NotNull GlobalSearchScope scope,
                                    @Nullable IdFilter idFilter) {
    var trace = lookupAllKeysStarted(indexId)
      .withProject(scope.getProject());
    try {
      waitUntilIndicesAreInitialized();
      UpdatableIndex<K, ?, FileContent, ?> index = getIndex(indexId);
      if (!ensureUpToDate(indexId, scope.getProject(), scope, null)) {
        return true;
      }

      trace.indexValidationFinished();

      IdFilter idFilterAdjusted = idFilter == null ? extractIdFilter(scope, scope.getProject()) : idFilter;
      trace.totalKeysIndexed(keysCountApproximatelyIfPossible(index));
      return index.processAllKeys(processor, scope, idFilterAdjusted);
    }
    catch (StorageException e) {
      trace.lookupFailed();
      scheduleRebuild(indexId, e);
    }
    catch (RuntimeException e) {
      trace.lookupFailed();
      final Throwable cause = e.getCause();
      if (cause instanceof StorageException || cause instanceof IOException) {
        scheduleRebuild(indexId, cause);
      }
      else {
        throw e;
      }
    }
    finally {
      //Not using try-with-resources because in case of exceptions are thrown, .close() needs to be called _after_ catch,
      //  so .lookupFailed() is invoked on a not-yet-closed trace -- but TWR does the opposite: first close resources, then
      //  do all catch/finally blocks
      trace.close();
    }

    return false;
  }

  @NotNull
  @Override
  public <K, V> Map<K, V> getFileData(@NotNull ID<K, V> id, @NotNull VirtualFile virtualFile, @NotNull Project project) {
    if (!(virtualFile instanceof VirtualFileWithId)) return Collections.emptyMap();
    int fileId = getFileId(virtualFile);

    if (getAccessibleFileIdFilter(project).test(fileId)) {
      Map<K, V> map = processExceptions(id, virtualFile, GlobalSearchScope.fileScope(project, virtualFile), index -> {

        if ((IndexDebugProperties.DEBUG && !ApplicationManager.getApplication().isUnitTestMode()) &&
            !((FileBasedIndexExtension<K, V>)index.getExtension()).needsForwardIndexWhenSharing()) {
          getLogger().error("Index extension " + id + " doesn't require forward index but accesses it");
        }

        return index.getIndexedFileData(fileId);
      });
      return ContainerUtil.notNullize(map);
    }
    return Collections.emptyMap();
  }

  @Override
  public <V> @Nullable V getSingleEntryIndexData(@NotNull ID<Integer, V> id,
                                                 @NotNull VirtualFile virtualFile,
                                                 @NotNull Project project) {
    if (!(getIndex(id).getExtension() instanceof SingleEntryFileBasedIndexExtension)) {
      throw new IllegalArgumentException("'" + id + "' index is not a SingleEntryFileBasedIndex");
    }
    Map<Integer, V> data = getFileData(id, virtualFile, project);
    if (data.isEmpty()) return null;
    if (data.size() == 1) return data.values().iterator().next();
    throw new IllegalStateException("Invalid single entry index data '" + id + "'");
  }

  @Override
  @NotNull
  public <K, V> Collection<VirtualFile> getContainingFiles(@NotNull ID<K, V> indexId,
                                                           @NotNull K dataKey,
                                                           @NotNull GlobalSearchScope filter) {
    return ContainerUtil.newHashSet(getContainingFilesIterator(indexId, dataKey, filter));
  }

  @Override
  public @NotNull <K, V> Iterator<VirtualFile> getContainingFilesIterator(@NotNull ID<K, V> indexId,
                                                                          @NotNull K dataKey,
                                                                          @NotNull GlobalSearchScope scope) {
    Project project = scope.getProject();
    try (var trace = lookupEntriesStarted(indexId)) {
      trace.keysWithAND(1)
        .withProject(project);

      if (project instanceof LightEditCompatible) return Collections.emptyIterator();
      @Nullable Iterator<VirtualFile> restrictToFileIt = extractSingleFileOrEmpty(scope);
      if (restrictToFileIt != null) {
        VirtualFile restrictToFile = restrictToFileIt.hasNext() ? restrictToFileIt.next() : null;
        if (restrictToFile == null) return Collections.emptyIterator();
        return !processValuesInOneFile(indexId, dataKey, restrictToFile, scope, (f, v) -> false) ?
               Collections.singleton(restrictToFile).iterator() : Collections.emptyIterator();
      }

      IdFilter filter = extractIdFilter(scope, project);
      IntPredicate accessibleFileFilter = getAccessibleFileIdFilter(project);

      IntSet fileIds = processExceptions(indexId, null, scope, index -> {
        IntSet fileIdsInner = new IntOpenHashSet();
        trace.totalKeysIndexed(keysCountApproximatelyIfPossible(index));
        index.getData(dataKey).forEach((id, value) -> {
          if (!accessibleFileFilter.test(id) || (filter != null && !filter.containsFileId(id))) return true;
          fileIdsInner.add(id);
          return true;
        });
        return fileIdsInner;
      });

      trace.lookupResultSize(fileIds != null ? fileIds.size() : 0);

      return createLazyFileIterator(fileIds, scope);
    }
  }

  @Override
  public <K, V> boolean processValues(@NotNull final ID<K, V> indexId, @NotNull final K dataKey, @Nullable final VirtualFile inFile,
                                      @NotNull ValueProcessor<? super V> processor, @NotNull final GlobalSearchScope filter) {
    return processValues(indexId, dataKey, inFile, processor, filter, null);
  }

  @Override
  public <K, V> boolean processValues(@NotNull ID<K, V> indexId,
                                      @NotNull K dataKey,
                                      @Nullable VirtualFile inFile,
                                      @NotNull ValueProcessor<? super V> processor,
                                      @NotNull GlobalSearchScope filter,
                                      @Nullable IdFilter idFilter) {
    return inFile != null
           ? processValuesInOneFile(indexId, dataKey, inFile, filter, processor)
           : processValuesInScope(indexId, dataKey, false, filter, idFilter, processor);
  }

  @Override
  public <K, V> long getIndexModificationStamp(@NotNull ID<K, V> indexId, @NotNull Project project) {
    waitUntilIndicesAreInitialized();
    UpdatableIndex<K, V, FileContent, ?> index = getIndex(indexId);
    ensureUpToDate(indexId, project, GlobalSearchScope.allScope(project));
    return index.getModificationStamp();
  }

  @Nullable
  private <K, V, R> R processExceptions(@NotNull final ID<K, V> indexId,
                                        @Nullable final VirtualFile restrictToFile,
                                        @NotNull final GlobalSearchScope filter,
                                        @NotNull ThrowableConvertor<? super UpdatableIndex<K, V, FileContent, ?>, ? extends R, StorageException> computable) {
    try {
      waitUntilIndicesAreInitialized();
      UpdatableIndex<K, V, FileContent, ?> index = getIndex(indexId);
      Project project = filter.getProject();
      //assert project != null : "GlobalSearchScope#getProject() should be not-null for all index queries";
      if (!ensureUpToDate(indexId, project, filter, restrictToFile)) {
        return null;
      }
      TRACE_OF_ENTRIES_LOOKUP.get()
        .indexValidationFinished();

      return ConcurrencyUtil.withLock(index.getLock().readLock(), () -> computable.convert(index));
    }
    catch (StorageException e) {
      TRACE_OF_ENTRIES_LOOKUP.get().lookupFailed();
      scheduleRebuild(indexId, e);
    }
    catch (RuntimeException e) {
      final Throwable cause = getCauseToRebuildIndex(e);
      if (cause != null) {
        scheduleRebuild(indexId, cause);
      }
      else {
        throw e;
      }
    }
    return null;
  }

  protected <K, V> boolean processValuesInOneFile(@NotNull ID<K, V> indexId,
                                                  @NotNull K dataKey,
                                                  @NotNull VirtualFile restrictToFile,
                                                  @NotNull GlobalSearchScope scope,
                                                  @NotNull ValueProcessor<? super V> processor) {
    Project project = scope.getProject();
    if (!(restrictToFile instanceof VirtualFileWithId)) {
      return project == null ||
             ModelBranch.getFileBranch(restrictToFile) == null ||
             processInMemoryFileData(indexId, dataKey, project, restrictToFile, processor);
    }

    int restrictedFileId = getFileId(restrictToFile);

    if (!getAccessibleFileIdFilter(project).test(restrictedFileId)) return true;

    return processValueIterator(indexId, dataKey, restrictToFile, scope, valueIt -> {
      while (valueIt.hasNext()) {
        V value = valueIt.next();
        if (valueIt.getValueAssociationPredicate().test(restrictedFileId) && !processor.process(restrictToFile, value)) {
          return false;
        }
        ProgressManager.checkCanceled();
      }
      return true;
    });
  }

  private <K, V> boolean processInMemoryFileData(ID<K, V> indexId,
                                                 K dataKey,
                                                 Project project,
                                                 VirtualFile file,
                                                 ValueProcessor<? super V> processor) {
    Map<K, V> data = getFileData(indexId, file, project);
    return !data.containsKey(dataKey) || processor.process(file, data.get(dataKey));
  }

  protected <K, V> boolean processValuesInScope(@NotNull ID<K, V> indexId,
                                                @NotNull K dataKey,
                                                boolean ensureValueProcessedOnce,
                                                @NotNull GlobalSearchScope scope,
                                                @Nullable IdFilter idFilter,
                                                @NotNull ValueProcessor<? super V> processor) {
    Project project = scope.getProject();
    if (project != null &&
        !ModelBranchImpl.processModifiedFilesInScope(scope, file -> processInMemoryFileData(indexId, dataKey, project, file, processor))) {
      return false;
    }

    IdFilter filter = idFilter != null ? idFilter : extractIdFilter(scope, project);
    IntPredicate accessibleFileFilter = getAccessibleFileIdFilter(project);

    return processValueIterator(indexId, dataKey, null, scope, valueIt -> {
      Collection<ModelBranch> branches = null;
      while (valueIt.hasNext()) {
        final V value = valueIt.next();
        for (final ValueContainer.IntIterator inputIdsIterator = valueIt.getInputIdsIterator(); inputIdsIterator.hasNext(); ) {
          final int id = inputIdsIterator.next();
          if (!accessibleFileFilter.test(id) || (filter != null && !filter.containsFileId(id))) continue;
          VirtualFile file = findFileById(id);
          if (file != null) {
            if (branches == null) branches = scope.getModelBranchesAffectingScope();
            for (VirtualFile eachFile : filesInScopeWithBranches(scope, file, branches)) {
              if (!processor.process(eachFile, value)) {
                return false;
              }
              if (ensureValueProcessedOnce) {
                ProgressManager.checkCanceled();
                break; // continue with the next value
              }
            }
          }

          ProgressManager.checkCanceled();
        }
      }
      return true;
    });
  }

  private <K, V> boolean processValueIterator(@NotNull ID<K, V> indexId,
                                              @NotNull K dataKey,
                                              @Nullable VirtualFile restrictToFile,
                                              @NotNull GlobalSearchScope scope,
                                              @NotNull Processor<? super InvertedIndexValueIterator<V>> valueProcessor) {
    try (var trace = lookupEntriesStarted(indexId)) {
      trace.keysWithAND(1)
        .withProject(scope.getProject());
      //TODO RC: .scopeFiles( restrictToFile == null ? -1 : 1 )
      final ThrowableConvertor<UpdatableIndex<K, V, FileContent, ?>, Boolean, StorageException> convertor = index -> {
        trace.totalKeysIndexed(keysCountApproximatelyIfPossible(index));
        var valuesIterator = (InvertedIndexValueIterator<V>)index.getData(dataKey).getValueIterator();
        return valueProcessor.process(valuesIterator);
      };
      final Boolean result = processExceptions(indexId, restrictToFile, scope, convertor);
      return result == null || result.booleanValue();
    }
  }

  @Override
  public <K, V> boolean processFilesContainingAllKeys(@NotNull ID<K, V> indexId,
                                                      @NotNull Collection<? extends K> dataKeys,
                                                      @NotNull GlobalSearchScope filter,
                                                      @Nullable Condition<? super V> valueChecker,
                                                      @NotNull Processor<? super VirtualFile> processor) {
    IdFilter filesSet = extractIdFilter(filter, filter.getProject());
    IntSet set = collectFileIdsContainingAllKeys(indexId, dataKeys, filter, valueChecker, filesSet, null);
    return set != null && processVirtualFiles(set, filter, processor);
  }

  @Override
  public <K, V> boolean processFilesContainingAnyKey(@NotNull ID<K, V> indexId,
                                                     @NotNull Collection<? extends K> dataKeys,
                                                     @NotNull GlobalSearchScope filter,
                                                     @Nullable IdFilter idFilter,
                                                     @Nullable Condition<? super V> valueChecker,
                                                     @NotNull Processor<? super VirtualFile> processor) {
    IdFilter idFilterAdjusted = idFilter != null ? idFilter : extractIdFilter(filter, filter.getProject());
    IntSet set = collectFileIdsContainingAnyKey(indexId, dataKeys, filter, valueChecker, idFilterAdjusted);
    return set != null && processVirtualFiles(set, filter, processor);
  }

  private boolean processFilesContainingAllKeysInPhysicalFiles(@NotNull Collection<? extends AllKeysQuery<?, ?>> queries,
                                                               @NotNull GlobalSearchScope filter,
                                                               Processor<? super VirtualFile> processor,
                                                               IdFilter filesSet) {
    IntSet set = null;
    if (filter instanceof GlobalSearchScope.FilesScope) {
      VirtualFileEnumeration hint = VirtualFileEnumeration.extract(filter);
      set = hint != null ? new IntOpenHashSet(hint.asArray()) : IntSet.of();
    }

    //noinspection rawtypes
    for (AllKeysQuery query : queries) {
      @SuppressWarnings("unchecked")
      IntSet queryResult = collectFileIdsContainingAllKeys(query.getIndexId(),
                                                           query.getDataKeys(),
                                                           filter,
                                                           query.getValueChecker(),
                                                           filesSet,
                                                           set);
      if (queryResult == null) return false;
      if (queryResult.isEmpty()) return true;
      if (set == null) {
        set = new IntOpenHashSet(queryResult);
      }
      else {
        set = queryResult;
      }
    }
    if (set == null || !processVirtualFiles(set, filter, processor)) {
      return false;
    }
    return true;
  }

  @Override
  public boolean processFilesContainingAllKeys(@NotNull Collection<? extends AllKeysQuery<?, ?>> queries,
                                               @NotNull GlobalSearchScope filter,
                                               @NotNull Processor<? super VirtualFile> processor) {
    Project project = filter.getProject();
    IdFilter filesSet = extractIdFilter(filter, filter.getProject());

    if (!processFilesContainingAllKeysInPhysicalFiles(queries, filter, processor, filesSet)) {
      return false;
    }

    if (project == null) return true;
    return ModelBranchImpl.processModifiedFilesInScope(filter, file -> {
      for (AllKeysQuery<?, ?> query : queries) {
        ID<?, ?> id = query.getIndexId();
        Map<?, ?> data = getFileData(id, file, project);
        if (!data.keySet().containsAll(query.getDataKeys())) return true;
      }
      return processor.process(file);
    });
  }

  @Override
  public <K, V> boolean getFilesWithKey(@NotNull final ID<K, V> indexId,
                                        @NotNull final Set<? extends K> dataKeys,
                                        @NotNull Processor<? super VirtualFile> processor,
                                        @NotNull GlobalSearchScope filter) {
    return processFilesContainingAllKeys(indexId, dataKeys, filter, null, processor);
  }


  @Override
  public <K> void scheduleRebuild(@NotNull final ID<K, ?> indexId, @NotNull final Throwable e) {
    requestRebuild(indexId, e);
  }

  /**
   * DO NOT CALL DIRECTLY IN CLIENT CODE
   * The method is internal to indexing engine end is called internally. The method is public due to implementation details
   */
  @Override
  public <K> void ensureUpToDate(@NotNull final ID<K, ?> indexId, @Nullable Project project, @Nullable GlobalSearchScope filter) {
    waitUntilIndicesAreInitialized();
    ensureUpToDate(indexId, project, filter, null);
  }

  @Override
  public void iterateIndexableFiles(@NotNull ContentIterator processor, @NotNull Project project, @Nullable ProgressIndicator indicator) {
    List<IndexableFilesIterator> providers = getIndexableFilesProviders(project);
    IndexableFilesDeduplicateFilter indexableFilesDeduplicateFilter = IndexableFilesDeduplicateFilter.create();
    for (IndexableFilesIterator provider : providers) {
      if (indicator != null) {
        indicator.checkCanceled();
      }
      if (!provider.iterateFiles(project, processor, indexableFilesDeduplicateFilter)) {
        break;
      }
    }
  }

  /**
   * Returns providers of files to be indexed.
   */
  @NotNull
  public List<IndexableFilesIterator> getIndexableFilesProviders(@NotNull Project project) {
    List<String> allowedIteratorPatterns = StringUtil.split(System.getProperty("idea.test.files.allowed.iterators", ""), ";");
    if (project instanceof LightEditCompatible) {
      return Collections.emptyList();
    }
    if (IndexableFilesIndex.isEnabled() && allowedIteratorPatterns.isEmpty()) {
      return IndexableFilesIndex.getInstance(project).getIndexingIterators();
    }
    List<IndexableFilesIterator> providers = IndexableFilesContributor.EP_NAME
      .getExtensionList()
      .stream()
      .flatMap(c -> {
        return ReadAction.nonBlocking(() -> c.getIndexableFiles(project)).expireWith(project).executeSynchronously().stream();
      })
      .collect(Collectors.toList());
    if (!allowedIteratorPatterns.isEmpty()) {
      providers = ContainerUtil.filter(providers, p -> {
        return ContainerUtil.exists(allowedIteratorPatterns, pattern -> p.getDebugName().contains(pattern));
      });
    }
    return providers;
  }

  @Nullable
  private <K, V> IntSet collectFileIdsContainingAllKeys(@NotNull ID<K, V> indexId,
                                                        @NotNull Collection<? extends K> dataKeys,
                                                        @NotNull GlobalSearchScope scope,
                                                        @Nullable Condition<? super V> valueChecker,
                                                        @Nullable IdFilter projectFilesFilter,
                                                        @Nullable IntSet restrictedIds) {
    try (var trace = lookupEntriesStarted(indexId)) {
      trace.keysWithAND(dataKeys.size())
        .withProject(scope.getProject());

      IntPredicate accessibleFileFilter = getAccessibleFileIdFilter(scope.getProject());
      IntPredicate idChecker = id -> (projectFilesFilter == null || projectFilesFilter.containsFileId(id)) &&
                                     accessibleFileFilter.test(id) &&
                                     (restrictedIds == null || restrictedIds.contains(id));
      ThrowableConvertor<UpdatableIndex<K, V, FileContent, ?>, IntSet, StorageException> convertor = index -> {
        trace.totalKeysIndexed(keysCountApproximatelyIfPossible(index));
        IndexDebugProperties.DEBUG_INDEX_ID.set(indexId);
        try {
          return InvertedIndexUtil.collectInputIdsContainingAllKeys(index, dataKeys, valueChecker, idChecker);
        }
        finally {
          IndexDebugProperties.DEBUG_INDEX_ID.remove();
        }
      };

      final IntSet ids = processExceptions(indexId, null, scope, convertor);

      trace.lookupResultSize(ids != null ? ids.size() : 0);
      return ids;
    }
  }

  @Nullable
  private <K, V> IntSet collectFileIdsContainingAnyKey(@NotNull ID<K, V> indexId,
                                                       @NotNull Collection<? extends K> dataKeys,
                                                       @NotNull GlobalSearchScope filter,
                                                       @Nullable Condition<? super V> valueChecker,
                                                       @Nullable IdFilter projectFilesFilter) {
    try (var trace = lookupEntriesStarted(indexId)) {
      trace.keysWithOR(dataKeys.size());
      IntPredicate accessibleFileFilter = getAccessibleFileIdFilter(filter.getProject());
      IntPredicate idChecker = id -> (projectFilesFilter == null || projectFilesFilter.containsFileId(id)) &&
                                     accessibleFileFilter.test(id);
      ThrowableConvertor<UpdatableIndex<K, V, FileContent, ?>, IntSet, StorageException> convertor = index -> {
        trace.totalKeysIndexed(keysCountApproximatelyIfPossible(index));
        IndexDebugProperties.DEBUG_INDEX_ID.set(indexId);
        try {
          return InvertedIndexUtil.collectInputIdsContainingAnyKey(index, dataKeys, valueChecker, idChecker);
        }
        finally {
          IndexDebugProperties.DEBUG_INDEX_ID.remove();
        }
      };

      final IntSet ids = processExceptions(indexId, null, filter, convertor);
      trace.lookupResultSize(ids != null ? ids.size() : 0);
      return ids;
    }
  }

  private boolean processVirtualFiles(@NotNull IntCollection ids,
                                      @NotNull GlobalSearchScope filter,
                                      @NotNull Processor<? super VirtualFile> processor) {
    // ensure predictable order because result might be cached by consumer
    IntList sortedIds = new IntArrayList(ids);
    sortedIds.sort(null);

    Collection<ModelBranch> branches = null;
    for (IntIterator iterator = sortedIds.iterator(); iterator.hasNext(); ) {
      ProgressManager.checkCanceled();
      int id = iterator.nextInt();
      VirtualFile file = findFileById(id);
      if (file != null) {
        if (branches == null) branches = filter.getModelBranchesAffectingScope();
        for (VirtualFile fileInBranch : filesInScopeWithBranches(filter, file, branches)) {
          boolean processNext = processor.process(fileInBranch);
          ProgressManager.checkCanceled();
          if (!processNext) {
            return false;
          }
        }
      }
    }
    return true;
  }

  @Override
  public @Nullable DumbModeAccessType getCurrentDumbModeAccessType() {
    DumbModeAccessType result = getCurrentDumbModeAccessType_NoDumbChecks();
    if (result != null) {
      getLogger().assertTrue(ContainerUtil.exists(ProjectManager.getInstance().getOpenProjects(), p -> DumbService.isDumb(p)),
                             "getCurrentDumbModeAccessType may only be called during indexing");
    }
    return result;
  }

  @Nullable DumbModeAccessType getCurrentDumbModeAccessType_NoDumbChecks() {
    Stack<DumbModeAccessType> dumbModeAccessTypeStack = ourDumbModeAccessTypeStack.get();
    if (dumbModeAccessTypeStack.isEmpty()) {
      return null;
    }

    ApplicationManager.getApplication().assertReadAccessAllowed();
    ourIgnoranceGuard.prohibitResultCaching(dumbModeAccessTypeStack.get(0));
    return dumbModeAccessTypeStack.peek();
  }

  @Override
  @ApiStatus.Internal
  public <T> @NotNull Processor<? super T> inheritCurrentDumbAccessType(@NotNull Processor<? super T> processor) {
    Stack<DumbModeAccessType> stack = ourDumbModeAccessTypeStack.get();
    if (stack.isEmpty()) return processor;

    DumbModeAccessType access = stack.peek();
    return t -> ignoreDumbMode(access, () -> processor.process(t));
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  @Override
  public <T, E extends Throwable> T ignoreDumbMode(@NotNull DumbModeAccessType dumbModeAccessType,
                                                   @NotNull ThrowableComputable<T, E> computable) throws E {
    Application app = ApplicationManager.getApplication();
    app.assertReadAccessAllowed();
    if (FileBasedIndex.isIndexAccessDuringDumbModeEnabled()) {
      Stack<DumbModeAccessType> dumbModeAccessTypeStack = ourDumbModeAccessTypeStack.get();
      boolean preventCaching = dumbModeAccessTypeStack.empty();
      dumbModeAccessTypeStack.push(dumbModeAccessType);
      Disposable disposable = Disposer.newDisposable();
      if (app.isWriteIntentLockAcquired()) {
        app.getMessageBus().connect(disposable).subscribe(PsiModificationTracker.TOPIC,
                                                          () -> RecursionManager.dropCurrentMemoizationCache());
      }
      try {
        return preventCaching
               ? ourIgnoranceGuard.computePreventingRecursion(dumbModeAccessType, false, computable)
               : computable.compute();
      }
      finally {
        Disposer.dispose(disposable);
        DumbModeAccessType type = dumbModeAccessTypeStack.pop();
        assert dumbModeAccessType == type;
      }
    }
    else {
      return computable.compute();
    }
  }

  @Nullable
  @ApiStatus.Internal
  public abstract VirtualFile findFileById(int id);

  @NotNull
  @ApiStatus.Internal
  public abstract Logger getLogger();

  @NotNull
  @ApiStatus.Internal
  public static List<VirtualFile> filesInScopeWithBranches(@NotNull GlobalSearchScope scope,
                                                           @NotNull VirtualFile file,
                                                           @NotNull Collection<ModelBranch> branchesAffectingScope) {
    List<VirtualFile> filesInScope;
    filesInScope = new SmartList<>();
    if (scope.contains(file)) filesInScope.add(file);
    ProgressManager.checkCanceled();
    for (ModelBranch branch : branchesAffectingScope) {
      VirtualFile copy = branch.findFileCopy(file);
      if (!((ModelBranchImpl)branch).hasModifications(copy) && scope.contains(copy)) {
        filesInScope.add(copy);
      }
      ProgressManager.checkCanceled();
    }
    return filesInScope;
  }

  @Nullable
  public static Throwable getCauseToRebuildIndex(@NotNull RuntimeException e) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // avoid rebuilding index in tests since we do it synchronously in requestRebuild, and we can have readAction at hand
      return null;
    }
    if (e instanceof ProcessCanceledException) {
      return null;
    }
    if (e instanceof MapReduceIndexMappingException) {
      if (e.getCause() instanceof SnapshotInputMappingException) {
        // IDEA-258515: corrupted snapshot index storage must be rebuilt.
        return e.getCause();
      }
      // If exception has happened on input mapping (DataIndexer.map),
      // it is handled as the indexer exception and must not lead to index rebuild.
      return null;
    }
    if (e instanceof IndexOutOfBoundsException) return e; // something wrong with direct byte buffer
    Throwable cause = e.getCause();
    if (cause instanceof StorageException
        || cause instanceof IOException
        || cause instanceof IllegalArgumentException
    ) {
      return cause;
    }
    return null;
  }

  @ApiStatus.Internal
  public static boolean isTooLarge(@NotNull VirtualFile file,
                                   @Nullable("if content size should be retrieved from a file") Long contentSize,
                                   @NotNull Set<FileType> noLimitFileTypes) {
    if (SingleRootFileViewProvider.isTooLargeForIntelligence(file, contentSize)) {
      return !noLimitFileTypes.contains(file.getFileType()) || SingleRootFileViewProvider.isTooLargeForContentLoading(file, contentSize);
    }
    return false;
  }

  public static boolean acceptsInput(@NotNull InputFilter filter, @NotNull IndexedFile indexedFile) {
    if (filter instanceof ProjectSpecificInputFilter) {
      if (indexedFile.getProject() == null) {
        Project project = ProjectLocator.getInstance().guessProjectForFile(indexedFile.getFile());
        ((IndexedFileImpl)indexedFile).setProject(project);
      }
      return ((ProjectSpecificInputFilter)filter).acceptInput(indexedFile);
    }
    return filter.acceptInput(indexedFile.getFile());
  }

  @NotNull
  public static InputFilter composeInputFilter(@NotNull InputFilter filter,
                                               @NotNull BiPredicate<? super VirtualFile, ? super Project> condition) {
    return new ProjectSpecificInputFilter() {
      @Override
      public boolean acceptInput(@NotNull IndexedFile file) {
        boolean doesMainFilterAccept = filter instanceof ProjectSpecificInputFilter
                                       ? ((ProjectSpecificInputFilter)filter).acceptInput(file)
                                       : filter.acceptInput(file.getFile());
        return doesMainFilterAccept && condition.test(file.getFile(), file.getProject());
      }
    };
  }

  @ApiStatus.Internal
  public void runCleanupAction(@NotNull Runnable cleanupAction) {
  }

  @ApiStatus.Internal
  public static <T, E extends Throwable> T disableUpToDateCheckIn(@NotNull ThrowableComputable<T, E> runnable) throws E {
    return IndexUpToDateCheckIn.disableUpToDateCheckIn(runnable);
  }

  @ApiStatus.Internal
  static boolean belongsToScope(@Nullable VirtualFile file, @Nullable VirtualFile restrictedTo, @Nullable GlobalSearchScope filter) {
    if (!(file instanceof VirtualFileWithId) || !file.isValid()) {
      return false;
    }

    return (restrictedTo == null || Comparing.equal(file, restrictedTo)) &&
           (filter == null || restrictedTo != null || filter.accept(file));
  }

  @ApiStatus.Internal
  @NotNull
  public static Iterator<VirtualFile> createLazyFileIterator(@Nullable IntSet result, @NotNull GlobalSearchScope scope) {
    Set<VirtualFile> fileSet = new CompactVirtualFileSet(result == null ? IntSets.emptySet() : result).freezed();
    return fileSet.stream().filter(scope::contains).iterator();
  }

  @ApiStatus.Internal
  @SuppressWarnings("unchecked")
  public static @Nullable Iterator<VirtualFile> extractSingleFileOrEmpty(@Nullable GlobalSearchScope scope) {
    if (scope == null) return null;

    VirtualFileEnumeration enumeration = VirtualFileEnumeration.extract(scope);
    Iterable<VirtualFile> scopeAsFileIterable = enumeration != null ? enumeration.getFilesIfCollection() :
                                                scope instanceof Iterable<?> ? (Iterable<VirtualFile>)scope :
                                                null;
    if (scopeAsFileIterable == null) return null;

    VirtualFile result = null;
    boolean isFirst = true;

    for (VirtualFile file : scopeAsFileIterable) {
      if (!isFirst) return null;
      result = file;
      isFirst = false;
    }

    return isFirst ? ObjectIterators.emptyIterator() :
           result instanceof VirtualFileWithId ? ObjectIterators.singleton(result) :
           null;
  }

  public static @NotNull Iterable<VirtualFile> toFileIterable(int @NotNull [] fileIds) {
    if (fileIds.length == 0) return Collections.emptyList();
    return () -> new Iterator<>() {
      int myId;
      VirtualFile myNext;

      @Override
      public boolean hasNext() {
        while (myNext == null && myId < fileIds.length) {
          myNext = VirtualFileManager.getInstance().findFileById(fileIds[myId++]);
        }
        return myNext != null;
      }

      @Override
      public VirtualFile next() {
        if (!hasNext()) throw new NoSuchElementException();
        VirtualFile next = myNext;
        myNext = null;
        return next;
      }
    };
  }
}
