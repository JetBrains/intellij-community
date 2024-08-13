// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.util.indexing.roots.IndexableFilesDeduplicateFilter;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.ObjectIterators;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

import static com.intellij.util.indexing.diagnostic.IndexLookupTimingsReporting.IndexOperationFusCollector.*;
import static com.intellij.util.io.MeasurableIndexStore.keysCountApproximatelyIfPossible;

@SuppressWarnings("TypeParameterHidesVisibleType")
@ApiStatus.Internal
public abstract class FileBasedIndexEx extends FileBasedIndex {
  public static final boolean TRACE_STUB_INDEX_UPDATES = SystemProperties.getBooleanProperty("idea.trace.stub.index.update", false) ||
                                                         SystemProperties.getBooleanProperty("trace.stub.index.update", false);
  private static final boolean TRACE_INDEX_UPDATES = SystemProperties.getBooleanProperty("trace.file.based.index.update", false);
  private static final boolean TRACE_SHARED_INDEX_UPDATES = SystemProperties.getBooleanProperty("trace.shared.index.update", false);

  @SuppressWarnings("SSBasedInspection")
  private static final ThreadLocal<Stack<DumbModeAccessType>> ourDumbModeAccessTypeStack =
    ThreadLocal.withInitial(() -> new com.intellij.util.containers.Stack<>());
  /**
   * Prevents caching of the inner computations that happened inside ignoreDumbMode (in case if access to indexes was in fact requested)
   * But it doesn't prevent caching of the result returned from ignoreDumbMode (for example, if ignoreDumbMode is
   * wrapped in CachedValuesManager.getCachedValue)
   * <p>
   * It doesn't work as a recursion guard and computePreventingRecursion is not called recursively.
   */
  private static final RecursionGuard<Object> ourIgnoranceGuard = RecursionManager.createGuard("ignoreDumbMode");

  @ApiStatus.Internal
  static boolean doTraceIndexUpdates() {
    return TRACE_INDEX_UPDATES;
  }

  @ApiStatus.Internal
  public static boolean doTraceStubUpdates(@NotNull IndexId<?, ?> indexId) {
    return TRACE_STUB_INDEX_UPDATES && indexId.equals(StubUpdatingIndex.INDEX_ID);
  }

  @ApiStatus.Internal
  public static boolean doTraceSharedIndexUpdates() {
    return TRACE_SHARED_INDEX_UPDATES;
  }

  @ApiStatus.Internal
  public abstract @NotNull IntPredicate getAccessibleFileIdFilter(@Nullable Project project);

  @ApiStatus.Internal
  public abstract @Nullable IdFilter extractIdFilter(@Nullable GlobalSearchScope scope, @Nullable Project project);

  @ApiStatus.Internal
  public abstract @Nullable IdFilter projectIndexableFiles(@Nullable Project project);

  @ApiStatus.Internal
  public abstract @NotNull <K, V> UpdatableIndex<K, V, FileContent, ?> getIndex(ID<K, V> indexId);

  public void resetHints() { }

  @ApiStatus.Internal
  public abstract void waitUntilIndicesAreInitialized();

  /**
   * @return true if index can be processed after it or
   * false if no need to process it because, for example, scope is empty or index is going to rebuild.
   */
  @ApiStatus.Internal
  public abstract <K> boolean ensureUpToDate(final @NotNull ID<K, ?> indexId,
                                             @Nullable Project project,
                                             @Nullable GlobalSearchScope filter,
                                             @Nullable VirtualFile restrictedFile);

