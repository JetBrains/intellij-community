// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.find.FindBundle;
import com.intellij.find.FindInProjectSearchEngine;
import com.intellij.find.FindInProjectSearchEngine.FindInProjectSearcher;
import com.intellij.find.FindModel;
import com.intellij.find.FindModelExtension;
import com.intellij.find.findInProject.FindInProjectManager;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.progress.util.TooManyUsagesStatus;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.platform.workspace.jps.entities.ModuleId;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.FindUsagesProcessPresentation;
import com.intellij.usages.impl.UsageViewManagerImpl;
import com.intellij.util.Processor;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ConcurrentBitSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexEx;
import com.intellij.util.indexing.FilesDeque;
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.kind.ContentOrigin;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import com.intellij.util.text.StringSearcher;
import com.intellij.util.ui.EDT;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static com.intellij.find.impl.FindInProjectUtil.FIND_IN_FILES_SEARCH_IN_NON_INDEXABLE;
import static com.intellij.openapi.roots.impl.FilesScanExecutor.processOnAllThreadsInReadActionWithRetries;
import static com.intellij.util.containers.ContainerUtil.sorted;

final class FindInProjectTask {
  private static final Logger LOG = Logger.getInstance(FindInProjectTask.class);

  private static final Comparator<VirtualFile> SEARCH_RESULT_FILE_COMPARATOR =
    Comparator.comparing((VirtualFile f) -> (f instanceof VirtualFileWithId fileWithId) ? fileWithId.getId() : 0)
      .thenComparing(VirtualFile::getName) // in case files without id are also searched
      .thenComparing(VirtualFile::getPath);

  /** Total size of processed files before asking user 'too many files, should we continue?' */
  //TODO 70 Kb -- isn't it too small limit?
  private static final int TOTAL_FILES_SIZE_LIMIT_BEFORE_ASKING = 70 * 1024 * 1024; // megabytes.

  private final FindModel findModel;

  /**
   * Files to check for the pattern at first, before other candidates are appended from searchers.
   * Those files are only candidates -- i.e. they will still be checked for a pattern, just checked before any other candidates.
   * Usually previously found files are supplied here, to provide better UX in 'incremental search', so that already found files
   * are not re-ordered on each next key typed.
   */
  private final Set<? extends VirtualFile> filesToScanInitially;

  private final @NotNull FindInProjectSearcher @NotNull [] searchers;
  /** Is there at least one reliable searcher ({@link FindInProjectSearcher#isReliable()})? */
  private final boolean hasReliableSearchers;
  /** cached value of [searchers[i].isReliable() for all i] */
  private final boolean[] isSearcherReliable;

  private final Project project;
  private final ProjectFileIndex projectFileIndex;

  private final PsiManager psiManager;

  //3 fields below are all derived from the findModel -- cached in ctor because derivation is too tedious:
  private final @Nullable Module moduleToSearchIn;
  private final @Nullable VirtualFile directoryToSearchIn;
  private final Predicate<VirtualFile> fileMaskFilter;


  private final Set<VirtualFile> largeFiles = Collections.synchronizedSet(new HashSet<>());

  private final ProgressIndicator progressIndicator;
  private final AtomicLong totalFilesSize = new AtomicLong();

  private long searchStartedAtNs;

