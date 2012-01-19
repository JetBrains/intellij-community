/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.CommentUtil;
import com.intellij.concurrency.JobUtil;
import com.intellij.ide.todo.TodoIndexPatternProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.cache.impl.IndexCacheManagerImpl;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.IndexPatternSearch;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.text.StringSearcher;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PsiSearchHelperImpl implements PsiSearchHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.PsiSearchHelperImpl");

  private final PsiManagerEx myManager;
  private static final TodoItem[] EMPTY_TODO_ITEMS = new TodoItem[0];

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


  public PsiSearchHelperImpl(PsiManagerEx manager) {
    myManager = manager;
  }

  @Override
  @NotNull
  public PsiFile[] findFilesWithTodoItems() {
    return CacheManager.SERVICE.getInstance(myManager.getProject()).getFilesWithTodoItems();
  }

  @Override
  @NotNull
  public TodoItem[] findTodoItems(@NotNull PsiFile file) {
    return findTodoItems(file, 0, file.getTextLength());
  }

  @Override
  @NotNull
  public TodoItem[] findTodoItems(@NotNull PsiFile file, int startOffset, int endOffset) {
    final Collection<IndexPatternOccurrence> occurrences = IndexPatternSearch.search(file, TodoIndexPatternProvider.getInstance()).findAll();
    if (occurrences.isEmpty()) {
      return EMPTY_TODO_ITEMS;
    }

    return processTodoOccurences(startOffset, endOffset, occurrences);
  }

  private TodoItem[] processTodoOccurences(int startOffset, int endOffset, Collection<IndexPatternOccurrence> occurrences) {
    List<TodoItem> items = new ArrayList<TodoItem>(occurrences.size());
    TextRange textRange = new TextRange(startOffset, endOffset);
    final TodoItemsCreator todoItemsCreator = new TodoItemsCreator();
    for(IndexPatternOccurrence occurrence: occurrences) {
      TextRange occurrenceRange = occurrence.getTextRange();
      if (textRange.contains(occurrenceRange)) {
        items.add(todoItemsCreator.createTodo(occurrence));
      }
    }

    return items.toArray(new TodoItem[items.size()]);
  }

  @NotNull
  @Override
  public TodoItem[] findTodoItemsLight(@NotNull PsiFile file) {
    return findTodoItemsLight(file, 0, file.getTextLength());
  }

  @NotNull
  @Override
  public TodoItem[] findTodoItemsLight(@NotNull PsiFile file, int startOffset, int endOffset) {
    final Collection<IndexPatternOccurrence> occurrences =
      LightIndexPatternSearch.SEARCH.createQuery(new IndexPatternSearch.SearchParameters(file, TodoIndexPatternProvider.getInstance())).findAll();

    if (occurrences.isEmpty()) {
      return EMPTY_TODO_ITEMS;
    }

    return processTodoOccurences(startOffset, endOffset, occurrences);
  }

  @Override
  public int getTodoItemsCount(@NotNull PsiFile file) {
    int count = CacheManager.SERVICE.getInstance(myManager.getProject()).getTodoCount(file.getVirtualFile(), TodoIndexPatternProvider.getInstance());
    if (count != -1) return count;
    return findTodoItems(file).length;
  }

  @Override
  public int getTodoItemsCount(@NotNull PsiFile file, @NotNull TodoPattern pattern) {
    int count = CacheManager.SERVICE.getInstance(myManager.getProject()).getTodoCount(file.getVirtualFile(), pattern.getIndexPattern());
    if (count != -1) return count;
    TodoItem[] items = findTodoItems(file);
    count = 0;
    for (TodoItem item : items) {
      if (item.getPattern().equals(pattern)) count++;
    }
    return count;
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
      return PsiUtilBase.toPsiElementArray(results);
    }
  }

  @Override
  public boolean processCommentsContainingIdentifier(@NotNull String identifier,
                                                     @NotNull SearchScope searchScope,
                                                     @NotNull final Processor<PsiElement> processor) {
    TextOccurenceProcessor occurrenceProcessor = new TextOccurenceProcessor() {
      @Override
      public boolean execute(PsiElement element, int offsetInElement) {
        if (CommentUtil.isCommentTextElement(element)) {
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
  public boolean processElementsWithWord(@NotNull final TextOccurenceProcessor processor,
                                         @NotNull SearchScope searchScope,
                                         @NotNull final String text,
                                         short searchContext,
                                         final boolean caseSensitively) {
    if (text.length() == 0) {
      throw new IllegalArgumentException("Cannot search for elements with empty text");
    }
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (searchScope instanceof GlobalSearchScope) {
      StringSearcher searcher = new StringSearcher(text, caseSensitively, true);

      return processElementsWithTextInGlobalScope(processor,
                                                  (GlobalSearchScope)searchScope,
                                                  searcher,
                                                  searchContext, caseSensitively, progress);
    }
    else {
      LocalSearchScope scope = (LocalSearchScope)searchScope;
      PsiElement[] scopeElements = scope.getScope();
      final boolean ignoreInjectedPsi = scope.isIgnoreInjectedPsi();

      return JobUtil.invokeConcurrentlyUnderProgress(Arrays.asList(scopeElements), progress, false, new Processor<PsiElement>() {
        @Override
        public boolean process(PsiElement scopeElement) {
          return processElementsWithWordInScopeElement(scopeElement, processor, text, caseSensitively, ignoreInjectedPsi, progress);
        }
      });
    }
  }

  private static boolean processElementsWithWordInScopeElement(final PsiElement scopeElement,
                                                               final TextOccurenceProcessor processor,
                                                               final String word,
                                                               final boolean caseSensitive,
                                                               final boolean ignoreInjectedPsi,
                                                               final ProgressIndicator progress) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        StringSearcher searcher = new StringSearcher(word, caseSensitive, true);

        return LowLevelSearchUtil.processElementsContainingWordInElement(processor, scopeElement, searcher, !ignoreInjectedPsi, progress);
      }
    }).booleanValue();
  }

  private boolean processElementsWithTextInGlobalScope(@NotNull final TextOccurenceProcessor processor,
                                                       @NotNull final GlobalSearchScope scope,
                                                       @NotNull final StringSearcher searcher,
                                                       final short searchContext,
                                                       final boolean caseSensitively,
                                                       final ProgressIndicator progress) {
    LOG.assertTrue(!Thread.holdsLock(PsiLock.LOCK), "You must not run search from within updating PSI activity. Please consider invokeLatering it instead.");
    if (progress != null) {
      progress.pushState();
      progress.setText(PsiBundle.message("psi.scanning.files.progress"));
    }

    String text = searcher.getPattern();
    List<VirtualFile> fileSet = getFilesWithText(scope, searchContext, caseSensitively, text, progress);

    if (progress != null) {
      progress.setText(PsiBundle.message("psi.search.for.word.progress", text));
    }

    try {
      return processPsiFileRoots(fileSet, new Processor<PsiElement>() {
        @Override
        public boolean process(PsiElement psiRoot) {
          return LowLevelSearchUtil.processElementsContainingWordInElement(processor, psiRoot, searcher, true, progress);
        }
      }, progress);
    }
    finally {
      if (progress != null) {
        progress.popState();
      }
    }
  }

  private boolean processPsiFileRoots(@NotNull List<VirtualFile> files,
                                      @NotNull final Processor<PsiElement> psiRootProcessor,
                                      final ProgressIndicator progress) {
    myManager.startBatchFilesProcessingMode();
    try {
      final AtomicInteger counter = new AtomicInteger(0);
      final AtomicBoolean canceled = new AtomicBoolean(false);
      final AtomicBoolean pceThrown = new AtomicBoolean(false);

      final int size = files.size();
      boolean completed = JobUtil.invokeConcurrentlyUnderProgress(files, progress, false, new Processor<VirtualFile>() {
        @Override
        public boolean process(final VirtualFile vfile) {
          final PsiFile file = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
            @Override
            public PsiFile compute() {
              return myManager.findFile(vfile);
            }
          });
          if (file != null && !(file instanceof PsiBinaryFile)) {
            file.getViewProvider().getContents(); // load contents outside readaction
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              @Override
              public void run() {
                try {
                  if (myManager.getProject().isDisposed()) throw new ProcessCanceledException();
                  PsiElement[] psiRoots = file.getPsiRoots();
                  Set<PsiElement> processed = new HashSet<PsiElement>(psiRoots.length * 2, (float)0.5);
                  for (PsiElement psiRoot : psiRoots) {
                    if (progress != null) progress.checkCanceled();
                    if (!processed.add(psiRoot)) continue;
                    if (!psiRootProcessor.process(psiRoot)) {
                      canceled.set(true);
                      return;
                    }
                  }
                  myManager.dropResolveCaches();
                }
                catch (ProcessCanceledException e) {
                  canceled.set(true);
                  pceThrown.set(true);
                }
              }
            });
          }
          if (progress != null && progress.isRunning()) {
            double fraction = (double)counter.incrementAndGet() / size;
            progress.setFraction(fraction);
          }
          return !canceled.get();
        }
      });

      if (pceThrown.get()) {
        throw new ProcessCanceledException();
      }

      return completed;
    }
    finally {
      myManager.finishBatchFilesProcessingMode();
    }
  }

  @NotNull
  private List<VirtualFile> getFilesWithText(@NotNull GlobalSearchScope scope,
                                         final short searchContext,
                                         final boolean caseSensitively,
                                         @NotNull String text,
                                         ProgressIndicator progress) {
    myManager.startBatchFilesProcessingMode();
    try {
      final List<VirtualFile> result = new ArrayList<VirtualFile>();
      boolean success = processFilesWithText(
        scope,
        searchContext,
        caseSensitively,
        text,
        new CommonProcessors.CollectProcessor<VirtualFile>(result)
      );
      LOG.assertTrue(success);
      return result;
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
    final ArrayList<IdIndexEntry> entries = getWordEntries(text, caseSensitively);
    if (entries.isEmpty()) return true;

    final Collection<VirtualFile> fileSet = ApplicationManager.getApplication().runReadAction(new Computable<Collection<VirtualFile>>() {
      @Override
      public Collection<VirtualFile> compute() {
        final CommonProcessors.CollectProcessor<VirtualFile> collectProcessor = new CommonProcessors.CollectProcessor<VirtualFile>();
        FileBasedIndex.getInstance().processFilesContainingAllKeys(IdIndex.NAME, entries, scope, new Condition<Integer>() {
          @Override
          public boolean value(Integer integer) {
            return (integer.intValue() & searchContext) != 0;
          }
        }, collectProcessor);
        return collectProcessor.getResults();
      }
    });

    final FileIndexFacade index = FileIndexFacade.getInstance(myManager.getProject());
    return ContainerUtil.process(fileSet, new ReadActionProcessor<VirtualFile>() {
      @Override
      public boolean processInReadAction(VirtualFile virtualFile) {
        return !IndexCacheManagerImpl.shouldBeFound(scope, virtualFile, index) || processor.process(virtualFile);
      }
    });
  }

  @Override
  @NotNull
  public PsiFile[] findFilesWithPlainTextWords(@NotNull String word) {
    return CacheManager.SERVICE.getInstance(myManager.getProject()).getFilesWithWord(word,
                                                                                     UsageSearchContext.IN_PLAIN_TEXT,
                                                                                     GlobalSearchScope.projectScope(myManager.getProject()),
                                                                                     true);
  }


  @Override
  public void processUsagesInNonJavaFiles(@NotNull String qName,
                                          @NotNull PsiNonJavaFileReferenceProcessor processor,
                                          @NotNull GlobalSearchScope searchScope) {
    processUsagesInNonJavaFiles(null, qName, processor, searchScope);
  }

  @Override
  public void processUsagesInNonJavaFiles(@Nullable final PsiElement originalElement,
                                          @NotNull String qName,
                                          @NotNull final PsiNonJavaFileReferenceProcessor processor,
                                          @NotNull GlobalSearchScope searchScope) {
    if (qName.length() == 0) {
      throw new IllegalArgumentException("Cannot search for elements with empty text");
    }
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();

    int dotIndex = qName.lastIndexOf('.');
    int dollarIndex = qName.lastIndexOf('$');
    int maxIndex = Math.max(dotIndex, dollarIndex);
    final String wordToSearch = maxIndex >= 0 ? qName.substring(maxIndex + 1) : qName;
    if (originalElement != null && myManager.isInProject(originalElement) && searchScope.isSearchInLibraries()) {
      searchScope = searchScope.intersectWith(GlobalSearchScope.projectScope(myManager.getProject()));
    }
    final GlobalSearchScope theSearchScope = searchScope;
    PsiFile[] files = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile[]>() {
      @Override
      public PsiFile[] compute() {
        return CacheManager.SERVICE.getInstance(myManager.getProject()).getFilesWithWord(wordToSearch, UsageSearchContext.IN_PLAIN_TEXT, theSearchScope, true);
      }
    });

    final StringSearcher searcher = new StringSearcher(qName, true, true);

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
    final GlobalSearchScope finalScope = searchScope;
    for (int i = 0; i < files.length; i++) {
      if (progress != null) progress.checkCanceled();

      final PsiFile psiFile = files[i];

      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          CharSequence text = psiFile.getViewProvider().getContents();
          for (int index = LowLevelSearchUtil.searchWord(text, 0, text.length(), searcher, progress); index >= 0;) {
            PsiReference referenceAt = psiFile.findReferenceAt(index);
            if (referenceAt == null || useScope == null ||
                !PsiSearchScopeUtil.isInScope(useScope.intersectWith(finalScope), psiFile)) {
              if (!processor.process(psiFile, index, index + searcher.getPattern().length())) {
                cancelled.set(Boolean.TRUE);
                return;
              }
            }

            index = LowLevelSearchUtil.searchWord(text, index + searcher.getPattern().length(), text.length(), searcher, progress);
          }
        }
      });
      if (cancelled.get()) break;
      if (progress != null) {
        progress.setFraction((double)(i + 1) / files.length);
      }
    }

    if (progress != null) {
      progress.popState();
    }
  }

  @Override
  public void processAllFilesWithWord(@NotNull String word, @NotNull GlobalSearchScope scope, @NotNull Processor<PsiFile> processor, final boolean caseSensitively) {
    CacheManager.SERVICE.getInstance(myManager.getProject()).processFilesWithWord(processor, word, UsageSearchContext.IN_CODE, scope, caseSensitively);
  }

  @Override
  public void processAllFilesWithWordInText(@NotNull final String word, @NotNull final GlobalSearchScope scope, @NotNull final Processor<PsiFile> processor,
                                            final boolean caseSensitively) {
    CacheManager.SERVICE.getInstance(myManager.getProject()).processFilesWithWord(processor, word, UsageSearchContext.IN_PLAIN_TEXT, scope, caseSensitively);
  }

  @Override
  public void processAllFilesWithWordInComments(@NotNull String word, @NotNull GlobalSearchScope scope, @NotNull Processor<PsiFile> processor) {
    CacheManager.SERVICE.getInstance(myManager.getProject()).processFilesWithWord(processor, word, UsageSearchContext.IN_COMMENTS, scope, true);
  }

  @Override
  public void processAllFilesWithWordInLiterals(@NotNull String word, @NotNull GlobalSearchScope scope, @NotNull Processor<PsiFile> processor) {
    CacheManager.SERVICE.getInstance(myManager.getProject()).processFilesWithWord(processor, word, UsageSearchContext.IN_STRINGS, scope, true);
  }

  private static class RequestWithProcessor {
    final PsiSearchRequest request;
    Processor<PsiReference> refProcessor;

    private RequestWithProcessor(PsiSearchRequest first, Processor<PsiReference> second) {
      request = first;
      refProcessor = second;
    }

    boolean uniteWith(final RequestWithProcessor another) {
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
  public boolean processRequests(@NotNull SearchRequestCollector collector, @NotNull Processor<PsiReference> processor) {
    Map<SearchRequestCollector, Processor<PsiReference>> collectors = CollectionFactory.hashMap();
    collectors.put(collector, processor);

    appendCollectorsFromQueryRequests(collectors);

    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    do {
      final MultiMap<Set<IdIndexEntry>, RequestWithProcessor> globals = new MultiMap<Set<IdIndexEntry>, RequestWithProcessor>();
      final List<Computable<Boolean>> customs = CollectionFactory.arrayList();
      final LinkedHashSet<RequestWithProcessor> locals = CollectionFactory.linkedHashSet();
      distributePrimitives(collectors, locals, globals, customs);

      if (!processGlobalRequestsOptimized(globals, progress)) {
        return false;
      }

      for (RequestWithProcessor local : locals) {
        if (!processSingleRequest(local.request, local.refProcessor)) {
          return false;
        }
      }

      for (Computable<Boolean> custom : customs) {
        if (!custom.compute()) {
          return false;
        }
      }
    } while (appendCollectorsFromQueryRequests(collectors));

    return true;
  }

  private static boolean appendCollectorsFromQueryRequests(Map<SearchRequestCollector, Processor<PsiReference>> collectors) {
    boolean changed = false;
    LinkedList<SearchRequestCollector> queue = new LinkedList<SearchRequestCollector>(collectors.keySet());
    while (!queue.isEmpty()) {
      final SearchRequestCollector each = queue.removeFirst();
      for (QuerySearchRequest request : each.takeQueryRequests()) {
        request.runQuery();
        collectors.put(request.collector, request.processor);
        queue.addLast(request.collector);
        changed = true;
      }
    }
    return changed;
  }

  private boolean processGlobalRequestsOptimized(MultiMap<Set<IdIndexEntry>, RequestWithProcessor> singles,
                                                 final ProgressIndicator progress) {
    if (singles.isEmpty()) {
      return true;
    }

    if (singles.size() == 1) {
      final Collection<RequestWithProcessor> requests = singles.get(singles.keySet().iterator().next());
      if (requests.size() == 1) {
        final RequestWithProcessor theOnly = requests.iterator().next();
        return processSingleRequest(theOnly.request, theOnly.refProcessor);
      }
    }

    if (progress != null) {
      progress.pushState();
      progress.setText(PsiBundle.message("psi.scanning.files.progress"));
    }

    final MultiMap<VirtualFile, RequestWithProcessor> candidateFiles = collectFiles(singles, progress);

    try {
      if (candidateFiles.isEmpty()) {
        return true;
      }

      final Map<RequestWithProcessor, StringSearcher> searchers = new HashMap<RequestWithProcessor, StringSearcher>();
      final Set<String> allWords = new TreeSet<String>();
      for (RequestWithProcessor singleRequest : candidateFiles.values()) {
        searchers.put(singleRequest, new StringSearcher(singleRequest.request.word, singleRequest.request.caseSensitive, true));
        allWords.add(singleRequest.request.word);
      }

      if (progress != null) {
        final StringBuilder result = new StringBuilder();
        for (String string : allWords) {
          if (string != null && string.length() != 0) {
            if (result.length() > 50) {
              result.append("...");
              break;
            }
            if (result.length() != 0) result.append(", ");
            result.append(string);
          }
        }
        progress.setText(PsiBundle.message("psi.search.for.word.progress", result.toString()));
      }

      return processPsiFileRoots(new ArrayList<VirtualFile>(candidateFiles.keySet()), new Processor<PsiElement>() {
                                   @Override
                                   public boolean process(PsiElement psiRoot) {
                                     final VirtualFile vfile = psiRoot.getContainingFile().getVirtualFile();
                                     for (final RequestWithProcessor singleRequest : candidateFiles.get(vfile)) {
                                       StringSearcher searcher = searchers.get(singleRequest);
                                       TextOccurenceProcessor adapted = adaptProcessor(singleRequest.request, singleRequest.refProcessor);
                                       if (!LowLevelSearchUtil.processElementsContainingWordInElement(adapted, psiRoot, searcher, true, progress)) {
                                         return false;
                                       }
                                     }
                                     return true;
                                   }
                                 }, progress);
    }
    finally {
      if (progress != null) {
        progress.popState();
      }
    }
  }

  private static TextOccurenceProcessor adaptProcessor(final PsiSearchRequest singleRequest,
                                                       final Processor<PsiReference> consumer) {
    final SearchScope searchScope = singleRequest.searchScope;
    final boolean ignoreInjectedPsi = searchScope instanceof LocalSearchScope && ((LocalSearchScope)searchScope).isIgnoreInjectedPsi();
    final RequestResultProcessor wrapped = singleRequest.processor;
    return new TextOccurenceProcessor() {
      @Override
      public boolean execute(PsiElement element, int offsetInElement) {
        if (ignoreInjectedPsi && element instanceof PsiLanguageInjectionHost) return true;

        return wrapped.processTextOccurrence(element, offsetInElement, consumer);
      }
    };
  }

  private MultiMap<VirtualFile, RequestWithProcessor> collectFiles(MultiMap<Set<IdIndexEntry>, RequestWithProcessor> singles,
                                                                   ProgressIndicator progress) {
    final FileIndexFacade index = FileIndexFacade.getInstance(myManager.getProject());
    final MultiMap<VirtualFile, RequestWithProcessor> result = createMultiMap();
    for (final Set<IdIndexEntry> key : singles.keySet()) {
      if (key.isEmpty()) {
        continue;
      }


      final Collection<RequestWithProcessor> data = singles.get(key);
      final GlobalSearchScope commonScope = uniteScopes(data);

      if (key.size() == 1) {
        result.putAllValues(findFilesWithIndexEntry(key.iterator().next(), index, data, commonScope, progress));
        continue;
      }

      final Collection<VirtualFile> fileSet = ApplicationManager.getApplication().runReadAction(new Computable<Collection<VirtualFile>>() {
        @Override
        public Collection<VirtualFile> compute() {
          final CommonProcessors.CollectProcessor<VirtualFile> processor = new CommonProcessors.CollectProcessor<VirtualFile>();
          FileBasedIndex.getInstance().processFilesContainingAllKeys(IdIndex.NAME, key, commonScope, null, processor);
          return processor.getResults();
        }
      });
      
      for (final VirtualFile file : fileSet) {
        if (progress != null) {
          progress.checkCanceled();
        }
        for (final IdIndexEntry entry : key) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
              FileBasedIndex.getInstance().processValues(IdIndex.NAME, entry, file, new FileBasedIndex.ValueProcessor<Integer>() {
                @Override
                public boolean process(VirtualFile file, Integer value) {
                  if (IndexCacheManagerImpl.shouldBeFound(commonScope, file, index)) {
                    int mask = value.intValue();
                    for (RequestWithProcessor single : data) {
                      final PsiSearchRequest request = single.request;
                      if ((mask & request.searchContext) != 0 && ((GlobalSearchScope)request.searchScope).contains(file)) {
                        result.putValue(file, single);
                      }
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
    return result;
  }

  private static MultiMap<VirtualFile, RequestWithProcessor> createMultiMap() {
    return new MultiMap<VirtualFile, RequestWithProcessor>(){
      @Override
      protected Collection<RequestWithProcessor> createCollection() {
        return new SmartList<RequestWithProcessor>(); // usually there is just one request
      }
    };
  }

  private static GlobalSearchScope uniteScopes(Collection<RequestWithProcessor> requests) {
    GlobalSearchScope commonScope = null;
    for (RequestWithProcessor r : requests) {
      final GlobalSearchScope scope = (GlobalSearchScope)r.request.searchScope;
      commonScope = commonScope == null ? scope : commonScope.uniteWith(scope);
    }
    assert commonScope != null;
    return commonScope;
  }

  private static MultiMap<VirtualFile, RequestWithProcessor> findFilesWithIndexEntry(final IdIndexEntry entry,
                                                                                     final FileIndexFacade index,
                                                                                     final Collection<RequestWithProcessor> data,
                                                                                     final GlobalSearchScope commonScope,
                                                                                     final ProgressIndicator progress) {
    final MultiMap<VirtualFile, RequestWithProcessor> local = createMultiMap();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        if (progress != null) progress.checkCanceled();
        FileBasedIndex.getInstance().processValues(IdIndex.NAME, entry, null, new FileBasedIndex.ValueProcessor<Integer>() {
            @Override
            public boolean process(VirtualFile file, Integer value) {
              if (progress != null) progress.checkCanceled();

              if (IndexCacheManagerImpl.shouldBeFound(commonScope, file, index)) {
                int mask = value.intValue();
                for (RequestWithProcessor single : data) {
                  final PsiSearchRequest request = single.request;
                  if ((mask & request.searchContext) != 0 && ((GlobalSearchScope)request.searchScope).contains(file)) {
                    local.putValue(file, single);
                  }
                }
              }
              return true;
            }
          }, commonScope);
      }
    });

    return local;
  }

  private static void distributePrimitives(final Map<SearchRequestCollector, Processor<PsiReference>> collectors,
                                    LinkedHashSet<RequestWithProcessor> locals,
                                    MultiMap<Set<IdIndexEntry>, RequestWithProcessor> singles,
                                    List<Computable<Boolean>> customs) {
    for (final SearchRequestCollector collector : collectors.keySet()) {
      final Processor<PsiReference> processor = collectors.get(collector);
      for (final PsiSearchRequest primitive : collector.takeSearchRequests()) {
        final SearchScope scope = primitive.searchScope;
        if (scope instanceof LocalSearchScope) {
          registerRequest(locals, primitive, processor);
        } else {
          final List<String> words = StringUtil.getWordsIn(primitive.word);
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
  }

  private static void registerRequest(Collection<RequestWithProcessor> collection,
                                      PsiSearchRequest primitive, Processor<PsiReference> processor) {
    final RequestWithProcessor newValue = new RequestWithProcessor(primitive, processor);
    for (RequestWithProcessor existing : collection) {
      if (existing.uniteWith(newValue)) {
        return;
      }
    }
    collection.add(newValue);
  }

  private boolean processSingleRequest(PsiSearchRequest single, Processor<PsiReference> consumer) {
    return processElementsWithWord(adaptProcessor(single, consumer), single.searchScope, single.word, single.searchContext, single.caseSensitive);
  }

  @Override
  public SearchCostResult isCheapEnoughToSearch(@NotNull String name,
                                                @NotNull final GlobalSearchScope scope,
                                                @Nullable final PsiFile fileToIgnoreOccurencesIn,
                                                @Nullable ProgressIndicator progress) {
    
    final ArrayList<IdIndexEntry> keys = getWordEntries(name, true);
    if (keys.isEmpty()) return SearchCostResult.ZERO_OCCURRENCES;
    
    final TIntHashSet set = ApplicationManager.getApplication().runReadAction(new NullableComputable<TIntHashSet>() {
      @Override
      public TIntHashSet compute() {
        return FileBasedIndex.getInstance().collectFileIdsContainingAllKeys(IdIndex.NAME, keys, scope, null);
      }
    }); 

    if (set == null || set.size() > 1000 && !ApplicationManager.getApplication().isUnitTestMode()) {
      return SearchCostResult.TOO_MANY_OCCURRENCES;
    }

    final AtomicInteger count = new AtomicInteger();

    final FileIndexFacade index = FileIndexFacade.getInstance(myManager.getProject());
    final Processor<VirtualFile> processor = new Processor<VirtualFile>() {
      private final VirtualFile fileToIgnoreOccurencesInVirtualFile =
        fileToIgnoreOccurencesIn != null ? fileToIgnoreOccurencesIn.getVirtualFile() : null;

      @Override
      public boolean process(VirtualFile file) {
        if (file == fileToIgnoreOccurencesInVirtualFile) return true;
        if (!IndexCacheManagerImpl.shouldBeFound(scope, file, index)) return true;
        final int value = count.incrementAndGet();
        return value < 10;
      }
    };
    final boolean cheap = ApplicationManager.getApplication().runReadAction(new NullableComputable<Boolean>() {
      @Override
      public Boolean compute() {
        return FileBasedIndex.processVirtualFiles(set, scope, processor);
      }
    });

    if (!cheap) {
      return SearchCostResult.TOO_MANY_OCCURRENCES;
    }

    return count.get() == 0 ? SearchCostResult.ZERO_OCCURRENCES : SearchCostResult.FEW_OCCURRENCES;
  }

  private static ArrayList<IdIndexEntry> getWordEntries(String name, boolean caseSensitively) {
    List<String> words = StringUtil.getWordsIn(name);
    final ArrayList<IdIndexEntry> keys = new ArrayList<IdIndexEntry>();
    for (String word : words) {
      keys.add(new IdIndexEntry(word, caseSensitively));
    }
    return keys;
  }
}
