// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.model.ModelBranch;
import com.intellij.model.ModelBranchImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.util.indexing.diagnostic.IndexAccessValidator;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.indexing.impl.InvertedIndexValueIterator;
import com.intellij.util.indexing.roots.IndexableFilesContributor;
import com.intellij.util.indexing.roots.IndexableFilesDeduplicateFilter;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

import static com.intellij.util.indexing.FileBasedIndexImpl.LOG;
import static com.intellij.util.indexing.FileBasedIndexImpl.getCauseToRebuildIndex;

@ApiStatus.Internal
public abstract class FileBasedIndexEx extends FileBasedIndex {
  @SuppressWarnings("SSBasedInspection")
  private static final ThreadLocal<Stack<DumbModeAccessType>> ourDumbModeAccessTypeStack = ThreadLocal.withInitial(() -> new com.intellij.util.containers.Stack<>());
  private static final RecursionGuard<Object> ourIgnoranceGuard = RecursionManager.createGuard("ignoreDumbMode");
  private final IndexAccessValidator myAccessValidator = new IndexAccessValidator();

  @ApiStatus.Internal
  @NotNull
  public abstract IntPredicate getAccessibleFileIdFilter(@Nullable Project project);

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
    VirtualFile restrictToFile = null;

    if (filter instanceof Iterable) {
      // optimisation: in case of one-file-scope we can do better.
      // check if the scope knows how to extract some files off itself
      //noinspection unchecked
      Iterator<VirtualFile> virtualFileIterator = ((Iterable<VirtualFile>)filter).iterator();
      if (virtualFileIterator.hasNext()) {
        VirtualFile restrictToFileCandidate = virtualFileIterator.next();
        if (!virtualFileIterator.hasNext()) {
          restrictToFile = restrictToFileCandidate;
        }
      }
    }

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

  @Override
  @NotNull
  public <K> Collection<K> getAllKeys(@NotNull final ID<K, ?> indexId, @NotNull Project project) {
    Set<K> allKeys = new HashSet<>();
    processAllKeys(indexId, Processors.cancelableCollectProcessor(allKeys), project);
    return allKeys;
  }

  @Override
  public <K> boolean processAllKeys(@NotNull ID<K, ?> indexId, @NotNull Processor<? super K> processor, @Nullable Project project) {
    return processAllKeys(indexId, processor, project == null ? new EverythingGlobalScope() : GlobalSearchScope.everythingScope(project), null);
  }

