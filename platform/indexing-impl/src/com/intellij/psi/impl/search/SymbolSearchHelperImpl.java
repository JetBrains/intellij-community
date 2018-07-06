// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.model.SymbolReference;
import com.intellij.model.search.SymbolReferenceSearchParameters;
import com.intellij.model.search.SymbolSearchHelper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.impl.search.PsiSearchHelperImpl.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.util.Preprocessor;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.text.StringSearcher;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

import static com.intellij.psi.impl.search.PsiSearchHelperImpl.*;
import static com.intellij.util.Processors.cancelableCollectProcessor;
import static com.intellij.util.containers.ContainerUtil.*;
import static java.util.stream.Collectors.groupingBy;

public class SymbolSearchHelperImpl implements SymbolSearchHelper {

  private static final Logger LOG = Logger.getInstance(SymbolSearchHelper.class);

  private final Project myProject;
  private final DumbService myDumbService;
  private final PsiSearchHelperImpl myHelper;

  public SymbolSearchHelperImpl(@NotNull Project project, @NotNull DumbService dumbService, @NotNull PsiSearchHelper helper) {
    myProject = project;
    myDumbService = dumbService;
    myHelper = (PsiSearchHelperImpl)helper;
  }

  @Override
  public boolean runSearch(@NotNull SymbolReferenceSearchParameters parameters, @NotNull Processor<? super SymbolReference> processor) {
    final List<SearchRequestCollectorImpl> collectors = new LinkedList<>();
    collectors.add(initCollector(parameters, Preprocessor.id()));
    return runCollectors(collectors, processor);
  }

  @NotNull
  private static SearchRequestCollectorImpl initCollector(@NotNull SymbolReferenceSearchParameters parameters,
                                                          @NotNull Preprocessor<SymbolReference, SymbolReference> preprocessor) {
    SearchRequestCollectorImpl childCollector = new SearchRequestCollectorImpl(parameters, preprocessor);
    SearchRequestors.collectSearchRequests(childCollector);
    return childCollector;
  }

  private boolean runCollectors(@NotNull Collection<SearchRequestCollectorImpl> collectors,
                                @NotNull Processor<? super SymbolReference> processor) {
    final ProgressIndicator progress = getIndicatorOrEmpty();

    while (!collectors.isEmpty()) {
      progress.checkCanceled();

      if (!processQueryRequests(progress, collectors, processor)) return false;
      if (!processWordRequests(progress, collectors, processor)) return false;

      final Collection<SearchParamsRequest> paramsRequests = flatten(map(collectors, it -> it.takeParametersRequests()));
      for (SearchParamsRequest request : paramsRequests) {
        progress.checkCanceled();
        collectors.add(initCollector(request.parameters, request.preprocessor));
      }

      collectors.removeIf(it -> it.isEmpty());
    }

    return true;
  }

  private static boolean processQueryRequests(@NotNull ProgressIndicator progress,
                                              @NotNull Collection<SearchRequestCollectorImpl> collectors,
                                              @NotNull Processor<? super SymbolReference> processor) {
    for (SearchRequestCollectorImpl collector : collectors) {
      progress.checkCanceled();
      for (SearchQueryRequest<?> request : collector.takeQueryRequests()) {
        progress.checkCanceled();
        if (!processQueryRequest(request, processor)) return false;
      }
    }
    return true;
  }

  private static <T> boolean processQueryRequest(@NotNull SearchQueryRequest<T> request,
                                                 @NotNull Processor<? super SymbolReference> processor) {
    Processor<? super T> preprocessed = request.preprocessor.apply(processor);
    return request.query.forEach(preprocessed);
  }

