// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.find.FindBundle;
import com.intellij.find.FindInProjectSearchEngine;
import com.intellij.find.FindModel;
import com.intellij.find.FindModelExtension;
import com.intellij.find.findInProject.FindInProjectManager;
import com.intellij.find.ngrams.TrigramTextSearchService;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class FindInProjectTask {
  private static final Logger LOG = Logger.getInstance(FindInProjectTask.class);

  private static final Comparator<VirtualFile> SEARCH_RESULT_FILE_COMPARATOR =
    Comparator.comparing((VirtualFile f) -> f instanceof VirtualFileWithId ? ((VirtualFileWithId)f).getId() : 0)
      .thenComparing(VirtualFile::getName) // in case files without id are also searched
      .thenComparing(VirtualFile::getPath);
  private static final int FILES_SIZE_LIMIT = 70 * 1024 * 1024; // megabytes.

  private final FindModel myFindModel;
  private final Project myProject;
  private final PsiManager myPsiManager;
  private final @Nullable VirtualFile myDirectory;
  private final ProjectFileIndex myProjectFileIndex;
  private final Condition<VirtualFile> myFileMask;
  private final ProgressIndicator myProgress;
  private final @Nullable Module myModule;
  private final Set<VirtualFile> myLargeFiles = Collections.synchronizedSet(new HashSet<>());
  private final Set<? extends VirtualFile> myFilesToScanInitially;
  private final AtomicLong myTotalFilesSize = new AtomicLong();
  private final @NotNull List<FindInProjectSearchEngine.@NotNull FindInProjectSearcher> mySearchers;
  private long mySearchStartedAt;

  FindInProjectTask(@NotNull FindModel findModel, @NotNull Project project, @NotNull Set<? extends VirtualFile> filesToScanInitially,
                    boolean tooManyUsagesStatus) {
    myFindModel = findModel;
    myProject = project;
    myFilesToScanInitially = filesToScanInitially;
    myDirectory = FindInProjectUtil.getDirectory(findModel);
    myPsiManager = PsiManager.getInstance(project);

    String moduleName = findModel.getModuleName();
    myModule = moduleName == null ? null : ReadAction.compute(() -> ModuleManager.getInstance(project).findModuleByName(moduleName));
    myProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    Condition<CharSequence> patternCondition = FindInProjectUtil.createFileMaskCondition(findModel.getFileFilter());

    myFileMask = file -> file != null && patternCondition.value(file.getNameSequence());

    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    myProgress = progress != null ? progress : new EmptyProgressIndicator();

    if (tooManyUsagesStatus) {
      TooManyUsagesStatus.createFor(myProgress);
    }

    mySearchers = ContainerUtil.mapNotNull(FindInProjectSearchEngine.EP_NAME.getExtensions(), se -> se.createSearcher(findModel, project));
  }

  void findUsages(@NotNull FindUsagesProcessPresentation processPresentation, @NotNull Processor<? super UsageInfo> usageProcessor) {
    if (!EDT.isCurrentThreadEdt()) {
      ApplicationManager.getApplication().assertReadAccessNotAllowed();
    }
    CoreProgressManager.assertUnderProgress(myProgress);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Searching for '" + myFindModel.getStringToFind() + "'");
    }
    mySearchStartedAt = System.nanoTime();
    try {
      Processor<VirtualFile> fileProcessor = wrapUsageProcessor(processPresentation, usageProcessor);

      myProgress.setIndeterminate(true);
      myProgress.setText(FindBundle.message("progress.text.scanning.indexed.files"));
      Set<VirtualFile> filesForFastWordSearch = getFilesForFastWordSearch();

      myProgress.setIndeterminate(false);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Search found " + filesForFastWordSearch.size() + " indexed files");
      }

      ConcurrentLinkedDeque<VirtualFile> deque = new ConcurrentLinkedDeque<>(
        ContainerUtil.sorted(filesForFastWordSearch, SEARCH_RESULT_FILE_COMPARATOR));
      AtomicInteger processedFastFiles = new AtomicInteger();
      FilesScanExecutor.processOnAllThreadsInReadActionWithRetries(deque, o -> {
        boolean result = fileProcessor.process(o);
        if (myProgress.isRunning()) {
          double fraction = (double)processedFastFiles.incrementAndGet() / filesForFastWordSearch.size();
          myProgress.setFraction(fraction);
        }
        return result;
      });

      myProgress.setIndeterminate(true);
      myProgress.setText(FindBundle.message("progress.text.scanning.non.indexed.files"));

      boolean canRelyOnIndices = canRelyOnSearchers();
      AtomicInteger otherFilesCount = new AtomicInteger();
      processFilesInScope(filesForFastWordSearch, canRelyOnIndices, file -> {
        boolean result = fileProcessor.process(file);
        otherFilesCount.incrementAndGet();
        return result;
      });
      if (LOG.isDebugEnabled()) {
        LOG.debug("Search processed " + otherFilesCount.get() + " non-indexed files");
        LOG.debug("Search completed in " + TimeoutUtil.getDurationMillis(mySearchStartedAt) + " ms");
      }
    }
    catch (ProcessCanceledException e) {
      processPresentation.setCanceled(true);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Search canceled after " + TimeoutUtil.getDurationMillis(mySearchStartedAt) + " ms", e);
      }
    }
    catch (Throwable th) {
      LOG.error(th);
    }

    if (!myLargeFiles.isEmpty()) {
      processPresentation.setLargeFilesWereNotScanned(myLargeFiles);
    }

    if (!myProgress.isCanceled()) {
      myProgress.setText(FindBundle.message("find.progress.search.completed"));
    }
  }

  private @NotNull Processor<VirtualFile> wrapUsageProcessor(@NotNull FindUsagesProcessPresentation processPresentation,
                                                             @NotNull Processor<? super UsageInfo> usageConsumer) {
    AtomicInteger occurrenceCount = new AtomicInteger();
    Map<VirtualFile, Set<UsageInfo>> usagesBeingProcessed = new ConcurrentHashMap<>();
    AtomicBoolean reportedFirst = new AtomicBoolean();

    StringSearcher searcher = myFindModel.isRegularExpressions() || StringUtil.isEmpty(myFindModel.getStringToFind()) ? null :
                              new StringSearcher(myFindModel.getStringToFind(), myFindModel.isCaseSensitive(), true);

    ClientId currentClientId = ClientId.getCurrent();

    return virtualFile -> {
      try (AccessToken ignored = ClientId.withClientId(currentClientId)) {
        return processFindInFilesUsagesInFile(processPresentation, usageConsumer, occurrenceCount,
                                              usagesBeingProcessed, reportedFirst, searcher,
                                              virtualFile);
      }
    };
  }

  private boolean processFindInFilesUsagesInFile(@NotNull FindUsagesProcessPresentation processPresentation,
                                                 @NotNull Processor<? super UsageInfo> usageConsumer,
                                                 @NotNull AtomicInteger occurrenceCount,
                                                 @NotNull Map<? super VirtualFile, Set<UsageInfo>> usagesBeingProcessed,
                                                 @NotNull AtomicBoolean reportedFirst,
                                                 @Nullable StringSearcher searcher,
                                                 @NotNull VirtualFile virtualFile) {
    if (!virtualFile.isValid()) return true;

    long fileLength = UsageViewManagerImpl.getFileLength(virtualFile);
    if (fileLength == -1) return true;

    boolean skipProjectFile = ProjectUtil.isProjectOrWorkspaceFile(virtualFile) && !myFindModel.isSearchInProjectFiles();
    if (skipProjectFile && !Registry.is("find.search.in.project.files")) return true;

    if (fileLength > FileUtilRt.LARGE_FOR_CONTENT_LOADING) {
      myLargeFiles.add(virtualFile);
      return true;
    }

    myProgress.checkCanceled();
    String text = FindBundle.message("find.searching.for.string.in.file.progress",
                                     myFindModel.getStringToFind(), virtualFile.getPresentableUrl());
    myProgress.setText(text);
    myProgress.setText2(FindBundle.message("find.searching.for.string.in.file.occurrences.progress", occurrenceCount));

    Pair.NonNull<PsiFile, VirtualFile> pair = ReadAction.compute(() -> findFile(virtualFile));
    if (pair == null) return true;

    Set<UsageInfo> processedUsages = usagesBeingProcessed.computeIfAbsent(virtualFile, __ -> ConcurrentCollectionFactory.createConcurrentSet());
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
    boolean processedSuccessfully = FindInProjectUtil.processUsagesInFile(psiFile, sourceVirtualFile, myFindModel, info -> {
      if (skipProjectFile) {
        projectFileUsagesFound.set(true);
        return true;
      }
      if (reportedFirst.compareAndSet(false, true) && LOG.isDebugEnabled()) {
        LOG.debug("First usage found in " + TimeoutUtil.getDurationMillis(mySearchStartedAt) + " ms");
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
        FindModel model = myFindModel.clone();
        model.setSearchInProjectFiles(true);
        FindInProjectManager.getInstance(myProject).startFindInProject(model);
      });
      return true;
    }

    long totalSize;
    if (processedUsages.isEmpty()) {
      totalSize = myTotalFilesSize.get();
    }
    else {
      occurrenceCount.addAndGet(processedUsages.size());
      totalSize = myTotalFilesSize.addAndGet(fileLength);
    }

    if (totalSize > FILES_SIZE_LIMIT) {
      TooManyUsagesStatus tooManyUsagesStatus = TooManyUsagesStatus.getFrom(myProgress);
      if (tooManyUsagesStatus.switchTooManyUsagesStatus()) {
        UsageViewManagerImpl.showTooManyUsagesWarningLater(myProject, tooManyUsagesStatus, myProgress, null, () -> FindBundle.message("find.excessive.total.size.prompt",
                                                        UsageViewManagerImpl.presentableSize(myTotalFilesSize.longValue()),
                                                        ApplicationNamesInfo.getInstance().getProductName()), null);
      }
      tooManyUsagesStatus.pauseProcessingIfTooManyUsages();
      myProgress.checkCanceled();
    }
    return true;
  }

  // must return non-binary files
  private void processFilesInScope(@NotNull Set<? extends VirtualFile> alreadySearched,
                                   boolean checkCoveredBySearchers,
                                   @NotNull Processor<? super VirtualFile> fileProcessor) {
    SearchScope customScope = myFindModel.isCustomScope() ? myFindModel.getCustomScope() : null;
    GlobalSearchScope globalCustomScope = customScope == null ? null : GlobalSearchScopeUtil.toGlobalSearchScope(customScope, myProject);

    boolean ignoreExcluded = myDirectory != null
                             && !Registry.is("find.search.in.excluded.dirs")
                             && !ReadAction.compute(() -> myProjectFileIndex.isExcluded(myDirectory));
    boolean withSubdirs = myDirectory != null && myFindModel.isWithSubdirectories();
    boolean locateClassSources = myDirectory != null && ReadAction.compute(() -> myProjectFileIndex.getClassRootForFile(myDirectory)) != null;
    boolean searchInLibs = globalCustomScope != null && ReadAction.compute(() -> globalCustomScope.isSearchInLibraries());

    ConcurrentLinkedDeque<Object> deque = new ConcurrentLinkedDeque<>();
    if (customScope instanceof LocalSearchScope) {
      deque.addAll(GlobalSearchScopeUtil.getLocalScopeFiles((LocalSearchScope)customScope));
    }
    else if (customScope instanceof VirtualFileEnumeration) {
      // GlobalSearchScope can include files out of project roots e.g., FileScope / FilesScope
      ContainerUtil.addAll(deque, FileBasedIndexEx.toFileIterable(((VirtualFileEnumeration)customScope).asArray()));
    }
    else if (myDirectory != null) {
      deque.addAll(withSubdirs ? List.of(myDirectory) : List.of(myDirectory.getChildren()));
    }
    else if (myModule != null) {
      EntityStorage storage = WorkspaceModel.getInstance(myProject).getCurrentSnapshot();
      ModuleEntity moduleEntity = Objects.requireNonNull(storage.resolve(new ModuleId(myModule.getName())));
      deque.addAll(IndexableEntityProviderMethods.INSTANCE.createIterators(moduleEntity, storage, myProject));
    }
    else {
      deque.addAll(((FileBasedIndexEx)FileBasedIndex.getInstance()).getIndexableFilesProviders(myProject));
    }
    deque.addAll(FindModelExtension.EP_NAME.getExtensionList());

    ConcurrentBitSet visitedFiles = ConcurrentBitSet.create();
    Processor<Object> consumer = obj -> {
      ProgressManager.checkCanceled();
      if (obj instanceof IndexableFilesIterator) {
        IndexableSetOrigin origin = ((IndexableFilesIterator)obj).getOrigin();
        if (!searchInLibs && !(origin instanceof ContentOrigin)) return true;
        ((IndexableFilesIterator)obj).iterateFiles(myProject, file -> {
          if (file.isDirectory()) return true;
          deque.add(file);
          return true;
        }, VirtualFileFilter.ALL);
      }
      else if (obj instanceof FindModelExtension) {
        ((FindModelExtension)obj).iterateAdditionalFiles(myFindModel, myProject, o -> {
          if (o.isDirectory()) return true;
          if (!alreadySearched.contains(o)) {
            return fileProcessor.process(o);
          }
          return true;
        });
      }
      else if (obj instanceof VirtualFile file) {
        if (file instanceof VirtualFileWithId && visitedFiles.set(((VirtualFileWithId)file).getId())) {
          return true;
        }
        if (!file.isValid()) {
          return true;
        }
        if (ignoreExcluded && myProjectFileIndex.isExcluded(file)) {
          return true;
        }
        if (file.isDirectory()) {
          if (withSubdirs) {
            ContainerUtil.addAll(deque, ((VirtualFile)obj).getChildren());
          }
          return true;
        }
        if (!myFileMask.value(file) ||
            globalCustomScope != null && !globalCustomScope.contains(file) ||
            checkCoveredBySearchers && ContainerUtil.find(mySearchers, p -> p.isCovered(file)) != null) {
          return true;
        }

        VirtualFile adjustedFile;
        if (file.getFileType().isBinary()) {
          if (locateClassSources) {
            Pair.NonNull<PsiFile, VirtualFile> pair = findFile(file);
            if (pair == null) return true;
            adjustedFile = Objects.requireNonNull(pair.second);
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
      else {
        throw new AssertionError("unknown item: " + obj);
      }
      return true;
    };
    FilesScanExecutor.processOnAllThreadsInReadActionWithRetries(deque, consumer::process);
  }

  private boolean canRelyOnSearchers() {
    if (!TrigramTextSearchService.useIndexingSearchExtensions()) return false;
    return ContainerUtil.find(mySearchers, s -> s.isReliable()) != null;
  }

  private @NotNull Set<VirtualFile> getFilesForFastWordSearch() {
    Set<VirtualFile> resultFiles = VfsUtilCore.createCompactVirtualFileSet();
    for (VirtualFile file : myFilesToScanInitially) {
      if (myFileMask.value(file)) {
        resultFiles.add(file);
      }
    }
    if (!TrigramTextSearchService.useIndexingSearchExtensions()) return resultFiles;
    for (FindInProjectSearchEngine.FindInProjectSearcher searcher : mySearchers) {
      Collection<VirtualFile> virtualFiles = searcher.searchForOccurrences();
      for (VirtualFile file : virtualFiles) {
        if (myFileMask.value(file)) {
          resultFiles.add(file);
        }
      }
    }

    return resultFiles;
  }

  private Pair.NonNull<PsiFile, VirtualFile> findFile(@NotNull VirtualFile virtualFile) {
    PsiFile psiFile = myPsiManager.findFile(virtualFile);
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
