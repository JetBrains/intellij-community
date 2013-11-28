/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.concurrency.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.util.TooManyUsagesStatus;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.search.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.codeInsight.CommentUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.StringSearcher;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PsiSearchHelperImpl implements PsiSearchHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.PsiSearchHelperImpl");
  private final PsiManagerEx myManager;

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
    return scope;
  }

  public PsiSearchHelperImpl(@NotNull PsiManagerEx manager) {
    myManager = manager;
  }

  @Override
  @NotNull
  public PsiElement[] findCommentsContainingIdentifier(@NotNull String identifier, @NotNull SearchScope searchScope) {
    final ArrayList<PsiElement> results = new ArrayList<PsiElement>();
    processCommentsContainingIdentifier(identifier, searchScope, new Processor<PsiElement>() {
      @Override
      public boolean process(PsiElement element) {
        synchronized (results) {
          results.add(element);
        }
        return true;
      }
    });
    synchronized (results) {
      return PsiUtilCore.toPsiElementArray(results);
    }
  }

  @Override
  public boolean processCommentsContainingIdentifier(@NotNull String identifier,
                                                     @NotNull SearchScope searchScope,
                                                     @NotNull final Processor<PsiElement> processor) {
    TextOccurenceProcessor occurrenceProcessor = new TextOccurenceProcessor() {
      @Override
      public boolean execute(PsiElement element, int offsetInElement) {
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
    final AsyncFuture<Boolean> result =
      processElementsWithWordAsync(processor, searchScope, text, searchContext, caseSensitive, processInjectedPsi, null);
    return AsyncUtil.get(result);
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> processElementsWithWordAsync(@NotNull final TextOccurenceProcessor processor,
                                                           @NotNull SearchScope searchScope,
                                                           @NotNull final String text,
                                                           final short searchContext,
                                                           final boolean caseSensitively) {
    return processElementsWithWordAsync(processor, searchScope, text, searchContext, caseSensitively, shouldProcessInjectedPsi(searchScope), null);
  }

  @NotNull
  private AsyncFuture<Boolean> processElementsWithWordAsync(@NotNull final TextOccurenceProcessor processor,
                                                            @NotNull SearchScope searchScope,
                                                            @NotNull final String text,
                                                            final short searchContext,
                                                            final boolean caseSensitively,
                                                            boolean processInjectedPsi,
                                                            @Nullable String containerName) {
    if (text.isEmpty()) {
      return AsyncFutureFactory.wrapException(new IllegalArgumentException("Cannot search for elements with empty text"));
    }
    final ProgressIndicator progress = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (searchScope instanceof GlobalSearchScope) {
      StringSearcher searcher = new StringSearcher(text, caseSensitively, true, searchContext == UsageSearchContext.IN_STRINGS);

      return processElementsWithTextInGlobalScopeAsync(processor,
                                                       (GlobalSearchScope)searchScope,
                                                       searcher,
                                                       searchContext, caseSensitively, containerName, progress, processInjectedPsi);
    }
    LocalSearchScope scope = (LocalSearchScope)searchScope;
    PsiElement[] scopeElements = scope.getScope();
    final StringSearcher searcher = new StringSearcher(text, caseSensitively, true, searchContext == UsageSearchContext.IN_STRINGS);
    Processor<PsiElement> localProcessor = localProcessor(processor, progress, processInjectedPsi, searcher);
    return wrapInFuture(Arrays.asList(scopeElements), progress, localProcessor);
  }

  private static <T> AsyncFuture<Boolean> wrapInFuture(@NotNull List<T> files, final ProgressIndicator progress, @NotNull Processor<T> processor) {
    AsyncFutureResult<Boolean> asyncFutureResult = AsyncFutureFactory.getInstance().createAsyncFutureResult();
    try {
      boolean result = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(files, progress, true, true, processor);
      asyncFutureResult.set(result);
    }
    catch (Throwable t) {
      asyncFutureResult.setException(t);
    }
    return asyncFutureResult;
  }

  private static boolean shouldProcessInjectedPsi(SearchScope scope) {
    return scope instanceof LocalSearchScope ? !((LocalSearchScope)scope).isIgnoreInjectedPsi() : true;
  }

  @NotNull
  private static Processor<PsiElement> localProcessor(@NotNull final TextOccurenceProcessor processor,
                                                      final ProgressIndicator progress,
                                                      final boolean processInjectedPsi,
                                                      @NotNull final StringSearcher searcher) {
    return new Processor<PsiElement>() {
        @Override
        public boolean process(final PsiElement scopeElement) {
          return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
            @Override
            public Boolean compute() {
              return LowLevelSearchUtil.processElementsContainingWordInElement(processor, scopeElement, searcher, processInjectedPsi, progress);
            }
          }).booleanValue();
        }

      @Override
      public String toString() {
        return processor.toString();
      }
    };
  }

  @NotNull
  private AsyncFuture<Boolean> processElementsWithTextInGlobalScopeAsync(@NotNull final TextOccurenceProcessor processor,
                                                                         @NotNull final GlobalSearchScope scope,
                                                                         @NotNull final StringSearcher searcher,
                                                                         final short searchContext,
                                                                         final boolean caseSensitively,
                                                                         String containerName,
                                                                         final ProgressIndicator progress, final boolean processInjectedPsi) {
    if (Thread.holdsLock(PsiLock.LOCK)) {
      throw new AssertionError("You must not run search from within updating PSI activity. Please consider invokeLatering it instead.");
    }
    if (progress != null) {
      progress.pushState();
      progress.setText(PsiBundle.message("psi.scanning.files.progress"));
    }

    String text = searcher.getPattern();
    Set<VirtualFile> fileSet = new THashSet<VirtualFile>();
    getFilesWithText(scope, searchContext, caseSensitively, text, progress, fileSet);

    if (progress != null) {
      progress.setText(PsiBundle.message("psi.search.for.word.progress", text));
    }

    final Processor<PsiElement> localProcessor = localProcessor(processor, progress, processInjectedPsi, searcher);
    if (containerName != null) {
      List<VirtualFile> intersectionWithContainerFiles = new ArrayList<VirtualFile>();
      // intersectionWithContainerFiles holds files containing words from both `text` and `containerName`
      getFilesWithText(scope, searchContext, caseSensitively, text+" "+containerName, progress, intersectionWithContainerFiles);
      if (!intersectionWithContainerFiles.isEmpty()) {
        int totalSize = fileSet.size();
        AsyncFuture<Boolean> intersectionResult = processPsiFileRootsAsync(intersectionWithContainerFiles, totalSize, 0, progress, localProcessor);
        AsyncFuture<Boolean> result;
        try {
          if (intersectionResult.get()) {
            fileSet.removeAll(intersectionWithContainerFiles);
            if (fileSet.isEmpty()) {
              result = intersectionResult;
            }
            else {
              AsyncFuture<Boolean> restResult =
                processPsiFileRootsAsync(new ArrayList<VirtualFile>(fileSet), totalSize, intersectionWithContainerFiles.size(), progress, localProcessor);
              result = bind(intersectionResult, restResult);
            }
          }
          else {
            result = intersectionResult;
          }
        }
        catch (ExecutionException e) {
          Throwable cause = e.getCause();
          if (cause instanceof RuntimeException) throw (RuntimeException)cause;
          if (cause instanceof Error) throw (Error)cause;
          result = AsyncFutureFactory.wrapException(cause);
        }
        catch (InterruptedException e) {
          result = AsyncFutureFactory.wrapException(e);
        }
        return popStateAfter(result, progress);
      }
    }

    AsyncFuture<Boolean> result = fileSet.isEmpty()
                                  ? AsyncFutureFactory.wrap(Boolean.TRUE)
                                  : processPsiFileRootsAsync(new ArrayList<VirtualFile>(fileSet), fileSet.size(), 0, progress, localProcessor);
    return popStateAfter(result, progress);
  }

  @NotNull
  private static FinallyFuture<Boolean> popStateAfter(@NotNull AsyncFuture<Boolean> result, final ProgressIndicator progress) {
    return new FinallyFuture<Boolean>(result, new Runnable() {
      @Override
      public void run() {
        if (progress != null) {
          progress.popState();
        }
      }
    });
  }

  @NotNull
  private static AsyncFuture<Boolean> bind(@NotNull AsyncFuture<Boolean> first, @NotNull final AsyncFuture<Boolean> second) {
    final AsyncFutureResult<Boolean> totalResult = AsyncFutureFactory.getInstance().createAsyncFutureResult();
    first.addConsumer(SameThreadExecutor.INSTANCE, new ResultConsumer<Boolean>() {
      @Override
      public void onSuccess(Boolean value) {
        second.addConsumer(SameThreadExecutor.INSTANCE, new DefaultResultConsumer<Boolean>(totalResult));
      }

      @Override
      public void onFailure(Throwable t) {
        totalResult.setException(t);
      }
    });
    return totalResult;
  }

  private static class CannotRunReadActionException extends RuntimeException{
    @Override
    public Throwable fillInStackTrace() {
      return this;
    }
  }
  // throws exception if can't grab read action right now
  private static <T> T tryRead(final Computable<T> computable) throws CannotRunReadActionException {
    final Ref<T> result = new Ref<T>();
    if (((ApplicationEx)ApplicationManager.getApplication()).tryRunReadAction(new Runnable() {
      @Override
      public void run() {
        result.set(computable.compute());
      }
    })) {
      return result.get();
    }
    throw new CannotRunReadActionException();
  }

  /**
   * @param files to scan for references in this pass.
   * @param totalSize the number of files to scan in both passes. Can be different from <code>files.size()</code> in case of
   *                  two-pass scan, where we first scan files containing container name and then all the rest files.
   * @param alreadyProcessedFiles the number of files scanned in previous pass.
   */
  @NotNull
  private AsyncFuture<Boolean> processPsiFileRootsAsync(@NotNull List<VirtualFile> files,
                                                        final int totalSize,
                                                        int alreadyProcessedFiles,
                                                        final ProgressIndicator progress,
                                                        @NotNull final Processor<? super PsiFile> localProcessor) {
    myManager.startBatchFilesProcessingMode();
    final AtomicInteger counter = new AtomicInteger(alreadyProcessedFiles);
    final AtomicBoolean canceled = new AtomicBoolean(false);

    AsyncFutureResult<Boolean> asyncFutureResult = AsyncFutureFactory.getInstance().createAsyncFutureResult();
    final List<VirtualFile> failedFiles = Collections.synchronizedList(new SmartList<VirtualFile>());
    try {
      boolean completed =
        JobLauncher.getInstance().invokeConcurrentlyUnderProgress(files, progress, false, false, new Processor<VirtualFile>() {
          @Override
          public boolean process(final VirtualFile vfile) {
            try {
              TooManyUsagesStatus.getFrom(progress).pauseProcessingIfTooManyUsages();
              processVirtualFile(vfile, progress, localProcessor, canceled, counter, totalSize);
            }
            catch (CannotRunReadActionException action) {
              failedFiles.add(vfile);
            }
            return !canceled.get();
          }
        });
      if (!failedFiles.isEmpty()) {
        for (final VirtualFile vfile : failedFiles) {
          TooManyUsagesStatus.getFrom(progress).pauseProcessingIfTooManyUsages();
          // we failed to run read action in job launcher thread
          // run read action in our thread instead
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
              processVirtualFile(vfile, progress, localProcessor, canceled, counter, totalSize);
            }
          });
        }
      }
      asyncFutureResult.set(completed);
      myManager.finishBatchFilesProcessingMode();
    }
    catch (Throwable t) {
      asyncFutureResult.setException(t);
    }

    return asyncFutureResult;
  }

  private void processVirtualFile(@NotNull final VirtualFile vfile,
                                  final ProgressIndicator progress,
                                  @NotNull final Processor<? super PsiFile> localProcessor,
                                  @NotNull final AtomicBoolean canceled,
                                  @NotNull AtomicInteger counter,
                                  int totalSize) {
    final PsiFile file = tryRead(new Computable<PsiFile>() {
      @Override
      public PsiFile compute() {
        return vfile.isValid() ? myManager.findFile(vfile) : null;
      }
    });
    if (file != null && !(file instanceof PsiBinaryFile)) {
      // load contents outside read action
      if (FileDocumentManager.getInstance().getCachedDocument(vfile) == null) {
        LoadTextUtil.loadText(vfile); // cache bytes in vfs
      }
      tryRead(new Computable<Void>() {
        @Override
        public Void compute() {
          if (myManager.getProject().isDisposed()) throw new ProcessCanceledException();
          List<PsiFile> psiRoots = file.getViewProvider().getAllFiles();
          Set<PsiElement> processed = new THashSet<PsiElement>(psiRoots.size() * 2, (float)0.5);
          for (final PsiFile psiRoot : psiRoots) {
            checkCanceled(progress);
            assert psiRoot != null : "One of the roots of file " + file + " is null. All roots: " + psiRoots +
                                     "; ViewProvider: " + file.getViewProvider() + "; Virtual file: " + file.getViewProvider().getVirtualFile();
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
    if (progress != null && progress.isRunning()) {
      double fraction = (double)counter.incrementAndGet() / totalSize;
      progress.setFraction(fraction);
    }
  }

  private static void checkCanceled(ProgressIndicator progress) {
    if (progress != null) progress.checkCanceled();
  }

  private void getFilesWithText(@NotNull GlobalSearchScope scope,
                                final short searchContext,
                                final boolean caseSensitively,
                                @NotNull String text,
                                final ProgressIndicator progress,
                                @NotNull Collection<VirtualFile> result) {
    myManager.startBatchFilesProcessingMode();
    try {
      Processor<VirtualFile> processor = new CommonProcessors.CollectProcessor<VirtualFile>(result){
        @Override
        public boolean process(VirtualFile file) {
          checkCanceled(progress);
          return super.process(file);
        }
      };
      boolean success = processFilesWithText(scope, searchContext, caseSensitively, text, processor);
      LOG.assertTrue(success);
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

    Condition<Integer> contextMatches = new Condition<Integer>() {
      @Override
      public boolean value(Integer integer) {
        return (integer.intValue() & searchContext) != 0;
      }
    };
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
    final ProgressIndicator progress = ProgressIndicatorProvider.getGlobalProgressIndicator();

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

    if (progress != null) {
      progress.pushState();
      progress.setText(PsiBundle.message("psi.search.in.non.java.files.progress"));
    }

    final SearchScope useScope = new ReadAction<SearchScope>() {
      @Override
      protected void run(final Result<SearchScope> result) {
        if (originalElement != null) {
          result.setResult(getUseScope(originalElement));
        }
      }
    }.execute().getResultObject();

    final Ref<Boolean> cancelled = new Ref<Boolean>(Boolean.FALSE);
    for (int i = 0; i < files.length; i++) {
      checkCanceled(progress);
      final PsiFile psiFile = files[i];
      if (psiFile instanceof PsiBinaryFile) continue;

      final CharSequence text = ApplicationManager.getApplication().runReadAction(new Computable<CharSequence>() {
        @Override
        public CharSequence compute() {
          return psiFile.getViewProvider().getContents();
        }
      });
      final char[] textArray = ApplicationManager.getApplication().runReadAction(new Computable<char[]>() {
        @Override
        public char[] compute() {
          return CharArrayUtil.fromSequenceWithoutCopying(text);
        }
      });
      for (int index = LowLevelSearchUtil.searchWord(text, textArray, 0, text.length(), searcher, progress); index >= 0;) {
        final int finalIndex = index;
        boolean isReferenceOK = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
          @Override
          public Boolean compute() {
            PsiReference referenceAt = psiFile.findReferenceAt(finalIndex);
            return referenceAt == null || useScope == null ||
                   !PsiSearchScopeUtil.isInScope(useScope.intersectWith(initialScope), psiFile);
          }
        });
        if (isReferenceOK && !processor.process(psiFile, index, index + patternLength)) {
          cancelled.set(Boolean.TRUE);
          break;
        }

        index = LowLevelSearchUtil.searchWord(text, textArray, index + patternLength, text.length(), searcher, progress);
      }
      if (cancelled.get()) break;
      if (progress != null) {
        progress.setFraction((double)(i + 1) / files.length);
      }
    }

    if (progress != null) {
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
    @NotNull final PsiSearchRequest request;
    @NotNull Processor<PsiReference> refProcessor;

    private RequestWithProcessor(@NotNull PsiSearchRequest first, @NotNull Processor<PsiReference> second) {
      request = first;
      refProcessor = second;
    }

    boolean uniteWith(@NotNull final RequestWithProcessor another) {
      if (request.equals(another.request)) {
        final Processor<PsiReference> myProcessor = refProcessor;
        if (myProcessor != another.refProcessor) {
          refProcessor = new Processor<PsiReference>() {
            @Override
            public boolean process(PsiReference psiReference) {
              return myProcessor.process(psiReference) && another.refProcessor.process(psiReference);
            }
          };
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
  public boolean processRequests(@NotNull SearchRequestCollector request, @NotNull Processor<PsiReference> processor) {
    return AsyncUtil.get(processRequestsAsync(request, processor));
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> processRequestsAsync(@NotNull SearchRequestCollector collector, @NotNull Processor<PsiReference> processor) {
    final Map<SearchRequestCollector, Processor<PsiReference>> collectors = ContainerUtil.newHashMap();
    collectors.put(collector, processor);

    appendCollectorsFromQueryRequests(collectors);

    final ProgressIndicator progress = ProgressIndicatorProvider.getGlobalProgressIndicator();
    final DoWhile doWhile = new DoWhile() {
      @NotNull
      @Override
      protected AsyncFuture<Boolean> body() {
        final AsyncFutureResult<Boolean> result = AsyncFutureFactory.getInstance().createAsyncFutureResult();
        MultiMap<Set<IdIndexEntry>, RequestWithProcessor> globals = new MultiMap<Set<IdIndexEntry>, RequestWithProcessor>();
        final List<Computable<Boolean>> customs = ContainerUtil.newArrayList();
        final Set<RequestWithProcessor> locals = ContainerUtil.newLinkedHashSet();
        Map<RequestWithProcessor, Processor<PsiElement>> localProcessors = new THashMap<RequestWithProcessor, Processor<PsiElement>>();
        distributePrimitives(collectors, locals, globals, customs, localProcessors, progress);
        AsyncFuture<Boolean> future = processGlobalRequestsOptimizedAsync(globals, progress, localProcessors);
        future.addConsumer(SameThreadExecutor.INSTANCE, new DefaultResultConsumer<Boolean>(result) {
          @Override
          public void onSuccess(Boolean value) {
            if (!value.booleanValue()) {
              result.set(value);
            }
            else {
              final Iterate<RequestWithProcessor> iterate = new Iterate<RequestWithProcessor>(locals) {
                @NotNull
                @Override
                protected AsyncFuture<Boolean> process(RequestWithProcessor local) {
                  return processSingleRequestAsync(local.request, local.refProcessor);
                }
              };

              iterate.getResult()
                .addConsumer(SameThreadExecutor.INSTANCE, new DefaultResultConsumer<Boolean>(result) {
                  @Override
                  public void onSuccess(Boolean value) {
                    if (!value.booleanValue()) {
                      result.set(false);
                      return;
                    }
                    for (Computable<Boolean> custom : customs) {
                      if (!custom.compute()) {
                        result.set(false);
                        return;
                      }
                    }
                    result.set(true);
                  }
                });
            }
          }
        });
        return result;
      }

      @Override
      protected boolean condition() {
        return appendCollectorsFromQueryRequests(collectors);
      }
    };

    return doWhile.getResult();
  }

  private static boolean appendCollectorsFromQueryRequests(@NotNull Map<SearchRequestCollector, Processor<PsiReference>> collectors) {
    boolean changed = false;
    LinkedList<SearchRequestCollector> queue = new LinkedList<SearchRequestCollector>(collectors.keySet());
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

  @NotNull
  private AsyncFuture<Boolean> processGlobalRequestsOptimizedAsync(@NotNull MultiMap<Set<IdIndexEntry>, RequestWithProcessor> singles,
                                                                   final ProgressIndicator progress,
                                                                   @NotNull final Map<RequestWithProcessor, Processor<PsiElement>> localProcessors) {
    if (singles.isEmpty()) {
      return AsyncFutureFactory.wrap(true);
    }

    if (singles.size() == 1) {
      final Collection<? extends RequestWithProcessor> requests = singles.values();
      if (requests.size() == 1) {
        final RequestWithProcessor theOnly = requests.iterator().next();
        return processSingleRequestAsync(theOnly.request, theOnly.refProcessor);
      }
    }

    if (progress != null) {
      progress.pushState();
      progress.setText(PsiBundle.message("psi.scanning.files.progress"));
    }

    // intersectionCandidateFiles holds files containing words from all requests in `singles` and words in corresponding container names
    final MultiMap<VirtualFile, RequestWithProcessor> intersectionCandidateFiles = createMultiMap();
    // restCandidateFiles holds files containing words from all requests in `singles` but EXCLUDING words in corresponding container names
    final MultiMap<VirtualFile, RequestWithProcessor> restCandidateFiles = createMultiMap();
    collectFiles(singles, progress, intersectionCandidateFiles, restCandidateFiles);

    if (intersectionCandidateFiles.isEmpty() && restCandidateFiles.isEmpty()) {
      return AsyncFutureFactory.wrap(true);
    }

    if (progress != null) {
      final Set<String> allWords = new TreeSet<String>();
      for (RequestWithProcessor singleRequest : localProcessors.keySet()) {
        allWords.add(singleRequest.request.word);
      }
      progress.setText(PsiBundle.message("psi.search.for.word.progress", getPresentableWordsDescription(allWords)));
    }

    AsyncFuture<Boolean> result;
    if (intersectionCandidateFiles.isEmpty()) {
      result = processCandidatesAsync(progress, localProcessors, restCandidateFiles, restCandidateFiles.size(), 0);
    }
    else {
      int totalSize = restCandidateFiles.size() + intersectionCandidateFiles.size();
      AsyncFuture<Boolean> intersectionResult = processCandidatesAsync(progress, localProcessors, intersectionCandidateFiles, totalSize, 0);
      try {
        if (intersectionResult.get()) {
          AsyncFuture<Boolean> restResult = processCandidatesAsync(progress, localProcessors, restCandidateFiles, totalSize, intersectionCandidateFiles.size());
          result = bind(intersectionResult, restResult);
        }
        else {
          result = intersectionResult;
        }
      }
      catch (ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) throw (RuntimeException)cause;
        if (cause instanceof Error) throw (Error)cause;
        result = AsyncFutureFactory.wrapException(cause);
      }
      catch (InterruptedException e) {
        result = AsyncFutureFactory.wrapException(e);
      }
    }

    return popStateAfter(result, progress);
  }

  @NotNull
  private AsyncFuture<Boolean> processCandidatesAsync(final ProgressIndicator progress,
                                                      @NotNull final Map<RequestWithProcessor, Processor<PsiElement>> localProcessors,
                                                      @NotNull final MultiMap<VirtualFile, RequestWithProcessor> candidateFiles,
                                                      int totalSize,
                                                      int alreadyProcessedFiles) {
    List<VirtualFile> files = new ArrayList<VirtualFile>(candidateFiles.keySet());

    return processPsiFileRootsAsync(files, totalSize, alreadyProcessedFiles, progress, new Processor<PsiFile>() {
      @Override
      public boolean process(final PsiFile psiRoot) {
        return tryRead(new Computable<Boolean>() {
          @Override
          public Boolean compute() {
            final VirtualFile vfile = psiRoot.getVirtualFile();
            for (final RequestWithProcessor singleRequest : candidateFiles.get(vfile)) {
              Processor<PsiElement> localProcessor = localProcessors.get(singleRequest);
              if (!localProcessor.process(psiRoot)) {
                return false;
              }
            }
            return true;
          }
        });
      }
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
      public boolean execute(PsiElement element, int offsetInElement) {
        if (ignoreInjectedPsi && element instanceof PsiLanguageInjectionHost) return true;

        return wrapped.processTextOccurrence(element, offsetInElement, consumer);
      }

      @Override
      public String toString() {
        return consumer.toString();
      }
    };
  }

  private void collectFiles(@NotNull MultiMap<Set<IdIndexEntry>, RequestWithProcessor> singles,
                            ProgressIndicator progress,
                            @NotNull final MultiMap<VirtualFile, RequestWithProcessor> intersectionResult,
                            @NotNull final MultiMap<VirtualFile, RequestWithProcessor> restResult) {
    for (final Set<IdIndexEntry> keys : singles.keySet()) {
      if (keys.isEmpty()) {
        continue;
      }

      final Collection<RequestWithProcessor> data = singles.get(keys);
      final GlobalSearchScope commonScope = uniteScopes(data);
      final Set<VirtualFile> intersectionWithContainerNameFiles = intersectionWithContainerNameFiles(commonScope, data, keys);

      List<VirtualFile> files = new ArrayList<VirtualFile>();
      CommonProcessors.CollectProcessor<VirtualFile> processor = new CommonProcessors.CollectProcessor<VirtualFile>(files);
      processFilesContainingAllKeys(myManager.getProject(), commonScope, null, keys, processor);
      for (final VirtualFile file : files) {
        checkCanceled(progress);
        for (final IdIndexEntry entry : keys) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
              FileBasedIndex.getInstance().processValues(IdIndex.NAME, entry, file, new FileBasedIndex.ValueProcessor<Integer>() {
                @Override
                public boolean process(VirtualFile file, Integer value) {
                  int mask = value.intValue();
                  for (RequestWithProcessor single : data) {
                    final PsiSearchRequest request = single.request;
                    if ((mask & request.searchContext) != 0 && ((GlobalSearchScope)request.searchScope).contains(file)) {
                      MultiMap<VirtualFile, RequestWithProcessor> result =
                        intersectionWithContainerNameFiles == null || !intersectionWithContainerNameFiles.contains(file) ? restResult : intersectionResult;
                      result.putValue(file, single);
                    }
                  }
                  return true;
                }
              }, commonScope);
            }
          });
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
    Condition<Integer> contextMatches = new Condition<Integer>() {
      @Override
      public boolean value(Integer context) {
        return (context.intValue() & finalSearchContext) != 0;
      }
    };
    processFilesContainingAllKeys(myManager.getProject(), commonScope, contextMatches, entries, new CommonProcessors.CollectProcessor<VirtualFile>(containerFiles));

    return containerFiles;
  }

  @NotNull
  private static MultiMap<VirtualFile, RequestWithProcessor> createMultiMap() {
    // usually there is just one request
    return MultiMap.createSmartList();
  }

  @NotNull
  private static GlobalSearchScope uniteScopes(@NotNull Collection<RequestWithProcessor> requests) {
    GlobalSearchScope commonScope = null;
    for (RequestWithProcessor r : requests) {
      final GlobalSearchScope scope = (GlobalSearchScope)r.request.searchScope;
      commonScope = commonScope == null ? scope : commonScope.uniteWith(scope);
    }
    assert commonScope != null;
    return commonScope;
  }

  private static void distributePrimitives(@NotNull Map<SearchRequestCollector, Processor<PsiReference>> collectors,
                                           @NotNull Set<RequestWithProcessor> locals,
                                           @NotNull MultiMap<Set<IdIndexEntry>, RequestWithProcessor> singles,
                                           @NotNull List<Computable<Boolean>> customs,
                                           @NotNull Map<RequestWithProcessor, Processor<PsiElement>> localProcessors,
                                           ProgressIndicator progress) {
    for (final Map.Entry<SearchRequestCollector, Processor<PsiReference>> entry : collectors.entrySet()) {
      final Processor<PsiReference> processor = entry.getValue();
      SearchRequestCollector collector = entry.getKey();
      for (final PsiSearchRequest primitive : collector.takeSearchRequests()) {
        final SearchScope scope = primitive.searchScope;
        if (scope instanceof LocalSearchScope) {
          registerRequest(locals, primitive, processor);
        }
        else {
          final List<String> words = StringUtil.getWordsInStringLongestFirst(primitive.word);
          final Set<IdIndexEntry> key = new HashSet<IdIndexEntry>(words.size() * 2);
          for (String word : words) {
            key.add(new IdIndexEntry(word, primitive.caseSensitive));
          }
          registerRequest(singles.getModifiable(key), primitive, processor);
        }
      }
      for (final Processor<Processor<PsiReference>> customAction : collector.takeCustomSearchActions()) {
        customs.add(new Computable<Boolean>() {
          @Override
          public Boolean compute() {
            return customAction.process(processor);
          }
        });
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

  @NotNull
  private AsyncFuture<Boolean> processSingleRequestAsync(@NotNull PsiSearchRequest single, @NotNull Processor<PsiReference> consumer) {
    return processElementsWithWordAsync(adaptProcessor(single, consumer), single.searchScope, single.word, single.searchContext,
                                        single.caseSensitive, shouldProcessInjectedPsi(single.searchScope), single.containerName);
  }

  @NotNull
  @Override
  public SearchCostResult isCheapEnoughToSearch(@NotNull String name,
                                                @NotNull final GlobalSearchScope scope,
                                                @Nullable final PsiFile fileToIgnoreOccurrencesIn,
                                                @Nullable final ProgressIndicator progress) {
    final AtomicInteger count = new AtomicInteger();
    final Processor<VirtualFile> processor = new Processor<VirtualFile>() {
      private final VirtualFile virtualFileToIgnoreOccurrencesIn =
        fileToIgnoreOccurrencesIn == null ? null : fileToIgnoreOccurrencesIn.getVirtualFile();

      @Override
      public boolean process(VirtualFile file) {
        checkCanceled(progress);
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
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return FileBasedIndex.getInstance().processFilesContainingAllKeys(IdIndex.NAME, keys, scope, checker, new Processor<VirtualFile>() {
          @Override
          public boolean process(VirtualFile file) {
            return !index.shouldBeFound(scope, file) || processor.process(file);
          }
        });
      }
    });
  }

  @NotNull
  private static List<IdIndexEntry> getWordEntries(@NotNull String name, boolean caseSensitively) {
    List<String> words = StringUtil.getWordsInStringLongestFirst(name);
    if (words.isEmpty()) return Collections.emptyList();
    List<IdIndexEntry> keys = new ArrayList<IdIndexEntry>(words.size());
    for (String word : words) {
      keys.add(new IdIndexEntry(word, caseSensitively));
    }
    return keys;
  }
}
