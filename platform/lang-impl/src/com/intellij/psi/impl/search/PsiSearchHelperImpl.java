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

import com.intellij.concurrency.JobUtil;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.cache.impl.IndexCacheManagerImpl;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.IndexPatternSearch;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
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
    return element.getUseScope();
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
    return doFindTodoItems(file, new TextRange(0, file.getTextLength()));
  }

  @NotNull
  public TodoItem[] findTodoItems(@NotNull PsiFile file, int startOffset, int endOffset) {
    return doFindTodoItems(file, new TextRange(startOffset, endOffset));
  }

  private static TodoItem[] doFindTodoItems(final PsiFile file, final TextRange textRange) {
    final Collection<IndexPatternOccurrence> occurrences = IndexPatternSearch.search(file, TodoConfiguration.getInstance()).findAll();
    if (occurrences.isEmpty()) {
      return EMPTY_TODO_ITEMS;
    }

    List<TodoItem> items = new ArrayList<TodoItem>(occurrences.size());
    for(IndexPatternOccurrence occurrence: occurrences) {
      TextRange occurrenceRange = occurrence.getTextRange();
      if (textRange.contains(occurrenceRange)) {
        items.add(new TodoItemImpl(occurrence.getFile(), occurrenceRange.getStartOffset(), occurrenceRange.getEndOffset(),
                                   mapPattern(occurrence.getPattern())));
      }
    }

    return items.toArray(new TodoItem[items.size()]);
  }

  private static TodoPattern mapPattern(final IndexPattern pattern) {
    for(TodoPattern todoPattern: TodoConfiguration.getInstance().getTodoPatterns()) {
      if (todoPattern.getIndexPattern() == pattern) {
        return todoPattern;
      }
    }
    LOG.error("Could not find matching TODO pattern for index pattern " + pattern.getPatternString());
    return null;
  }

  public int getTodoItemsCount(@NotNull PsiFile file) {
    int count = myManager.getCacheManager().getTodoCount(file.getVirtualFile(), TodoConfiguration.getInstance());
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
      return results.toArray(new PsiElement[results.size()]);
    }
  }

  public boolean processCommentsContainingIdentifier(@NotNull String identifier,
                                                     @NotNull SearchScope searchScope,
                                                     @NotNull final Processor<PsiElement> processor) {
    TextOccurenceProcessor occurenceProcessor = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(element.getLanguage());
        if (parserDefinition == null) return true;

        if (element.getNode() != null && !parserDefinition.getCommentTokens().contains(element.getNode().getElementType())) return true;
        if (element.findReferenceAt(offsetInElement) == null) {
          return processor.process(element);
        }
        return true;
      }
    };
    return processElementsWithWord(occurenceProcessor, searchScope, identifier, UsageSearchContext.IN_COMMENTS, true);
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
                                                  searchContext, caseSensitively);
    }
    else {
      LocalSearchScope scope = (LocalSearchScope)searchScope;
      PsiElement[] scopeElements = scope.getScope();
      final boolean ignoreInjectedPsi = scope.isIgnoreInjectedPsi();

      return JobUtil.invokeConcurrentlyUnderMyProgress(Arrays.asList(scopeElements), new Processor<PsiElement>() {
        public boolean process(PsiElement scopeElement) {
          return processElementsWithWordInScopeElement(scopeElement, processor, text, caseSensitively, ignoreInjectedPsi, progress);
        }
      }, false);
    }
  }

  private static boolean processElementsWithWordInScopeElement(final PsiElement scopeElement,
                                                               final TextOccurenceProcessor processor,
                                                               final String word,
                                                               final boolean caseSensitive,
                                                               final boolean ignoreInjectedPsi, final ProgressIndicator progress) {
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
                                                       final boolean caseSensitively) {
    LOG.assertTrue(!Thread.holdsLock(PsiLock.LOCK), "You must not run search from within updating PSI activity. Please consider invokeLatering it instead.");
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) {
      progress.pushState();
      progress.setText(PsiBundle.message("psi.scanning.files.progress"));
    }

    String text = searcher.getPattern();
    List<VirtualFile> fileSet = getFilesWithText(scope, searchContext, caseSensitively, text, progress);

    if (progress != null) {
      progress.setText(PsiBundle.message("psi.search.for.word.progress", text));
    }

    return processPsiFileRoots(progress, fileSet, new Processor<PsiElement>() {
      public boolean process(PsiElement psiRoot) {
        return LowLevelSearchUtil.processElementsContainingWordInElement(processor, psiRoot, searcher, false, progress);
      }
    });
  }

  private boolean processPsiFileRoots(final ProgressIndicator progress,
                                      List<VirtualFile> files,
                                      final Processor<PsiElement> psiRootProcessor) {
    myManager.startBatchFilesProcessingMode();
    try {
      final AtomicInteger counter = new AtomicInteger(0);
      final AtomicBoolean canceled = new AtomicBoolean(false);
      final AtomicBoolean pceThrown = new AtomicBoolean(false);

      final int size = files.size();
      boolean completed = JobUtil.invokeConcurrentlyUnderMyProgress(files, new Processor<VirtualFile>() {
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
      }, false);

      if (pceThrown.get()) {
        throw new ProcessCanceledException();
      }

      return completed;
    }
    finally {
      if (progress != null) {
        progress.popState();
      }
      myManager.finishBatchFilesProcessingMode();
    }
  }

  private List<VirtualFile> getFilesWithText(@NotNull GlobalSearchScope scope,
                                         final short searchContext,
                                         final boolean caseSensitively,
                                         @NotNull String text,
                                         ProgressIndicator progress) {
    myManager.startBatchFilesProcessingMode();
    try {
      final List<VirtualFile> result = new ArrayList<VirtualFile>();
      if (!processFilesWithText(scope, searchContext, caseSensitively, text, new Processor<PsiFile>() {
        public boolean process(PsiFile file) {
          result.add(file.getViewProvider().getVirtualFile());
          return true;
        }
      }, progress)) {
        return Collections.emptyList();
      }
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
    ProgressManager progressManager = ProgressManager.getInstance();
    final ProgressIndicator progress = progressManager.getProgressIndicator();

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

    final Ref<Boolean> cancelled = new Ref<Boolean>(Boolean.FALSE);
    for (int i = 0; i < files.length; i++) {
      if (progress != null) progress.checkCanceled();

      final PsiFile psiFile = files[i];

      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          CharSequence text = psiFile.getViewProvider().getContents();
          for (int index = LowLevelSearchUtil.searchWord(text, 0, text.length(), searcher, progress); index >= 0;) {
            final int endOffset = index + searcher.getPattern().length();
            if (originalElement == null || !containsReferenceTo(psiFile, index, endOffset, originalElement)) {
              if (!processor.process(psiFile, index, endOffset)) {
                cancelled.set(Boolean.TRUE);
                return;
              }
            }

            index = LowLevelSearchUtil.searchWord(text, endOffset, text.length(), searcher, progress);
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

  private static boolean containsReferenceTo(@NotNull PsiFile psiFile, int startOffset, int endOffset, @NotNull PsiElement originalElement) {
    int index = startOffset;
    while (index < endOffset) {
      PsiReference referenceAt = psiFile.findReferenceAt(index);
      if (referenceAt == null) return false;
      if (referenceAt.isReferenceTo(originalElement)) {
        return true;
      }
      int end = referenceAt.getElement().getTextRange().getStartOffset() + referenceAt.getRangeInElement().getLength();
      index = Math.max(end, index + 1);
    }
    return false;
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

  public boolean processRequest(@NotNull PsiSearchRequest request) {
    if (request instanceof PsiSearchRequest.SingleRequest) {
      return processSingleRequest((PsiSearchRequest.SingleRequest)request);
    }
    else if (request instanceof PsiSearchRequest.CustomRequest) {
      return ((PsiSearchRequest.CustomRequest)request).searchAction.compute();
    }
    else if (request instanceof PsiSearchRequest.ComplexRequest) {
      final MultiMap<Set<IdIndexEntry>, PsiSearchRequest.SingleRequest> singles = new MultiMap<Set<IdIndexEntry>, PsiSearchRequest.SingleRequest>();
      final List<PsiSearchRequest.CustomRequest> customs = new ArrayList<PsiSearchRequest.CustomRequest>();
      distributePrimitives((PsiSearchRequest.ComplexRequest)request, singles, customs);

      if (processRequestsOptimized(singles)) {
        for (PsiSearchRequest.CustomRequest custom : customs) {
          if (!custom.searchAction.compute()) {
            return false;
          }
        }
        return true;
      }

      return false;
    } else {
      LOG.error("Unknown request: " + request);
      return true;
    }
  }

  private boolean processRequestsOptimized(MultiMap<Set<IdIndexEntry>, PsiSearchRequest.SingleRequest> singles) {
    if (singles.isEmpty()) {
      return true;
    }

    if (singles.size() == 1) {
      final Collection<PsiSearchRequest.SingleRequest> requests = singles.get(singles.keySet().iterator().next());
      if (requests.size() == 1) {
        return processSingleRequest(requests.iterator().next());
      }
    }

    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) {
      progress.pushState();
      progress.setText(PsiBundle.message("psi.scanning.files.progress"));
    }

    final MultiMap<VirtualFile, PsiSearchRequest.SingleRequest> candidateFiles = collectFiles(singles);

    final Map<PsiSearchRequest.SingleRequest, StringSearcher> searchers = new HashMap<PsiSearchRequest.SingleRequest, StringSearcher>();
    final Set<String> allWords = new TreeSet<String>();
    for (PsiSearchRequest.SingleRequest singleRequest : candidateFiles.values()) {
      searchers.put(singleRequest, new StringSearcher(singleRequest.word, singleRequest.caseSensitive, true));
      allWords.add(singleRequest.word);
    }

    if (progress != null) {
      progress.setText(PsiBundle.message("psi.search.for.word.progress", StringUtil.join(allWords, ", ")));
    }

    return processPsiFileRoots(progress, new ArrayList<VirtualFile>(candidateFiles.keySet()), new Processor<PsiElement>() {
      public boolean process(PsiElement psiRoot) {
        final VirtualFile vfile = psiRoot.getContainingFile().getVirtualFile();
        for (PsiSearchRequest.SingleRequest singleRequest : candidateFiles.get(vfile)) {
          StringSearcher searcher = searchers.get(singleRequest);
          if (!LowLevelSearchUtil.processElementsContainingWordInElement(singleRequest.processor, psiRoot, searcher, false, progress)) {
            return false;
          }
        }
        return true;
      }
    });
  }

  private MultiMap<VirtualFile, PsiSearchRequest.SingleRequest> collectFiles(MultiMap<Set<IdIndexEntry>, PsiSearchRequest.SingleRequest> singles) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(myManager.getProject()).getFileIndex();
    final MultiMap<VirtualFile, PsiSearchRequest.SingleRequest> result = new MultiMap<VirtualFile, PsiSearchRequest.SingleRequest>();
    for (Set<IdIndexEntry> key : singles.keySet()) {
      final Collection<PsiSearchRequest.SingleRequest> data = singles.get(key);
      GlobalSearchScope commonScope = uniteScopes(data);

      MultiMap<VirtualFile, PsiSearchRequest.SingleRequest> intersection = null;

      boolean first = true;
      for (IdIndexEntry entry : key) {
        final MultiMap<VirtualFile, PsiSearchRequest.SingleRequest> local = findFilesWithIndexEntry(entry, index, data, commonScope);
        if (first) {
          intersection = local;
          first = false;
        } else {
          for (VirtualFile file : local.keySet()) {
            if (intersection.containsKey(file)) {
              intersection.putValues(file, local.get(file));
            }
          }
        }
      }
      result.putAllValues(intersection);
    }
    return result;
  }

  private static GlobalSearchScope uniteScopes(Collection<PsiSearchRequest.SingleRequest> requests) {
    GlobalSearchScope commonScope = null;
    for (PsiSearchRequest.SingleRequest r : requests) {
      final GlobalSearchScope scope = (GlobalSearchScope)r.searchScope;
      commonScope = commonScope == null ? scope : commonScope.uniteWith(scope);
    }
    assert commonScope != null;
    return commonScope;
  }

  private static MultiMap<VirtualFile, PsiSearchRequest.SingleRequest> findFilesWithIndexEntry(IdIndexEntry entry,
                                              final ProjectFileIndex index,
                                              final Collection<PsiSearchRequest.SingleRequest> data,
                                              GlobalSearchScope commonScope) {
    final MultiMap<VirtualFile, PsiSearchRequest.SingleRequest> local = new MultiMap<VirtualFile, PsiSearchRequest.SingleRequest>();
    FileBasedIndex.getInstance().processValues(IdIndex.NAME, entry, null, new FileBasedIndex.ValueProcessor<Integer>() {
      public boolean process(VirtualFile file, Integer value) {
        if (!IndexCacheManagerImpl.shouldBeFound(file, index)) {
          return true;
        }
        int mask = value.intValue();
        for (PsiSearchRequest.SingleRequest single : data) {
          if ((mask & single.searchContext) != 0 && ((GlobalSearchScope)single.searchScope).contains(file)) {
            local.putValue(file, single);
          }
        }
        return true;
      }
    }, commonScope);
    return local;
  }

  private void distributePrimitives(PsiSearchRequest.ComplexRequest request,
                                    MultiMap<Set<IdIndexEntry>, PsiSearchRequest.SingleRequest> singles,
                                    List<PsiSearchRequest.CustomRequest> customs) {
    for (PsiSearchRequest primitive : request.getConstituents()) {
      if (primitive instanceof PsiSearchRequest.CustomRequest) {
        customs.add((PsiSearchRequest.CustomRequest)primitive);
      } else {
        final PsiSearchRequest.SingleRequest single = (PsiSearchRequest.SingleRequest) primitive;
        final SearchScope scope = single.searchScope;
        if (scope instanceof LocalSearchScope) {
          customs.add(PsiSearchRequest.custom(new Computable<Boolean>() {
            public Boolean compute() {
              return processSingleRequest(single);
            }
          }));
        } else {
          final List<String> words = StringUtil.getWordsIn(single.word);
          final Set<IdIndexEntry> key = new HashSet<IdIndexEntry>(words.size());
          for (String word : words) {
            key.add(new IdIndexEntry(word, single.caseSensitive));
          }
          singles.putValue(key, single);
        }

      }
    }
  }

  private boolean processSingleRequest(PsiSearchRequest.SingleRequest single) {
    return processElementsWithWord(single.processor, single.searchScope, single.word, single.searchContext, single.caseSensitive);
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
