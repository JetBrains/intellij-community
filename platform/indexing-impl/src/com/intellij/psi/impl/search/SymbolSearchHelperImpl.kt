// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.model.Symbol
import com.intellij.model.SymbolReference
import com.intellij.model.search.SearchScopeOptimizer
import com.intellij.model.search.SymbolReferenceSearchParameters
import com.intellij.model.search.SymbolSearchHelper
import com.intellij.model.search.TextOccurrence
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiBundle
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.impl.search.PsiSearchHelperImpl.*
import com.intellij.psi.search.*
import com.intellij.util.Processor
import com.intellij.util.Processors.cancelableCollectProcessor
import com.intellij.util.Query
import com.intellij.util.SmartList
import com.intellij.util.TransformingQuery
import com.intellij.util.containers.cancellable
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.text.StringSearcher
import gnu.trove.THashMap
import gnu.trove.THashSet
import java.util.*
import java.util.function.BinaryOperator
import java.util.stream.Stream
import kotlin.collections.LinkedHashSet
import kotlin.experimental.or

typealias OccurrenceProcessor = Processor<in TextOccurrence>

class SymbolSearchHelperImpl(private val myProject: Project,
                             private val myDumbService: DumbService,
                             helper: PsiSearchHelper) : SymbolSearchHelper {
  private val myHelper: PsiSearchHelperImpl = helper as PsiSearchHelperImpl

  override fun runSearch(parameters: SymbolReferenceSearchParameters, processor: Processor<in SymbolReference>): Boolean {
    return runSearch(paramsQueries(parameters), processor)
  }

  private fun runSearch(queries: Collection<Query<out SymbolReference>>, processor: Processor<in SymbolReference>): Boolean {
    val progress = indicatorOrEmpty
    var currentQueries = queries
    while (currentQueries.isNotEmpty()) {
      progress.checkCanceled()
      val layer = buildLayer(progress, currentQueries)
      val layerResult = runLayer(progress, layer, processor)
      when (layerResult) {
        is Result.Ok -> currentQueries = layerResult.subqueries
        is Result.Stop -> return false
      }
    }
    return true
  }

  private class Layer<T>(
    val queryRequests: Map<Query<*>, Collection<Transformation<*, T>>>,
    val wordRequests: Collection<WordRequest<out T>>,
    val subqueryRequests: Collection<SubqueryRequest<*, *, out T>>
  )

  private fun buildLayer(progress: ProgressIndicator, queries: Collection<Query<out SymbolReference>>): Layer<out SymbolReference> {
    val queue = LinkedList<Query<out SymbolReference>>()
    queue.addAll(queries)

    val queryRequests = THashMap<Query<*>, MutableCollection<Transformation<*, SymbolReference>>>()
    val wordRequests = LinkedList<WordRequest<out SymbolReference>>()
    val subqueryRequests = LinkedList<SubqueryRequest<*, *, out SymbolReference>>()

    while (queue.isNotEmpty()) {
      progress.checkCanceled()
      val query = queue.remove()
      val flatRequests = flatten(query)
      for (queryRequest in flatRequests.myQueryRequests) {
        queryRequests.getOrPut(queryRequest.query) { SmartList() }.add(queryRequest.transformation)
      }
      wordRequests.addAll(flatRequests.myWordRequests)
      subqueryRequests.addAll(flatRequests.mySubQueryRequests)
      for (paramsRequest in flatRequests.myParamsRequests.cancellable(progress)) {
        val paramsQueries = paramsQueries(paramsRequest.params)
        for (paramsQuery in paramsQueries.cancellable(progress)) {
          queue.offer(TransformingQuery.flatMapping(paramsQuery, paramsRequest.transformation))
        }
      }
    }

    return Layer(queryRequests, wordRequests, subqueryRequests)
  }

  private sealed class Result<out T> {
    object Stop : Result<Nothing>()
    class Ok<T>(val subqueries: Collection<Query<out T>>) : Result<T>()
  }

  private fun <T> runLayer(progress: ProgressIndicator, layer: Layer<T>, processor: Processor<in T>): Result<T> {
    if (!processQueryRequests(progress, layer.queryRequests, processor)) {
      return Result.Stop
    }

    if (!processWordRequests(progress, layer.wordRequests, processor)) {
      return Result.Stop
    }

    val subQueries = SmartList<Query<out T>>()
    runSubqueryRequests(layer.subqueryRequests.cancellable(progress)) {
      subQueries.add(it)
    }
    return Result.Ok(subQueries)
  }

  private fun paramsQueries(parameters: SymbolReferenceSearchParameters): Collection<Query<out SymbolReference>> {
    val subQueries = LinkedList<Query<out SymbolReference>>()
    myDumbService.runReadActionInSmartMode {
      SearchRequestors.collectSearchRequests(parameters) {
        subQueries.add(it)
      }
    }
    return subQueries
  }

  private fun <T> processWordRequests(progress: ProgressIndicator,
                                      wordRequests: Collection<WordRequest<out T>>,
                                      processor: Processor<in T>): Boolean {
    if (wordRequests.isEmpty()) return true

    val locals = LinkedHashMap<SearchWordRequest, MutableSet<OccurrenceProcessor>>()
    val globals = LinkedHashMap<SearchWordRequest, MutableSet<OccurrenceProcessor>>()

    for ((request, transform) in wordRequests) {
      progress.checkCanceled()
      val map = if (request.searchScope is LocalSearchScope) locals else globals
      require(request !in map)
      map[request] = mutableSetOf(processor.transform(transform))
    }

    return processGlobalRequests(progress, globals) &&
           processLocalRequests(progress, locals)
  }

  private fun processGlobalRequests(progress: ProgressIndicator, globals: Map<SearchWordRequest, Set<OccurrenceProcessor>>): Boolean {
    if (globals.isEmpty()) {
      return true
    }
    else if (globals.size == 1) {
      for ((key, value) in globals) {
        return processSingleRequest(key, value)
      }
    }

    try {
      progress.pushState()
      progress.text = PsiBundle.message("psi.scanning.files.progress")
      return doProcessGlobalRequests(progress, globals)
    }
    finally {
      progress.popState()
    }
  }

  private fun doProcessGlobalRequests(progress: ProgressIndicator,
                                      globals: Map<SearchWordRequest, Collection<OccurrenceProcessor>>): Boolean {
    val globalsIds: Map<Set<IdIndexEntry>, List<SearchWordRequest>> = globals.keys.groupBy {
      getWordEntries(it.word, it.caseSensitive).toSet()
    }

    // intersectionCandidateFiles holds files containing words from all requests in `singles` and words in corresponding container names
    val intersectionCandidateFiles = HashMap<VirtualFile, MutableCollection<SearchWordRequest>>()
    // restCandidateFiles holds files containing words from all requests in `singles` but EXCLUDING words in corresponding container names
    val restCandidateFiles = HashMap<VirtualFile, MutableCollection<SearchWordRequest>>()
    collectFiles(progress, globalsIds, intersectionCandidateFiles, restCandidateFiles)

    if (intersectionCandidateFiles.isEmpty() && restCandidateFiles.isEmpty()) {
      return true
    }

    val localProcessors = buildLocalProcessors(progress, globals)
    val allWords = getAllWords(progress, localProcessors.keys)
    progress.text = PsiBundle.message("psi.search.for.word.progress", getPresentableWordsDescription(allWords))

    if (intersectionCandidateFiles.isEmpty()) {
      return processCandidates(progress, localProcessors, restCandidateFiles, restCandidateFiles.size, 0)
    }
    else {
      val totalSize = restCandidateFiles.size + intersectionCandidateFiles.size
      return processCandidates(progress, localProcessors, intersectionCandidateFiles, totalSize, 0) &&
             processCandidates(progress, localProcessors, restCandidateFiles, totalSize, intersectionCandidateFiles.size)
    }
  }

  private fun collectFiles(progress: ProgressIndicator,
                           globalsIds: Map<Set<IdIndexEntry>, Collection<SearchWordRequest>>,
                           intersectionResult: MutableMap<VirtualFile, MutableCollection<SearchWordRequest>>,
                           restResult: MutableMap<VirtualFile, MutableCollection<SearchWordRequest>>) {
    for ((keys, requests) in globalsIds) {
      progress.checkCanceled()
      if (keys.isEmpty()) {
        continue
      }
      val commonScope = uniteScopes(requests)
      val intersectionWithContainerNameFiles = intersectionWithContainerNameFiles(commonScope, requests, keys)

      val files = ArrayList<VirtualFile>()
      processFilesContainingAllKeys(myProject, commonScope, null, keys, cancelableCollectProcessor(files))
      for (file in files) {
        progress.checkCanceled()
        for (indexEntry in keys) {
          progress.checkCanceled()
          myDumbService.runReadActionInSmartMode<Boolean> {
            FileBasedIndex.getInstance().processValues(
              IdIndex.NAME, indexEntry, file, { file1, mask ->
              for (request in requests) {
                progress.checkCanceled()
                if (mask and request.searchContext.toInt() != 0 && request.searchScope.contains(file1)) {
                  val result = if (intersectionWithContainerNameFiles != null && intersectionWithContainerNameFiles.contains(file1))
                    intersectionResult
                  else
                    restResult
                  result.computeIfAbsent(file1) { SmartList() }.add(request)
                }
              }
              true
            },
              commonScope
            )
          }
        }
      }
    }
  }

  private fun intersectionWithContainerNameFiles(commonScope: GlobalSearchScope,
                                                 requests: Collection<SearchWordRequest>,
                                                 keys: Set<IdIndexEntry>): Set<VirtualFile>? {
    var commonName: String? = null
    var searchContext: Short = 0
    var caseSensitive = true
    for (request in requests) {
      ProgressManager.checkCanceled()
      val containerName = request.containerName
      if (containerName != null) {
        if (commonName == null) {
          commonName = containerName
          searchContext = request.searchContext
          caseSensitive = request.caseSensitive
        }
        else if (commonName == containerName) {
          searchContext = searchContext or request.searchContext
          caseSensitive = caseSensitive and request.caseSensitive
        }
        else {
          return null
        }
      }
    }
    if (commonName == null) return null

    val entries = getWordEntries(commonName, caseSensitive)
    if (entries.isEmpty()) return null
    entries.addAll(keys) // should find words from both text and container names

    val finalSearchContext = searchContext.toInt()
    val contextMatches = { context: Int -> context and finalSearchContext != 0 }
    val containerFiles = THashSet<VirtualFile>()
    val processor = cancelableCollectProcessor(containerFiles)
    processFilesContainingAllKeys(myProject, commonScope, Condition { contextMatches(it) }, entries, processor)
    return containerFiles
  }

  private fun processCandidates(progress: ProgressIndicator,
                                localProcessors: Map<SearchWordRequest, Processor<PsiElement>>,
                                candidateFiles: Map<VirtualFile, Collection<SearchWordRequest>>,
                                totalSize: Int,
                                alreadyProcessedFiles: Int): Boolean {
    val files = ArrayList(candidateFiles.keys)
    return myHelper.processPsiFileRoots(files, totalSize, alreadyProcessedFiles, progress) { psiRoot ->
      val vfile = psiRoot.virtualFile
      for (request in candidateFiles.getValue(vfile)) {
        ProgressManager.checkCanceled()
        val localProcessor = localProcessors.getValue(request)
        if (!localProcessor.process(psiRoot)) {
          return@processPsiFileRoots false
        }
      }
      true
    }
  }

  private fun processLocalRequests(progress: ProgressIndicator, locals: Map<SearchWordRequest, Collection<OccurrenceProcessor>>): Boolean {
    if (locals.isEmpty()) {
      return true
    }
    for ((key, value) in locals) {
      progress.checkCanceled()
      if (!processSingleRequest(key, value)) return false
    }
    return true
  }

  private fun processSingleRequest(request: SearchWordRequest, occurenceProcessors: Collection<OccurrenceProcessor>): Boolean {
    val options = EnumSet.of(Options.PROCESS_ONLY_JAVA_IDENTIFIERS_IF_POSSIBLE)
    if (request.caseSensitive) options.add(Options.CASE_SENSITIVE_SEARCH)
    if (request.shouldProcessInjectedPsi()) options.add(Options.PROCESS_INJECTED_PSI)

    return myHelper.bulkProcessElementsWithWord(
      request.searchScope,
      request.word,
      request.searchContext,
      options,
      request.containerName,
      adaptProcessors(request.shouldProcessInjectedPsi(), occurenceProcessors)
    )
  }
}

