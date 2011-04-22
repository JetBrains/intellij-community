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
import com.intellij.openapi.application.Result;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
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
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.text.StringSearcher;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PsiSearchHelperImpl implements PsiSearchHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.PsiSearchHelperImpl");

  private final PsiManagerEx myManager;
  private static final TodoItem[] EMPTY_TODO_ITEMS = new TodoItem[0];

  static {
    IndexPatternSearch.INDEX_PATTERN_SEARCH_INSTANCE = new IndexPatternSearchImpl();
  }

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

  @NotNull
  public PsiFile[] findFilesWithTodoItems() {
    return myManager.getCacheManager().getFilesWithTodoItems();
  }

  @NotNull
  public TodoItem[] findTodoItems(@NotNull PsiFile file) {
    return findTodoItems(file, 0, file.getTextLength());
  }

  @NotNull
  public TodoItem[] findTodoItems(@NotNull PsiFile file, int startOffset, int endOffset) {
    final Collection<IndexPatternOccurrence> occurrences = IndexPatternSearch.search(file, TodoIndexPatternProvider.getInstance()).findAll();
    if (occurrences.isEmpty()) {
      return EMPTY_TODO_ITEMS;
    }

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

  public int getTodoItemsCount(@NotNull PsiFile file) {
    int count = myManager.getCacheManager().getTodoCount(file.getVirtualFile(), TodoIndexPatternProvider.getInstance());
    if (count != -1) return count;
    return findTodoItems(file).length;
  }

  public int getTodoItemsCount(@NotNull PsiFile file, @NotNull TodoPattern pattern) {
    int count = myManager.getCacheManager().getTodoCount(file.getVirtualFile(), pattern.getIndexPattern());
    if (count != -1) return count;
    TodoItem[] items = findTodoItems(file);
    count = 0;
    for (TodoItem item : items) {
      if (item.getPattern().equals(pattern)) count++;
    }
    return count;
  }

  @NotNull
  public PsiElement[] findCommentsContainingIdentifier(@NotNull String identifier, @NotNull SearchScope searchScope) {
    final ArrayList<PsiElement> results = new ArrayList<PsiElement>();
    processCommentsContainingIdentifier(identifier, searchScope, new Processor<PsiElement>() {
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

  public boolean processCommentsContainingIdentifier(@NotNull String identifier,
                                                     @NotNull SearchScope searchScope,
                                                     @NotNull final Processor<PsiElement> processor) {
    TextOccurenceProcessor occurrenceProcessor = new TextOccurenceProcessor() {
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

      return JobUtil.invokeConcurrentlyUnderProgress(Arrays.asList(scopeElements), new Processor<PsiElement>() {
        public boolean process(PsiElement scopeElement) {
          return processElementsWithWordInScopeElement(scopeElement, processor, text, caseSensitively, ignoreInjectedPsi, progress);
        }
      }, false, progress);
    }
  }

  private static boolean processElementsWithWordInScopeElement(final PsiElement scopeElement,
                                                               final TextOccurenceProcessor processor,
                                                               final String word,
                                                               final boolean caseSensitive,
                                                               final boolean ignoreInjectedPsi,
                                                               final ProgressIndicator progress) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        StringSearcher searcher = new StringSearcher(word, caseSensitive, true);

        return LowLevelSearchUtil.processElementsContainingWordInElement(processor, scopeElement, searcher, ignoreInjectedPsi, progress);
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
        public boolean process(PsiElement psiRoot) {
          return LowLevelSearchUtil.processElementsContainingWordInElement(processor, psiRoot, searcher, false, progress);
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
      boolean completed = JobUtil.invokeConcurrentlyUnderProgress(files, new Processor<VirtualFile>() {
        public boolean process(final VirtualFile vfile) {
          final PsiFile file = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
            public PsiFile compute() {
              return myManager.findFile(vfile);
            }
          });
          if (file != null && !(file instanceof PsiBinaryFile)) {
            file.getViewProvider().getContents(); // load contents outside readaction
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                try {
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
          if (progress != null) {
            double fraction = (double)counter.incrementAndGet() / size;
            progress.setFraction(fraction);
          }
          return !canceled.get();
        }
      }, false, progress);

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
      boolean success = processFilesWithText(scope, searchContext, caseSensitively, text, new Processor<PsiFile>() {
                                               public boolean process(PsiFile file) {
                                                 result.add(file.getViewProvider().getVirtualFile());
                                                 return true;
                                               }
                                             }, progress);
      LOG.assertTrue(success);
      return result;
    }
    finally {
      myManager.finishBatchFilesProcessingMode();
    }
  }

  private boolean processFilesWithText(@NotNull final GlobalSearchScope scope,
                                       final short searchContext,
                                       final boolean caseSensitively,
                                       @NotNull String text,
                                       @NotNull final Processor<PsiFile> processor,
                                       ProgressIndicator progress) {
    List<String> words = StringUtil.getWordsIn(text);
    if (words.isEmpty()) return true;
    Collections.sort(words, new Comparator<String>() {
      public int compare(String o1, String o2) {
        return o2.length() - o1.length();
      }
    });
    final Set<PsiFile> fileSet;
    if (words.size() > 1) {
      fileSet = new THashSet<PsiFile>();
      Set<PsiFile> copy = new THashSet<PsiFile>();
      for (int i = 0; i < words.size() - 1; i++) {
        if (progress != null) {
          progress.checkCanceled();
        }
        else {
          ProgressManager.checkCanceled();
        }
        final String word = words.get(i);
        myManager.getCacheManager().processFilesWithWord(new CommonProcessors.CollectProcessor<PsiFile>(copy), word, searchContext, scope, caseSensitively);
        if (i == 0) {
          fileSet.addAll(copy);
        }
        else {
          fileSet.retainAll(copy);
        }
        copy.clear();
        if (fileSet.isEmpty()) break;
      }
      if (fileSet.isEmpty()) return true;
    }
    else {
      fileSet = null;
    }
    return myManager.getCacheManager().processFilesWithWord(new Processor<PsiFile>() {
      public boolean process(PsiFile psiFile) {
        if (fileSet != null && !fileSet.contains(psiFile)) {
          return true;
        }
        return processor.process(psiFile);
      }
    }, words.get(words.size()-1), searchContext, scope, caseSensitively);
  }

  @NotNull
  public PsiFile[] findFilesWithPlainTextWords(@NotNull String word) {
    return myManager.getCacheManager().getFilesWithWord(word,
                                                        UsageSearchContext.IN_PLAIN_TEXT,
                                                        GlobalSearchScope.projectScope(myManager.getProject()), true);
  }


  public void processUsagesInNonJavaFiles(@NotNull String qName,
                                          @NotNull PsiNonJavaFileReferenceProcessor processor,
                                          @NotNull GlobalSearchScope searchScope) {
    processUsagesInNonJavaFiles(null, qName, processor, searchScope);
  }

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
      public PsiFile[] compute() {
        return myManager.getCacheManager().getFilesWithWord(wordToSearch, UsageSearchContext.IN_PLAIN_TEXT, theSearchScope, true);
      }
    });

    final StringSearcher searcher = new StringSearcher(qName, true, true);

    if (progress != null) {
      progress.pushState();
      progress.setText(PsiBundle.message("psi.search.in.non.java.files.progress"));
    }

    final SearchScope useScope = new ReadAction<SearchScope>() {
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

  public void processAllFilesWithWord(@NotNull String word, @NotNull GlobalSearchScope scope, @NotNull Processor<PsiFile> processor, final boolean caseSensitively) {
    myManager.getCacheManager().processFilesWithWord(processor,word, UsageSearchContext.IN_CODE, scope, caseSensitively);
  }

  public void processAllFilesWithWordInText(@NotNull final String word, @NotNull final GlobalSearchScope scope, @NotNull final Processor<PsiFile> processor,
                                            final boolean caseSensitively) {
    myManager.getCacheManager().processFilesWithWord(processor,word, UsageSearchContext.IN_PLAIN_TEXT, scope, caseSensitively);
  }

  public void processAllFilesWithWordInComments(@NotNull String word, @NotNull GlobalSearchScope scope, @NotNull Processor<PsiFile> processor) {
    myManager.getCacheManager().processFilesWithWord(processor, word, UsageSearchContext.IN_COMMENTS, scope, true);
  }

  public void processAllFilesWithWordInLiterals(@NotNull String word, @NotNull GlobalSearchScope scope, @NotNull Processor<PsiFile> processor) {
    myManager.getCacheManager().processFilesWithWord(processor, word, UsageSearchContext.IN_STRINGS, scope, true);
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
        progress.setText(PsiBundle.message("psi.search.for.word.progress", StringUtil.join(allWords, ", ")));
      }

      return processPsiFileRoots(new ArrayList<VirtualFile>(candidateFiles.keySet()), new Processor<PsiElement>() {
                                   public boolean process(PsiElement psiRoot) {
                                     final VirtualFile vfile = psiRoot.getContainingFile().getVirtualFile();
                                     for (final RequestWithProcessor singleRequest : candidateFiles.get(vfile)) {
                                       StringSearcher searcher = searchers.get(singleRequest);
                                       TextOccurenceProcessor adapted = adaptProcessor(singleRequest.request, singleRequest.refProcessor);
                                       if (!LowLevelSearchUtil.processElementsContainingWordInElement(adapted, psiRoot, searcher, false, progress)) {
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
      public boolean execute(PsiElement element, int offsetInElement) {
        if (ignoreInjectedPsi && element instanceof PsiLanguageInjectionHost) return true;

        return wrapped.processTextOccurrence(element, offsetInElement, consumer);
      }
    };
  }

  private MultiMap<VirtualFile, RequestWithProcessor> collectFiles(MultiMap<Set<IdIndexEntry>, RequestWithProcessor> singles,
                                                                   ProgressIndicator progress) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(myManager.getProject()).getFileIndex();
    final MultiMap<VirtualFile, RequestWithProcessor> result = createMultiMap();
    for (Set<IdIndexEntry> key : singles.keySet()) {
      if (key.isEmpty()) {
        continue;
      }

      final Collection<RequestWithProcessor> data = singles.get(key);
      GlobalSearchScope commonScope = uniteScopes(data);

      MultiMap<VirtualFile, RequestWithProcessor> intersection = null;

      boolean first = true;
      for (IdIndexEntry entry : key) {
        final MultiMap<VirtualFile, RequestWithProcessor> local = findFilesWithIndexEntry(entry, index, data, commonScope, progress);
        if (first) {
          intersection = local;
          first = false;
        }
        else {
          intersection.keySet().retainAll(local.keySet());
          for (VirtualFile file : intersection.keySet()) {
            intersection.get(file).retainAll(local.get(file));
          }
        }
      }
      result.putAllValues(intersection);
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
                                                                                     final ProjectFileIndex index,
                                                                                     final Collection<RequestWithProcessor> data,
                                                                                     final GlobalSearchScope commonScope,
                                                                                     final ProgressIndicator progress) {
    final MultiMap<VirtualFile, RequestWithProcessor> local = createMultiMap();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (progress != null) progress.checkCanceled();
        FileBasedIndex.getInstance().processValues(IdIndex.NAME, entry, null, new FileBasedIndex.ValueProcessor<Integer>() {
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

  public SearchCostResult isCheapEnoughToSearch(@NotNull String name,
                                                @NotNull GlobalSearchScope scope,
                                                @Nullable final PsiFile fileToIgnoreOccurencesIn,
                                                ProgressIndicator progress) {
    final int[] count = {0};
    if (!processFilesWithText(scope, UsageSearchContext.ANY, true, name, new Processor<PsiFile>() {
      public boolean process(PsiFile file) {
        if (file == fileToIgnoreOccurencesIn) return true;
        synchronized (count) {
          count[0]++;
          return count[0] <= 10;
        }
      }
    }, progress)) {
      return SearchCostResult.TOO_MANY_OCCURRENCES;
    }

    synchronized (count) {
      return count[0] == 0 ? SearchCostResult.ZERO_OCCURRENCES : SearchCostResult.FEW_OCCURRENCES;
    }
  }
}
