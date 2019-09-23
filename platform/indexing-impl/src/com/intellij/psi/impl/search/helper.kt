// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.model.search.SearchParameters
import com.intellij.model.search.Searcher
import com.intellij.model.search.impl.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClassExtension
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.util.ObjectUtils
import com.intellij.util.Processor
import com.intellij.util.Query
import com.intellij.util.SmartList
import com.intellij.util.text.StringSearcher
import gnu.trove.THashMap
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

internal val LOG: Logger = logger(::LOG)

private val searchersExtension = ClassExtension<Searcher<*, *>>("com.intellij.searcher")

internal val indicatorOrEmpty: ProgressIndicator
  get() = EmptyProgressIndicator.notNullize(ProgressIndicatorProvider.getGlobalProgressIndicator())


@Internal
fun <R> runSearch(project: Project, query: Query<out R>, processor: Processor<in R>): Boolean {
  val progress = indicatorOrEmpty
  var currentQueries: Collection<Query<out R>> = listOf(query)
  while (currentQueries.isNotEmpty()) {
    progress.checkCanceled()
    val layer = buildLayer(progress, project, currentQueries)
    when (val layerResult = layer.runLayer(processor)) {
      is LayerResult.Ok -> currentQueries = layerResult.subqueries
      is LayerResult.Stop -> return false
    }
  }
  return true
}

private fun <R> buildLayer(progress: ProgressIndicator, project: Project, queries: Collection<Query<out R>>): Layer<out R> {
  val queue: Queue<Query<out R>> = ArrayDeque()
  queue.addAll(queries)

  val queryRequests = SmartList<QueryRequest<*, R>>()
  val subQueryQueryRequests = SmartList<QueryRequest<*, Query<out R>>>()
  val wordRequests = SmartList<WordRequest<R>>()
  val subQueryWordRequests = SmartList<WordRequest<Query<out R>>>()

  while (queue.isNotEmpty()) {
    progress.checkCanceled()
    val query: Query<out R> = queue.remove()
    val primitives: PrimitiveRequests<R> = decompose(query)

    val resultRequests: Requests<R> = primitives.resultRequests
    queryRequests.addAll(resultRequests.queryRequests)
    wordRequests.addAll(resultRequests.wordRequests)
    for (parametersRequest: ParametersRequest<*, R> in resultRequests.parametersRequests) {
      progress.checkCanceled()
      handleParamRequest(progress, parametersRequest) {
        queue.offer(it)
      }
    }

    val subQueryRequests: Requests<Query<out R>> = primitives.subQueryRequests
    subQueryQueryRequests.addAll(subQueryRequests.queryRequests)
    subQueryWordRequests.addAll(subQueryRequests.wordRequests)
    for (parametersRequest: ParametersRequest<*, Query<out R>> in subQueryRequests.parametersRequests) {
      progress.checkCanceled()
      handleSubQueryParamRequest(progress, parametersRequest) {
        queue.offer(it)
      }
    }
  }
  return Layer(project, progress, queryRequests, wordRequests, subQueryQueryRequests, subQueryWordRequests)
}

private fun <B, R> handleParamRequest(progress: ProgressIndicator,
                                      request: ParametersRequest<B, R>,
                                      queue: (Query<out R>) -> Unit) {
  val searchRequests: Collection<Query<out B>> = collectSearchRequests(request.params)
  for (query: Query<out B> in searchRequests) {
    progress.checkCanceled()
    queue(transformingQuery(query, request.transformation))
  }
}

private fun <B, R> handleSubQueryParamRequest(progress: ProgressIndicator,
                                              request: ParametersRequest<B, Query<out R>>,
                                              queue: (Query<out R>) -> Unit) {
  val searchRequests: Collection<Query<out B>> = collectSearchRequests(request.params)
  for (query: Query<out B> in searchRequests) {
    progress.checkCanceled()
    queue(LayeredQuery(query, request.transformation))
  }
}

private fun <R> collectSearchRequests(parameters: SearchParameters<R>): Collection<Query<out R>> {
  return DumbService.getInstance(parameters.project).runReadActionInSmartMode(Computable {
    if (parameters.areValid()) {
      doCollectSearchRequests(parameters)
    }
    else {
      emptyList()
    }
  })
}