  /**
   * This operation may add more requests into each collector
   */
  private boolean processWordRequests(@NotNull ProgressIndicator progress,
                                      @NotNull Collection<SearchRequestCollectorImpl> collectors,
                                      @NotNull Processor<? super SymbolReference> processor) {
    if (collectors.isEmpty()) return true;

    final Map<SearchWordRequest, Set<TextOccurenceProcessor>> locals = new LinkedHashMap<>();
    final Map<SearchWordRequest, Set<TextOccurenceProcessor>> globals = new LinkedHashMap<>();

    BiConsumer<SearchWordRequest, Collection<TextOccurenceProcessor>> distributor = (request, processors) -> {
      progress.checkCanceled();
      (request.searchScope instanceof LocalSearchScope ? locals : globals)
        .computeIfAbsent(request, r -> new LinkedHashSet<>())
        .addAll(processors);
    };

    for (SearchRequestCollectorImpl collector : collectors) {
      progress.checkCanceled();
      WordRequests wordRequests = collector.takeWordRequests();
      if (wordRequests.isEmpty()) continue;
      // process deferred requests first because they may feed something into processor and return early
      wordRequests.deferredWordRequests.forEach((request, processorProviders) -> distributor.accept(
        request, map(processorProviders, it -> it.apply(processor))
      ));
      // there requests can't feed anything into processor -> they can't return early -> process them last
      wordRequests.immediateWordRequests.forEach(distributor);
    }

    return processGlobalRequests(progress, globals) &&
           processLocalRequests(progress, locals);
  }

  private boolean processGlobalRequests(@NotNull ProgressIndicator progress,
                                        @NotNull Map<SearchWordRequest, Set<TextOccurenceProcessor>> globals) {
    if (globals.isEmpty()) {
      return true;
    }
    else if (globals.size() == 1) {
      //noinspection LoopStatementThatDoesntLoop
      for (Entry<SearchWordRequest, Set<TextOccurenceProcessor>> entry : globals.entrySet()) {
        return processSingleRequest(entry.getKey(), entry.getValue());
      }
    }

    try {
      progress.pushState();
      progress.setText(PsiBundle.message("psi.scanning.files.progress"));
      return doProcessGlobalRequests(progress, globals);
    }
    finally {
      progress.popState();
    }
  }

  private boolean doProcessGlobalRequests(@NotNull ProgressIndicator progress,
                                          @NotNull Map<SearchWordRequest, ? extends Collection<TextOccurenceProcessor>> globals) {
    final Map<Set<IdIndexEntry>, List<SearchWordRequest>> globalsIds = globals.keySet().stream().collect(
      groupingBy(it -> new HashSet<>(getWordEntries(it.word, it.caseSensitive)))
    );
    // intersectionCandidateFiles holds files containing words from all requests in `singles` and words in corresponding container names
    final Map<VirtualFile, Collection<SearchWordRequest>> intersectionCandidateFiles = new HashMap<>();
    // restCandidateFiles holds files containing words from all requests in `singles` but EXCLUDING words in corresponding container names
    final Map<VirtualFile, Collection<SearchWordRequest>> restCandidateFiles = new HashMap<>();
    collectFiles(progress, globalsIds, intersectionCandidateFiles, restCandidateFiles);

    if (intersectionCandidateFiles.isEmpty() && restCandidateFiles.isEmpty()) {
      return true;
    }

    final Map<SearchWordRequest, Processor<PsiElement>> localProcessors = buildLocalProcessors(progress, globals);
    final Set<String> allWords = getAllWords(progress, localProcessors.keySet());
    progress.setText(PsiBundle.message("psi.search.for.word.progress", getPresentableWordsDescription(allWords)));

    if (intersectionCandidateFiles.isEmpty()) {
      return processCandidates(progress, localProcessors, restCandidateFiles, restCandidateFiles.size(), 0);
    }
    else {
      int totalSize = restCandidateFiles.size() + intersectionCandidateFiles.size();
      if (!processCandidates(progress, localProcessors, intersectionCandidateFiles, totalSize, 0)) return false;
      if (!processCandidates(progress, localProcessors, restCandidateFiles, totalSize, intersectionCandidateFiles.size())) return false;
      return true;
    }
  }