  FindInProjectTask(@NotNull FindModel findModel,
                    @NotNull Project project,
                    @NotNull Set<? extends VirtualFile> filesToScanInitially,
                    boolean tooManyUsagesStatus) {
    this.findModel = findModel;
    this.project = project;
    this.filesToScanInitially = filesToScanInitially;

    searchers = ContainerUtil.mapNotNull(
      FindInProjectSearchEngine.EP_NAME.getExtensionList(),
      se -> se.createSearcher(findModel, project)
    ).toArray(FindInProjectSearcher[]::new);

    hasReliableSearchers = ContainerUtil.find(searchers, s -> s.isReliable()) != null;
    isSearcherReliable = new boolean[searchers.length];
    for (int i = 0; i < searchers.length; i++) {
      isSearcherReliable[i] = searchers[i].isReliable();
    }


    psiManager = PsiManager.getInstance(project);
    projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    directoryToSearchIn = FindInProjectUtil.getDirectory(findModel);

    String moduleName = findModel.getModuleName();
    moduleToSearchIn = moduleName == null ?
                       null :
                       ReadAction.compute(() -> ModuleManager.getInstance(project).findModuleByName(moduleName));

    Predicate<CharSequence> fileNamePatternCondition = FindInProjectUtil.createFileMaskCondition(findModel.getFileFilter());

    fileMaskFilter = file -> file != null && fileNamePatternCondition.test(file.getNameSequence());

    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    progressIndicator = progress != null ?
                        progress :
                        new EmptyProgressIndicator();

    if (tooManyUsagesStatus) {
      TooManyUsagesStatus.createFor(progressIndicator);
    }
  }

  /**
   * Find all usages of a given pattern in a given set of files, and deliver them to the usageProcessor. The pattern, set of files,
   * and most other details are defined by {@link #findModel}.
   * <p>
   * To better understand find usage code take into account that find usage is not an abstract task -- it is very much tailored to
   * the specific needs and user's expectations of FindUsage UX.
   * <p>
   * The find usages process is split into two phases:
   * <ol>
   *   <li>
   *     'Fast search': query the {@link #searchers} for a list of candidate files. Searchers represent a 'fast' way of finding
   *     the matching candidates -- i.e. some kind of index. The files returned by the searchers are only candidates -- they
   *     still must be checked against file mask, and looked up for the pattern.
   *     On this stage we also scan through {@link #filesToScanInitially} -- those are files that were found previously. We
   *     re-check them so that files that match before and still match now are remains at the top. This provides better UX
   *     then search pattern is expanded as user types additional symbols.
   *   </li>
   *   <li>
   *     'Brute force search': query all files in the scope defined by {@link #findModel} (including files that were already
   *     found by searchers on the 1st phase!), and process them, multi-threaded, against fileMask, and the pattern. On this
   *     phase we skip files already processed on 1st phase.
   *   </li>
   * </ol>
   * Those 2 phases combined give us the chance to deliver indexed files results almost instantly, keep top results consistent
   * as user continues typing in the search pattern, and still search extensively over (partially-)not-indexed scopes -- slower,
   * but still.
   */
  void findUsages(@NotNull FindUsagesProcessPresentation processPresentation,
                  @NotNull Processor<? super UsageInfo> usageProcessor) {
    if (!EDT.isCurrentThreadEdt()) {
      ThreadingAssertions.assertNoOwnReadAccess();
    }
    CoreProgressManager.assertUnderProgress(progressIndicator);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Searching for '" + findModel.getStringToFind() + "'");
    }
    searchStartedAtNs = System.nanoTime();
    try {
      Processor<VirtualFile> fileProcessor = adaptUsageProcessorAsFileProcessor(processPresentation, usageProcessor);

      progressIndicator.setIndeterminate(true);
      progressIndicator.setText(FindBundle.message("progress.text.scanning.indexed.files"));
      progressIndicator.setIndeterminate(false);

      //first process files from searchers (=index):
      Set<VirtualFile> filesFoundByFastSearch = collectFiles(searchers, fileMaskFilter);
      for (VirtualFile file : filesToScanInitially) {
        if (fileMaskFilter.test(file)) {
          filesFoundByFastSearch.add(file);
        }
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Search found " + filesFoundByFastSearch.size() + " indexed files, "
                  + filesToScanInitially.size() + " to scan initially");
      }

      AtomicInteger processedFastFiles = new AtomicInteger();
      processOnAllThreadsInReadActionWithRetries(
        new ConcurrentLinkedDeque<>(sorted(filesFoundByFastSearch, SEARCH_RESULT_FILE_COMPARATOR)),
        file -> {
          boolean result = fileProcessor.process(file);

          if (progressIndicator.isRunning()) {
            double fraction = (double)processedFastFiles.incrementAndGet() / filesFoundByFastSearch.size();
            progressIndicator.setFraction(fraction);
          }

          return result;
        }
      );