  @Override
  public @NotNull <K, V> List<V> getValues(final @NotNull ID<K, V> indexId, @NotNull K dataKey, final @NotNull GlobalSearchScope filter) {
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
  public @NotNull <K> Collection<K> getAllKeys(final @NotNull ID<K, ?> indexId, @NotNull Project project) {
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
      requestRebuild(indexId, e);
    }
    catch (RuntimeException e) {
      trace.lookupFailed();
      final Throwable cause = e.getCause();
      if (cause instanceof StorageException || cause instanceof IOException) {
        requestRebuild(indexId, cause);
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

  @Override
  public @NotNull <K, V> Map<K, V> getFileData(@NotNull ID<K, V> id, @NotNull VirtualFile virtualFile, @NotNull Project project) {
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
  public @NotNull <K, V> Collection<VirtualFile> getContainingFiles(@NotNull ID<K, V> indexId,
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
          ProgressManager.checkCanceled();
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
  public <K, V> boolean processValues(final @NotNull ID<K, V> indexId, final @NotNull K dataKey, final @Nullable VirtualFile inFile,
                                      @NotNull ValueProcessor<? super V> processor, final @NotNull GlobalSearchScope filter) {
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

  private @Nullable <K, V, R> R processExceptions(final @NotNull ID<K, V> indexId,
                                                  final @Nullable VirtualFile restrictToFile,
                                                  final @NotNull GlobalSearchScope filter,
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
      requestRebuild(indexId, e);
    }
    catch (RuntimeException e) {
      final Throwable cause = getCauseToRebuildIndex(e);
      if (cause != null) {
        requestRebuild(indexId, cause);
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
      return true;
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

  protected <K, V> boolean processValuesInScope(@NotNull ID<K, V> indexId,
                                                @NotNull K dataKey,
                                                boolean ensureValueProcessedOnce,
                                                @NotNull GlobalSearchScope scope,
                                                @Nullable IdFilter idFilter,
                                                @NotNull ValueProcessor<? super V> processor) {
    Project project = scope.getProject();

    IdFilter filter = idFilter != null ? idFilter : extractIdFilter(scope, project);
    IntPredicate accessibleFileFilter = getAccessibleFileIdFilter(project);

    return processValueIterator(indexId, dataKey, null, scope, valueIt -> {
      while (valueIt.hasNext()) {
        final V value = valueIt.next();
        for (final ValueContainer.IntIterator inputIdsIterator = valueIt.getInputIdsIterator(); inputIdsIterator.hasNext(); ) {
          final int id = inputIdsIterator.next();
          if (!accessibleFileFilter.test(id) || (filter != null && !filter.containsFileId(id))) continue;
          VirtualFile file = findFileById(id);
          if (file != null && scope.contains(file)) {
            if (!processor.process(file, value)) {
              return false;
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
                                                               @Nullable IdFilter filesSet,
                                                               @NotNull Processor<? super VirtualFile> processor) {
    IntSet set = null;
    if (filter instanceof GlobalSearchScope.FilesScope filesScope) {
      set = new IntOpenHashSet(filesScope.asArray());
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
    return set != null && processVirtualFiles(set, filter, processor);
  }

  @Override
  public boolean processFilesContainingAllKeys(@NotNull Collection<? extends AllKeysQuery<?, ?>> queries,
                                               @NotNull GlobalSearchScope filter,
                                               @NotNull Processor<? super VirtualFile> processor) {
    IdFilter filesSet = extractIdFilter(filter, filter.getProject());

    return processFilesContainingAllKeysInPhysicalFiles(queries, filter, filesSet, processor);
  }

  @Override
  public <K, V> boolean getFilesWithKey(final @NotNull ID<K, V> indexId,
                                        final @NotNull Set<? extends K> dataKeys,
                                        @NotNull Processor<? super VirtualFile> processor,
                                        @NotNull GlobalSearchScope filter) {
    return processFilesContainingAllKeys(indexId, dataKeys, filter, null, processor);
  }


  @Override
  public <K> void scheduleRebuild(final @NotNull ID<K, ?> indexId, final @NotNull Throwable e) {
    requestRebuild(indexId, e);
  }

  /**
   * DO NOT CALL DIRECTLY IN CLIENT CODE
   * The method is internal to indexing engine end is called internally. The method is public due to implementation details
   */
  @Override
  public <K> void ensureUpToDate(final @NotNull ID<K, ?> indexId, @Nullable Project project, @Nullable GlobalSearchScope filter) {
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
   * <p>
   * Don't iterate over these providers in one [smart] read action because on large projects it will either cause a freeze
   * without a proper indicator or ProgressManager.checkCanceled() or will be constantly interrupted by write action and restarted.
   * Consider iterating without a read action if you don't require a consistent snapshot.
   */
  public @NotNull List<IndexableFilesIterator> getIndexableFilesProviders(@NotNull Project project) {
    if (project instanceof LightEditCompatible) {
      return Collections.emptyList();
    }
    return IndexableFilesIndex.getInstance(project).getIndexingIterators();
  }

  private @Nullable <K, V> IntSet collectFileIdsContainingAllKeys(@NotNull ID<K, V> indexId,
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

  private @Nullable <K, V> IntSet collectFileIdsContainingAnyKey(@NotNull ID<K, V> indexId,
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

    for (IntIterator iterator = sortedIds.iterator(); iterator.hasNext(); ) {
      ProgressManager.checkCanceled();
      int id = iterator.nextInt();
      VirtualFile file = findFileById(id);
      if (file != null && filter.contains(file)) {
        boolean processNext = processor.process(file);
        ProgressManager.checkCanceled();
        if (!processNext) {
          return false;
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

  @Nullable
  DumbModeAccessType getCurrentDumbModeAccessType_NoDumbChecks() {
    Stack<DumbModeAccessType> dumbModeAccessTypeStack = ourDumbModeAccessTypeStack.get();
    if (dumbModeAccessTypeStack.isEmpty()) {
      return null;
    }

    ApplicationManager.getApplication().assertReadAccessAllowed();
    // after prohibitResultCaching all new attempts to cache something will be unsuccessful until ignoreDumbMode call stack finishes,
    // e.g., if resolve triggers another resolve that will try to cache CachedValuesManager.getCachedValue it won't be cached
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
    Stack<DumbModeAccessType> dumbModeAccessTypeStack = ourDumbModeAccessTypeStack.get();
    boolean preventCaching = dumbModeAccessTypeStack.empty();
    dumbModeAccessTypeStack.push(dumbModeAccessType);
    Disposable disposable = null;
    if (app.isWriteIntentLockAcquired()) {
      disposable = Disposer.newDisposable();
      app.getMessageBus().connect(disposable).subscribe(PsiModificationTracker.TOPIC,
                                                        () -> RecursionManager.dropCurrentMemoizationCache());
    }
    try {
      return preventCaching
             ? ourIgnoranceGuard.computePreventingRecursion(dumbModeAccessType, false, computable)
             : computable.compute();
    }
    finally {
      if (disposable != null) {
        Disposer.dispose(disposable);
      }
      DumbModeAccessType type = dumbModeAccessTypeStack.pop();
      assert dumbModeAccessType == type;
    }
  }

  @ApiStatus.Internal
  public abstract @Nullable VirtualFile findFileById(int id);

  @ApiStatus.Internal
  public abstract @NotNull Logger getLogger();

  public static @Nullable Throwable getCauseToRebuildIndex(@NotNull RuntimeException e) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // avoid rebuilding index in tests since we do it synchronously in requestRebuild, and we can have readAction at hand
      return null;
    }
    if (e instanceof ProcessCanceledException) {
      return null;
    }
    if (e instanceof MapReduceIndexMappingException) {
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

  public static @NotNull InputFilter composeInputFilter(@NotNull InputFilter filter,
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
  public static @NotNull Iterator<VirtualFile> createLazyFileIterator(@Nullable IntSet result, @NotNull GlobalSearchScope scope) {
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

    return isFirst ? Collections.emptyIterator() :
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