  private void collectFiles(@NotNull ProgressIndicator progress,
                            @NotNull Map<Set<IdIndexEntry>, ? extends Collection<SearchWordRequest>> globalsIds,
                            @NotNull Map<VirtualFile, Collection<SearchWordRequest>> intersectionResult,
                            @NotNull Map<VirtualFile, Collection<SearchWordRequest>> restResult) {
    for (Entry<Set<IdIndexEntry>, ? extends Collection<SearchWordRequest>> entry : globalsIds.entrySet()) {
      progress.checkCanceled();
      final Set<IdIndexEntry> keys = entry.getKey();
      if (keys.isEmpty()) {
        continue;
      }
      final Collection<SearchWordRequest> requests = entry.getValue();
      final GlobalSearchScope commonScope = uniteScopes(requests);
      final Set<VirtualFile> intersectionWithContainerNameFiles = intersectionWithContainerNameFiles(commonScope, requests, keys);

      final List<VirtualFile> files = new ArrayList<>();
      processFilesContainingAllKeys(myProject, commonScope, null, keys, cancelableCollectProcessor(files));
      for (VirtualFile file : files) {
        progress.checkCanceled();
        for (final IdIndexEntry indexEntry : keys) {
          progress.checkCanceled();
          myDumbService.runReadActionInSmartMode(() -> FileBasedIndex.getInstance().processValues(
            IdIndex.NAME, indexEntry, file, (file1, value) -> {
              int mask = value.intValue();
              for (SearchWordRequest request : requests) {
                progress.checkCanceled();
                if ((mask & request.searchContext) != 0 && request.searchScope.contains(file1)) {
                  Map<VirtualFile, Collection<SearchWordRequest>> result =
                    intersectionWithContainerNameFiles != null && intersectionWithContainerNameFiles.contains(file1)
                    ? intersectionResult
                    : restResult;
                  result.computeIfAbsent(file1, f -> new SmartList<>()).add(request);
                }
              }
              return true;
            },
            commonScope
          ));
        }
      }
    }
  }

