// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.vfs.VirtualFile;
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
import com.intellij.util.indexing.diagnostic.IndexAccessValidator;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.indexing.impl.InvertedIndexValueIterator;
import com.intellij.util.indexing.impl.MapReduceIndexMappingException;
import com.intellij.util.indexing.roots.IndexableFilesContributor;
import com.intellij.util.indexing.roots.IndexableFilesDeduplicateFilter;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.snapshot.SnapshotInputMappingException;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@ApiStatus.Internal
public abstract class FileBasedIndexEx extends FileBasedIndex {
  public static final boolean DO_TRACE_STUB_INDEX_UPDATE = Boolean.getBoolean("idea.trace.stub.index.update");
  @SuppressWarnings("SSBasedInspection")
  private static final ThreadLocal<Stack<DumbModeAccessType>> ourDumbModeAccessTypeStack =
    ThreadLocal.withInitial(() -> new com.intellij.util.containers.Stack<>());
  private static final RecursionGuard<Object> ourIgnoranceGuard = RecursionManager.createGuard("ignoreDumbMode");
  private final IndexAccessValidator myAccessValidator = new IndexAccessValidator();
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
  public abstract <K, V> UpdatableIndex<K, V, FileContent> getIndex(ID<K, V> indexId);

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
    VirtualFile restrictToFile = getTheOnlyFileInScope(filter);