private fun <R> doCollectSearchRequests(parameters: SearchParameters<R>): Collection<Query<out R>> {
  val queries = ArrayList<Query<out R>>()
  @Suppress("UNCHECKED_CAST")
  val searchers = searchersExtension.forKey(parameters.javaClass) as List<Searcher<SearchParameters<R>, R>>
  for (searcher: Searcher<SearchParameters<R>, R> in searchers) {
    ProgressManager.checkCanceled()
    queries += searcher.collectSearchRequests(parameters)
  }
  return queries
}

private sealed class LayerResult<out T> {
  object Stop : LayerResult<Nothing>()
  class Ok<T>(val subqueries: Collection<Query<out T>>) : LayerResult<T>()
}

private class Layer<T>(
  private val project: Project,
  private val progress: ProgressIndicator,
  private val queryRequests: Collection<QueryRequest<*, T>>,
  private val wordRequests: Collection<WordRequest<T>>,
  private val subQueryQueryRequests: Collection<QueryRequest<*, Query<out T>>>,
  private val subQueryWordRequests: Collection<WordRequest<Query<out T>>>
) {

  private val myHelper = PsiSearchHelper.getInstance(project) as PsiSearchHelperImpl

  fun runLayer(processor: Processor<in T>): LayerResult<T> {
    if (!processQueryRequests(progress, queryRequests, processor)) {
      return LayerResult.Stop
    }
    val subQueries = ArrayList<Query<out T>>()
    val subQueryProcessor: Processor<Query<out T>> = synchronizedCollectProcessor(subQueries)
    if (!processWordRequests(processor, subQueryProcessor)) {
      return LayerResult.Stop
    }
    processQueryRequests(progress, subQueryQueryRequests, subQueryProcessor)
    return LayerResult.Ok(subQueries)
  }

  private fun processWordRequests(processor: Processor<in T>, subQueryProcessor: Processor<in Query<out T>>): Boolean {
    if (wordRequests.isEmpty() && subQueryWordRequests.isEmpty()) {
      return true
    }
    val allRequests: Collection<RequestAndProcessors> = distributeWordRequests(processor, subQueryProcessor)
    val globals = SmartList<RequestAndProcessors>()
    val locals = SmartList<RequestAndProcessors>()
    for (requestAndProcessor: RequestAndProcessors in allRequests) {
      if (requestAndProcessor.request.searchScope is LocalSearchScope) {
        locals += requestAndProcessor
      }
      else {
        globals += requestAndProcessor
      }
    }
    return processGlobalRequests(globals)
           && processLocalRequests(locals)
  }

  private fun distributeWordRequests(processor: Processor<in T>,
                                     subQueryProcessor: Processor<in Query<out T>>): Collection<RequestAndProcessors> {
    val theMap = LinkedHashMap<
      WordRequestInfo,
      Pair<
        MutableCollection<OccurrenceProcessor>,
        MutableMap<LanguageInfo, MutableCollection<OccurrenceProcessor>>
        >
      >()

    fun <X> distribute(requests: Collection<WordRequest<X>>, processor: Processor<in X>) {
      for (wordRequest: WordRequest<X> in requests) {
        progress.checkCanceled()
        val byRequest = theMap.getOrPut(wordRequest.searchWordRequest) {
          Pair(SmartList(), LinkedHashMap())
        }
        val occurrenceProcessors: MutableCollection<OccurrenceProcessor> = when (val injectionInfo = wordRequest.injectionInfo) {
          InjectionInfo.NoInjection -> byRequest.first
          is InjectionInfo.InInjection -> byRequest.second.getOrPut(injectionInfo.languageInfo) {
            SmartList()
          }
        }
        occurrenceProcessors += wordRequest.occurrenceProcessor(processor)
      }
    }

    distribute(wordRequests, processor)
    distribute(subQueryWordRequests, subQueryProcessor)

    return theMap.map { (wordRequest: WordRequestInfo, byRequest) ->
      progress.checkCanceled()
      val (hostProcessors, injectionProcessors) = byRequest
      RequestAndProcessors(wordRequest, RequestProcessors(hostProcessors, injectionProcessors))
    }
  }

  private fun processGlobalRequests(globals: Collection<RequestAndProcessors>): Boolean {
    if (globals.isEmpty()) {
      return true
    }
    else if (globals.size == 1) {
      return processSingleRequest(globals.first())
    }

    val globalsIds: Map<Set<IdIndexEntry>, List<WordRequestInfo>> = globals.groupBy(
      { (request: WordRequestInfo, _) -> PsiSearchHelperImpl.getWordEntries(request.word, request.isCaseSensitive).toSet() },
      { (request: WordRequestInfo, _) -> progress.checkCanceled(); request }
    )
    return myHelper.processGlobalRequests(globalsIds, progress, scopeProcessors(globals))
  }

  private fun scopeProcessors(globals: Collection<RequestAndProcessors>): Map<WordRequestInfo, Processor<in PsiElement>> {
    val result = THashMap<WordRequestInfo, Processor<in PsiElement>>()
    for (requestAndProcessors: RequestAndProcessors in globals) {
      progress.checkCanceled()
      result[requestAndProcessors.request] = scopeProcessor(requestAndProcessors)
    }
    return result
  }

  private fun scopeProcessor(requestAndProcessors: RequestAndProcessors): Processor<in PsiElement> {
    val (request: WordRequestInfo, processors: RequestProcessors) = requestAndProcessors
    val searcher = StringSearcher(request.word, request.isCaseSensitive, true, false)
    val adapted = MyBulkOccurrenceProcessor(project, processors)
    return PsiSearchHelperImpl.localProcessor(searcher, adapted)
  }

  private fun processLocalRequests(locals: Collection<RequestAndProcessors>): Boolean {
    if (locals.isEmpty()) {
      return true
    }
    for (requestAndProcessors: RequestAndProcessors in locals) {
      progress.checkCanceled()
      if (!processSingleRequest(requestAndProcessors)) return false
    }
    return true
  }

  private fun processSingleRequest(requestAndProcessors: RequestAndProcessors): Boolean {
    val (request: WordRequestInfo, processors: RequestProcessors) = requestAndProcessors
    val options = EnumSet.of(PsiSearchHelperImpl.Options.PROCESS_ONLY_JAVA_IDENTIFIERS_IF_POSSIBLE)
    if (request.isCaseSensitive) options.add(PsiSearchHelperImpl.Options.CASE_SENSITIVE_SEARCH)

    return myHelper.bulkProcessElementsWithWord(
      request.searchScope,
      request.word,
      request.searchContext,
      options,
      request.containerName,
      MyBulkOccurrenceProcessor(project, processors)
    )
  }
}