  @Nullable("null means we did not find common container files")
  private Set<VirtualFile> intersectionWithContainerNameFiles(@NotNull GlobalSearchScope commonScope,
                                                              @NotNull Collection<SearchWordRequest> requests,
                                                              @NotNull Set<IdIndexEntry> keys) {
    String commonName = null;
    short searchContext = 0;
    boolean caseSensitive = true;
    for (SearchWordRequest request : requests) {
      ProgressManager.checkCanceled();
      String containerName = request.containerName;
      if (containerName != null) {
        if (commonName == null) {
          commonName = containerName;
          searchContext = request.searchContext;
          caseSensitive = request.caseSensitive;
        }
        else if (commonName.equals(containerName)) {
          searchContext |= request.searchContext;
          caseSensitive &= request.caseSensitive;
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
    Processor<VirtualFile> processor = cancelableCollectProcessor(containerFiles);
    processFilesContainingAllKeys(myProject, commonScope, contextMatches, entries, processor);

    return containerFiles;
  }

  private static Map<SearchWordRequest, Processor<PsiElement>> buildLocalProcessors(@NotNull ProgressIndicator progress,
                                                                                    @NotNull Map<SearchWordRequest, ? extends Collection<TextOccurenceProcessor>> globals) {
    Map<SearchWordRequest, Processor<PsiElement>> result = new THashMap<>();
    globals.forEach((request, processors) -> {
      progress.checkCanceled();

      StringSearcher searcher = new StringSearcher(request.word, request.caseSensitive, true, false);
      BulkOccurrenceProcessor adapted = adaptProcessors(request.shouldProcessInjectedPsi(), processors);
      Processor<PsiElement> localProcessor = localProcessor(progress, searcher, adapted);

      assert !result.containsKey(request) || result.get(request) == localProcessor;
      result.put(request, localProcessor);
    });
    return result;
  }

  private boolean processCandidates(@NotNull ProgressIndicator progress,
                                    @NotNull Map<SearchWordRequest, Processor<PsiElement>> localProcessors,
                                    @NotNull Map<VirtualFile, Collection<SearchWordRequest>> candidateFiles,
                                    int totalSize,
                                    int alreadyProcessedFiles) {
    List<VirtualFile> files = new ArrayList<>(candidateFiles.keySet());
    return myHelper.processPsiFileRoots(files, totalSize, alreadyProcessedFiles, progress, psiRoot -> {
      final VirtualFile vfile = psiRoot.getVirtualFile();
      for (SearchWordRequest request : candidateFiles.get(vfile)) {
        ProgressManager.checkCanceled();
        Processor<PsiElement> localProcessor = localProcessors.get(request);
        if (!localProcessor.process(psiRoot)) {
          return false;
        }
      }
      return true;
    });
  }

  private boolean processLocalRequests(@NotNull ProgressIndicator progress,
                                       @NotNull Map<SearchWordRequest, Set<TextOccurenceProcessor>> locals) {
    if (locals.isEmpty()) {
      return true;
    }
    for (Entry<SearchWordRequest, Set<TextOccurenceProcessor>> entry : locals.entrySet()) {
      progress.checkCanceled();
      if (!processSingleRequest(entry.getKey(), entry.getValue())) return false;
    }
    return true;
  }

  private boolean processSingleRequest(@NotNull SearchWordRequest request,
                                       @NotNull Collection<TextOccurenceProcessor> occurenceProcessors) {
    final EnumSet<Options> options = EnumSet.of(Options.PROCESS_ONLY_JAVA_IDENTIFIERS_IF_POSSIBLE);
    if (request.caseSensitive) options.add(Options.CASE_SENSITIVE_SEARCH);
    if (request.shouldProcessInjectedPsi()) options.add(Options.PROCESS_INJECTED_PSI);

    return myHelper.bulkProcessElementsWithWord(
      request.searchScope,
      request.word,
      request.searchContext,
      options,
      request.containerName,
      adaptProcessors(request.shouldProcessInjectedPsi(), occurenceProcessors)
    );
  }

  private static BulkOccurrenceProcessor adaptProcessors(boolean processInjected, @NotNull Collection<TextOccurenceProcessor> processors) {
    return (scope, offsetsInScope, searcher) -> {
      try {
        ProgressIndicator progress = getIndicatorOrEmpty();
        progress.checkCanceled();
        return LowLevelSearchUtil.processElementsAtOffsets(
          scope, searcher, processInjected, progress, offsetsInScope,
          (element, offsetInElement) -> {
            if (!processInjected && element instanceof PsiLanguageInjectionHost) {
              return true;
            }
            for (TextOccurenceProcessor processor : processors) {
              if (!processor.execute(element, offsetInElement)) {
                return false;
              }
            }
            return true;
          }
        );
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception | Error e) {
        LOG.error(e);
        return true;
      }
    };
  }

  @NotNull
  private static Set<String> getAllWords(@NotNull ProgressIndicator progress, @NotNull Collection<SearchWordRequest> requests) {
    final Set<String> allWords = new TreeSet<>();
    for (SearchWordRequest request : requests) {
      progress.checkCanceled();
      allWords.add(request.word);
    }
    return allWords;
  }

  @NotNull
  private static GlobalSearchScope uniteScopes(@NotNull Collection<SearchWordRequest> requests) {
    Set<GlobalSearchScope> scopes = map2LinkedSet(requests, r -> (GlobalSearchScope)r.searchScope);
    return GlobalSearchScope.union(scopes);
  }

  @NotNull
  private static ProgressIndicator getIndicatorOrEmpty() {
    return EmptyProgressIndicator.notNullize(ProgressIndicatorProvider.getGlobalProgressIndicator());
  }
}