    final List<V> values = new SmartList<>();
    ValueProcessor<V> processor = (file, value) -> {
      values.add(value);
      return true;
    };
    if (restrictToFile != null) {
      processValuesInOneFile(indexId, dataKey, restrictToFile, filter, processor);
    }
    else {
      processValuesInScope(indexId, dataKey, true, filter, null, processor);
    }
    return values;
  }

  private static @Nullable VirtualFile getTheOnlyFileInScope(@NotNull GlobalSearchScope filter) {
    VirtualFileEnumeration files = VirtualFileEnumeration.extract(filter);
    if (files == null) return null;
    Iterator<VirtualFile> iterator = files.asIterable().iterator();
    if (iterator.hasNext()) {
      VirtualFile first = iterator.next();
      if (!iterator.hasNext()) {
        return first;
      }
    }
    return null;
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
    try {
      waitUntilIndicesAreInitialized();
      UpdatableIndex<K, ?, FileContent> index = getIndex(indexId);
      if (!ensureUpToDate(indexId, scope.getProject(), scope, null)) {
        return true;
      }
      IdFilter idFilterAdjusted = idFilter == null ? extractIdFilter(scope, scope.getProject()) : idFilter;
      return myAccessValidator.validate(indexId, () -> index.processAllKeys(processor, scope, idFilterAdjusted));
    }
    catch (StorageException e) {
      scheduleRebuild(indexId, e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof StorageException || cause instanceof IOException) {
        scheduleRebuild(indexId, cause);
      }
      else {
        throw e;
      }
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
    if (filter.getProject() instanceof LightEditCompatible) return Collections.emptyList();
    VirtualFile restrictToFile = getTheOnlyFileInScope(filter);
    if (restrictToFile != null) {
      return !processValuesInOneFile(indexId, dataKey, restrictToFile, filter, (f, v) -> false) ?
             Collections.singleton(restrictToFile) : Collections.emptyList();
    }
    Set<VirtualFile> files = new HashSet<>();
    processValuesInScope(indexId, dataKey, false, filter, null, (file, value) -> {
      files.add(file);
      return true;
    });
    return files;
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
    UpdatableIndex<K, V, FileContent> index = getIndex(indexId);
    ensureUpToDate(indexId, project, GlobalSearchScope.allScope(project));
    return index.getModificationStamp();
  }

  @Nullable
  private <K, V, R> R processExceptions(@NotNull final ID<K, V> indexId,
                                        @Nullable final VirtualFile restrictToFile,
                                        @NotNull final GlobalSearchScope filter,
                                        @NotNull ThrowableConvertor<? super UpdatableIndex<K, V, FileContent>, ? extends R, StorageException> computable) {
    try {
      waitUntilIndicesAreInitialized();
      UpdatableIndex<K, V, FileContent> index = getIndex(indexId);
      Project project = filter.getProject();
      //assert project != null : "GlobalSearchScope#getProject() should be not-null for all index queries";
      if (!ensureUpToDate(indexId, project, filter, restrictToFile)) {
        return null;
      }

      return myAccessValidator.validate(indexId,
                                        () -> ConcurrencyUtil.withLock(index.getLock().readLock(), () -> computable.convert(index)));
    }
    catch (StorageException e) {
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
      while (valueIt.hasNext()) {
        final V value = valueIt.next();
        for (final ValueContainer.IntIterator inputIdsIterator = valueIt.getInputIdsIterator(); inputIdsIterator.hasNext(); ) {
          final int id = inputIdsIterator.next();
          if (!accessibleFileFilter.test(id) || (filter != null && !filter.containsFileId(id))) continue;
          VirtualFile file = findFileById(id);
          if (file != null) {
            for (VirtualFile eachFile : filesInScopeWithBranches(scope, file)) {
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
    final Boolean result = processExceptions(indexId, restrictToFile, scope,
                                             index -> valueProcessor.process(
                                               (InvertedIndexValueIterator<V>)index.getData(dataKey).getValueIterator()));
    return result == null || result.booleanValue();
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
      set = new IntOpenHashSet();
      VirtualFileEnumeration hint = VirtualFileEnumeration.extract(filter);
      if (hint != null) {
        for (VirtualFile file : hint.asIterable()) {
          if (file instanceof VirtualFileWithId) {
            set.add(((VirtualFileWithId)file).getId());
          }
        }
      }
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
    List<IndexableFilesIterator> providers = IndexableFilesContributor.EP_NAME
      .getExtensionList()
      .stream()
      .flatMap(c -> {
        return ReadAction.nonBlocking(() -> c.getIndexableFiles(project)).expireWith(project).executeSynchronously().stream();
      })
      .collect(Collectors.toList());
    if (!allowedIteratorPatterns.isEmpty()) {
      providers = ContainerUtil.filter(providers, p -> {
        return allowedIteratorPatterns.stream().anyMatch(pattern -> p.getDebugName().contains(pattern));
      });
    }
    return providers;
  }

  @Nullable
  private <K, V> IntSet collectFileIdsContainingAllKeys(@NotNull ID<K, V> indexId,
                                                        @NotNull Collection<? extends K> dataKeys,
                                                        @NotNull GlobalSearchScope filter,
                                                        @Nullable Condition<? super V> valueChecker,
                                                        @Nullable IdFilter projectFilesFilter,
                                                        @Nullable IntSet restrictedIds) {
    IntPredicate accessibleFileFilter = getAccessibleFileIdFilter(filter.getProject());
    IntPredicate idChecker = id -> (projectFilesFilter == null || projectFilesFilter.containsFileId(id)) &&
                                   accessibleFileFilter.test(id) &&
                                   (restrictedIds == null || restrictedIds.contains(id));
    ThrowableConvertor<UpdatableIndex<K, V, FileContent>, IntSet, StorageException> convertor = index -> {
      IndexDebugProperties.DEBUG_INDEX_ID.set(indexId);
      try {
        return InvertedIndexUtil.collectInputIdsContainingAllKeys(index, dataKeys, valueChecker, idChecker);
      }
      finally {
        IndexDebugProperties.DEBUG_INDEX_ID.remove();
      }
    };

    return processExceptions(indexId, null, filter, convertor);
  }

  @Nullable
  private <K, V> IntSet collectFileIdsContainingAnyKey(@NotNull ID<K, V> indexId,
                                                        @NotNull Collection<? extends K> dataKeys,
                                                        @NotNull GlobalSearchScope filter,
                                                        @Nullable Condition<? super V> valueChecker,
                                                        @Nullable IdFilter projectFilesFilter) {
    IntPredicate accessibleFileFilter = getAccessibleFileIdFilter(filter.getProject());
    IntPredicate idChecker = id -> (projectFilesFilter == null || projectFilesFilter.containsFileId(id)) &&
                                   accessibleFileFilter.test(id);
    ThrowableConvertor<UpdatableIndex<K, V, FileContent>, IntSet, StorageException> convertor = index -> {
      IndexDebugProperties.DEBUG_INDEX_ID.set(indexId);
      try {
        return InvertedIndexUtil.collectInputIdsContainingAnyKey(index, dataKeys, valueChecker, idChecker);
      }
      finally {
        IndexDebugProperties.DEBUG_INDEX_ID.remove();
      }
    };

    return processExceptions(indexId, null, filter, convertor);
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
      if (file != null) {
        for (VirtualFile fileInBranch : filesInScopeWithBranches(filter, file)) {
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
      if (app.isWriteThread()) {
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
  public static List<VirtualFile> filesInScopeWithBranches(@NotNull GlobalSearchScope scope, @NotNull VirtualFile file) {
    List<VirtualFile> filesInScope;
    filesInScope = new SmartList<>();
    if (scope.contains(file)) filesInScope.add(file);
    ProgressManager.checkCanceled();
    for (ModelBranch branch : scope.getModelBranchesAffectingScope()) {
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
      // avoid rebuilding index in tests since we do it synchronously in requestRebuild and we can have readAction at hand
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
  public static InputFilter composeInputFilter(@NotNull InputFilter filter, @NotNull Predicate<? super VirtualFile> condition) {
    return filter instanceof ProjectSpecificInputFilter
           ? new ProjectSpecificInputFilter() {
      @Override
      public boolean acceptInput(@NotNull IndexedFile file) {
        return ((ProjectSpecificInputFilter)filter).acceptInput(file) && condition.test(file.getFile());
      }
    }
           : file -> filter.acceptInput(file) && condition.test(file);
  }

  @ApiStatus.Internal
  public void runCleanupAction(@NotNull Runnable cleanupAction) {
  }

  @ApiStatus.Internal
  public static <T,E extends Throwable> T disableUpToDateCheckIn(@NotNull ThrowableComputable<T, E> runnable) throws E {
    return IndexUpToDateCheckIn.disableUpToDateCheckIn(runnable);
  }
}
