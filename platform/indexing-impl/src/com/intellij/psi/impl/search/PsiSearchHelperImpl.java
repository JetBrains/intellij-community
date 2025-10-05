// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.CodeInsightContextManager;
import com.intellij.codeInsight.multiverse.CodeInsightContexts;
import com.intellij.concurrency.AsyncFuture;
import com.intellij.concurrency.AsyncUtil;
import com.intellij.concurrency.JobLauncher;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.find.ngrams.TrigramIndex;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.progress.util.TooManyUsagesStatus;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbUtil;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.TrigramBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.indexing.BinaryFileSourceProvider;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.search.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.SmartList;
import com.intellij.util.codeInsight.CommentUtilCore;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndex.AllKeysQuery;
import com.intellij.util.indexing.IndexingBundle;
import com.intellij.util.text.StringSearcher;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class PsiSearchHelperImpl implements PsiSearchHelper {
  private static final ExtensionPointName<ScopeOptimizer> USE_SCOPE_OPTIMIZER_EP_NAME = ExtensionPointName.create("com.intellij.useScopeOptimizer");

  public static final Logger LOG = Logger.getInstance(PsiSearchHelperImpl.class);
  private final PsiManagerEx myManager;
  private final DumbService myDumbService;

  public enum Options {
    PROCESS_INJECTED_PSI, CASE_SENSITIVE_SEARCH, PROCESS_ONLY_JAVA_IDENTIFIERS_IF_POSSIBLE
  }

  @Override
  public @NotNull SearchScope getUseScope(@NotNull PsiElement element) {
    return getUseScope(element, false);
  }

  @Override
  public @NotNull SearchScope getCodeUsageScope(@NotNull PsiElement element) {
    return getUseScope(element, true);
  }

  private static @NotNull SearchScope getUseScope(@NotNull PsiElement element, boolean restrictToCodeUsageScope) {
    SearchScope scope = PsiSearchScopeUtil.USE_SCOPE_KEY.get(element.getContainingFile());
    if (scope != null) return scope;
    scope = element.getUseScope();
    for (UseScopeEnlarger enlarger : UseScopeEnlarger.EP_NAME.getExtensionList()) {
      ProgressManager.checkCanceled();
      SearchScope additionalScope = null;
      try {
        additionalScope = enlarger.getAdditionalUseScope(element);
      }
      catch (IndexNotReadyException pce) {
        LOG.debug("ProcessCanceledException thrown while getUseScope() calculation", pce);
      }
      if (additionalScope != null) {
        scope = scope.union(additionalScope);
      }
    }

    scope = restrictScope(scope, USE_SCOPE_OPTIMIZER_EP_NAME.getExtensionList(), element);
    if (restrictToCodeUsageScope) {
      scope = restrictScope(scope, CODE_USAGE_SCOPE_OPTIMIZER_EP_NAME.getExtensionList(), element);
    }

    return scope;
  }

  private static @NotNull SearchScope restrictScope(@NotNull SearchScope baseScope,
                                                    @NotNull List<? extends ScopeOptimizer> optimizers,
                                                    @NotNull PsiElement element) {
    SearchScope scopeToRestrict = ScopeOptimizer.calculateOverallRestrictedUseScope(optimizers, element);
    if (scopeToRestrict != null) {
      return baseScope.intersectWith(scopeToRestrict);
    }

    return baseScope;
  }

  public PsiSearchHelperImpl(@NotNull Project project) {
    myManager = PsiManagerEx.getInstanceEx(project);
    myDumbService = DumbService.getInstance(myManager.getProject());
  }

  @Override
  public PsiElement @NotNull [] findCommentsContainingIdentifier(@NotNull String identifier, @NotNull SearchScope searchScope) {
    List<PsiElement> result = Collections.synchronizedList(new ArrayList<>());
    Processor<PsiElement> processor = Processors.cancelableCollectProcessor(result);
    processCommentsContainingIdentifier(identifier, searchScope, processor);
    return PsiUtilCore.toPsiElementArray(result);
  }

  @Override
  public boolean processCommentsContainingIdentifier(@NotNull String identifier,
                                                     @NotNull SearchScope searchScope,
                                                     @NotNull Processor<? super PsiElement> processor) {
    TextOccurenceProcessor occurrenceProcessor = (element, offsetInElement) -> {
      if (CommentUtilCore.isCommentTextElement(element) && element.findReferenceAt(offsetInElement) == null) {
        return processor.process(element);
      }
      return true;
    };
    return processElementsWithWord(occurrenceProcessor, searchScope, identifier, UsageSearchContext.IN_COMMENTS, true);
  }

  @Override
  public boolean processElementsWithWord(@NotNull TextOccurenceProcessor processor,
                                         @NotNull SearchScope searchScope,
                                         @NotNull String text,
                                         short searchContext,
                                         boolean caseSensitive) {
    return processElementsWithWord(processor, searchScope, text, searchContext, caseSensitive, shouldProcessInjectedPsi(searchScope));
  }

  @Override
  public boolean processElementsWithWord(@NotNull TextOccurenceProcessor processor,
                                         @NotNull SearchScope searchScope,
                                         @NotNull String text,
                                         short searchContext,
                                         boolean caseSensitive,
                                         boolean processInjectedPsi) {
    EnumSet<Options> options = makeOptions(caseSensitive, processInjectedPsi);

    return processElementsWithWord(searchScope, text, searchContext, options, null, new SearchSession(), processor);
  }

  @Override
  public boolean hasIdentifierInFile(@NotNull PsiFile psiFile, @NotNull String name) {
    PsiUtilCore.ensureValid(psiFile);
    if (psiFile.getVirtualFile() == null || DumbService.isDumb(psiFile.getProject())) {
      return StringUtil.contains(psiFile.getViewProvider().getContents(), name);
    }

    // TODO: direct forward index access is not used right now since IdIndex shared index doesn't have forward index
    GlobalSearchScope fileScope = GlobalSearchScope.fileScope(psiFile);
    IdIndexEntry key = new IdIndexEntry(name, true);
    return !FileBasedIndex.getInstance().getContainingFiles(IdIndex.NAME, key, fileScope).isEmpty();
  }

  @Override
  public @NotNull AsyncFuture<Boolean> processElementsWithWordAsync(@NotNull TextOccurenceProcessor processor,
                                                                    @NotNull SearchScope searchScope,
                                                                    @NotNull String text,
                                                                    short searchContext,
                                                                    boolean caseSensitively) {
    boolean result = processElementsWithWord(processor, searchScope, text, searchContext, caseSensitively,
                                             shouldProcessInjectedPsi(searchScope));
    return AsyncUtil.wrapBoolean(result);
  }

  @Override
  public @NotNull AsyncFuture<Boolean> processRequestsAsync(@NotNull SearchRequestCollector collector, @NotNull Processor<? super PsiReference> processor) {
    return AsyncUtil.wrapBoolean(processRequests(collector, processor));
  }

  public boolean processElementsWithWord(@NotNull SearchScope searchScope,
                                         @NotNull String text,
                                         short searchContext,
                                         @NotNull EnumSet<Options> options,
                                         @Nullable String containerName,
                                         @NotNull SearchSession session,
                                         @NotNull TextOccurenceProcessor processor) {
    return bulkProcessElementsWithWord(searchScope, text, searchContext, options, containerName, session, (scope, offsetsInScope, searcher) ->
      LowLevelSearchUtil.processElementsAtOffsets(scope, searcher, options.contains(Options.PROCESS_INJECTED_PSI), getOrCreateIndicator(),
                                                  offsetsInScope, processor));
  }

  boolean bulkProcessElementsWithWord(@NotNull SearchScope searchScope,
                                      @NotNull String text,
                                      short searchContext,
                                      @NotNull EnumSet<Options> options,
                                      @Nullable String containerName,
                                      @NotNull SearchSession session,
                                      @NotNull BulkOccurrenceProcessor processor) {
    if (text.isEmpty()) {
      throw new IllegalArgumentException("Cannot search for elements with empty text");
    }
    ProgressIndicator progress = getOrCreateIndicator();
    if (searchScope instanceof GlobalSearchScope) {
      StringSearcher searcher = new StringSearcher(text, options.contains(Options.CASE_SENSITIVE_SEARCH), true,
                                                   searchContext == UsageSearchContext.IN_STRINGS,
                                                   options.contains(Options.PROCESS_ONLY_JAVA_IDENTIFIERS_IF_POSSIBLE));

      return processElementsWithTextInGlobalScope((GlobalSearchScope)searchScope, searcher, searchContext,
                                                  options.contains(Options.CASE_SENSITIVE_SEARCH), containerName, session, progress, processor);
    }
    LocalSearchScope scope = (LocalSearchScope)searchScope;
    PsiElement[] scopeElements = scope.getScope();
    StringSearcher searcher = new StringSearcher(text, options.contains(Options.CASE_SENSITIVE_SEARCH), true,
                                                       searchContext == UsageSearchContext.IN_STRINGS,
                                                       options.contains(Options.PROCESS_ONLY_JAVA_IDENTIFIERS_IF_POSSIBLE));
    ReadActionProcessor<PsiElement> localProcessor = new ReadActionProcessor<>() {
      @Override
      public boolean processInReadAction(PsiElement scopeElement) {
        if (!scopeElement.isValid()) return true;
        if (!scopeElement.isPhysical() || scopeElement instanceof PsiCompiledElement) {
          scopeElement = scopeElement.getNavigationElement();
        }
        if (scopeElement instanceof PsiCompiledElement) {
          // can't scan text of the element
          return true;
        }
        if (scopeElement.getTextRange() == null) {
          // clients can put whatever they want to the LocalSearchScope. Skip what we can't process.
          LOG.debug("Element " + scopeElement + " of class " + scopeElement.getClass() + " has null range");
          return true;
        }
        return processor.execute(scopeElement, LowLevelSearchUtil.getTextOccurrencesInScope(scopeElement, searcher), searcher);
      }

      @Override
      public String toString() {
        return processor.toString();
      }
    };
    return JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Arrays.asList(scopeElements), progress, localProcessor);
  }

  private @Nullable("null means we did not find common container files") Set<VirtualFile> intersectionWithContainerNameFiles(@NotNull GlobalSearchScope commonScope,
                                                                                                                             @NotNull Collection<? extends WordRequestInfo> data,
                                                                                                                             @NotNull TextIndexQuery query) {
    String commonName = null;
    short searchContext = 0;
    boolean caseSensitive = true;
    for (WordRequestInfo r : data) {
      ProgressManager.checkCanceled();
      String containerName = r.getContainerName();
      if (containerName != null) {
        if (commonName == null) {
          commonName = containerName;
          searchContext = r.getSearchContext();
          caseSensitive = r.isCaseSensitive();
        }
        else if (commonName.equals(containerName)) {
          searchContext |= r.getSearchContext();
          caseSensitive &= r.isCaseSensitive();
        }
        else {
          return null;
        }
      }
    }
    if (commonName == null) return null;

    TextIndexQuery commonNameQuery = TextIndexQuery.fromWord(commonName, caseSensitive, searchContext);

    Set<VirtualFile> containerFiles = new HashSet<>();
    Processor<VirtualFile> processor = Processors.cancelableCollectProcessor(containerFiles);
    processFilesContainingAllKeys(myManager.getProject(), commonScope, processor, query, commonNameQuery);

    return containerFiles;
  }

  public static boolean shouldProcessInjectedPsi(@NotNull SearchScope scope) {
    return !(scope instanceof LocalSearchScope) || !((LocalSearchScope)scope).isIgnoreInjectedPsi();
  }

  @Override
  public @NotNull SearchCostResult isCheapEnoughToSearch(@NotNull String name,
                                                         @NotNull GlobalSearchScope scope,
                                                         @Nullable PsiFile psiFileToIgnoreOccurrencesIn,
                                                         @Nullable ProgressIndicator progress) {
    return isCheapEnoughToSearch(name, scope, psiFileToIgnoreOccurrencesIn);
  }

  @Override
  public @NotNull SearchCostResult isCheapEnoughToSearch(@NotNull String name,
                                                         @NotNull GlobalSearchScope scope,
                                                         @Nullable PsiFile psiFileToIgnoreOccurrencesIn) {
    if (!ReadAction.compute(() -> scope.getUnloadedModulesBelongingToScope().isEmpty())) {
      return SearchCostResult.TOO_MANY_OCCURRENCES;
    }

    AtomicInteger filesCount = new AtomicInteger();
    AtomicLong filesSizeToProcess = new AtomicLong();

    Processor<VirtualFile> processor = new Processor<>() {
      private final VirtualFile virtualFileToIgnoreOccurrencesIn =
        psiFileToIgnoreOccurrencesIn == null ? null : psiFileToIgnoreOccurrencesIn.getVirtualFile();
      private final int maxFilesToProcess = Registry.intValue("ide.unused.symbol.calculation.maxFilesToSearchUsagesIn", 10);
      private final int maxFilesSizeToProcess = Registry.intValue("ide.unused.symbol.calculation.maxFilesSizeToSearchUsagesIn", 524288);

      @Override
      public boolean process(VirtualFile file) {
        ProgressManager.checkCanceled();
        if (Comparing.equal(file, virtualFileToIgnoreOccurrencesIn)) return true;
        int currentFilesCount = filesCount.incrementAndGet();

        assert file != null;
        long estimatedLength = file.isDirectory() ? 0 : file.getLength();

        long accumulatedFileSizeToProcess = filesSizeToProcess.addAndGet(estimatedLength);
        return currentFilesCount < maxFilesToProcess && accumulatedFileSizeToProcess < maxFilesSizeToProcess;
      }
    };
    TextIndexQuery query = TextIndexQuery.fromWord(name, true, null);
    boolean cheap = processFilesContainingAllKeys(myManager.getProject(), scope, processor, query);

    if (!cheap) {
      return SearchCostResult.TOO_MANY_OCCURRENCES;
    }

    return filesCount.get() == 0 ? SearchCostResult.ZERO_OCCURRENCES : SearchCostResult.FEW_OCCURRENCES;
  }

  private boolean processElementsWithTextInGlobalScope(@NotNull GlobalSearchScope scope,
                                                       @NotNull StringSearcher searcher,
                                                       short searchContext,
                                                       boolean caseSensitively,
                                                       @Nullable String containerName,
                                                       @NotNull SearchSession session,
                                                       @NotNull ProgressIndicator progress,
                                                       @NotNull BulkOccurrenceProcessor processor) {
    progress.setIndeterminate(false);
    progress.pushState();
    try {
      progress.setText(IndexingBundle.message("psi.scanning.files.progress"));

      Processor<? super CandidateFileInfo> localProcessor = localProcessor(searcher, processor);

      // Lists of files to search in this order.
      // First, there are lists with higher probability of hits (e.g., files with `containerName` or files near the target)
      List<List<VirtualFile>> priorities = computePriorities(scope, searcher, searchContext, caseSensitively, containerName, session);
      if (priorities.isEmpty()) return true;
      int totalSize = priorities.stream().mapToInt(l -> l.size()).sum();
      progress.setText(IndexingBundle.message("psi.search.for.word.progress", searcher.getPattern(), totalSize));

      int alreadyProcessedFiles = 0;
      for (List<VirtualFile> files : priorities) {
        if (!processPsiFileRoots(files, totalSize, alreadyProcessedFiles, progress, localProcessor)) return false;
        alreadyProcessedFiles += files.size();
      }
    }
    finally {
      progress.popState();
    }
    return true;
  }

  private @NotNull List<List<VirtualFile>> computePriorities(@NotNull GlobalSearchScope scope,
                                                             @NotNull StringSearcher searcher,
                                                             short searchContext,
                                                             boolean caseSensitively,
                                                             @Nullable String containerName,
                                                             @NotNull SearchSession session) {
    String text = searcher.getPattern();
    Set<VirtualFile> allFiles = new HashSet<>();
    getFilesWithText(scope, searchContext, caseSensitively, text, allFiles);

    List<List<VirtualFile>> priorities = new ArrayList<>();

    List<VirtualFile> targets = ReadAction.compute(() -> ContainerUtil.filter(session.getTargetVirtualFiles(), scope::contains));
    List<@NotNull VirtualFile> directories;
    if (targets.isEmpty()) {
      directories = Collections.emptyList();
    }
    else {
      priorities.add(targets);
      targets.forEach(allFiles::remove);

      directories = ContainerUtil.mapNotNull(targets, v -> v.getParent());

      GlobalSearchScope directoryNearTargetScope = new DelegatingGlobalSearchScope(scope) {
        @Override
        public boolean contains(@NotNull VirtualFile file) {
          return super.contains(file) && directories.contains(file.getParent());
        }
      };

      List<VirtualFile> directoryNearTargetFiles = ReadAction.compute(() ->
        ContainerUtil.filter(allFiles, f -> directoryNearTargetScope.contains(f) && !targets.contains(f))
      );
      if (!directoryNearTargetFiles.isEmpty()) {
        priorities.add(directoryNearTargetFiles);
        directoryNearTargetFiles.forEach(allFiles::remove);
      }
    }
    if (containerName != null) {
      Set<VirtualFile> intersectionWithContainerFiles = new HashSet<>();
      // intersectionWithContainerFiles holds files containing words from both `text` and `containerName`
      getFilesWithText(scope, searchContext, caseSensitively, text+" "+containerName, intersectionWithContainerFiles);
      targets.forEach(intersectionWithContainerFiles::remove);
      directories.forEach(intersectionWithContainerFiles::remove);
      if (!intersectionWithContainerFiles.isEmpty()) {
        priorities.add(new ArrayList<>(intersectionWithContainerFiles));

        allFiles.removeAll(intersectionWithContainerFiles);
      }
    }
    if (!allFiles.isEmpty()) {
      priorities.add(new ArrayList<>(allFiles));
    }

    return priorities;
  }

  /**
   * NOTE: candidateFile and file might be actually two different files (e.g., we may find a class file in java, but the PsiFile
   * will be from the mirror source class)
   *
   * @param files to scan for references in this pass.
   * @param totalSize the number of files to scan in both passes. Can be different from {@code files.size()} in case of
   *                  two-pass scan, where we first scan files containing container name and then all the rest files.
   * @param alreadyProcessedFiles the number of files scanned in previous pass.
   * @return true if completed
   */
  private boolean processPsiFileRoots(@NotNull List<? extends VirtualFile> files,
                                      int totalSize,
                                      int alreadyProcessedFiles,
                                      @NotNull ProgressIndicator progress,
                                      @NotNull Processor<? super CandidateFileInfo> localProcessor) {
    return myManager.runInBatchFilesMode(() -> {
      AtomicInteger counter = new AtomicInteger(alreadyProcessedFiles);
      AtomicBoolean stopped = new AtomicBoolean(false);
      if (progress.isRunning()) {
        progress.setIndeterminate(false);
      }
      ProgressIndicator originalIndicator = ProgressWrapper.unwrapAll(progress);
      return processFilesConcurrentlyDespiteWriteActions(myManager.getProject(), files, progress, stopped, vfile -> {
        TooManyUsagesStatus.getFrom(originalIndicator).pauseProcessingIfTooManyUsages();
        try {
          processVirtualFile(vfile, stopped, localProcessor);
        }
        catch (CancellationException | IndexNotReadyException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error("Error during processing of: " + vfile.getName(), e);
          throw e;
        }
        if (progress.isRunning() && !progress.isIndeterminate()) {
          double fraction = (double)counter.incrementAndGet() / totalSize;
          progress.setFraction(fraction);
        }
        return !stopped.get();
      });
    });
  }

  /**
   * NOTE: {@link #candidateVirtualFile()} and {@link #psiFile()} might be actually two different files
   * (e.g. when we find class file in java, but the PsiFile is from the mirror source class)
   */
  record CandidateFileInfo(
    @NotNull VirtualFile candidateVirtualFile,
    @NotNull PsiFile psiFile
  ) { }

  // Tries to run {@code localProcessor} for each file in {@code files} concurrently on ForkJoinPool.
  // When encounters write action request, stops all threads, waits for write action to finish and re-starts all threads again,
  // trying to finish the unprocessed files (i.e. those for which {@code localProcessor} hasn't been called yet).
  // {@code localProcessor} must be as idempotent as possible (and must not return false on progress cancel)
  private static boolean processFilesConcurrentlyDespiteWriteActions(@NotNull Project project,
                                                                     @NotNull List<? extends VirtualFile> files,
                                                                     @NotNull ProgressIndicator progress,
                                                                     @NotNull AtomicBoolean stopped,
                                                                     @NotNull Processor<? super VirtualFile> localProcessor) {
    ApplicationEx app = (ApplicationEx)ApplicationManager.getApplication();
    // doesn't work with coroutine based progress
    // if (!app.isDispatchThread()) {
    //  CoreProgressManager.assertUnderProgress(progress);
    //}
    List<VirtualFile> processedFiles = Collections.synchronizedList(new ArrayList<>(files.size()));
    while (true) {
      ProgressManager.checkCanceled();
      ProgressIndicator wrapper = new SensitiveProgressWrapper(progress);
      ApplicationListener listener = new ApplicationListener() {
        @Override
        public void beforeWriteActionStart(@NotNull Object action) {
          wrapper.cancel();
        }
      };
      processedFiles.clear();
      Disposable disposable = Disposer.newDisposable();
      app.addApplicationListener(listener, disposable);
      boolean processorCanceled = false;
      try {
        if (app.isWriteAccessAllowed() || app.isReadAccessAllowed() && app.isWriteActionPending()) {
          // no point in processing in separate threads - they are doomed to fail to obtain read action anyway
          // do not wrap in impatient reader because every read action inside would trigger AU.CRRAE
          processorCanceled = !ContainerUtil.process(files, localProcessor);
          if (processorCanceled) {
            stopped.set(true);
          }
          processedFiles.addAll(files);
        }
        else if (app.isWriteActionPending()) {
          // we don't have read action now so wait for write action to complete
        }
        else {
          AtomicBoolean someTaskFailed = new AtomicBoolean();
          Processor<VirtualFile> processor = vfile -> {
            ProgressManager.checkCanceled();
            // optimisation: avoid unnecessary processing if it's doomed to fail because some other task has failed already,
            // and bail out of fork/join task as soon as possible
            if (someTaskFailed.get()) {
              return false;
            }
            try {
              // wrap in unconditional impatient reader to bail early at write action start,
              // regardless of whether was called from highlighting (already impatient-wrapped) or Find Usages action
              app.executeByImpatientReader(() -> {
                if (localProcessor.process(vfile)) {
                  processedFiles.add(vfile);
                }
                else {
                  stopped.set(true);
                }
              });
            }
            catch (ProcessCanceledException e) {
              someTaskFailed.set(true);
              throw e;
            }
            return !stopped.get();
          };
          // try to run parallel read actions but fail as soon as possible
          try {
            JobLauncher.getInstance().invokeConcurrentlyUnderProgress(files, wrapper, processor);
            processorCanceled = stopped.get();
          }
          catch (ProcessCanceledException e) {
            // wrapper can interrupt us (means write action is about to start) or by genuine exception in progress
            progress.checkCanceled();
          }
        }
      }
      finally {
        Disposer.dispose(disposable);
      }
      if (processorCanceled) {
        return false;
      }

      if (processedFiles.size() == files.size()) {
        break;
      }
      // we failed to run read action in job launcher thread
      // run read action in our thread instead to wait for a write action to complete and resume parallel processing
      DumbService.getInstance(project).runReadActionInSmartMode(EmptyRunnable.getInstance());
      Set<VirtualFile> t = new HashSet<>(files);
      synchronized (processedFiles) {
        t.removeAll(processedFiles);
      }
      files = new ArrayList<>(t);
    }
    return true;
  }

  private void processVirtualFile(@NotNull VirtualFile vfile,
                                  @NotNull AtomicBoolean stopped,
                                  @NotNull Processor<? super CandidateFileInfo> localProcessor) throws ApplicationUtil.CannotRunReadActionException {
    // try to pre-cache virtual file content outside read action to avoid stalling EDT
    if (!vfile.isDirectory() && !vfile.getFileType().isBinary()) {
      try {
        vfile.contentsToByteArray();
      }
      catch (IOException ignored) {
      }
    }
    if (!ApplicationManagerEx.getApplicationEx().tryRunReadAction(() -> {
      if (!vfile.isValid()) return;

      List<CodeInsightContext> allContexts;
      boolean skipDefaultContext;
      if (CodeInsightContexts.isSharedSourceSupportEnabled(myManager.getProject())) {
        allContexts = CodeInsightContextManager.getInstance(myManager.getProject()).getCodeInsightContexts(vfile);
        skipDefaultContext = ContainerUtil.find(allContexts, c -> c != CodeInsightContexts.defaultContext()) != null;
      }
      else {
        allContexts = Collections.singletonList(CodeInsightContexts.defaultContext());
        skipDefaultContext = false;
      }
      for (CodeInsightContext context : allContexts) {
        if (skipDefaultContext && context == CodeInsightContexts.defaultContext()) continue;

        PsiFile psiFile = myManager.findFile(vfile, context);


        if (psiFile instanceof PsiBinaryFile binaryFile) {
          PsiFile originalPsiFile = findOriginalPsiFile(binaryFile);
          if (originalPsiFile != null) {
            psiFile = originalPsiFile;
          }
        }

        if (psiFile != null && !(psiFile instanceof PsiBinaryFile)) {
          Project project = myManager.getProject();
          if (project.isDisposed()) throw new ProcessCanceledException();
          if (!DumbUtil.getInstance(project).mayUseIndices()) {
            throw ApplicationUtil.CannotRunReadActionException.create();
          }

          FileViewProvider provider = psiFile.getViewProvider();
          List<PsiFile> psiRoots = provider.getAllFiles();
          Set<PsiFile> processed = new HashSet<>(psiRoots.size() * 2, (float)0.5);
          for (PsiFile psiRoot : psiRoots) {
            ProgressManager.checkCanceled();
            assert psiRoot != null : "One of the roots of file " + psiFile + " is null. All roots: " + psiRoots + "; ViewProvider: " +
                                     provider + "; Virtual file: " + provider.getVirtualFile();
            if (!processed.add(psiRoot)) continue;
            if (!psiRoot.isValid()) {
              continue;
            }

            if (!localProcessor.process(new CandidateFileInfo(vfile, psiRoot))) {
              stopped.set(true);
              break;
            }
          }
        }
      }
    })) {
      throw ApplicationUtil.CannotRunReadActionException.create();
    }
  }

  private static @Nullable PsiFile findOriginalPsiFile(@NotNull PsiBinaryFile psiFile) {
    List<BinaryFileSourceProvider> providers = BinaryFileSourceProvider.EP.getExtensionList();
    for (BinaryFileSourceProvider provider : providers) {
      PsiFile originalFile = provider.findSourceFile(psiFile);
      if (originalFile != null) return originalFile;
    }
    return null;
  }

  private void getFilesWithText(@NotNull GlobalSearchScope scope,
                                short searchContext,
                                boolean caseSensitively,
                                @NotNull String text,
                                @NotNull Collection<? super VirtualFile> result) {
    processCandidateFilesForText(scope, searchContext, caseSensitively, text, Processors.cancelableCollectProcessor(result));
  }

  public boolean processCandidateFilesForText(@NotNull GlobalSearchScope scope,
                                              short searchContext,
                                              boolean caseSensitively,
                                              boolean useOnlyWordHashToSearch,
                                              @NotNull String text,
                                              @NotNull Processor<? super VirtualFile> processor) {
    return processFilesContainingAllKeys(myManager.getProject(), scope, processor,
                                         TextIndexQuery.fromWord(text, caseSensitively, useOnlyWordHashToSearch, searchContext));
  }

  @Override
  public boolean processCandidateFilesForText(@NotNull GlobalSearchScope scope,
                                              short searchContext,
                                              boolean caseSensitively,
                                              @NotNull String text,
                                              @NotNull Processor<? super VirtualFile> processor) {
    return processCandidateFilesForText(scope, searchContext, caseSensitively, false, text, processor);
  }

  @Override
  public PsiFile @NotNull [] findFilesWithPlainTextWords(@NotNull String word) {
    return CacheManager.getInstance(myManager.getProject()).getFilesWithWord(word, UsageSearchContext.IN_PLAIN_TEXT,
                                                                             GlobalSearchScope.projectScope(myManager.getProject()),
                                                                             true);
  }


  @Override
  public boolean processUsagesInNonJavaFiles(@NotNull String qName,
                                             @NotNull PsiNonJavaFileReferenceProcessor processor,
                                             @NotNull GlobalSearchScope searchScope) {
    return processUsagesInNonJavaFiles(null, qName, processor, searchScope);
  }

  @Override
  public boolean processUsagesInNonJavaFiles(@Nullable PsiElement originalElement,
                                             @NotNull String qName,
                                             @NotNull PsiNonJavaFileReferenceProcessor processor,
                                             @NotNull GlobalSearchScope initialScope) {
    if (qName.isEmpty()) {
      throw new IllegalArgumentException("Cannot search for elements with empty text. Element: "+originalElement+ "; "+(originalElement == null ? null : originalElement.getClass()));
    }
    ProgressIndicator progress = getOrCreateIndicator();

    int dotIndex = qName.lastIndexOf('.');
    int dollarIndex = qName.lastIndexOf('$');
    int maxIndex = Math.max(dotIndex, dollarIndex);
    String wordToSearch = maxIndex >= 0 ? qName.substring(maxIndex + 1) : qName;
    GlobalSearchScope theSearchScope = ReadAction.compute(() -> {
      if (originalElement != null && myManager.isInProject(originalElement) && initialScope.isSearchInLibraries()) {
        return initialScope.intersectWith(GlobalSearchScope.projectScope(myManager.getProject()));
      }
      return initialScope;
    });
    PsiFile[] files = myDumbService.runReadActionInSmartMode(() -> CacheManager.getInstance(myManager.getProject())
      .getFilesWithWord(wordToSearch, UsageSearchContext.IN_PLAIN_TEXT, theSearchScope, true));

    StringSearcher searcher = new StringSearcher(qName, true, true, false);

    progress.pushState();
    Ref<Boolean> stopped = Ref.create(Boolean.FALSE);
    try {
      progress.setText(IndexingBundle.message("psi.search.in.non.java.files.progress"));

      SearchScope useScope = originalElement == null ? null : myDumbService.runReadActionInSmartMode(() -> getUseScope(originalElement));

      int patternLength = qName.length();
      for (int i = 0; i < files.length; i++) {
        ProgressManager.checkCanceled();
        PsiFile psiFile = files[i];
        if (psiFile instanceof PsiBinaryFile) continue;

        CharSequence text = ReadAction.compute(() -> psiFile.getViewProvider().getContents());

        LowLevelSearchUtil.processTexts(text, 0, text.length(), searcher, index -> {
          boolean isReferenceOK = myDumbService.runReadActionInSmartMode(() -> {
            PsiReference referenceAt = psiFile.findReferenceAt(index);
            return referenceAt == null || useScope == null || !PsiSearchScopeUtil.isInScope(useScope.intersectWith(initialScope), psiFile);
          });
          if (isReferenceOK && !processor.process(psiFile, index, index + patternLength)) {
            stopped.set(Boolean.TRUE);
            return false;
          }

          return true;
        });
        if (stopped.get()) break;
        progress.setFraction((double)(i + 1) / files.length);
      }
    }
    finally {
      progress.popState();
    }

    return !stopped.get();
  }

  @Override
  public boolean processAllFilesWithWord(@NotNull String word,
                                         @NotNull GlobalSearchScope scope,
                                         @NotNull Processor<? super PsiFile> processor,
                                         boolean caseSensitively) {
    return CacheManager.getInstance(myManager.getProject()).processFilesWithWord(processor, word, UsageSearchContext.IN_CODE, scope, caseSensitively);
  }

  @Override
  public boolean processAllFilesWithWordInText(@NotNull String word,
                                               @NotNull GlobalSearchScope scope,
                                               @NotNull Processor<? super PsiFile> processor,
                                               boolean caseSensitively) {
    return CacheManager.getInstance(myManager.getProject()).processFilesWithWord(processor, word, UsageSearchContext.IN_PLAIN_TEXT, scope, caseSensitively);
  }

  @Override
  public boolean processAllFilesWithWordInComments(@NotNull String word,
                                                   @NotNull GlobalSearchScope scope,
                                                   @NotNull Processor<? super PsiFile> processor) {
    return CacheManager.getInstance(myManager.getProject()).processFilesWithWord(processor, word, UsageSearchContext.IN_COMMENTS, scope, true);
  }

  @Override
  public boolean processAllFilesWithWordInLiterals(@NotNull String word,
                                                   @NotNull GlobalSearchScope scope,
                                                   @NotNull Processor<? super PsiFile> processor) {
    return CacheManager.getInstance(myManager.getProject()).processFilesWithWord(processor, word, UsageSearchContext.IN_STRINGS, scope, true);
  }

  private static @NotNull EnumSet<Options> makeOptions(boolean caseSensitive, boolean processInjectedPsi) {
    EnumSet<Options> options = EnumSet.of(Options.PROCESS_ONLY_JAVA_IDENTIFIERS_IF_POSSIBLE);
    if (caseSensitive) options.add(Options.CASE_SENSITIVE_SEARCH);
    if (processInjectedPsi) options.add(Options.PROCESS_INJECTED_PSI);
    return options;
  }

  @Override
  public boolean processRequests(@NotNull SearchRequestCollector collector, @NotNull Processor<? super PsiReference> processor) {
    Map<SearchRequestCollector, Processor<? super PsiReference>> collectors = new HashMap<>();
    collectors.put(collector, processor);

    ProgressIndicator progress = getOrCreateIndicator();
    if (appendCollectorsFromQueryRequests(progress, collectors) == QueryRequestsRunResult.STOPPED) {
      return false;
    }
    do {
      Map<TextIndexQuery, Collection<RequestWithProcessor>> globals = new HashMap<>();
      List<Computable<Boolean>> customs = new ArrayList<>();
      Set<RequestWithProcessor> locals = new LinkedHashSet<>();
      Map<RequestWithProcessor, Processor<? super CandidateFileInfo>> localProcessors = new HashMap<>();
      distributePrimitives(collectors, locals, globals, customs, localProcessors);
      if (!processGlobalRequestsOptimized(globals, progress, localProcessors)) {
        return false;
      }
      for (RequestWithProcessor local : locals) {
        progress.checkCanceled();
        if (!processSingleRequest(local.request, local.refProcessor)) {
          return false;
        }
      }
      for (Computable<Boolean> custom : customs) {
        progress.checkCanceled();
        if (!custom.compute()) {
          return false;
        }
      }
      QueryRequestsRunResult result = appendCollectorsFromQueryRequests(progress, collectors);
      if (result == QueryRequestsRunResult.STOPPED) {
        return false;
      }
      else if (result == QueryRequestsRunResult.UNCHANGED) {
        return true;
      }
    }
    while (true);
  }

  private static @NotNull ProgressIndicator getOrCreateIndicator() {
    ProgressIndicator progress = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (progress == null) progress = new EmptyProgressIndicator();
    progress.setIndeterminate(false);
    return progress;
  }

  private enum QueryRequestsRunResult {
    STOPPED,
    UNCHANGED,
    CHANGED,
  }

  static @NotNull Processor<? super CandidateFileInfo> localProcessor(@NotNull StringSearcher searcher,
                                                                      @NotNull BulkOccurrenceProcessor processor) {
    return new ReadActionProcessor<>() {
      @Override
      public boolean processInReadAction(CandidateFileInfo candidateFileInfo) {
        PsiElement scopeElement = candidateFileInfo.psiFile();
        if (scopeElement instanceof PsiCompiledElement) {
          // can't scan text of the element
          return true;
        }

        return scopeElement.isValid() &&
               processor.execute(scopeElement, LowLevelSearchUtil.getTextOccurrencesInScope(scopeElement, searcher), searcher);
      }

      @Override
      public String toString() {
        return processor.toString();
      }
    };
  }

  private boolean processGlobalRequestsOptimized(@NotNull Map<TextIndexQuery, Collection<RequestWithProcessor>> singles,
                                                 @NotNull ProgressIndicator progress,
                                                 @NotNull Map<RequestWithProcessor, Processor<? super CandidateFileInfo>> localProcessors) {
    if (singles.isEmpty()) {
      return true;
    }

    if (singles.size() == 1) {
      Collection<RequestWithProcessor> requests = singles.values().iterator().next();
      if (requests.size() == 1) {
        RequestWithProcessor theOnly = requests.iterator().next();
        return processSingleRequest(theOnly.request, theOnly.refProcessor);
      }
    }

    return processGlobalRequests(singles, progress, localProcessors);
  }

  <T extends WordRequestInfo> boolean processGlobalRequests(@NotNull Map<TextIndexQuery, Collection<T>> singles,
                                                            @NotNull ProgressIndicator progress,
                                                            @NotNull Map<T, Processor<? super CandidateFileInfo>> localProcessors) {
    progress.pushState();
    progress.setText(IndexingBundle.message("psi.scanning.files.progress"));
    boolean result;

    try {
      // files which are target of the search
      Map<VirtualFile, Collection<T>> targetFiles = new HashMap<>();
      // directories in which target files are contained
      Map<VirtualFile, Collection<T>> nearDirectoryFiles = new HashMap<>();
      // intersectionCandidateFiles holds files containing words from all requests in `singles` and words in corresponding container names
      Map<VirtualFile, Collection<T>> intersectionCandidateFiles = new HashMap<>();
      // restCandidateFiles holds files containing words from all requests in `singles` but EXCLUDING words in corresponding container names
      Map<VirtualFile, Collection<T>> restCandidateFiles = new HashMap<>();
      int totalSize = collectFiles(singles, targetFiles, nearDirectoryFiles, intersectionCandidateFiles, restCandidateFiles);

      if (totalSize == 0) {
        return true;
      }

      Set<String> allWords = new TreeSet<>();
      for (WordRequestInfo singleRequest : localProcessors.keySet()) {
        ProgressManager.checkCanceled();
        allWords.add(singleRequest.getWord());
      }
      progress.setText(IndexingBundle.message("psi.search.for.word.progress", concat(allWords), totalSize));

      int alreadyProcessedFiles = 0;
      FileRankerMlService fileRankerService = FileRankerMlService.getInstance();
      boolean useOldImpl = (fileRankerService == null || fileRankerService.shouldUseOldImplementation());
      List<String> queryNames = new ArrayList<>(allWords);
      List<VirtualFile> queryFiles = ReadAction.compute(
        () -> ContainerUtil.flatMap(localProcessors.keySet(), requestInfo -> requestInfo.getSearchSession().getTargetVirtualFiles()));
      if (useOldImpl) {
        // This is the original implementation for processing files before introducing FileRankerMlService.
        // It should be removed after validating that the new implementation does not cause any issues/performance degradations.
        result = processCandidatesInChunks(progress,
                                           localProcessors,
                                           targetFiles,
                                           totalSize,
                                           alreadyProcessedFiles,
                                           nearDirectoryFiles,
                                           intersectionCandidateFiles,
                                           restCandidateFiles,
                                           fileRankerService,
                                           queryNames,
                                           queryFiles);
      }
      else {
        result = processCandidatesInOneCall(progress,
                                            localProcessors,
                                            targetFiles,
                                            totalSize,
                                            alreadyProcessedFiles,
                                            nearDirectoryFiles,
                                            intersectionCandidateFiles,
                                            restCandidateFiles,
                                            fileRankerService,
                                            queryNames,
                                            queryFiles);
      }
    }
    finally {
      progress.popState();
    }

    return result;
  }

  private <T extends WordRequestInfo> Optional<Integer> processUnsortedCandidates(@NotNull Map<T, Processor<? super CandidateFileInfo>> localProcessors,
                                                                                  @NotNull Map<VirtualFile, Collection<T>> candidateFiles,
                                                                                  @NotNull ProgressIndicator progress,
                                                                                  int totalSize,
                                                                                  int alreadyProcessedFiles) {
    if (!candidateFiles.isEmpty()) {
      boolean result = processCandidates(localProcessors, candidateFiles, new ArrayList<>(candidateFiles.keySet()), progress, totalSize,
                                         alreadyProcessedFiles);
      if (!result) return Optional.empty();
    }
    return Optional.of(alreadyProcessedFiles + candidateFiles.size());
  }

  private <T extends WordRequestInfo> boolean processCandidatesInChunks(@NotNull ProgressIndicator progress,
                                                                        @NotNull Map<T, Processor<? super CandidateFileInfo>> localProcessors,
                                                                        @NotNull Map<VirtualFile, Collection<T>> targetFiles,
                                                                        int totalSize,
                                                                        int alreadyProcessedFiles,
                                                                        @NotNull Map<VirtualFile, Collection<T>> nearDirectoryFiles,
                                                                        @NotNull Map<VirtualFile, Collection<T>> intersectionCandidateFiles,
                                                                        @NotNull Map<VirtualFile, Collection<T>> restCandidateFiles,
                                                                        @Nullable FileRankerMlService fileRankerMlService,
                                                                        @NotNull List<String> queryNames,
                                                                        @NotNull List<VirtualFile> queryFiles) {

    if (fileRankerMlService != null) {
      // Inform fileRankerMlService about this session, but discard the order, as it is not used.
      ArrayList<VirtualFile> candidateFiles = new ArrayList<>(totalSize);
      candidateFiles.addAll(targetFiles.keySet());
      candidateFiles.addAll(nearDirectoryFiles.keySet());
      candidateFiles.addAll(intersectionCandidateFiles.keySet());
      candidateFiles.addAll(restCandidateFiles.keySet());

      fileRankerMlService.getFileOrder(queryNames, queryFiles, candidateFiles);
    }

    for (Map<VirtualFile, Collection<T>> files : List.of(targetFiles, nearDirectoryFiles, intersectionCandidateFiles, restCandidateFiles)) {
      Optional<Integer> resultProcessed = processUnsortedCandidates(localProcessors, files, progress, totalSize, alreadyProcessedFiles);
      if (resultProcessed.isEmpty()) return false;
      alreadyProcessedFiles = resultProcessed.get();
    }
    return true;
  }

  private <T extends WordRequestInfo> boolean processCandidatesInOneCall(@NotNull ProgressIndicator progress,
                                                                         @NotNull Map<T, Processor<? super CandidateFileInfo>> localProcessors,
                                                                         @NotNull Map<VirtualFile, Collection<T>> targetFiles,
                                                                         int totalSize,
                                                                         int alreadyProcessedFiles,
                                                                         @NotNull Map<VirtualFile, Collection<T>> nearDirectoryFiles,
                                                                         @NotNull Map<VirtualFile, Collection<T>> intersectionCandidateFiles,
                                                                         @NotNull Map<VirtualFile, Collection<T>> restCandidateFiles,
                                                                         @Nullable FileRankerMlService fileRankerService,
                                                                         @NotNull List<String> queryNames,
                                                                         @NotNull List<VirtualFile> queryFiles) {
    Map<VirtualFile, Collection<T>> allFiles = new HashMap<>(totalSize);
    allFiles.putAll(targetFiles);
    allFiles.putAll(nearDirectoryFiles);
    allFiles.putAll(intersectionCandidateFiles);
    allFiles.putAll(restCandidateFiles);

    List<VirtualFile> allFilesList = new ArrayList<>(totalSize);
    allFilesList.addAll(targetFiles.keySet());
    allFilesList.addAll(nearDirectoryFiles.keySet());
    allFilesList.addAll(intersectionCandidateFiles.keySet());
    allFilesList.addAll(restCandidateFiles.keySet());

    List<VirtualFile> orderedFiles = fileRankerService.getFileOrder(queryNames, queryFiles, allFilesList);
    return processCandidates(localProcessors, allFiles, orderedFiles, progress, totalSize, alreadyProcessedFiles);
  }


  private <T> boolean processCandidates(@NotNull Map<T, Processor<? super CandidateFileInfo>> localProcessors,
                                        @NotNull Map<VirtualFile, Collection<T>> candidateFiles,
                                        @NotNull List<VirtualFile> orderedFiles,
                                        @NotNull ProgressIndicator progress,
                                        int totalSize,
                                        int alreadyProcessedFiles) {
    return processPsiFileRoots(orderedFiles, totalSize, alreadyProcessedFiles, progress, candidateInfo -> {
      VirtualFile vfile = candidateInfo.candidateVirtualFile();
      if (vfile instanceof BackedVirtualFile) {
        vfile = ((BackedVirtualFile)vfile).getOriginFile();
      }
      for (T singleRequest : candidateFiles.get(vfile)) {
        ProgressManager.checkCanceled();
        Processor<? super CandidateFileInfo> localProcessor = localProcessors.get(singleRequest);
        if (!localProcessor.process(candidateInfo)) {
          return false;
        }
      }
      return true;
    });
  }

  private static @NotNull QueryRequestsRunResult appendCollectorsFromQueryRequests(@NotNull ProgressIndicator progress,
                                                                                   @NotNull Map<SearchRequestCollector, Processor<? super PsiReference>> collectors) {
    boolean changed = false;
    Deque<SearchRequestCollector> queue = new LinkedList<>(collectors.keySet());
    while (!queue.isEmpty()) {
      progress.checkCanceled();
      SearchRequestCollector each = queue.removeFirst();
      for (QuerySearchRequest request : each.takeQueryRequests()) {
        progress.checkCanceled();
        if (!request.runQuery()) {
          return QueryRequestsRunResult.STOPPED;
        }
        assert !collectors.containsKey(request.collector) || collectors.get(request.collector) == request.processor;
        collectors.put(request.collector, request.processor);
        queue.addLast(request.collector);
        changed = true;
      }
    }
    return changed ? QueryRequestsRunResult.CHANGED : QueryRequestsRunResult.UNCHANGED;
  }

  private static @NotNull CharSequence concat(@NotNull Set<String> allWords) {
    StringBuilder result = new StringBuilder(50);
    for (String string : allWords) {
      ProgressManager.checkCanceled();
      if (!string.isEmpty()) {
        if (result.length() > 50) {
          result.append("...");
          break;
        }
        if (!result.isEmpty()) result.append(", ");
        result.append(string);
      }
    }
    return result;
  }

  // returns total size
  private <T extends WordRequestInfo> int collectFiles(@NotNull Map<TextIndexQuery, Collection<T>> singles,
                                                       @NotNull Map<VirtualFile, Collection<T>> targetFiles,
                                                       @NotNull Map<VirtualFile, Collection<T>> nearDirectoryFiles,
                                                       @NotNull Map<VirtualFile, Collection<T>> containerNameFiles,
                                                       @NotNull Map<VirtualFile, Collection<T>> restFiles) {
    for (Map.Entry<TextIndexQuery, Collection<T>> entry : singles.entrySet()) {
      ProgressManager.checkCanceled();
      TextIndexQuery key = entry.getKey();
      if (key.isEmpty()) {
        continue;
      }
      Collection<T> processors = entry.getValue();
      GlobalSearchScope commonScope = uniteScopes(processors);
      // files which are target of the search
      Set<VirtualFile> thisTargetFiles = ReadAction.compute(() -> {
        return processors.stream().flatMap(p -> {
            List<VirtualFile> files = p.getSearchSession().getTargetVirtualFiles();
            return files.stream();
          }).filter(commonScope::contains)
          .collect(Collectors.toSet());
      });
      // directories in which target files are contained
      Set<VirtualFile> thisTargetDirectories = ContainerUtil.map2SetNotNull(thisTargetFiles, f -> f.getParent());
      Set<VirtualFile> intersectionWithContainerNameFiles = intersectionWithContainerNameFiles(commonScope, processors, key);
      List<VirtualFile> allFilesForKeys = new ArrayList<>();
      processFilesContainingAllKeys(myManager.getProject(), commonScope, Processors.cancelableCollectProcessor(allFilesForKeys), key);
      Object2IntMap<VirtualFile> file2Mask = new Object2IntOpenHashMap<>();
      file2Mask.defaultReturnValue(-1);
      IntRef maskRef = new IntRef();
      for (VirtualFile file : allFilesForKeys) {
        ProgressManager.checkCanceled();
        for (IdIndexEntry indexEntry : key.myIdIndexEntries) {
          ProgressManager.checkCanceled();
          maskRef.set(0);
          myDumbService.runReadActionInSmartMode(
            () -> FileBasedIndex.getInstance().processValues(IdIndex.NAME, indexEntry, file, (__, value) -> {
              maskRef.set(value);
              return true;
            }, commonScope));
          int oldMask = file2Mask.getOrDefault(file, UsageSearchContext.ANY);
          file2Mask.put(file, oldMask & maskRef.get());
        }
      }

      for (Object2IntMap.Entry<VirtualFile> fileEntry : file2Mask.object2IntEntrySet()) {
        VirtualFile file = fileEntry.getKey();
        int mask = fileEntry.getIntValue();
        myDumbService.runReadActionInSmartMode(() -> {
          Map<VirtualFile, Collection<T>> result =
            thisTargetFiles.contains(file)
            ? targetFiles
            : thisTargetDirectories.contains(file.getParent())
              ? nearDirectoryFiles
              : intersectionWithContainerNameFiles != null && intersectionWithContainerNameFiles.contains(file)
                ? containerNameFiles
                : restFiles;
          for (T single : processors) {
            ProgressManager.checkCanceled();
            if ((mask & single.getSearchContext()) != 0 && single.getSearchScope().contains(file)) {
              result.computeIfAbsent(file, ___ -> new SmartList<>()).add(single);
            }
          }
        });
      }
    }
    return targetFiles.size() + nearDirectoryFiles.size() + containerNameFiles.size() + restFiles.size();
  }

  private static @NotNull BulkOccurrenceProcessor adaptProcessor(@NotNull PsiSearchRequest singleRequest,
                                                                 @NotNull Processor<? super PsiReference> consumer) {
    SearchScope searchScope = singleRequest.searchScope;
    boolean ignoreInjectedPsi = searchScope instanceof LocalSearchScope && ((LocalSearchScope)searchScope).isIgnoreInjectedPsi();
    RequestResultProcessor wrapped = singleRequest.processor;
    return new BulkOccurrenceProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement scope, int @NotNull [] offsetsInScope, @NotNull StringSearcher searcher) {
        ProgressManager.checkCanceled();

        return LowLevelSearchUtil.processElementsAtOffsets(scope, searcher, !ignoreInjectedPsi,
                                                           getOrCreateIndicator(), offsetsInScope,
                                                           (element, offsetInElement) -> {
            if (ignoreInjectedPsi && element instanceof PsiLanguageInjectionHost) return true;
            return wrapped.processTextOccurrence(element, offsetInElement, consumer);
          });
      }

      @Override
      public String toString() {
        return consumer.toString();
      }
    };
  }

  private static @NotNull Condition<Integer> matchContextCondition(short searchContext) {
    return context -> (context & searchContext) != 0;
  }

  private static @NotNull GlobalSearchScope uniteScopes(@NotNull Collection<? extends WordRequestInfo> requests) {
    Set<GlobalSearchScope> scopes = ContainerUtil.map2LinkedSet(requests, r -> (GlobalSearchScope)r.getSearchScope());
    return GlobalSearchScope.union(scopes.toArray(GlobalSearchScope.EMPTY_ARRAY));
  }

  private static void distributePrimitives(@NotNull Map<SearchRequestCollector, Processor<? super PsiReference>> collectors,
                                           @NotNull Set<RequestWithProcessor> locals,
                                           @NotNull Map<TextIndexQuery, Collection<RequestWithProcessor>> globals,
                                           @NotNull List<? super Computable<Boolean>> customs,
                                           @NotNull Map<RequestWithProcessor, Processor<? super CandidateFileInfo>> localProcessors) {
    for (Map.Entry<SearchRequestCollector, Processor<? super PsiReference>> entry : collectors.entrySet()) {
      ProgressManager.checkCanceled();
      Processor<? super PsiReference> processor = entry.getValue();
      SearchRequestCollector collector = entry.getKey();
      for (PsiSearchRequest primitive : collector.takeSearchRequests()) {
        ProgressManager.checkCanceled();
        SearchScope scope = primitive.searchScope;
        if (scope instanceof LocalSearchScope) {
          registerRequest(locals, primitive, processor);
        }
        else {
          TextIndexQuery key = TextIndexQuery.fromWord(primitive.word, primitive.caseSensitive, null);
          registerRequest(globals.computeIfAbsent(key, __ -> new SmartList<>()), primitive, processor);
        }
      }
      for (Processor<? super Processor<? super PsiReference>> customAction : collector.takeCustomSearchActions()) {
        ProgressManager.checkCanceled();
        customs.add((Computable<Boolean>)() -> customAction.process(processor));
      }
    }

    for (Map.Entry<TextIndexQuery, Collection<RequestWithProcessor>> entry : globals.entrySet()) {
      ProgressManager.checkCanceled();
      for (RequestWithProcessor singleRequest : entry.getValue()) {
        ProgressManager.checkCanceled();
        PsiSearchRequest primitive = singleRequest.request;
        StringSearcher searcher = new StringSearcher(primitive.word, primitive.caseSensitive, true, false);
        BulkOccurrenceProcessor adapted = adaptProcessor(primitive, singleRequest.refProcessor);

        Processor<? super CandidateFileInfo> localProcessor = localProcessor(searcher, adapted);

        Processor<? super CandidateFileInfo> old = localProcessors.put(singleRequest, localProcessor);
        assert old == null : old + ";" + localProcessor +"; singleRequest="+singleRequest;
      }
    }
  }

  private static void registerRequest(@NotNull Collection<RequestWithProcessor> collection,
                                      @NotNull PsiSearchRequest primitive,
                                      @NotNull Processor<? super PsiReference> processor) {
    RequestWithProcessor singleRequest = new RequestWithProcessor(primitive, processor);

    for (RequestWithProcessor existing : collection) {
      ProgressManager.checkCanceled();
      if (existing.uniteWith(singleRequest)) {
        return;
      }
    }
    collection.add(singleRequest);
  }

  private boolean processSingleRequest(@NotNull PsiSearchRequest single, @NotNull Processor<? super PsiReference> consumer) {
    EnumSet<Options> options = makeOptions(single.caseSensitive, shouldProcessInjectedPsi(single.searchScope));

    return bulkProcessElementsWithWord(single.searchScope, single.word, single.searchContext, options, single.containerName,
                                       single.getSearchSession(), adaptProcessor(single, consumer));
  }

  private static final class RequestWithProcessor implements WordRequestInfo {
    private final @NotNull PsiSearchRequest request;
    private @NotNull Processor<? super PsiReference> refProcessor;

    private RequestWithProcessor(@NotNull PsiSearchRequest request, @NotNull Processor<? super PsiReference> processor) {
      this.request = request;
      refProcessor = processor;
    }

    private boolean uniteWith(@NotNull RequestWithProcessor another) {
      if (request.equals(another.request)) {
        Processor<? super PsiReference> myProcessor = refProcessor;
        if (myProcessor != another.refProcessor) {
          refProcessor = psiReference -> myProcessor.process(psiReference) && another.refProcessor.process(psiReference);
        }
        return true;
      }
      return false;
    }

    @Override
    public String toString() {
      return request.toString();
    }

    @Override
    public @NotNull String getWord() {
      return request.word;
    }

    @Override
    public @NotNull SearchScope getSearchScope() {
      return request.searchScope;
    }

    @Override
    public short getSearchContext() {
      return request.searchContext;
    }

    @Override
    public boolean isCaseSensitive() {
      return request.caseSensitive;
    }

    @Override
    public @NotNull SearchSession getSearchSession() {
      return request.getSearchSession();
    }

    @Override
    public @Nullable String getContainerName() {
      return request.containerName;
    }
  }

  private static boolean processFilesContainingAllKeys(@NotNull Project project,
                                                       @NotNull GlobalSearchScope scope,
                                                       @NotNull Processor<? super VirtualFile> processor,
                                                       TextIndexQuery @NotNull ... textIndexQueries) {
    if (ContainerUtil.all(textIndexQueries, query -> query.isEmpty())) return true;

    if (LOG.isTraceEnabled()) {
      List<String> words = ContainerUtil.map(textIndexQueries, q -> StringUtil.join(q.getInitialWords(), " "));
      LOG.trace("searching for words " + words + " in " + scope);
    }

    if (ApplicationManager.getApplication().isReadAccessAllowed() &&
        (!DumbService.isDumb(project) ||
         FileBasedIndex.getInstance().getCurrentDumbModeAccessType(project) != null)) {
      return computeQueries(scope, processor, textIndexQueries);
    }

    return ReadAction.compute(() -> DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode(() -> computeQueries(scope, processor, textIndexQueries)));
  }

  private static boolean computeQueries(@NotNull GlobalSearchScope scope,
                                        @NotNull Processor<? super VirtualFile> processor,
                                        @NotNull TextIndexQuery @NotNull [] textIndexQueries) {
    Collection<AllKeysQuery<?, ?>> queries = ContainerUtil.concat(textIndexQueries, q -> q.toFileBasedIndexQueries());
    return FileBasedIndex.getInstance().processFilesContainingAllKeys(queries, scope, processor);
  }

  @ApiStatus.Internal
  public static final class TextIndexQuery {
    /** Initial search terms, before conversion into idIndexEntries/trigrams */
    private final @NotNull Collection<String> myInitialWords;

    //Alternative lookup variants: idEntries and trigrams are generated from myInitialWords, and represent 2 different ways
    // to lookup the words -- via IdIndex and Trigram.Index accordingly
    private final @NotNull Set<? extends IdIndexEntry> myIdIndexEntries;
    private final @NotNull Set<? extends Integer> myTrigrams;

    /** {@link UsageSearchContext search context} as a bitmask (makes sense only for IdIndex lookup) */
    private final @Nullable Short myContext;

    /** true == 'use IdIndex only' -- which is the default option anyway */
    private final boolean myUseOnlyWordHashToSearch;

    private TextIndexQuery(@NotNull Set<? extends IdIndexEntry> idIndexEntries,
                           @NotNull Set<? extends Integer> trigrams,
                           @Nullable Short context,
                           boolean useOnlyWordHashToSearch,
                           @NotNull Collection<String> initialWords) {
      myIdIndexEntries = idIndexEntries;
      myTrigrams = trigrams;
      myContext = context;
      myUseOnlyWordHashToSearch = useOnlyWordHashToSearch;
      myInitialWords = initialWords;
    }

    @NotNull Collection<String> getInitialWords() {
      return myInitialWords;
    }

    public boolean isEmpty() {
      return myIdIndexEntries.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TextIndexQuery query = (TextIndexQuery)o;
      return myIdIndexEntries.equals(query.myIdIndexEntries) &&
             //TODO RC: why myUseOnlyWeakHashToSearch is not included (same question for hashCode)?
             myTrigrams.equals(query.myTrigrams) &&
             Objects.equals(myContext, query.myContext);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myIdIndexEntries, myTrigrams, myContext);
    }

    public @NotNull List<AllKeysQuery<?, ?>> toFileBasedIndexQueries() {
      Condition<Integer> contextCondition = myContext == null ? null : matchContextCondition(myContext);

      var idIndexQuery = new AllKeysQuery<>(IdIndex.NAME, myIdIndexEntries, contextCondition);

      if (myUseOnlyWordHashToSearch || myTrigrams.isEmpty()) {
        // short words don't produce trigrams
        return Collections.singletonList(idIndexQuery);
      }

      //Currently useStrongerHash is true => we are always using IdIndex:
      if (IdIndexEntry.useStrongerHash()) {
        return Collections.singletonList(idIndexQuery);
      }

      var trigramIndexQuery = new AllKeysQuery<>(TrigramIndex.INDEX_ID, myTrigrams, null);
      return Arrays.asList(idIndexQuery, trigramIndexQuery);
    }

    private static @NotNull TextIndexQuery fromWord(@NotNull String word,
                                                    boolean caseSensitively,
                                                    boolean useOnlyWordHashToSearch,
                                                    @Nullable Short context) {
      return fromWords(Collections.singleton(word), caseSensitively, useOnlyWordHashToSearch, context);
    }

    public static @NotNull TextIndexQuery fromWord(@NotNull String word, boolean caseSensitively, @Nullable Short context) {
      return fromWord(word, caseSensitively, false, context);
    }

    public static @NotNull TextIndexQuery fromWords(@NotNull Collection<String> words,
                                                    boolean caseSensitively,
                                                    boolean useOnlyWordHashToSearch,
                                                    @Nullable Short context) {
      Set<IdIndexEntry> keys = CollectionFactory.createSmallMemoryFootprintSet(ContainerUtil.flatMap(words, w -> getWordEntries(w, caseSensitively)));
      IntSet trigrams;
      if (!useOnlyWordHashToSearch) {
        trigrams = new IntOpenHashSet();
        for (String word : words) {
          trigrams.addAll(TrigramBuilder.getTrigrams(word));
        }
      }
      else {
        trigrams = IntSets.EMPTY_SET;
      }

      return new TextIndexQuery(keys, trigrams, context, useOnlyWordHashToSearch, words);
    }

    private static @Unmodifiable @NotNull List<IdIndexEntry> getWordEntries(@NotNull String name, boolean caseSensitively) {
      List<String> words = StringUtil.getWordsInStringLongestFirst(name);
      if (words.isEmpty()) {
        String trimmed = name.trim();
        if (StringUtil.isNotEmpty(trimmed)) {
          words = Collections.singletonList(trimmed);
        }
      }
      if (words.isEmpty()) return Collections.emptyList();
      return ContainerUtil.map(words, word -> new IdIndexEntry(word, caseSensitively));
    }
  }
}