private fun getAllWords(progress: ProgressIndicator, requests: Collection<SearchWordRequest>): Set<String> {
  val allWords = TreeSet<String>()
  for (request in requests) {
    progress.checkCanceled()
    allWords.add(request.word)
  }
  return allWords
}

private fun uniteScopes(requests: Collection<SearchWordRequest>): GlobalSearchScope {
  val scopes = requests.mapTo(LinkedHashSet()) {
    it.searchScope as GlobalSearchScope
  }
  return GlobalSearchScope.union(scopes)
}

fun getRestrictedScope(optimizers: Array<SearchScopeOptimizer>, project: Project, symbol: Symbol): SearchScope? {
  return Stream.of(*optimizers)
    .peek { ProgressManager.checkCanceled() }
    .map<SearchScope> { it.getRestrictedUseScope(project, symbol) }
    .reduce(BinaryOperator<SearchScope?> { scope1, scope2 -> intersectNullable(scope1, scope2) })
    .orElse(null)
}

private fun intersectNullable(scope1: SearchScope?, scope2: SearchScope?): SearchScope? {
  if (scope1 == null) return scope2
  return if (scope2 == null) scope1 else scope1.intersectWith(scope2)
}


private fun <T> processQueryRequests(progress: ProgressIndicator,
                                     queryRequests: Map<Query<*>, Collection<Transformation<*, T>>>,
                                     processor: Processor<in T>): Boolean {

  for ((query, transforms) in queryRequests) {
    progress.checkCanceled()
    @Suppress("UNCHECKED_CAST")
    if (!runQuery(query as Query<Any>, transforms as Collection<Transformation<Any, T>>, processor)) return false
  }
  return true
}