      //next: process files from non-indexed filesets by bruteforce:
      progressIndicator.setIndeterminate(true);
      progressIndicator.setText(FindBundle.message("progress.text.scanning.non.indexed.files"));

      //search item := { VirtualFile | IndexableFilesIterator | FindModelExtension | FilesDeque }
      List<Object> searchItems = collectSearchItems();

      AtomicInteger otherFilesCount = new AtomicInteger();
      AtomicInteger otherFilesTransientCount = new AtomicInteger();
      AtomicInteger otherFilesCacheAvoidingCount = new AtomicInteger();
      unfoldAndProcessSearchItems(
        searchItems,
        /*alreadySearched: */filesFoundByFastSearch::contains,
        file -> {
          boolean result = fileProcessor.process(file);

          otherFilesCount.incrementAndGet();
          if (file instanceof CacheAvoidingVirtualFile cacheAvoidingVirtualFile) {
            if(cacheAvoidingVirtualFile.isCached()){
              otherFilesCacheAvoidingCount.incrementAndGet();
            }
            else{
              otherFilesTransientCount.incrementAndGet();
            }
          }

          return result;
        }
      );

      if (LOG.isDebugEnabled()) {
        LOG.debug("Search processed " + otherFilesCount.get() + " non-indexed files: "
                  + otherFilesTransientCount.get() + " transient, " + otherFilesCacheAvoidingCount.get() + " cache-avoiding");
        LOG.debug("Search completed in " + TimeoutUtil.getDurationMillis(searchStartedAtNs) + " ms");
      }
    }
    catch (ProcessCanceledException e) {
      processPresentation.setCanceled(true);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Search canceled after " + TimeoutUtil.getDurationMillis(searchStartedAtNs) + " ms", new Exception(e));
      }
    }
    catch (Throwable th) {
      LOG.error(th);
    }

    if (!largeFiles.isEmpty()) {
      processPresentation.setLargeFilesWereNotScanned(largeFiles);
    }

    if (!progressIndicator.isCanceled()) {
      progressIndicator.setText(FindBundle.message("find.progress.search.completed"));
    }
  }

  /**
   * Adapt usageProcessor so it could be called as {@code Processor<VirtualFile>} -- i.e. it searches
   * a virtual file provided to {@code Processor<VirtualFile>} for the search pattern, and delivers all
   * the usages found (if any) to the usageProcessor.
   */
  private @NotNull Processor<VirtualFile> adaptUsageProcessorAsFileProcessor(@NotNull FindUsagesProcessPresentation processPresentation,
                                                                             @NotNull Processor<? super UsageInfo> usageProcessor) {
    AtomicInteger occurrenceCount = new AtomicInteger();
    ConcurrentMap<VirtualFile, Set<UsageInfo>> usagesBeingProcessed = new ConcurrentHashMap<>();
    AtomicBoolean reportedFirst = new AtomicBoolean();

    StringSearcher searcher = findModel.isRegularExpressions() || StringUtil.isEmpty(findModel.getStringToFind()) ?
                              null :
                              new StringSearcher(findModel.getStringToFind(), findModel.isCaseSensitive(), true);

    ClientId currentClientId = ClientId.getCurrent();

    return virtualFile -> {
      try (AccessToken ignored = ClientId.withClientId(currentClientId)) {
        return processFindInFilesUsagesInFile(
          processPresentation,
          usageProcessor,
          occurrenceCount,
          usagesBeingProcessed,
          reportedFirst,
          searcher,
          virtualFile
        );
      }
    };
  }

  /**
   * Looks up for the search pattern (=myFindModel) in the single virtualFile, and delivers all the usages found
   * (if any) to the usageProcessor.
   * Also does all the counting (occurrences found, etc) and the progress presentation updates (processPresentation)
   *
   * @return false if usageConsumer returns false for any of the occurrences found, true otherwise
   */
  private boolean processFindInFilesUsagesInFile(@NotNull FindUsagesProcessPresentation processPresentation,
                                                 @NotNull Processor<? super UsageInfo> usageConsumer,
                                                 @NotNull AtomicInteger occurrenceCount,
                                                 @NotNull ConcurrentMap<? super VirtualFile, Set<UsageInfo>> usagesBeingProcessed,
                                                 @NotNull AtomicBoolean reportedFirst,
                                                 @Nullable StringSearcher searcher,
                                                 @NotNull VirtualFile virtualFile) {
    if (!virtualFile.isValid()) return true;

    long fileLength = UsageViewManagerImpl.getFileLength(virtualFile);
    if (fileLength == -1) return true;

    boolean skipProjectFile = ProjectUtil.isProjectOrWorkspaceFile(virtualFile) && !findModel.isSearchInProjectFiles();
    if (skipProjectFile && !Registry.is("find.search.in.project.files")) return true;

    if (VirtualFileUtil.isTooLarge(virtualFile)) {
      largeFiles.add(virtualFile);
      return true;
    }

    progressIndicator.checkCanceled();
    String text = FindBundle.message("find.searching.for.string.in.file.progress",
                                     findModel.getStringToFind(), virtualFile.getPresentableUrl());
    progressIndicator.setText(text);
    progressIndicator.setText2(FindBundle.message("find.searching.for.string.in.file.occurrences.progress", occurrenceCount));

    Pair.NonNull<PsiFile, VirtualFile> pair = ReadAction.compute(() -> findFile(virtualFile));
    if (pair == null) return true;

    Set<UsageInfo> processedUsages =
      usagesBeingProcessed.computeIfAbsent(virtualFile, __ -> ConcurrentCollectionFactory.createConcurrentSet());
    PsiFile psiFile = pair.first;
    VirtualFile sourceVirtualFile = pair.second;

    if (searcher != null) {
      Document document = FileDocumentManager.getInstance().getCachedDocument(sourceVirtualFile);
      CharSequence s = document != null ? document.getCharsSequence() :
                       DiskQueryRelay.compute(() -> LoadTextUtil.loadText(sourceVirtualFile, -1));
      if (s.isEmpty() || searcher.scan(s) < 0) {
        return true;
      }
    }
    AtomicBoolean projectFileUsagesFound = new AtomicBoolean();
    boolean processedSuccessfully = FindInProjectUtil.processUsagesInFile(psiFile, sourceVirtualFile, findModel, info -> {
      if (skipProjectFile) {
        projectFileUsagesFound.set(true);
        return true;
      }
      if (reportedFirst.compareAndSet(false, true) && LOG.isDebugEnabled()) {
        LOG.debug("First usage found in " + TimeoutUtil.getDurationMillis(searchStartedAtNs) + " ms");
      }
      if (processedUsages.contains(info)) {
        return true;
      }
      boolean success = usageConsumer.process(info);
      processedUsages.add(info);
      return success;
    });
    if (!processedSuccessfully) {
      return false;
    }
    usagesBeingProcessed.remove(virtualFile); // after the whole virtualFile processed successfully, remove mapping to save memory

    if (projectFileUsagesFound.get()) {
      processPresentation.projectFileUsagesFound(() -> {
        FindModel model = findModel.clone();
        model.setSearchInProjectFiles(true);
        FindInProjectManager.getInstance(project).startFindInProject(model);
      });
      return true;
    }

    long totalSize;
    if (processedUsages.isEmpty()) {
      totalSize = totalFilesSize.get();
    }
    else {
      occurrenceCount.addAndGet(processedUsages.size());
      totalSize = totalFilesSize.addAndGet(fileLength);
    }

    if (totalSize > TOTAL_FILES_SIZE_LIMIT_BEFORE_ASKING) {
      TooManyUsagesStatus tooManyUsagesStatus = TooManyUsagesStatus.getFrom(progressIndicator);
      if (tooManyUsagesStatus.switchTooManyUsagesStatus()) {
        UsageViewManagerImpl.showTooManyUsagesWarningLater(project, tooManyUsagesStatus, progressIndicator, null,
                                                           () -> FindBundle.message("find.excessive.total.size.prompt",
                                                                                    UsageViewManagerImpl.presentableSize(
                                                                                      totalFilesSize.longValue()),
                                                                                    ApplicationNamesInfo.getInstance().getProductName()),
                                                           null);
      }
      tooManyUsagesStatus.pauseProcessingIfTooManyUsages();
      progressIndicator.checkCanceled();
    }
    return true;
  }

  /**
   * Unfolds search items (:={ VirtualFile | IndexableFilesIterator | FindModelExtension | FilesDeque }) down to individual
   * files, and process them with the fileProcessor. Also does filtering according to {@link #findModel} settings.
   */
  private void unfoldAndProcessSearchItems(@NotNull List<Object> searchItems,
                                           @NotNull Predicate<? super VirtualFile> alreadySearched,
                                           @NotNull Processor<? super VirtualFile> fileProcessor) {

    SearchScope customScope = findModel.isCustomScope() ? findModel.getCustomScope() : null;
    GlobalSearchScope globalCustomScope = customScope == null ? null : GlobalSearchScopeUtil.toGlobalSearchScope(customScope, project);
    boolean searchInLibs = globalCustomScope != null
                           && ReadAction.compute(() -> globalCustomScope.isSearchInLibraries());

    boolean unfoldSubdirs = directoryToSearchIn != null
                            && findModel.isWithSubdirectories();

    boolean ignoreExcluded = directoryToSearchIn != null
                             && !Registry.is("find.search.in.excluded.dirs")
                             && !ReadAction.compute(() -> projectFileIndex.isExcluded(directoryToSearchIn));
    boolean locateClassSources = directoryToSearchIn != null
                                 && ReadAction.compute(() -> projectFileIndex.getClassRootForFile(directoryToSearchIn)) != null;


    //wrap into concurrent deque for multi-threaded processing
    ConcurrentLinkedDeque<Object> searchItemsDeque = new ConcurrentLinkedDeque<>(searchItems);
    ConcurrentBitSet visitedFileIds = ConcurrentBitSet.create();
    final var workspaceFileIndex = WorkspaceFileIndex.getInstance(project);
    processOnAllThreadsInReadActionWithRetries(
      searchItemsDeque,

      searchItem -> { // := { VirtualFile | IndexableFilesIterator | FindModelExtension | FilesDeque }
        ProgressManager.checkCanceled();

        if (searchItem instanceof IndexableFilesIterator filesIterator) {
          IndexableSetOrigin origin = filesIterator.getOrigin();
          if (searchInLibs || origin instanceof ContentOrigin) {
            filesIterator.iterateFiles(project, file -> {
              if (!file.isDirectory()) {
                searchItemsDeque.add(file);
              }
              return true;
            }, VirtualFileFilter.ALL);
          }

          return true;
        }
        else if (searchItem instanceof FilesDeque filesDeque) {
          for (var file = filesDeque.computeNext(); file != null; file = filesDeque.computeNext()) {
            if (!file.isDirectory()) {
              searchItemsDeque.add(file);
            }
            ProgressManager.checkCanceled();
          }
          return true;
        }
        else if (searchItem instanceof FindModelExtension findModelExtension) {
          findModelExtension.iterateAdditionalFiles(findModel, project, file -> {
            if (!file.isDirectory() && !alreadySearched.test(file)) {
              //MAYBE RC: why don't we check fileMaskFilter here?
              //          Seems like the only implementation of FindModelExtension does this filtering by itself, inside it
              //          Same question about withSubdirs: here we ignore it, and just skip all the directories.
              //          Same for .isValid(), visitedFileIds, etc.
              //          ...In general, findModelExtension bypasses most of the regular file processing logic -- is there a reason
              //          for that?
              //          Maybe it is better to have _common_ processing pipeline, and findModelExtension just contributes the files
              //          into it -- instead of being completely independent branch, as it is now?
              return fileProcessor.process(file);
            }
            return true;
          });

          return true;
        }
        else if (searchItem instanceof VirtualFile file) {
          if (file instanceof VirtualFileWithId fileWithId
              && visitedFileIds.set(fileWithId.getId())) {//so files without id we _could_ search > once?
            return true;
          }
          if (!file.isValid()) {
            return true;
          }
          if (ignoreExcluded && projectFileIndex.isExcluded(file)) {
            return true;
          }
          if (file.isDirectory()) {
            if (unfoldSubdirs) {
              ContainerUtil.addAll(searchItemsDeque, file.getChildren());
            }
            return true;
          }
          if (!fileMaskFilter.test(file)) {
            return true;
          }
          if (globalCustomScope != null && !globalCustomScope.contains(file)) {
            return true;
          }

          if (hasReliableSearchers && workspaceFileIndex.isIndexable(file)) {
            for (int i = 0; i < searchers.length; i++) {
              FindInProjectSearcher searcher = searchers[i];
              if (isSearcherReliable[i] && searcher.isCovered(file)) {
                //if searcher is reliable, and it covers the file
                // => file either (is already processed), or (guaranteed to not contain the pattern)
                return true;
              }
            }
          }

          VirtualFile adjustedFile;
          if (file.getFileType().isBinary()) {
            if (locateClassSources) {
              Pair.NonNull<PsiFile, VirtualFile> pair = findFile(file);
              if (pair == null) return true;
              adjustedFile = pair.second;
            }
            else {
              return true;
            }
          }
          else {
            adjustedFile = file;
          }

          if (alreadySearched.test(adjustedFile)) {
            //TODO RC: why not check visitedFileIds also?
            //MAYBE RC: combine alreadySearched+fileMask+visitedFileIds into a single Predicate?
            return true;
          }
          return fileProcessor.process(adjustedFile);
        }

        throw new AssertionError("unknown item: " + searchItem);
      }
    );
  }


  /**
   * @return list of search 'items'. Item contains 1 or more files:
   * <pre>item := { VirtualFile | IndexableFilesIterator | FindModelExtension | FilesDeque }</pre>
   */
  private @NotNull List<Object> collectSearchItems() {
    SearchScope customScope = findModel.isCustomScope() ? findModel.getCustomScope() : null;

    List<Object> searchItems = new ArrayList<>();

    //fill the list from _one of_ {customScope | directory | module | indexingProviders} + FindModelExtensions:
    //so the resulting list is of { VirtualFile | IndexableFilesIterator | FindModelExtension | FilesDeque }

    if (customScope instanceof LocalSearchScope localSearchScope) {
      searchItems.addAll(GlobalSearchScopeUtil.getLocalScopeFiles(localSearchScope));
    }
    else if (customScope instanceof VirtualFileEnumeration virtualFileEnumeration) {
      // GlobalSearchScope can include files out of project roots e.g., FileScope / FilesScope. The starting files are all
      // in VFS already (because they have ids), but file-tree down from them could be not in VFS cache yet -- so it is worth
      // wrapping all the files into cache-avoiding wrappers here, and avoid trashing VFS cache with new entries during lookup:
      addAllWrappingAsCacheAvoiding(searchItems, FileBasedIndexEx.toFileIterable(virtualFileEnumeration.asArray()));
    }
    else if (directoryToSearchIn != null) {
      //Directory could be anywhere outside the project, hence it is worth wrapping it into a cache-avoiding wrapper,
      // so walking through its children won't trash VFS cache with new entries from some rarely used file-tree:
      VirtualFile cacheAvoidingDirectory = NewVirtualFile.asCacheAvoiding(directoryToSearchIn);
      boolean withSubdirs = findModel.isWithSubdirectories();
      if (withSubdirs) {
        searchItems.add(cacheAvoidingDirectory);
      }
      else {
        ContainerUtil.addAll(searchItems, cacheAvoidingDirectory.getChildren());
      }
      //MAYBE RC: should we return early here? Should FindModelExtension and nonIndexableFiles be added if user explicitly
      //          request a search in a specific directory _only_?
    }
    else if (moduleToSearchIn != null) {
      EntityStorage storage = WorkspaceModel.getInstance(project).getCurrentSnapshot();
      ModuleEntity moduleEntity = Objects.requireNonNull(storage.resolve(new ModuleId(moduleToSearchIn.getName())));
      //MAYBE RC: wrap files into a cache-avoiding wrappers?
      //          It seems useless, since files are all indexable, so they are already scanned and cached in VFS -- but is it true?
      searchItems.addAll(IndexableEntityProviderMethods.INSTANCE.createIterators(moduleEntity, storage, project));
    }
    else {
      FileBasedIndexEx indexes = (FileBasedIndexEx)FileBasedIndex.getInstance();
      //Don't wrap those files in cache-avoiding wrappers: indexable files are scanned, and hence (will be) cached in VFS anyway:
      searchItems.addAll(indexes.getIndexableFilesProviders(project));
    }

    searchItems.addAll(FindModelExtension.EP_NAME.getExtensionList());

    if (Boolean.TRUE.equals(project.getUserData(FIND_IN_FILES_SEARCH_IN_NON_INDEXABLE))) {
      //MAYBE RC: currently nonIndexableFiles() returns transient files already -- but maybe it is safer to return _regular_ files
      //          from nonIndexableFiles(), and wrap them all into transient here, in a unified way?
      searchItems.add(ReadAction.nonBlocking(() -> FilesDeque.Companion.nonIndexableDequeue(project)).executeSynchronously());
    }

    return searchItems;
  }

  /** @return candidate files found by searchers, filtered by fileMaskFilter */
  private static @NotNull Set<VirtualFile> collectFiles(@NotNull FindInProjectSearcher @NotNull [] searchers,
                                                        @NotNull Predicate<? super VirtualFile> fileFilter) {
    Set<VirtualFile> resultFiles = VfsUtilCore.createCompactVirtualFileSet();

    for (FindInProjectSearcher searcher : searchers) {
      Collection<VirtualFile> virtualFiles = searcher.searchForOccurrences();
      for (VirtualFile file : virtualFiles) {
        //MAYBE RC: we violate DRY here: do the same filtering in a few different places -- see processFilesInScope()
        if (fileFilter.test(file)) {
          resultFiles.add(file);
        }
      }
    }

    return resultFiles;
  }

  /** @return [psiFile, sourceFile] corresponding to the virtualFile */
  private @Nullable Pair.NonNull<PsiFile, VirtualFile> findFile(@NotNull VirtualFile virtualFile) {
    PsiFile psiFile = psiManager.findFile(virtualFile);
    if (psiFile != null) {
      PsiElement sourceFile = psiFile.getNavigationElement();
      if (sourceFile instanceof PsiFile) psiFile = (PsiFile)sourceFile;
      if (psiFile.getFileType().isBinary()) {
        psiFile = null;
      }
    }
    VirtualFile sourceVirtualFile = PsiUtilCore.getVirtualFile(psiFile);
    if (psiFile == null || psiFile.getFileType().isBinary()
        || sourceVirtualFile == null || sourceVirtualFile.getFileType().isBinary()) {
      return null;
    }

    return Pair.createNonNull(psiFile, sourceVirtualFile);
  }

  /**
   * Add all the files to the collection, wrapping them into a CacheAvoidingVirtualFileWrapper -- so walking through its children
   * won't trash VFS cache with new entries
   *
   * @see NewVirtualFile#asCacheAvoiding()
   * @see com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile
   */
  private static void addAllWrappingAsCacheAvoiding(@NotNull List<Object> collection,
                                                    @NotNull Iterable<VirtualFile> files) {
    for (VirtualFile file : files) {
      collection.add(NewVirtualFile.asCacheAvoiding(file));
    }
  }
}
