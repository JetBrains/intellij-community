// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.concurrency.AsyncFuture;
import com.intellij.concurrency.AsyncUtil;
import com.intellij.concurrency.JobLauncher;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.progress.util.TooManyUsagesStatus;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.search.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageInfoFactory;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.SmartList;
import com.intellij.util.codeInsight.CommentUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.text.StringSearcher;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PsiSearchHelperImpl implements PsiSearchHelper {
  private static final ExtensionPointName<ScopeOptimizer> USE_SCOPE_OPTIMIZER_EP_NAME = ExtensionPointName.create("com.intellij.useScopeOptimizer");

  private static final Logger LOG = Logger.getInstance(PsiSearchHelperImpl.class);
  private final PsiManagerEx myManager;
  private final DumbService myDumbService;

  public enum Options {
    PROCESS_INJECTED_PSI, CASE_SENSITIVE_SEARCH, PROCESS_ONLY_JAVA_IDENTIFIERS_IF_POSSIBLE
  }

  @Override
  @NotNull
  public SearchScope getUseScope(@NotNull PsiElement element) {
    SearchScope scope = element.getUseScope();
    for (UseScopeEnlarger enlarger : UseScopeEnlarger.EP_NAME.getExtensions()) {
      ProgressManager.checkCanceled();
      final SearchScope additionalScope = enlarger.getAdditionalUseScope(element);
      if (additionalScope != null) {
        scope = scope.union(additionalScope);
      }
    }

    SearchScope scopeToRestrict = ScopeOptimizer.calculateOverallRestrictedUseScope(USE_SCOPE_OPTIMIZER_EP_NAME.getExtensions(), element);
    if (scopeToRestrict != null) {
      scope = scope.intersectWith(scopeToRestrict);
    }
    return scope;
  }

  public PsiSearchHelperImpl(@NotNull Project project) {
    myManager = PsiManagerEx.getInstanceEx(project);
    myDumbService = DumbService.getInstance(myManager.getProject());
  }

  /**
   * @deprecated Use {@link #PsiSearchHelperImpl(Project)}
   */
  @Deprecated
  public PsiSearchHelperImpl(@NotNull PsiManagerEx psiManager) {
    myManager = psiManager;
    myDumbService = DumbService.getInstance(myManager.getProject());
  }

  @Override
  @NotNull
  public PsiElement[] findCommentsContainingIdentifier(@NotNull String identifier, @NotNull SearchScope searchScope) {
    final List<PsiElement> result = Collections.synchronizedList(new ArrayList<>());
    Processor<PsiElement> processor = Processors.cancelableCollectProcessor(result);
    processCommentsContainingIdentifier(identifier, searchScope, processor);
    return PsiUtilCore.toPsiElementArray(result);
  }

  @Override
  public boolean processCommentsContainingIdentifier(@NotNull String identifier,
                                                     @NotNull SearchScope searchScope,
                                                     @NotNull final Processor<? super PsiElement> processor) {
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
    final EnumSet<Options> options = EnumSet.of(Options.PROCESS_ONLY_JAVA_IDENTIFIERS_IF_POSSIBLE);
    if (caseSensitive) options.add(Options.CASE_SENSITIVE_SEARCH);
    if (processInjectedPsi) options.add(Options.PROCESS_INJECTED_PSI);

    return processElementsWithWord(processor, searchScope, text, searchContext, options, null);
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> processElementsWithWordAsync(@NotNull final TextOccurenceProcessor processor,
                                                           @NotNull SearchScope searchScope,
                                                           @NotNull final String text,
                                                           final short searchContext,
                                                           final boolean caseSensitively) {
    boolean result = processElementsWithWord(processor, searchScope, text, searchContext, caseSensitively,
                                             shouldProcessInjectedPsi(searchScope));
    return AsyncUtil.wrapBoolean(result);
  }

  public boolean processElementsWithWord(@NotNull final TextOccurenceProcessor processor,
                                         @NotNull SearchScope searchScope,
                                         @NotNull final String text,
                                         final short searchContext,
                                         @NotNull EnumSet<Options> options,
                                         @Nullable String containerName) {
    return bulkProcessElementsWithWord(searchScope, text, searchContext, options, containerName, (scope, offsetsInScope, searcher) ->
      LowLevelSearchUtil.processElementsAtOffsets(scope, searcher, options.contains(Options.PROCESS_INJECTED_PSI), getOrCreateIndicator(),
                                                  offsetsInScope, processor));
  }

  boolean bulkProcessElementsWithWord(@NotNull SearchScope searchScope,
                                              @NotNull String text,
                                              short searchContext,
                                              @NotNull EnumSet<Options> options,
                                              @Nullable String containerName,
                                              @NotNull BulkOccurrenceProcessor processor) {
    if (text.isEmpty()) {
      throw new IllegalArgumentException("Cannot search for elements with empty text");
    }
    final ProgressIndicator progress = getOrCreateIndicator();
    if (searchScope instanceof GlobalSearchScope) {
      StringSearcher searcher = new StringSearcher(text, options.contains(Options.CASE_SENSITIVE_SEARCH), true,
                                                   searchContext == UsageSearchContext.IN_STRINGS,
                                                   options.contains(Options.PROCESS_ONLY_JAVA_IDENTIFIERS_IF_POSSIBLE));

      return processElementsWithTextInGlobalScope((GlobalSearchScope)searchScope, searcher, searchContext,
                                                  options.contains(Options.CASE_SENSITIVE_SEARCH), containerName, progress, processor);
    }
    LocalSearchScope scope = (LocalSearchScope)searchScope;
    PsiElement[] scopeElements = scope.getScope();
    final StringSearcher searcher = new StringSearcher(text, options.contains(Options.CASE_SENSITIVE_SEARCH), true,
                                                       searchContext == UsageSearchContext.IN_STRINGS,
                                                       options.contains(Options.PROCESS_ONLY_JAVA_IDENTIFIERS_IF_POSSIBLE));
    ReadActionProcessor<PsiElement> localProcessor = new ReadActionProcessor<PsiElement>() {
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

  @NotNull
  private static ProgressIndicator getOrCreateIndicator() {
    ProgressIndicator progress = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (progress == null) progress = new EmptyProgressIndicator();
    progress.setIndeterminate(false);
    return progress;
  }

  public static boolean shouldProcessInjectedPsi(@NotNull SearchScope scope) {
    return !(scope instanceof LocalSearchScope) || !((LocalSearchScope)scope).isIgnoreInjectedPsi();
  }

  @NotNull
  static Processor<PsiElement> localProcessor(@NotNull StringSearcher searcher, @NotNull BulkOccurrenceProcessor processor) {
    return new ReadActionProcessor<PsiElement>() {
      @Override
      public boolean processInReadAction(PsiElement scopeElement) {
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

  private boolean processElementsWithTextInGlobalScope(@NotNull final GlobalSearchScope scope,
                                                       @NotNull final StringSearcher searcher,
                                                       final short searchContext,
                                                       final boolean caseSensitively,
                                                       @Nullable String containerName,
                                                       @NotNull ProgressIndicator progress,
                                                       @NotNull final BulkOccurrenceProcessor processor) {
    progress.pushState();
    boolean result;
    try {
      progress.setText(PsiBundle.message("psi.scanning.files.progress"));

      String text = searcher.getPattern();
      Set<VirtualFile> fileSet = new THashSet<>();
      getFilesWithText(scope, searchContext, caseSensitively, text, fileSet);

      progress.setText(PsiBundle.message("psi.search.for.word.progress", text));

      final Processor<PsiElement> localProcessor = localProcessor(searcher, processor);
      if (containerName != null) {
        List<VirtualFile> intersectionWithContainerFiles = new ArrayList<>();
        // intersectionWithContainerFiles holds files containing words from both `text` and `containerName`
        getFilesWithText(scope, searchContext, caseSensitively, text+" "+containerName, intersectionWithContainerFiles);
        if (!intersectionWithContainerFiles.isEmpty()) {
          int totalSize = fileSet.size();
          result = processPsiFileRoots(intersectionWithContainerFiles, totalSize, 0, progress, localProcessor);

          if (result) {
            fileSet.removeAll(intersectionWithContainerFiles);
            if (!fileSet.isEmpty()) {
              result = processPsiFileRoots(new ArrayList<>(fileSet), totalSize, intersectionWithContainerFiles.size(), progress, localProcessor);
            }
          }
          return result;
        }
      }
      result = fileSet.isEmpty() || processPsiFileRoots(new ArrayList<>(fileSet), fileSet.size(), 0, progress, localProcessor);
    }
    finally {
      progress.popState();
    }
    return result;
  }

  /**
   * @param files to scan for references in this pass.
   * @param totalSize the number of files to scan in both passes. Can be different from {@code files.size()} in case of
   *                  two-pass scan, where we first scan files containing container name and then all the rest files.
   * @param alreadyProcessedFiles the number of files scanned in previous pass.
   * @return true if completed
   */
  private boolean processPsiFileRoots(@NotNull List<? extends VirtualFile> files,
                                      final int totalSize,
                                      int alreadyProcessedFiles,
                                      @NotNull final ProgressIndicator progress,
                                      @NotNull final Processor<? super PsiFile> localProcessor) {
    myManager.startBatchFilesProcessingMode();
    try {
      final AtomicInteger counter = new AtomicInteger(alreadyProcessedFiles);
      final AtomicBoolean stopped = new AtomicBoolean(false);
      ProgressIndicator originalIndicator = ProgressWrapper.unwrapAll(progress);
      return processFilesConcurrentlyDespiteWriteActions(myManager.getProject(), files, progress, stopped, vfile -> {
        TooManyUsagesStatus.getFrom(originalIndicator).pauseProcessingIfTooManyUsages();
        try {
          processVirtualFile(vfile, stopped, localProcessor);
        }
        catch (ProcessCanceledException | IndexNotReadyException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error("Error during processing of: " + vfile.getName(), e);
          throw e;
        }
        if (progress.isRunning()) {
          double fraction = (double)counter.incrementAndGet() / totalSize;
          progress.setFraction(fraction);
        }
        return !stopped.get();
      });
    }
    finally {
      myManager.finishBatchFilesProcessingMode();
    }
  }

  // Tries to run {@code localProcessor} for each file in {@code files} concurrently on ForkJoinPool.
  // When encounters write action request, stops all threads, waits for write action to finish and re-starts all threads again.
  // {@code localProcessor} must be as idempotent as possible (and must not return false on progress cancel)
  public static boolean processFilesConcurrentlyDespiteWriteActions(@NotNull Project project,
                                                                    @NotNull List<? extends VirtualFile> files,
                                                                    @NotNull final ProgressIndicator progress,
                                                                    @NotNull AtomicBoolean stopped,
                                                                    @NotNull final Processor<? super VirtualFile> localProcessor) {
    ApplicationEx app = (ApplicationEx)ApplicationManager.getApplication();
    if (!app.isDispatchThread()) {
      CoreProgressManager.assertUnderProgress(progress);
    }
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
          if (processorCanceled) stopped.set(true);
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
            if (!someTaskFailed.get()) {
              try {
                // wrap in unconditional impatient reader to bail early at write action start,
                // regardless of whether was called from highlighting (already impatient-wrapped) or Find Usages action
                app.executeByImpatientReader(() -> {
                  if (!localProcessor.process(vfile)) {
                    stopped.set(true);
                  }
                  else {
                    processedFiles.add(vfile);
                  }
                });
              }
              catch (ProcessCanceledException e) {
                someTaskFailed.set(true);
                throw e;
              }
            }
            else {
              return false;
            }
            return !stopped.get();
          };
          // try to run parallel read actions but fail as soon as possible
          try {
            JobLauncher.getInstance().invokeConcurrentlyUnderProgress(files, wrapper, processor);
            processorCanceled = stopped.get();
          }
          catch (ProcessCanceledException e) {
            // we can be interrupted by wrapper (means write action is about to start) or by genuine exception in progress
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
      Set<VirtualFile> t = new THashSet<>(files);
      t.removeAll(processedFiles);
      files = new ArrayList<>(t);
    }
    return true;
  }

  private void processVirtualFile(@NotNull final VirtualFile vfile,
                                  @NotNull final AtomicBoolean stopped, @NotNull final Processor<? super PsiFile> localProcessor) throws ApplicationUtil.CannotRunReadActionException {
    final PsiFile file = ApplicationUtil.tryRunReadAction(() -> vfile.isValid() ? myManager.findFile(vfile) : null);
    if (file != null && !(file instanceof PsiBinaryFile)) {
      ApplicationUtil.tryRunReadAction(() -> {
        final Project project = myManager.getProject();
        if (project.isDisposed()) throw new ProcessCanceledException();
        if (DumbService.isDumb(project)) throw ApplicationUtil.CannotRunReadActionException.create();

        List<PsiFile> psiRoots = file.getViewProvider().getAllFiles();
        Set<PsiFile> processed = new THashSet<>(psiRoots.size() * 2, (float)0.5);
        for (final PsiFile psiRoot : psiRoots) {
          ProgressManager.checkCanceled();
          assert psiRoot != null : "One of the roots of file " + file + " is null. All roots: " + psiRoots + "; ViewProvider: " +
                                   file.getViewProvider() + "; Virtual file: " + file.getViewProvider().getVirtualFile();
          if (!processed.add(psiRoot)) continue;
          if (!psiRoot.isValid()) {
            continue;
          }

          if (!localProcessor.process(psiRoot)) {
            stopped.set(true);
            break;
          }
        }
      });
    }
  }

  private void getFilesWithText(@NotNull GlobalSearchScope scope,
                                final short searchContext,
                                final boolean caseSensitively,
                                @NotNull String text,
                                @NotNull Collection<? super VirtualFile> result) {
    myManager.startBatchFilesProcessingMode();
    try {
      Processor<? super VirtualFile> processor = Processors.cancelableCollectProcessor(result);
      boolean success = processFilesWithText(scope, searchContext, caseSensitively, text, processor);
      // success == false means exception in index
    }
    finally {
      myManager.finishBatchFilesProcessingMode();
    }
  }

  public boolean processFilesWithText(@NotNull final GlobalSearchScope scope,
                                      final short searchContext,
                                      final boolean caseSensitively,
                                      @NotNull String text,
                                      @NotNull final Processor<? super VirtualFile> processor) {
    List<IdIndexEntry> entries = getWordEntries(text, caseSensitively);
    if (entries.isEmpty()) return true;

    Condition<Integer> contextMatches = integer -> (integer.intValue() & searchContext) != 0;
    return processFilesContainingAllKeys(myManager.getProject(), scope, contextMatches, entries, processor);
  }

  @Override
  @NotNull
  public PsiFile[] findFilesWithPlainTextWords(@NotNull String word) {
    return CacheManager.SERVICE.getInstance(myManager.getProject()).getFilesWithWord(word, UsageSearchContext.IN_PLAIN_TEXT,
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
  public boolean processUsagesInNonJavaFiles(@Nullable final PsiElement originalElement,
                                             @NotNull String qName,
                                             @NotNull final PsiNonJavaFileReferenceProcessor processor,
                                             @NotNull final GlobalSearchScope initialScope) {
    if (qName.isEmpty()) {
      throw new IllegalArgumentException("Cannot search for elements with empty text. Element: "+originalElement+ "; "+(originalElement == null ? null : originalElement.getClass()));
    }
    final ProgressIndicator progress = getOrCreateIndicator();

    int dotIndex = qName.lastIndexOf('.');
    int dollarIndex = qName.lastIndexOf('$');
    int maxIndex = Math.max(dotIndex, dollarIndex);
    final String wordToSearch = maxIndex >= 0 ? qName.substring(maxIndex + 1) : qName;
    final GlobalSearchScope theSearchScope = ReadAction.compute(() -> {
      if (originalElement != null && myManager.isInProject(originalElement) && initialScope.isSearchInLibraries()) {
        return initialScope.intersectWith(GlobalSearchScope.projectScope(myManager.getProject()));
      }
      return initialScope;
    });
    PsiFile[] files = myDumbService.runReadActionInSmartMode(() -> CacheManager.SERVICE.getInstance(myManager.getProject()).getFilesWithWord(wordToSearch, UsageSearchContext.IN_PLAIN_TEXT, theSearchScope, true));

    final StringSearcher searcher = new StringSearcher(qName, true, true, false);

    progress.pushState();
    final Ref<Boolean> stopped = Ref.create(Boolean.FALSE);
    try {
      progress.setText(PsiBundle.message("psi.search.in.non.java.files.progress"));

      final SearchScope useScope = originalElement == null ? null : myDumbService.runReadActionInSmartMode(() -> getUseScope(originalElement));

      final int patternLength = qName.length();
      for (int i = 0; i < files.length; i++) {
        ProgressManager.checkCanceled();
        final PsiFile psiFile = files[i];
        if (psiFile instanceof PsiBinaryFile) continue;

        final CharSequence text = ReadAction.compute(() -> psiFile.getViewProvider().getContents());

        LowLevelSearchUtil.processTextOccurrences(text, 0, text.length(), searcher, index -> {
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
                                         final boolean caseSensitively) {
    return CacheManager.SERVICE.getInstance(myManager.getProject()).processFilesWithWord(processor, word, UsageSearchContext.IN_CODE, scope, caseSensitively);
  }

  @Override
  public boolean processAllFilesWithWordInText(@NotNull final String word,
                                               @NotNull final GlobalSearchScope scope,
                                               @NotNull final Processor<? super PsiFile> processor,
                                               final boolean caseSensitively) {
    return CacheManager.SERVICE.getInstance(myManager.getProject()).processFilesWithWord(processor, word, UsageSearchContext.IN_PLAIN_TEXT, scope, caseSensitively);
  }

  @Override
  public boolean processAllFilesWithWordInComments(@NotNull String word,
                                                   @NotNull GlobalSearchScope scope,
                                                   @NotNull Processor<? super PsiFile> processor) {
    return CacheManager.SERVICE.getInstance(myManager.getProject()).processFilesWithWord(processor, word, UsageSearchContext.IN_COMMENTS, scope, true);
  }

  @Override
  public boolean processAllFilesWithWordInLiterals(@NotNull String word,
                                                   @NotNull GlobalSearchScope scope,
                                                   @NotNull Processor<? super PsiFile> processor) {
    return CacheManager.SERVICE.getInstance(myManager.getProject()).processFilesWithWord(processor, word, UsageSearchContext.IN_STRINGS, scope, true);
  }

  private static class RequestWithProcessor implements WordRequestInfo {
    @NotNull private final PsiSearchRequest request;
    @NotNull private Processor<? super PsiReference> refProcessor;

    private RequestWithProcessor(@NotNull PsiSearchRequest request, @NotNull Processor<? super PsiReference> processor) {
      this.request = request;
      refProcessor = processor;
    }

    private boolean uniteWith(@NotNull final RequestWithProcessor another) {
      if (request.equals(another.request)) {
        final Processor<? super PsiReference> myProcessor = refProcessor;
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

    @NotNull
    @Override
    public String getWord() {
      return request.word;
    }

    @NotNull
    @Override
    public SearchScope getSearchScope() {
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

    @Nullable
    @Override
    public String getContainerName() {
      return request.containerName;
    }
  }

  @Override
  public boolean processRequests(@NotNull SearchRequestCollector collector, @NotNull Processor<? super PsiReference> processor) {
    final Map<SearchRequestCollector, Processor<? super PsiReference>> collectors = new HashMap<>();
    collectors.put(collector, processor);

    ProgressIndicator progress = getOrCreateIndicator();
    if (appendCollectorsFromQueryRequests(progress, collectors) == QueryRequestsRunResult.STOPPED) {
      return false;
    }
    do {
      final Map<Set<IdIndexEntry>, Collection<RequestWithProcessor>> globals = new HashMap<>();
      final List<Computable<Boolean>> customs = new ArrayList<>();
      final Set<RequestWithProcessor> locals = new LinkedHashSet<>();
      Map<RequestWithProcessor, Processor<? super PsiElement>> localProcessors = new THashMap<>();
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
      final QueryRequestsRunResult result = appendCollectorsFromQueryRequests(progress, collectors);
      if (result == QueryRequestsRunResult.STOPPED) {
        return false;
      }
      else if (result == QueryRequestsRunResult.UNCHANGED) {
        return true;
      }
    }
    while (true);
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> processRequestsAsync(@NotNull SearchRequestCollector collector, @NotNull Processor<? super PsiReference> processor) {
    return AsyncUtil.wrapBoolean(processRequests(collector, processor));
  }

  private enum QueryRequestsRunResult {
    STOPPED,
    UNCHANGED,
    CHANGED,
  }

  @NotNull
  private static QueryRequestsRunResult appendCollectorsFromQueryRequests(@NotNull ProgressIndicator progress,
                                                                          @NotNull Map<SearchRequestCollector, Processor<? super PsiReference>> collectors) {
    boolean changed = false;
    Deque<SearchRequestCollector> queue = new LinkedList<>(collectors.keySet());
    while (!queue.isEmpty()) {
      progress.checkCanceled();
      final SearchRequestCollector each = queue.removeFirst();
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

  private boolean processGlobalRequestsOptimized(@NotNull Map<Set<IdIndexEntry>, Collection<RequestWithProcessor>> singles,
                                                 @NotNull ProgressIndicator progress,
                                                 @NotNull final Map<RequestWithProcessor, Processor<? super PsiElement>> localProcessors) {
    if (singles.isEmpty()) {
      return true;
    }

    if (singles.size() == 1) {
      final Collection<? extends RequestWithProcessor> requests = singles.values().iterator().next();
      if (requests.size() == 1) {
        final RequestWithProcessor theOnly = requests.iterator().next();
        return processSingleRequest(theOnly.request, theOnly.refProcessor);
      }
    }

    return processGlobalRequests(singles, progress, localProcessors);
  }

  <T extends WordRequestInfo> boolean processGlobalRequests(@NotNull Map<Set<IdIndexEntry>, Collection<T>> singles,
                                                            @NotNull ProgressIndicator progress,
                                                            @NotNull Map<T, Processor<? super PsiElement>> localProcessors) {
    progress.pushState();
    progress.setText(PsiBundle.message("psi.scanning.files.progress"));
    boolean result;

    try {
      // intersectionCandidateFiles holds files containing words from all requests in `singles` and words in corresponding container names
      final Map<VirtualFile, Collection<T>> intersectionCandidateFiles = new HashMap<>();
      // restCandidateFiles holds files containing words from all requests in `singles` but EXCLUDING words in corresponding container names
      final Map<VirtualFile, Collection<T>> restCandidateFiles = new HashMap<>();
      collectFiles(singles, intersectionCandidateFiles, restCandidateFiles);

      if (intersectionCandidateFiles.isEmpty() && restCandidateFiles.isEmpty()) {
        return true;
      }

      final Set<String> allWords = new TreeSet<>();
      for (T singleRequest : localProcessors.keySet()) {
        ProgressManager.checkCanceled();
        allWords.add(singleRequest.getWord());
      }
      progress.setText(PsiBundle.message("psi.search.for.word.progress", getPresentableWordsDescription(allWords)));

      if (intersectionCandidateFiles.isEmpty()) {
        result = processCandidates(localProcessors, restCandidateFiles, progress, restCandidateFiles.size(), 0);
      }
      else {
        int totalSize = restCandidateFiles.size() + intersectionCandidateFiles.size();
        result = processCandidates(localProcessors, intersectionCandidateFiles, progress, totalSize, 0);
        if (result) {
          result = processCandidates(localProcessors, restCandidateFiles, progress, totalSize, intersectionCandidateFiles.size());
        }
      }
    }
    finally {
      progress.popState();
    }

    return result;
  }

  private <X> boolean processCandidates(@NotNull final Map<X, Processor<? super PsiElement>> localProcessors,
                                        @NotNull final Map<VirtualFile, Collection<X>> candidateFiles,
                                        @NotNull ProgressIndicator progress,
                                        int totalSize,
                                        int alreadyProcessedFiles) {
    List<VirtualFile> files = new ArrayList<>(candidateFiles.keySet());

    return processPsiFileRoots(files, totalSize, alreadyProcessedFiles, progress, psiRoot -> {
      final VirtualFile vfile = psiRoot.getVirtualFile();
      for (final X singleRequest : candidateFiles.get(vfile)) {
        ProgressManager.checkCanceled();
        Processor<? super PsiElement> localProcessor = localProcessors.get(singleRequest);
        if (!localProcessor.process(psiRoot)) {
          return false;
        }
      }
      return true;
    });
  }

  @NotNull
  private static String getPresentableWordsDescription(@NotNull Set<String> allWords) {
    final StringBuilder result = new StringBuilder();
    for (String string : allWords) {
      ProgressManager.checkCanceled();
        if (string != null && !string.isEmpty()) {
        if (result.length() > 50) {
          result.append("...");
          break;
        }
        if (result.length() != 0) result.append(", ");
        result.append(string);
      }
    }
    return result.toString();
  }

  @NotNull
  private static BulkOccurrenceProcessor adaptProcessor(@NotNull PsiSearchRequest singleRequest,
                                                       @NotNull Processor<? super PsiReference> consumer) {
    final SearchScope searchScope = singleRequest.searchScope;
    final boolean ignoreInjectedPsi = searchScope instanceof LocalSearchScope && ((LocalSearchScope)searchScope).isIgnoreInjectedPsi();
    final RequestResultProcessor wrapped = singleRequest.processor;
    return new BulkOccurrenceProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement scope, @NotNull int[] offsetsInScope, @NotNull StringSearcher searcher) {
        try {
          ProgressManager.checkCanceled();
          if (wrapped instanceof RequestResultProcessor.BulkResultProcessor) {
            return ((RequestResultProcessor.BulkResultProcessor)wrapped).processTextOccurrences(scope, offsetsInScope, consumer);
          }

          return LowLevelSearchUtil.processElementsAtOffsets(scope, searcher, !ignoreInjectedPsi,
                                                             getOrCreateIndicator(), offsetsInScope,
                                                             (element, offsetInElement) -> {
            if (ignoreInjectedPsi && element instanceof PsiLanguageInjectionHost) return true;
            return wrapped.processTextOccurrence(element, offsetInElement, consumer);
          });
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception | Error e) {
          PsiFile file = scope.getContainingFile();
          LOG.error("Error during processing of: " + (file != null ? file.getName() : scope), e);
          return true;
        }
      }

      @Override
      public String toString() {
        return consumer.toString();
      }
    };
  }

  private <T extends WordRequestInfo> void collectFiles(@NotNull final Map<Set<IdIndexEntry>, Collection<T>> singles,
                                                        @NotNull final Map<VirtualFile, Collection<T>> intersectionResult,
                                                        @NotNull final Map<VirtualFile, Collection<T>> restResult) {
    for (Map.Entry<Set<IdIndexEntry>, Collection<T>> entry : singles.entrySet()) {
      ProgressManager.checkCanceled();
      final Set<IdIndexEntry> keys = entry.getKey();
      if (keys.isEmpty()) {
        continue;
      }

      final Collection<T> processors = entry.getValue();
      final GlobalSearchScope commonScope = uniteScopes(processors);
      final Set<VirtualFile> intersectionWithContainerNameFiles = intersectionWithContainerNameFiles(commonScope, processors, keys);

      List<VirtualFile> result = new ArrayList<>();
      Processor<VirtualFile> processor = Processors.cancelableCollectProcessor(result);
      processFilesContainingAllKeys(myManager.getProject(), commonScope, null, keys, processor);
      for (final VirtualFile file : result) {
        ProgressManager.checkCanceled();
        for (final IdIndexEntry indexEntry : keys) {
          ProgressManager.checkCanceled();
          myDumbService.runReadActionInSmartMode(
            () -> FileBasedIndex.getInstance().processValues(IdIndex.NAME, indexEntry, file, (file1, value) -> {
              int mask = value.intValue();
              for (T single : processors) {
                ProgressManager.checkCanceled();
                if ((mask & single.getSearchContext()) != 0 && single.getSearchScope().contains(file1)) {
                  Map<VirtualFile, Collection<T>> result1 =
                    intersectionWithContainerNameFiles == null || !intersectionWithContainerNameFiles.contains(file1) ? restResult : intersectionResult;
                  result1.computeIfAbsent(file1, __ -> new SmartList<>()).add(single);
                }
              }
              return true;
            }, commonScope));
        }
      }
    }
  }

  @Nullable("null means we did not find common container files")
  private Set<VirtualFile> intersectionWithContainerNameFiles(@NotNull GlobalSearchScope commonScope,
                                                              @NotNull Collection<? extends WordRequestInfo> data,
                                                              @NotNull Set<IdIndexEntry> keys) {
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

    List<IdIndexEntry> entries = getWordEntries(commonName, caseSensitive);
    if (entries.isEmpty()) return null;
    entries.addAll(keys); // should find words from both text and container names

    final short finalSearchContext = searchContext;
    Condition<Integer> contextMatches = context -> (context.intValue() & finalSearchContext) != 0;
    Set<VirtualFile> containerFiles = new THashSet<>();
    Processor<VirtualFile> processor = Processors.cancelableCollectProcessor(containerFiles);
    processFilesContainingAllKeys(myManager.getProject(), commonScope, contextMatches, entries, processor);

    return containerFiles;
  }

  @NotNull
  private static GlobalSearchScope uniteScopes(@NotNull Collection<? extends WordRequestInfo> requests) {
    Set<GlobalSearchScope> scopes = ContainerUtil.map2LinkedSet(requests, r -> (GlobalSearchScope)r.getSearchScope());
    return GlobalSearchScope.union(scopes.toArray(GlobalSearchScope.EMPTY_ARRAY));
  }

  private static void distributePrimitives(@NotNull Map<SearchRequestCollector, Processor<? super PsiReference>> collectors,
                                           @NotNull Set<RequestWithProcessor> locals,
                                           @NotNull Map<Set<IdIndexEntry>, Collection<RequestWithProcessor>> globals,
                                           @NotNull List<? super Computable<Boolean>> customs,
                                           @NotNull Map<RequestWithProcessor, Processor<? super PsiElement>> localProcessors) {
    for (final Map.Entry<SearchRequestCollector, Processor<? super PsiReference>> entry : collectors.entrySet()) {
      ProgressManager.checkCanceled();
      final Processor<? super PsiReference> processor = entry.getValue();
      SearchRequestCollector collector = entry.getKey();
      for (final PsiSearchRequest primitive : collector.takeSearchRequests()) {
        ProgressManager.checkCanceled();
        final SearchScope scope = primitive.searchScope;
        if (scope instanceof LocalSearchScope) {
          registerRequest(locals, primitive, processor);
        }
        else {
          Set<IdIndexEntry> key = new HashSet<>(getWordEntries(primitive.word, primitive.caseSensitive));
          registerRequest(globals.computeIfAbsent(key, __ -> new SmartList<>()), primitive, processor);
        }
      }
      for (final Processor<Processor<? super PsiReference>> customAction : collector.takeCustomSearchActions()) {
        ProgressManager.checkCanceled();
        customs.add((Computable<Boolean>)() -> customAction.process(processor));
      }
    }

    for (Map.Entry<Set<IdIndexEntry>, Collection<RequestWithProcessor>> entry : globals.entrySet()) {
      ProgressManager.checkCanceled();
      for (RequestWithProcessor singleRequest : entry.getValue()) {
        ProgressManager.checkCanceled();
        PsiSearchRequest primitive = singleRequest.request;
        StringSearcher searcher = new StringSearcher(primitive.word, primitive.caseSensitive, true, false);
        BulkOccurrenceProcessor adapted = adaptProcessor(primitive, singleRequest.refProcessor);

        Processor<PsiElement> localProcessor = localProcessor(searcher, adapted);

        assert !localProcessors.containsKey(singleRequest) || localProcessors.get(singleRequest) == localProcessor;
        localProcessors.put(singleRequest, localProcessor);
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
    final EnumSet<Options> options = EnumSet.of(Options.PROCESS_ONLY_JAVA_IDENTIFIERS_IF_POSSIBLE);
    if (single.caseSensitive) options.add(Options.CASE_SENSITIVE_SEARCH);
    if (shouldProcessInjectedPsi(single.searchScope)) options.add(Options.PROCESS_INJECTED_PSI);

    return bulkProcessElementsWithWord(single.searchScope, single.word, single.searchContext, options, single.containerName,
                                       adaptProcessor(single, consumer)
    );
  }

  @NotNull
  @Override
  public SearchCostResult isCheapEnoughToSearch(@NotNull String name,
                                                @NotNull final GlobalSearchScope scope,
                                                @Nullable final PsiFile fileToIgnoreOccurrencesIn,
                                                @Nullable final ProgressIndicator progress) {
    if (!ReadAction.compute(() -> scope.getUnloadedModulesBelongingToScope().isEmpty())) {
      return SearchCostResult.TOO_MANY_OCCURRENCES;
    }

    final AtomicInteger filesCount = new AtomicInteger();
    final AtomicLong filesSizeToProcess = new AtomicLong();

    final Processor<VirtualFile> processor = new Processor<VirtualFile>() {
      private final VirtualFile virtualFileToIgnoreOccurrencesIn =
        fileToIgnoreOccurrencesIn == null ? null : fileToIgnoreOccurrencesIn.getVirtualFile();
      private final int maxFilesToProcess = Registry.intValue("ide.unused.symbol.calculation.maxFilesToSearchUsagesIn", 10);
      private final int maxFilesSizeToProcess = Registry.intValue("ide.unused.symbol.calculation.maxFilesSizeToSearchUsagesIn", 524288);

      @Override
      public boolean process(VirtualFile file) {
        ProgressManager.checkCanceled();
        if (Comparing.equal(file, virtualFileToIgnoreOccurrencesIn)) return true;
        int currentFilesCount = filesCount.incrementAndGet();
        long accumulatedFileSizeToProcess = filesSizeToProcess.addAndGet(file.isDirectory() ? 0 : file.getLength());
        return currentFilesCount < maxFilesToProcess && accumulatedFileSizeToProcess < maxFilesSizeToProcess;
      }
    };
    List<IdIndexEntry> keys = getWordEntries(name, true);
    boolean cheap = keys.isEmpty() || processFilesContainingAllKeys(myManager.getProject(), scope, null, keys, processor);

    if (!cheap) {
      return SearchCostResult.TOO_MANY_OCCURRENCES;
    }

    return filesCount.get() == 0 ? SearchCostResult.ZERO_OCCURRENCES : SearchCostResult.FEW_OCCURRENCES;
  }

  private static boolean processFilesContainingAllKeys(@NotNull Project project,
                                                       @NotNull final GlobalSearchScope scope,
                                                       @Nullable final Condition<? super Integer> checker,
                                                       @NotNull final Collection<? extends IdIndexEntry> keys,
                                                       @NotNull final Processor<? super VirtualFile> processor) {
    Computable<Boolean> query =
      () -> FileBasedIndex.getInstance().processFilesContainingAllKeys(IdIndex.NAME, keys, scope, checker, processor);

    Boolean[] result = {null};
    if (FileBasedIndex.indexAccessDuringDumbModeEnabled()) {
      ReadAction.run(() -> {
        FileBasedIndex.getInstance().ignoreDumbMode(() -> {
          result[0] = Boolean.valueOf(query.compute());
        }, project);
      });
    }
    return result[0] != null ? result[0] : DumbService.getInstance(project).runReadActionInSmartMode(query);
  }

  @NotNull
  static List<IdIndexEntry> getWordEntries(@NotNull String name, final boolean caseSensitively) {
    List<String> words = StringUtil.getWordsInStringLongestFirst(name);
    if (words.isEmpty()) {
      String trimmed = name.trim();
      if (StringUtil.isNotEmpty(trimmed)) {
        words = Collections.singletonList(trimmed);
      }
    }
    if (words.isEmpty()) return Collections.emptyList();
    return ContainerUtil.map2List(words, word -> new IdIndexEntry(word, caseSensitively));
  }

  public static boolean processTextOccurrences(@NotNull final PsiElement element,
                                               @NotNull String stringToSearch,
                                               @NotNull GlobalSearchScope searchScope,
                                               @NotNull final UsageInfoFactory factory,
                                               @NotNull final Processor<? super UsageInfo> processor) {
    PsiSearchHelper helper = ReadAction.compute(() -> PsiSearchHelper.getInstance(element.getProject()));

    return helper.processUsagesInNonJavaFiles(element, stringToSearch, (psiFile, startOffset, endOffset) -> {
      try {
        UsageInfo usageInfo = ReadAction.compute(() -> factory.createUsageInfo(psiFile, startOffset, endOffset));
        return usageInfo == null || processor.process(usageInfo);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
        return true;
      }
    }, searchScope);
  }
}