private fun <B, R> runQuery(query: Query<B>, transformations: Collection<Transformation<B, R>>, processor: Processor<in R>): Boolean {
  return query.forEach(fun(baseValue: B): Boolean {
    for (transformation in transformations) {
      for (resultValue in transformation.apply(baseValue)) {
        if (!processor.process(resultValue)) {
          return false
        }
      }
    }
    return true
  })
}

private fun <T> runSubqueryRequests(queryRequests: Iterable<SubqueryRequest<*, *, out T>>, consumer: (Query<out T>) -> Unit): Boolean {
  for (request in queryRequests) {
    request.run(consumer)
  }
  return true
}

private val LOG = Logger.getInstance(SymbolSearchHelper::class.java)


private fun buildLocalProcessors(progress: ProgressIndicator,
                                 globals: Map<SearchWordRequest, Collection<Processor<in TextOccurrence>>>): Map<SearchWordRequest, Processor<PsiElement>> {
  val result = THashMap<SearchWordRequest, Processor<PsiElement>>()
  globals.forEach { request, processors ->
    progress.checkCanceled()

    val searcher = StringSearcher(request.word, request.caseSensitive, true, false)
    val adapted = adaptProcessors(request.shouldProcessInjectedPsi(), processors)
    val localProcessor = localProcessor(progress, searcher, adapted)

    assert(!result.containsKey(request) || result[request] === localProcessor)
    result[request] = localProcessor
  }
  return result
}

private val indicatorOrEmpty: ProgressIndicator
  get() = EmptyProgressIndicator.notNullize(ProgressIndicatorProvider.getGlobalProgressIndicator())

private fun adaptProcessors(processInjected: Boolean, processors: Collection<OccurrenceProcessor>): BulkOccurrenceProcessor {
  return BulkOccurrenceProcessor { scope, offsetsInScope, searcher ->
    try {
      val progress = indicatorOrEmpty
      progress.checkCanceled()
      LowLevelSearchUtil.processElementsAtOffsets(
        scope, searcher, processInjected, progress, offsetsInScope,
        TextOccurenceProcessor { element, offsetInElement ->
          if (!processInjected && element is PsiLanguageInjectionHost) {
            return@TextOccurenceProcessor true
          }
          for (processor in processors) {
            if (!processor.process(TextOccurrence.of(element, offsetInElement))) {
              return@TextOccurenceProcessor false
            }
          }
          true
        }
      )
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Exception) {
      LOG.error(e)
    }
    catch (e: Error) {
      LOG.error(e)
    }
    true
  }
}
