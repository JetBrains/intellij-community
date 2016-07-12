/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.psi.impl.search;

import com.intellij.concurrency.AsyncFuture;
import com.intellij.concurrency.AsyncUtil;
import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.util.TooManyUsagesStatus;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.*;
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
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.SmartList;
import com.intellij.util.codeInsight.CommentUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.text.StringSearcher;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PsiSearchHelperImpl implements PsiSearchHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.PsiSearchHelperImpl");
  private final PsiManagerEx myManager;
  
  public enum Options {
    PROCESS_INJECTED_PSI, CASE_SENSITIVE_SEARCH, PROCESS_ONLY_JAVA_IDENTIFIERS_IF_POSSIBLE
  }

  @Override
  @NotNull
  public SearchScope getUseScope(@NotNull PsiElement element) {
    SearchScope scope = element.getUseScope();
    for (UseScopeEnlarger enlarger : UseScopeEnlarger.EP_NAME.getExtensions()) {
      final SearchScope additionalScope = enlarger.getAdditionalUseScope(element);
      if (additionalScope != null) {
        scope = scope.union(additionalScope);
      }
    }
    for (UseScopeOptimizer optimizer : UseScopeOptimizer.EP_NAME.getExtensions()) {
      final GlobalSearchScope scopeToExclude = optimizer.getScopeToExclude(element);
      if (scopeToExclude != null) {
        scope = scope.intersectWith(GlobalSearchScope.notScope(scopeToExclude));
      }
    }
    return scope;
  }

  public PsiSearchHelperImpl(@NotNull PsiManagerEx manager) {
    myManager = manager;
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
                                                     @NotNull final Processor<PsiElement> processor) {
    TextOccurenceProcessor occurrenceProcessor = new TextOccurenceProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, int offsetInElement) {
        if (CommentUtilCore.isCommentTextElement(element)) {
          if (element.findReferenceAt(offsetInElement) == null) {
            return processor.process(element);
          }
        }
        return true;
      }
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
    if (text.isEmpty()) {
      throw new IllegalArgumentException("Cannot search for elements with empty text");
    }
    final ProgressIndicator progress = getOrCreateIndicator();
    final boolean processInjectedPsi = options.contains(Options.PROCESS_INJECTED_PSI);
    if (searchScope instanceof GlobalSearchScope) {
      StringSearcher searcher = new StringSearcher(text, options.contains(Options.CASE_SENSITIVE_SEARCH), true,
                                                   searchContext == UsageSearchContext.IN_STRINGS,
                                                   options.contains(Options.PROCESS_ONLY_JAVA_IDENTIFIERS_IF_POSSIBLE));

      return processElementsWithTextInGlobalScope(processor,
                                                  (GlobalSearchScope)searchScope,
                                                  searcher,
                                                  searchContext, options.contains(Options.CASE_SENSITIVE_SEARCH), containerName, progress,
                                                  processInjectedPsi);
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
        if (!scopeElement.isPhysical()) {
          scopeElement = scopeElement.getNavigationElement();
        }
        if (scopeElement.getTextRange() == null) {
          // clients can put whatever they want to the LocalSearchScope. Skip what we can't process.
          LOG.debug("Element " + scopeElement + " of class " + scopeElement.getClass() + " has null range");
          return true;
        }
        return LowLevelSearchUtil.processElementsContainingWordInElement(processor, scopeElement, searcher, processInjectedPsi, progress);
      }

      @Override
      public String toString() {
        return processor.toString();
      }
    };
    return JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Arrays.asList(scopeElements), progress, true, true, localProcessor);
  }

  @NotNull
  private static ProgressIndicator getOrCreateIndicator() {
    ProgressIndicator progress = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (progress == null) progress = new EmptyProgressIndicator();
    return progress;
  }

  public static boolean shouldProcessInjectedPsi(@NotNull SearchScope scope) {
    return !(scope instanceof LocalSearchScope) || !((LocalSearchScope)scope).isIgnoreInjectedPsi();
  }

  @NotNull
  private static Processor<PsiElement> localProcessor(@NotNull final TextOccurenceProcessor processor,
                                                      @NotNull final ProgressIndicator progress,
                                                      final boolean processInjectedPsi,
                                                      @NotNull final StringSearcher searcher) {
    return new ReadActionProcessor<PsiElement>() {
      @Override
      public boolean processInReadAction(PsiElement scopeElement) {
        return scopeElement.isValid() &&
               LowLevelSearchUtil.processElementsContainingWordInElement(processor, scopeElement, searcher, processInjectedPsi, progress);
      }

      @Override
      public String toString() {
        return processor.toString();
      }
    };
  }

  private boolean processElementsWithTextInGlobalScope(@NotNull final TextOccurenceProcessor processor,
                                                       @NotNull final GlobalSearchScope scope,
                                                       @NotNull final StringSearcher searcher,
                                                       final short searchContext,
                                                       final boolean caseSensitively,
                                                       @Nullable String containerName,
                                                       @NotNull ProgressIndicator progress,
                                                       final boolean processInjectedPsi) {
    if (Thread.holdsLock(PsiLock.LOCK)) {
      throw new AssertionError("You must not run search from within updating PSI activity. Please consider invokeLatering it instead.");
    }
    progress.pushState();
    boolean result;
    try {
      progress.setText(PsiBundle.message("psi.scanning.files.progress"));

      String text = searcher.getPattern();
      Set<VirtualFile> fileSet = new THashSet<VirtualFile>();
      getFilesWithText(scope, searchContext, caseSensitively, text, progress, fileSet);

      progress.setText(PsiBundle.message("psi.search.for.word.progress", text));

      final Processor<PsiElement> localProcessor = localProcessor(processor, progress, processInjectedPsi, searcher);
      if (containerName != null) {
        List<VirtualFile> intersectionWithContainerFiles = new ArrayList<VirtualFile>();
        // intersectionWithContainerFiles holds files containing words from both `text` and `containerName`
        getFilesWithText(scope, searchContext, caseSensitively, text+" "+containerName, progress, intersectionWithContainerFiles);
        if (!intersectionWithContainerFiles.isEmpty()) {
          int totalSize = fileSet.size();
          result = processPsiFileRoots(intersectionWithContainerFiles, totalSize, 0, progress, localProcessor);

          if (result) {
            fileSet.removeAll(intersectionWithContainerFiles);
            if (!fileSet.isEmpty()) {
              result = processPsiFileRoots(new ArrayList<VirtualFile>(fileSet), totalSize, intersectionWithContainerFiles.size(), progress, localProcessor);
            }
          }
          return result;
        }
      }
      result = fileSet.isEmpty() || processPsiFileRoots(new ArrayList<VirtualFile>(fileSet), fileSet.size(), 0, progress, localProcessor);
    }
    finally {
      progress.popState();
    }
    return result;
  }

  /**
   * @param files to scan for references in this pass.
   * @param totalSize the number of files to scan in both passes. Can be different from <code>files.size()</code> in case of
   *                  two-pass scan, where we first scan files containing container name and then all the rest files.
   * @param alreadyProcessedFiles the number of files scanned in previous pass.
   * @return true if completed
   */
  private boolean processPsiFileRoots(@NotNull List<VirtualFile> files,
                                      final int totalSize,
                                      int alreadyProcessedFiles,
                                      @NotNull final ProgressIndicator progress,
                                      @NotNull final Processor<? super PsiFile> localProcessor) {
    myManager.startBatchFilesProcessingMode();
    try {
      final AtomicInteger counter = new AtomicInteger(alreadyProcessedFiles);
      final AtomicBoolean canceled = new AtomicBoolean(false);

      boolean completed = true;
      while (true) {
        List<VirtualFile> failedList = new SmartList<VirtualFile>();
        final List<VirtualFile> failedFiles = Collections.synchronizedList(failedList);
        final Processor<VirtualFile> processor = vfile -> {
          try {
            TooManyUsagesStatus.getFrom(progress).pauseProcessingIfTooManyUsages();
            processVirtualFile(vfile, progress, localProcessor, canceled, counter, totalSize);
          }
          catch (ApplicationUtil.CannotRunReadActionException action) {
            failedFiles.add(vfile);
          }
          return !canceled.get();
        };
        if (ApplicationManager.getApplication().isWriteAccessAllowed() || ((ApplicationEx)ApplicationManager.getApplication()).isWriteActionPending()) {
          // no point in processing in separate threads - they are doomed to fail to obtain read action anyway
          completed &= ContainerUtil.process(files, processor);
        }
        else {
          completed &= JobLauncher.getInstance().invokeConcurrentlyUnderProgress(files, progress, false, false, processor);
        }

        if (failedFiles.isEmpty()) {
          break;
        }
        // we failed to run read action in job launcher thread
        // run read action in our thread instead to wait for a write action to complete and resume parallel processing
        DumbService.getInstance(myManager.getProject()).runReadActionInSmartMode(EmptyRunnable.getInstance());
        files = failedList;
      }
      return completed;
    }
    finally {
      myManager.finishBatchFilesProcessingMode();
    }
  }

  private void processVirtualFile(@NotNull final VirtualFile vfile,
                                  @NotNull final ProgressIndicator progress,
                                  @NotNull final Processor<? super PsiFile> localProcessor,
                                  @NotNull final AtomicBoolean canceled,
                                  @NotNull AtomicInteger counter,
                                  int totalSize) throws ApplicationUtil.CannotRunReadActionException {
    final PsiFile file = ApplicationUtil.tryRunReadAction(() -> vfile.isValid() ? myManager.findFile(vfile) : null);
    if (file != null && !(file instanceof PsiBinaryFile)) {
      // load contents outside read action
      if (FileDocumentManager.getInstance().getCachedDocument(vfile) == null) {
        // cache bytes in vfs
        try {
          vfile.contentsToByteArray();
        }
        catch (IOException ignored) {
        }
      }
      ApplicationUtil.tryRunReadAction(new Computable<Void>() {
        @Override
        public Void compute() {
          final Project project = myManager.getProject();
          if (project.isDisposed()) throw new ProcessCanceledException();
          if (DumbService.isDumb(project)) throw new ApplicationUtil.CannotRunReadActionException();
          
          List<PsiFile> psiRoots = file.getViewProvider().getAllFiles();
          Set<PsiFile> processed = new THashSet<PsiFile>(psiRoots.size() * 2, (float)0.5);
          for (final PsiFile psiRoot : psiRoots) {
            progress.checkCanceled();
            assert psiRoot != null : "One of the roots of file " + file + " is null. All roots: " + psiRoots + "; ViewProvider: " +
                                     file.getViewProvider() + "; Virtual file: " + file.getViewProvider().getVirtualFile();
            if (!processed.add(psiRoot)) continue;
            if (!psiRoot.isValid()) {
              continue;
            }

            if (!localProcessor.process(psiRoot)) {
              canceled.set(true);
              break;
            }
          }
          return null;
        }
      });
    }
    if (progress.isRunning()) {
      double fraction = (double)counter.incrementAndGet() / totalSize;
      progress.setFraction(fraction);
    }
  }

  private void getFilesWithText(@NotNull GlobalSearchScope scope,
                                final short searchContext,
                                final boolean caseSensitively,
                                @NotNull String text,
                                @NotNull final ProgressIndicator progress,
                                @NotNull Collection<VirtualFile> result) {
    myManager.startBatchFilesProcessingMode();
    try {
      Processor<VirtualFile> processor = Processors.cancelableCollectProcessor(result);
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
                                      @NotNull final Processor<VirtualFile> processor) {
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
    final GlobalSearchScope theSearchScope = ApplicationManager.getApplication().runReadAction(new Computable<GlobalSearchScope>() {
      @Override
      public GlobalSearchScope compute() {
        if (originalElement != null && myManager.isInProject(originalElement) && initialScope.isSearchInLibraries()) {
          return initialScope.intersectWith(GlobalSearchScope.projectScope(myManager.getProject()));
        }
        return initialScope;
      }
    });
    PsiFile[] files = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile[]>() {
      @Override
      public PsiFile[] compute() {
        return CacheManager.SERVICE.getInstance(myManager.getProject()).getFilesWithWord(wordToSearch, UsageSearchContext.IN_PLAIN_TEXT, theSearchScope, true);
      }
    });

    final StringSearcher searcher = new StringSearcher(qName, true, true, false);
    final int patternLength = searcher.getPattern().length();

    progress.pushState();
    final Ref<Boolean> cancelled = Ref.create(Boolean.FALSE);
    try {
      progress.setText(PsiBundle.message("psi.search.in.non.java.files.progress"));

      final SearchScope useScope = originalElement == null ? null : ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
        @Override
        public SearchScope compute() {
          return getUseScope(originalElement);
        }
      });

      for (int i = 0; i < files.length; i++) {
        progress.checkCanceled();
        final PsiFile psiFile = files[i];
        if (psiFile instanceof PsiBinaryFile) continue;

        final CharSequence text = ApplicationManager.getApplication().runReadAction(new Computable<CharSequence>() {
          @Override
          public CharSequence compute() {
            return psiFile.getViewProvider().getContents();
          }
        });

        LowLevelSearchUtil.processTextOccurrences(text, 0, text.length(), searcher, progress, new TIntProcedure() {
          @Override
          public boolean execute(final int index) {
            boolean isReferenceOK = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
              @Override
              public Boolean compute() {
                PsiReference referenceAt = psiFile.findReferenceAt(index);
                return referenceAt == null || useScope == null || !PsiSearchScopeUtil.isInScope(useScope.intersectWith(initialScope), psiFile);
              }
            });
            if (isReferenceOK && !processor.process(psiFile, index, index + patternLength)) {
              cancelled.set(Boolean.TRUE);
              return false;
            }

            return true;
          }
        });
        if (cancelled.get()) break;
        progress.setFraction((double)(i + 1) / files.length);
      }
    }
    finally {
      progress.popState();
    }

    return !cancelled.get();
  }

  @Override
  public boolean processAllFilesWithWord(@NotNull String word,
                                         @NotNull GlobalSearchScope scope,
                                         @NotNull Processor<PsiFile> processor,
                                         final boolean caseSensitively) {
    return CacheManager.SERVICE.getInstance(myManager.getProject()).processFilesWithWord(processor, word, UsageSearchContext.IN_CODE, scope, caseSensitively);
  }

  @Override
  public boolean processAllFilesWithWordInText(@NotNull final String word,
                                               @NotNull final GlobalSearchScope scope,
                                               @NotNull final Processor<PsiFile> processor,
                                               final boolean caseSensitively) {
    return CacheManager.SERVICE.getInstance(myManager.getProject()).processFilesWithWord(processor, word, UsageSearchContext.IN_PLAIN_TEXT, scope, caseSensitively);
  }

  @Override
  public boolean processAllFilesWithWordInComments(@NotNull String word,
                                                   @NotNull GlobalSearchScope scope,
                                                   @NotNull Processor<PsiFile> processor) {
    return CacheManager.SERVICE.getInstance(myManager.getProject()).processFilesWithWord(processor, word, UsageSearchContext.IN_COMMENTS, scope, true);
  }

  @Override
  public boolean processAllFilesWithWordInLiterals(@NotNull String word,
                                                   @NotNull GlobalSearchScope scope,
                                                   @NotNull Processor<PsiFile> processor) {
    return CacheManager.SERVICE.getInstance(myManager.getProject()).processFilesWithWord(processor, word, UsageSearchContext.IN_STRINGS, scope, true);
  }

  private static class RequestWithProcessor {
    @NotNull private final PsiSearchRequest request;
    @NotNull private Processor<PsiReference> refProcessor;

    private RequestWithProcessor(@NotNull PsiSearchRequest request, @NotNull Processor<PsiReference> processor) {
      this.request = request;
      refProcessor = processor;
    }

    private boolean uniteWith(@NotNull final RequestWithProcessor another) {
      if (request.equals(another.request)) {
        final Processor<PsiReference> myProcessor = refProcessor;
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
  }

  @Override
  public boolean processRequests(@NotNull SearchRequestCollector collector, @NotNull Processor<PsiReference> processor) {
    final Map<SearchRequestCollector, Processor<PsiReference>> collectors = ContainerUtil.newHashMap();
    collectors.put(collector, processor);

    ProgressIndicator progress = getOrCreateIndicator();
    appendCollectorsFromQueryRequests(collectors);
    boolean result;
    do {
      MultiMap<Set<IdIndexEntry>, RequestWithProcessor> globals = new MultiMap<Set<IdIndexEntry>, RequestWithProcessor>();
      final List<Computable<Boolean>> customs = ContainerUtil.newArrayList();
      final Set<RequestWithProcessor> locals = ContainerUtil.newLinkedHashSet();
      Map<RequestWithProcessor, Processor<PsiElement>> localProcessors = new THashMap<RequestWithProcessor, Processor<PsiElement>>();
      distributePrimitives(collectors, locals, globals, customs, localProcessors, progress);
      result = processGlobalRequestsOptimized(globals, progress, localProcessors);
      if (result) {
        for (RequestWithProcessor local : locals) {
          result = processSingleRequest(local.request, local.refProcessor);
          if (!result) break;
        }
        if (result) {
          for (Computable<Boolean> custom : customs) {
            result = custom.compute();
            if (!result) break;
          }
        }
        if (!result) break;
      }
    }
    while(appendCollectorsFromQueryRequests(collectors));
    return result;
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> processRequestsAsync(@NotNull SearchRequestCollector collector, @NotNull Processor<PsiReference> processor) {
    return AsyncUtil.wrapBoolean(processRequests(collector, processor));
  }

  private static boolean appendCollectorsFromQueryRequests(@NotNull Map<SearchRequestCollector, Processor<PsiReference>> collectors) {
    boolean changed = false;
    Deque<SearchRequestCollector> queue = new LinkedList<SearchRequestCollector>(collectors.keySet());
    while (!queue.isEmpty()) {
      final SearchRequestCollector each = queue.removeFirst();
      for (QuerySearchRequest request : each.takeQueryRequests()) {
        request.runQuery();
        assert !collectors.containsKey(request.collector) || collectors.get(request.collector) == request.processor;
        collectors.put(request.collector, request.processor);
        queue.addLast(request.collector);
        changed = true;
      }
    }
    return changed;
  }

  private boolean processGlobalRequestsOptimized(@NotNull MultiMap<Set<IdIndexEntry>, RequestWithProcessor> singles,
                                                 @NotNull ProgressIndicator progress,
                                                 @NotNull final Map<RequestWithProcessor, Processor<PsiElement>> localProcessors) {
    if (singles.isEmpty()) {
      return true;
    }

    if (singles.size() == 1) {
      final Collection<? extends RequestWithProcessor> requests = singles.values();
      if (requests.size() == 1) {
        final RequestWithProcessor theOnly = requests.iterator().next();
        return processSingleRequest(theOnly.request, theOnly.refProcessor);
      }
    }

    progress.pushState();
    progress.setText(PsiBundle.message("psi.scanning.files.progress"));
    boolean result;

    try {
      // intersectionCandidateFiles holds files containing words from all requests in `singles` and words in corresponding container names
      final MultiMap<VirtualFile, RequestWithProcessor> intersectionCandidateFiles = createMultiMap();
      // restCandidateFiles holds files containing words from all requests in `singles` but EXCLUDING words in corresponding container names
      final MultiMap<VirtualFile, RequestWithProcessor> restCandidateFiles = createMultiMap();
      collectFiles(singles, progress, intersectionCandidateFiles, restCandidateFiles);

      if (intersectionCandidateFiles.isEmpty() && restCandidateFiles.isEmpty()) {
        return true;
      }

      final Set<String> allWords = new TreeSet<String>();
      for (RequestWithProcessor singleRequest : localProcessors.keySet()) {
        allWords.add(singleRequest.request.word);
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

  private boolean processCandidates(@NotNull final Map<RequestWithProcessor, Processor<PsiElement>> localProcessors,
                                    @NotNull final MultiMap<VirtualFile, RequestWithProcessor> candidateFiles,
                                    @NotNull ProgressIndicator progress,
                                    int totalSize,
                                    int alreadyProcessedFiles) {
    List<VirtualFile> files = new ArrayList<VirtualFile>(candidateFiles.keySet());

    return processPsiFileRoots(files, totalSize, alreadyProcessedFiles, progress, psiRoot -> {
      final VirtualFile vfile = psiRoot.getVirtualFile();
      for (final RequestWithProcessor singleRequest : candidateFiles.get(vfile)) {
        Processor<PsiElement> localProcessor = localProcessors.get(singleRequest);
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
  private static TextOccurenceProcessor adaptProcessor(@NotNull PsiSearchRequest singleRequest,
                                                       @NotNull final Processor<PsiReference> consumer) {
    final SearchScope searchScope = singleRequest.searchScope;
    final boolean ignoreInjectedPsi = searchScope instanceof LocalSearchScope && ((LocalSearchScope)searchScope).isIgnoreInjectedPsi();
    final RequestResultProcessor wrapped = singleRequest.processor;
    return new TextOccurenceProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, int offsetInElement) {
        if (ignoreInjectedPsi && element instanceof PsiLanguageInjectionHost) return true;

        try {
          return wrapped.processTextOccurrence(element, offsetInElement, consumer);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.error(e);
          return true;
        }
        catch (Error e) {
          LOG.error(e);
          return true;
        }
      }

      @Override
      public String toString() {
        return consumer.toString();
      }
    };
  }

  private void collectFiles(@NotNull MultiMap<Set<IdIndexEntry>, RequestWithProcessor> singles,
                            @NotNull ProgressIndicator progress,
                            @NotNull final MultiMap<VirtualFile, RequestWithProcessor> intersectionResult,
                            @NotNull final MultiMap<VirtualFile, RequestWithProcessor> restResult) {
    for (final Set<IdIndexEntry> keys : singles.keySet()) {
      if (keys.isEmpty()) {
        continue;
      }

      final Collection<RequestWithProcessor> data = singles.get(keys);
      final GlobalSearchScope commonScope = uniteScopes(data);
      final Set<VirtualFile> intersectionWithContainerNameFiles = intersectionWithContainerNameFiles(commonScope, data, keys);

      List<VirtualFile> result = new ArrayList<VirtualFile>();
      Processor<VirtualFile> processor = Processors.cancelableCollectProcessor(result);
      processFilesContainingAllKeys(myManager.getProject(), commonScope, null, keys, processor);
      for (final VirtualFile file : result) {
        progress.checkCanceled();
        for (final IdIndexEntry entry : keys) {
          DumbService.getInstance(myManager.getProject()).runReadActionInSmartMode(
            (Runnable)() -> FileBasedIndex.getInstance().processValues(IdIndex.NAME, entry, file, new FileBasedIndex.ValueProcessor<Integer>() {
              @Override
              public boolean process(VirtualFile file1, Integer value) {
                int mask = value.intValue();
                for (RequestWithProcessor single : data) {
                  final PsiSearchRequest request = single.request;
                  if ((mask & request.searchContext) != 0 && ((GlobalSearchScope)request.searchScope).contains(file1)) {
                    MultiMap<VirtualFile, RequestWithProcessor> result1 =
                      intersectionWithContainerNameFiles == null || !intersectionWithContainerNameFiles.contains(file1) ? restResult : intersectionResult;
                    result1.putValue(file1, single);
                  }
                }
                return true;
              }
            }, commonScope));
        }
      }
    }
  }

  @Nullable("null means we did not find common container files")
  private Set<VirtualFile> intersectionWithContainerNameFiles(@NotNull GlobalSearchScope commonScope,
                                                              @NotNull Collection<RequestWithProcessor> data,
                                                              @NotNull Set<IdIndexEntry> keys) {
    String commonName = null;
    short searchContext = 0;
    boolean caseSensitive = true;
    for (RequestWithProcessor r : data) {
      String name = r.request.containerName;
      if (name != null) {
        if (commonName == null) {
          commonName = r.request.containerName;
          searchContext = r.request.searchContext;
          caseSensitive = r.request.caseSensitive;
        }
        else if (commonName.equals(name)) {
          searchContext |= r.request.searchContext;
          caseSensitive &= r.request.caseSensitive;
        }
        else {
          return null;
        }
      }
    }
    if (commonName == null) return null;
    Set<VirtualFile> containerFiles = new THashSet<VirtualFile>();

    List<IdIndexEntry> entries = getWordEntries(commonName, caseSensitive);
    if (entries.isEmpty()) return null;
    entries.addAll(keys); // should find words from both text and container names

    final short finalSearchContext = searchContext;
    Condition<Integer> contextMatches = context -> (context.intValue() & finalSearchContext) != 0;
    Processor<VirtualFile> processor = Processors.cancelableCollectProcessor(containerFiles);
    processFilesContainingAllKeys(myManager.getProject(), commonScope, contextMatches, entries, processor);

    return containerFiles;
  }

  @NotNull
  private static MultiMap<VirtualFile, RequestWithProcessor> createMultiMap() {
    // usually there is just one request
    return MultiMap.createSmart();
  }

  @NotNull
  private static GlobalSearchScope uniteScopes(@NotNull Collection<RequestWithProcessor> requests) {
    Set<GlobalSearchScope> scopes = ContainerUtil.map2LinkedSet(requests, r -> (GlobalSearchScope)r.request.searchScope);
    return GlobalSearchScope.union(scopes.toArray(new GlobalSearchScope[scopes.size()]));
  }

  private static void distributePrimitives(@NotNull Map<SearchRequestCollector, Processor<PsiReference>> collectors,
                                           @NotNull Set<RequestWithProcessor> locals,
                                           @NotNull MultiMap<Set<IdIndexEntry>, RequestWithProcessor> singles,
                                           @NotNull List<Computable<Boolean>> customs,
                                           @NotNull Map<RequestWithProcessor, Processor<PsiElement>> localProcessors,
                                           @NotNull ProgressIndicator progress) {
    for (final Map.Entry<SearchRequestCollector, Processor<PsiReference>> entry : collectors.entrySet()) {
      final Processor<PsiReference> processor = entry.getValue();
      SearchRequestCollector collector = entry.getKey();
      for (final PsiSearchRequest primitive : collector.takeSearchRequests()) {
        final SearchScope scope = primitive.searchScope;
        if (scope instanceof LocalSearchScope) {
          registerRequest(locals, primitive, processor);
        }
        else {
          Set<IdIndexEntry> key = new HashSet<IdIndexEntry>(getWordEntries(primitive.word, primitive.caseSensitive));
          registerRequest(singles.getModifiable(key), primitive, processor);
        }
      }
      for (final Processor<Processor<PsiReference>> customAction : collector.takeCustomSearchActions()) {
        customs.add(() -> customAction.process(processor));
      }
    }

    for (Map.Entry<Set<IdIndexEntry>, Collection<RequestWithProcessor>> entry : singles.entrySet()) {
      for (RequestWithProcessor singleRequest : entry.getValue()) {
        PsiSearchRequest primitive = singleRequest.request;
        StringSearcher searcher = new StringSearcher(primitive.word, primitive.caseSensitive, true, false);
        final TextOccurenceProcessor adapted = adaptProcessor(primitive, singleRequest.refProcessor);

        Processor<PsiElement> localProcessor = localProcessor(adapted, progress, true, searcher);

        assert !localProcessors.containsKey(singleRequest) || localProcessors.get(singleRequest) == localProcessor;
        localProcessors.put(singleRequest, localProcessor);
      }
    }
  }

  private static void registerRequest(@NotNull Collection<RequestWithProcessor> collection,
                                      @NotNull PsiSearchRequest primitive,
                                      @NotNull Processor<PsiReference> processor) {
    RequestWithProcessor singleRequest = new RequestWithProcessor(primitive, processor);

    for (RequestWithProcessor existing : collection) {
      if (existing.uniteWith(singleRequest)) {
        return;
      }
    }
    collection.add(singleRequest);
  }

  private boolean processSingleRequest(@NotNull PsiSearchRequest single, @NotNull Processor<PsiReference> consumer) {
    final EnumSet<Options> options = EnumSet.of(Options.PROCESS_ONLY_JAVA_IDENTIFIERS_IF_POSSIBLE);
    if (single.caseSensitive) options.add(Options.CASE_SENSITIVE_SEARCH);
    if (shouldProcessInjectedPsi(single.searchScope)) options.add(Options.PROCESS_INJECTED_PSI);
    
    return processElementsWithWord(adaptProcessor(single, consumer), single.searchScope, single.word, single.searchContext, options, 
                                   single.containerName);
  }

  @NotNull
  @Override
  public SearchCostResult isCheapEnoughToSearch(@NotNull String name,
                                                @NotNull final GlobalSearchScope scope,
                                                @Nullable final PsiFile fileToIgnoreOccurrencesIn,
                                                @Nullable final ProgressIndicator progress) {
    final AtomicInteger count = new AtomicInteger();
    final ProgressIndicator indicator = progress == null ? new EmptyProgressIndicator() : progress;
    final Processor<VirtualFile> processor = new Processor<VirtualFile>() {
      private final VirtualFile virtualFileToIgnoreOccurrencesIn =
        fileToIgnoreOccurrencesIn == null ? null : fileToIgnoreOccurrencesIn.getVirtualFile();

      @Override
      public boolean process(VirtualFile file) {
        indicator.checkCanceled();
        if (Comparing.equal(file, virtualFileToIgnoreOccurrencesIn)) return true;
        final int value = count.incrementAndGet();
        return value < 10;
      }
    };
    List<IdIndexEntry> keys = getWordEntries(name, true);
    boolean cheap = keys.isEmpty() || processFilesContainingAllKeys(myManager.getProject(), scope, null, keys, processor);

    if (!cheap) {
      return SearchCostResult.TOO_MANY_OCCURRENCES;
    }

    return count.get() == 0 ? SearchCostResult.ZERO_OCCURRENCES : SearchCostResult.FEW_OCCURRENCES;
  }

  private static boolean processFilesContainingAllKeys(@NotNull Project project,
                                                       @NotNull final GlobalSearchScope scope,
                                                       @Nullable final Condition<Integer> checker,
                                                       @NotNull final Collection<IdIndexEntry> keys,
                                                       @NotNull final Processor<VirtualFile> processor) {
    final FileIndexFacade index = FileIndexFacade.getInstance(project);
    return DumbService.getInstance(project).runReadActionInSmartMode(
      () -> FileBasedIndex.getInstance().processFilesContainingAllKeys(IdIndex.NAME, keys, scope, checker,
                                                                        file -> !index.shouldBeFound(scope, file) || processor.process(file)));
  }

  @NotNull
  private static List<IdIndexEntry> getWordEntries(@NotNull String name, final boolean caseSensitively) {
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
                                               @NotNull final Processor<UsageInfo> processor,
                                               @NotNull final UsageInfoFactory factory) {
    PsiSearchHelper helper = ApplicationManager.getApplication().runReadAction(new Computable<PsiSearchHelper>() {
      @Override
      public PsiSearchHelper compute() {
        return SERVICE.getInstance(element.getProject());
      }
    });

    return helper.processUsagesInNonJavaFiles(element, stringToSearch, new PsiNonJavaFileReferenceProcessor() {
      @Override
      public boolean process(final PsiFile psiFile, final int startOffset, final int endOffset) {
        try {
          UsageInfo usageInfo = ApplicationManager.getApplication().runReadAction(new Computable<UsageInfo>() {
            @Override
            public UsageInfo compute() {
              return factory.createUsageInfo(psiFile, startOffset, endOffset);
            }
          });
          return usageInfo == null || processor.process(usageInfo);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.error(e);
          return true;
        }
      }
    }, searchScope);
  }
}
