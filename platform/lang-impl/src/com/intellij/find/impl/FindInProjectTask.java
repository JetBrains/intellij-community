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
import com.intellij.find.ngrams.TrigramTextSearchService;
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
import com.intellij.openapi.roots.impl.FilesScanExecutor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
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
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.kind.ContentOrigin;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import com.intellij.util.text.StringSearcher;
import com.intellij.util.ui.EDT;
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
  private final Set<? extends VirtualFile> filesToScanInitially;
  private final @NotNull List<@NotNull FindInProjectSearcher> searchers;

  private final Project project;
  private final ProjectFileIndex projectFileIndex;

  private final PsiManager psiManager;

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
    );

    psiManager = PsiManager.getInstance(project);

    directoryToSearchIn = FindInProjectUtil.getDirectory(findModel);

    String moduleName = findModel.getModuleName();
    moduleToSearchIn = moduleName == null ?
                       null :
                       ReadAction.compute(() -> ModuleManager.getInstance(project).findModuleByName(moduleName));
    projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();

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
      Processor<VirtualFile> fileProcessor = adaptUsageProcessor(processPresentation, usageProcessor);

      progressIndicator.setIndeterminate(true);
      progressIndicator.setText(FindBundle.message("progress.text.scanning.indexed.files"));
      Set<VirtualFile> filesForFastWordSearch = getFilesForFastWordSearch();

      progressIndicator.setIndeterminate(false);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Search found " + filesForFastWordSearch.size() + " indexed files");
      }

      ConcurrentLinkedDeque<VirtualFile> deque = new ConcurrentLinkedDeque<>(
        ContainerUtil.sorted(filesForFastWordSearch, SEARCH_RESULT_FILE_COMPARATOR)
      );
      AtomicInteger processedFastFiles = new AtomicInteger();
      FilesScanExecutor.processOnAllThreadsInReadActionWithRetries(deque, o -> {
        boolean result = fileProcessor.process(o);
        if (progressIndicator.isRunning()) {
          double fraction = (double)processedFastFiles.incrementAndGet() / filesForFastWordSearch.size();
          progressIndicator.setFraction(fraction);
        }
        return result;
      });

      progressIndicator.setIndeterminate(true);
      progressIndicator.setText(FindBundle.message("progress.text.scanning.non.indexed.files"));

      boolean hasReliableSearchers = hasReliableSearchers();
      AtomicInteger otherFilesCount = new AtomicInteger();
      processFilesInScope(filesForFastWordSearch, hasReliableSearchers, file -> {
        boolean result = fileProcessor.process(file);
        otherFilesCount.incrementAndGet();
        return result;
      });

      if (LOG.isDebugEnabled()) {
        LOG.debug("Search processed " + otherFilesCount.get() + " non-indexed files");
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
  private @NotNull Processor<VirtualFile> adaptUsageProcessor(@NotNull FindUsagesProcessPresentation processPresentation,
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

  // must return non-binary files
  private void processFilesInScope(@NotNull Set<? extends VirtualFile> alreadySearched,
                                   boolean checkCoveredBySearchers,
                                   @NotNull Processor<? super VirtualFile> fileProcessor) {
    SearchScope customScope = findModel.isCustomScope() ? findModel.getCustomScope() : null;
    GlobalSearchScope globalCustomScope = customScope == null ? null : GlobalSearchScopeUtil.toGlobalSearchScope(customScope, project);

    boolean ignoreExcluded = directoryToSearchIn != null
                             && !Registry.is("find.search.in.excluded.dirs")
                             && !ReadAction.compute(() -> projectFileIndex.isExcluded(directoryToSearchIn));
    boolean withSubdirs = directoryToSearchIn != null
                          && findModel.isWithSubdirectories();
    boolean locateClassSources = directoryToSearchIn != null
                                 && ReadAction.compute(() -> projectFileIndex.getClassRootForFile(directoryToSearchIn)) != null;
    boolean searchInLibs = globalCustomScope != null
                           && ReadAction.compute(() -> globalCustomScope.isSearchInLibraries());

    //Queue{ VirtualFile | IndexableFilesIterator | FindModelExtension }
    ConcurrentLinkedDeque<Object> searchTaskDeque = new ConcurrentLinkedDeque<>();
    if (customScope instanceof LocalSearchScope) {
      searchTaskDeque.addAll(GlobalSearchScopeUtil.getLocalScopeFiles((LocalSearchScope)customScope));
    }
    else if (customScope instanceof VirtualFileEnumeration) {
      // GlobalSearchScope can include files out of project roots e.g., FileScope / FilesScope
      ContainerUtil.addAll(searchTaskDeque, FileBasedIndexEx.toFileIterable(((VirtualFileEnumeration)customScope).asArray()));
    }
    else if (directoryToSearchIn != null) {
      //TODO RC: why here, if below we still unfold subdirectories?
      searchTaskDeque.addAll(withSubdirs ? List.of(directoryToSearchIn) : List.of(directoryToSearchIn.getChildren()));
    }
    else if (moduleToSearchIn != null) {
      EntityStorage storage = WorkspaceModel.getInstance(project).getCurrentSnapshot();
      ModuleEntity moduleEntity = Objects.requireNonNull(storage.resolve(new ModuleId(moduleToSearchIn.getName())));
      searchTaskDeque.addAll(IndexableEntityProviderMethods.INSTANCE.createIterators(moduleEntity, storage, project));
    }
    else {
      searchTaskDeque.addAll(((FileBasedIndexEx)FileBasedIndex.getInstance()).getIndexableFilesProviders(project));
    }
    searchTaskDeque.addAll(FindModelExtension.EP_NAME.getExtensionList());

    ConcurrentBitSet visitedFileIds = ConcurrentBitSet.create();

    FilesScanExecutor.processOnAllThreadsInReadActionWithRetries(
      searchTaskDeque,
      searchItem -> { // == { VirtualFile | IndexableFilesIterator | FindModelExtension }
        ProgressManager.checkCanceled();

        if (searchItem instanceof IndexableFilesIterator filesIterator) {
          IndexableSetOrigin origin = filesIterator.getOrigin();
          if (searchInLibs || origin instanceof ContentOrigin) {
            filesIterator.iterateFiles(project, file -> {
              if (!file.isDirectory()) {
                searchTaskDeque.add(file);
              }
              return true;
            }, VirtualFileFilter.ALL);
          }

          return true;
        }
        else if (searchItem instanceof FindModelExtension findModelExtension) {
          findModelExtension.iterateAdditionalFiles(findModel, project, file -> {
            if (!file.isDirectory() && !alreadySearched.contains(file)) {
              //TODO RC: why don't we check fileMaskFilter here?
              //         Seems like the only implementation of FindModelExtension does this filtering by itself, inside it
              return fileProcessor.process(file);
            }
            return true;
          });

          return true;
        }
        else if (searchItem instanceof VirtualFile file) {
          if (file instanceof VirtualFileWithId fileWithId
              && visitedFileIds.set(fileWithId.getId())) {//so files without id we could search > once?
            return true;
          }
          if (!file.isValid()) {
            return true;
          }
          if (ignoreExcluded && projectFileIndex.isExcluded(file)) {
            return true;
          }
          if (file.isDirectory()) {
            if (withSubdirs) {
              ContainerUtil.addAll(searchTaskDeque, file.getChildren());
            }
            return true;
          }
          if (!fileMaskFilter.test(file)
              || globalCustomScope != null && !globalCustomScope.contains(file)
              || checkCoveredBySearchers && ContainerUtil.find(searchers, p -> p.isCovered(file)) != null) {
            return true;
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
          if (alreadySearched.contains(adjustedFile)) {
            return true;
          }
          return fileProcessor.process(adjustedFile);
        }

        throw new AssertionError("unknown item: " + searchItem);
      }
    );
  }

  /**
   * @return true if there is at least one reliable searcher ({@link FindInProjectSearcher#isReliable()}),
   * false otherwise. I.e. return true if everything could be found by _some_ searcher, or
   * false if there is something outside all searchers
   */
  private boolean hasReliableSearchers() {
    return ContainerUtil.find(searchers, s -> s.isReliable()) != null;
  }

  private @NotNull Set<VirtualFile> getFilesForFastWordSearch() {
    Set<VirtualFile> resultFiles = VfsUtilCore.createCompactVirtualFileSet();
    for (VirtualFile file : filesToScanInitially) {
      if (fileMaskFilter.test(file)) {
        resultFiles.add(file);
      }
    }
    //TODO RC: move it to IdeaIndexBasedFindInProjectSearchEngine
    if (!TrigramTextSearchService.useIndexingSearchExtensions()) {
      return resultFiles;
    }
    for (FindInProjectSearcher searcher : searchers) {
      Collection<VirtualFile> virtualFiles = searcher.searchForOccurrences();
      for (VirtualFile file : virtualFiles) {
        if (fileMaskFilter.test(file)) {
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
    if (psiFile == null || psiFile.getFileType().isBinary() || sourceVirtualFile == null || sourceVirtualFile.getFileType().isBinary()) {
      return null;
    }

    return Pair.createNonNull(psiFile, sourceVirtualFile);
  }
}