  @Override
  public <K> boolean processAllKeys(@NotNull ID<K, ?> indexId, @NotNull Processor<? super K> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter idFilter) {
    try {
      waitUntilIndicesAreInitialized();
      UpdatableIndex<K, ?, FileContent> index = getIndex(indexId);
      if (!ensureUpToDate(indexId, scope.getProject(), scope, null)) {
        return true;
      }
      if (idFilter == null) {
        idFilter = projectIndexableFiles(scope.getProject());
      }
      @Nullable IdFilter finalIdFilter = idFilter;
      return myAccessValidator.validate(indexId, () -> index.processAllKeys(processor, scope, finalIdFilter));
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
          LOG.error("Index extension " + id + " doesn't require forward index but accesses it");
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
    if (LightEdit.owns(filter.getProject())) return Collections.emptyList();
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

      return myAccessValidator.validate(indexId, () -> ConcurrencyUtil.withLock(index.getLock().readLock(), ()->computable.convert(index)));
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

  private <K, V> boolean processValuesInOneFile(@NotNull ID<K, V> indexId,
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
        if (valueIt.getValueAssociationPredicate().contains(restrictedFileId) && !processor.process(restrictToFile, value)) {
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

  private <K, V> boolean processValuesInScope(@NotNull ID<K, V> indexId,
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

    PersistentFS fs = PersistentFS.getInstance();
    IdFilter filter = idFilter != null ? idFilter : projectIndexableFiles(project);
    IntPredicate accessibleFileFilter = getAccessibleFileIdFilter(project);

    return processValueIterator(indexId, dataKey, null, scope, valueIt -> {
      while (valueIt.hasNext()) {
        final V value = valueIt.next();
        for (final ValueContainer.IntIterator inputIdsIterator = valueIt.getInputIdsIterator(); inputIdsIterator.hasNext(); ) {
          final int id = inputIdsIterator.next();
          if (!accessibleFileFilter.test(id) || (filter != null && !filter.containsFileId(id))) continue;
          VirtualFile file = fs.findFileByIdIfCached(id);
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
                                             index -> valueProcessor.process((InvertedIndexValueIterator<V>)index.getData(dataKey).getValueIterator()));
    return result == null || result.booleanValue();
  }

  @Override
  public <K, V> boolean processFilesContainingAllKeys(@NotNull ID<K, V> indexId,
                                                      @NotNull Collection<? extends K> dataKeys,
                                                      @NotNull GlobalSearchScope filter,
                                                      @Nullable Condition<? super V> valueChecker,
                                                      @NotNull Processor<? super VirtualFile> processor) {
    IdFilter filesSet = projectIndexableFiles(filter.getProject());
    IntSet set = collectFileIdsContainingAllKeys(indexId, dataKeys, filter, valueChecker, filesSet, null);
    return set != null && processVirtualFiles(set, filter, processor);
  }


  @Override
  public boolean processFilesContainingAllKeys(@NotNull Collection<AllKeysQuery<?, ?>> queries,
                                               @NotNull GlobalSearchScope filter,
                                               @NotNull Processor<? super VirtualFile> processor) {
    Project project = filter.getProject();
    IdFilter filesSet = projectIndexableFiles(project);
    IntSet set = null;

    if (filter instanceof GlobalSearchScope.FilesScope) {
      set = new IntOpenHashSet();
      for (VirtualFile file : (Iterable<VirtualFile>)filter) {
        if (file instanceof VirtualFileWithId) {
          set.add(((VirtualFileWithId)file).getId());
        }
      }
    }

    //noinspection rawtypes
    for (AllKeysQuery query : queries) {
      @SuppressWarnings("unchecked")
      IntSet queryResult = collectFileIdsContainingAllKeys(query.getIndexId(), query.getDataKeys(), filter, query.getValueChecker(), filesSet, set);
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
    List<IndexableFilesIterator> providers = getOrderedIndexableFilesProviders(project);
    IndexableFilesDeduplicateFilter indexableFilesDeduplicateFilter = IndexableFilesDeduplicateFilter.create();
    boolean wasIndeterminate = false;
    if (indicator != null) {
      wasIndeterminate = indicator.isIndeterminate();
      indicator.setIndeterminate(false);
      indicator.setFraction(0);
      indicator.pushState();
    }
    try {
      for (int i = 0; i < providers.size(); i++) {
        if (indicator != null) {
          indicator.checkCanceled();
        }
        IndexableFilesIterator provider = providers.get(i);
        if (!provider.iterateFiles(project, processor, indexableFilesDeduplicateFilter)) {
          break;
        }
        if (indicator != null) {
          indicator.setFraction((i + 1) * 1.0 / providers.size());
        }
      }
    } finally {
      if (indicator != null) {
        indicator.popState();
        indicator.setIndeterminate(wasIndeterminate);
      }
    }
  }

  /**
   * Returns providers of files to be indexed. Indexing is performed in the order corresponding to the resulting list.
   */
  @NotNull
  public List<IndexableFilesIterator> getOrderedIndexableFilesProviders(@NotNull Project project) {
    if (LightEdit.owns(project)) {
      return Collections.emptyList();
    }
    return ReadAction.compute(() -> {
      if (project.isDisposed()) {
        return Collections.emptyList();
      }

      return IndexableFilesContributor.EP_NAME
        .getExtensionList()
        .stream()
        .flatMap(c -> c.getIndexableFiles(project).stream())
        .collect(Collectors.toList());
    });
  }

  @Nullable
  private <K, V> IntSet collectFileIdsContainingAllKeys(@NotNull final ID<K, V> indexId,
                                                        @NotNull final Collection<? extends K> dataKeys,
                                                        @NotNull final GlobalSearchScope filter,
                                                        @Nullable final Condition<? super V> valueChecker,
                                                        @Nullable final IdFilter projectFilesFilter,
                                                        @Nullable IntSet restrictedIds) {
    IntPredicate accessibleFileFilter = getAccessibleFileIdFilter(filter.getProject());
    ValueContainer.IntPredicate idChecker = projectFilesFilter == null ? accessibleFileFilter::test : id ->
      projectFilesFilter.containsFileId(id) && accessibleFileFilter.test(id) && (restrictedIds == null || restrictedIds.contains(id));
    Condition<? super K> keyChecker = __ -> {
      ProgressManager.checkCanceled();
      return true;
    };
    ThrowableConvertor<UpdatableIndex<K, V, FileContent>, IntSet, StorageException> convertor = index -> {
      IndexDebugProperties.DEBUG_INDEX_ID.set(indexId);
      try {
        return InvertedIndexUtil.collectInputIdsContainingAllKeys(index, dataKeys, keyChecker, valueChecker, idChecker);
      }
      finally {
        IndexDebugProperties.DEBUG_INDEX_ID.remove();
      }
    };

    return processExceptions(indexId, null, filter, convertor);
  }

  private static boolean processVirtualFiles(@NotNull IntCollection ids,
                                             @NotNull GlobalSearchScope filter,
                                             @NotNull Processor<? super VirtualFile> processor) {
    // ensure predictable order because result might be cached by consumer
    IntList sortedIds = new IntArrayList(ids);
    sortedIds.sort(null);

    PersistentFS fs = PersistentFS.getInstance();
    for (IntIterator iterator = sortedIds.iterator(); iterator.hasNext(); ) {
      ProgressManager.checkCanceled();
      int id = iterator.nextInt();
      VirtualFile file = fs.findFileByIdIfCached(id);
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
      LOG.assertTrue(ContainerUtil.exists(ProjectManager.getInstance().getOpenProjects(), p -> DumbService.isDumb(p)), "getCurrentDumbModeAccessType may only be called during indexing");
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
    } else {
      return computable.compute();
    }
  }

  @NotNull
  @ApiStatus.Internal
  public static List<VirtualFile> filesInScopeWithBranches(@NotNull GlobalSearchScope scope, @NotNull VirtualFile file) {
    List<VirtualFile> filesInScope;
    filesInScope = new SmartList<>();
    if (scope.contains(file)) filesInScope.add(file);
    for (ModelBranch branch : scope.getModelBranchesAffectingScope()) {
      VirtualFile copy = branch.findFileCopy(file);
      if (!((ModelBranchImpl)branch).hasModifications(copy) && scope.contains(copy)) {
        filesInScope.add(copy);
      }
    }
    return filesInScope;
  }
}