private fun <R> processQueryRequests(progress: ProgressIndicator,
                                     requests: Collection<QueryRequest<*, R>>,
                                     processor: Processor<in R>): Boolean {
  if (requests.isEmpty()) {
    return true
  }
  val map: Map<Query<*>, List<Transformation<*, R>>> = requests.groupBy({ it.query }, { it.transformation })
  for ((query: Query<*>, transforms: List<Transformation<*, R>>) in map.iterator()) {
    progress.checkCanceled()
    @Suppress("UNCHECKED_CAST")
    if (!runQueryRequest(query as Query<Any>, transforms as Collection<Transformation<Any, R>>, processor)) {
      return false
    }
  }
  return true
}

private fun <B, R> runQueryRequest(query: Query<out B>,
                                   transformations: Collection<Transformation<B, R>>,
                                   processor: Processor<in R>): Boolean {
  return query.forEach(fun(baseValue: B): Boolean {
    for (transformation: Transformation<B, R> in transformations) {
      for (resultValue: R in transformation(baseValue)) {
        if (!processor.process(resultValue)) {
          return false
        }
      }
    }
    return true
  })
}

private fun <T> synchronizedCollectProcessor(subQueries: MutableCollection<in T>): Processor<T> {
  val lock = ObjectUtils.sentinel("synchronizedCollectProcessor")
  return Processor {
    synchronized(lock) {
      subQueries += it
    }
    true
  }
}

private data class RequestAndProcessors(
  val request: WordRequestInfo,
  val processors: RequestProcessors
)

internal class RequestProcessors(
  val hostProcessors: Collection<OccurrenceProcessor>,
  val injectionProcessors: Map<LanguageInfo, Collection<OccurrenceProcessor>>
)
