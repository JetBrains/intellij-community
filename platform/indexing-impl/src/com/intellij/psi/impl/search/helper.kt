// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.model.search.SearchParameters
import com.intellij.model.search.Searcher
import com.intellij.model.search.impl.*
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClassExtension
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchSession
import com.intellij.util.Processor
import com.intellij.util.Query
import com.intellij.util.SmartList
import com.intellij.util.text.StringSearcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

private val searchersExtension = ClassExtension<Searcher<*, *>>("com.intellij.searcher")

@Suppress("UNCHECKED_CAST")
internal fun <R : Any> searchers(parameters: SearchParameters<R>): List<Searcher<SearchParameters<R>, R>> {
  return searchersExtension.forKey(parameters.javaClass) as List<Searcher<SearchParameters<R>, R>>
}

internal val indicatorOrEmpty: ProgressIndicator
  get() = EmptyProgressIndicator.notNullize(ProgressIndicatorProvider.getGlobalProgressIndicator())

fun <R> runSearch(cs: CoroutineScope, project: Project, query: Query<R>): ReceiveChannel<R> {
  @Suppress("EXPERIMENTAL_API_USAGE")
  return cs.produce(capacity = Channel.UNLIMITED) {
    runUnderIndicator {
      runSearch(project, query, Processor {
        require(channel.trySend(it).isSuccess)
        true
      })
    }
  }
}

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
  val wordRequests = SmartList<WordRequest<R>>()

  while (queue.isNotEmpty()) {
    progress.checkCanceled()
    val query: Query<out R> = queue.remove()
    val primitives: Requests<R> = decompose(query)
    queryRequests.addAll(primitives.queryRequests)
    wordRequests.addAll(primitives.wordRequests)
    for (parametersRequest in primitives.parametersRequests) {
      progress.checkCanceled()
      handleParamRequest(progress, parametersRequest) {
        queue.offer(it)
      }
    }
  }

  return Layer(project, progress, queryRequests, wordRequests)
}

private fun <B, R> handleParamRequest(progress: ProgressIndicator,
                                      request: ParametersRequest<B, R>,
                                      queue: (Query<out R>) -> Unit) {
  val searchRequests: Collection<Query<out B>> = collectSearchRequests(request.params)
  for (query: Query<out B> in searchRequests) {
    progress.checkCanceled()
    queue(XQuery(query, request.transformation))
  }
}

private fun <R : Any> collectSearchRequests(parameters: SearchParameters<R>): Collection<Query<out R>> {
  return DumbService.getInstance(parameters.project).runReadActionInSmartMode(Computable {
    if (parameters.areValid()) {
      doCollectSearchRequests(parameters)
    }
    else {
      emptyList()
    }
  })
}

private fun <R : Any> doCollectSearchRequests(parameters: SearchParameters<R>): Collection<Query<out R>> {
  val queries = ArrayList<Query<out R>>()
  queries.add(SearchersQuery(parameters))
  val searchers = searchers(parameters)
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
  private val wordRequests: Collection<WordRequest<T>>
) {

  private val myHelper = PsiSearchHelper.getInstance(project) as PsiSearchHelperImpl

  fun runLayer(processor: Processor<in T>): LayerResult<T> {
    val subQueries = Collections.synchronizedList(ArrayList<Query<out T>>())
    val xProcessor = Processor<XResult<T>> { result ->
      when (result) {
        is ValueResult -> processor.process(result.value)
        is QueryResult -> {
          subQueries.add(result.query)
          true
        }
      }
    }
    if (!processQueryRequests(progress, queryRequests, xProcessor)) {
      return LayerResult.Stop
    }
    if (!processWordRequests(xProcessor)) {
      return LayerResult.Stop
    }
    return LayerResult.Ok(subQueries)
  }

  private fun processWordRequests(processor: Processor<in XResult<T>>): Boolean {
    if (wordRequests.isEmpty()) {
      return true
    }
    val allRequests: Collection<RequestAndProcessors> = distributeWordRequests(processor)
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

  private fun distributeWordRequests(processor: Processor<in XResult<T>>): Collection<RequestAndProcessors> {
    val theMap = LinkedHashMap<
      WordRequestInfo,
      Pair<
        MutableCollection<OccurrenceProcessor>,
        MutableMap<LanguageInfo, MutableCollection<OccurrenceProcessor>>
        >
      >()

    for (wordRequest: WordRequest<T> in wordRequests) {
      progress.checkCanceled()
      val occurrenceProcessor: OccurrenceProcessor = wordRequest.occurrenceProcessor(processor)
      val byRequest = theMap.getOrPut(wordRequest.searchWordRequest) {
        Pair(SmartList(), LinkedHashMap())
      }
      val injectionInfo = wordRequest.injectionInfo
      if (injectionInfo == InjectionInfo.NoInjection || injectionInfo == InjectionInfo.IncludeInjections) {
        byRequest.first.add(occurrenceProcessor)
      }
      if (injectionInfo is InjectionInfo.InInjection || injectionInfo == InjectionInfo.IncludeInjections) {
        val languageInfo: LanguageInfo = if (injectionInfo is InjectionInfo.InInjection) {
          injectionInfo.languageInfo
        }
        else {
          LanguageInfo.NoLanguage
        }
        byRequest.second.getOrPut(languageInfo) { SmartList() }.add(occurrenceProcessor)
      }
    }

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

    val globalsIds: Map<PsiSearchHelperImpl.TextIndexQuery, List<WordRequestInfo>> = globals.groupBy(
      { (request: WordRequestInfo, _) -> PsiSearchHelperImpl.TextIndexQuery.fromWord(request.word, request.isCaseSensitive, null) },
      { (request: WordRequestInfo, _) -> progress.checkCanceled(); request }
    )
    return myHelper.processGlobalRequests(globalsIds, progress, scopeProcessors(globals))
  }

  private fun scopeProcessors(globals: Collection<RequestAndProcessors>): Map<WordRequestInfo, Processor<in PsiElement>> {
    val result = HashMap<WordRequestInfo, Processor<in PsiElement>>()
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
      SearchSession(),
      MyBulkOccurrenceProcessor(project, processors)
    )
  }
}

private fun <R> processQueryRequests(progress: ProgressIndicator,
                                     requests: Collection<QueryRequest<*, R>>,
                                     processor: Processor<in XResult<R>>): Boolean {
  if (requests.isEmpty()) {
    return true
  }
  val map: Map<Query<*>, List<XTransformation<*, R>>> = requests.groupBy({ it.query }, { it.transformation })
  for ((query: Query<*>, transforms: List<XTransformation<*, R>>) in map.iterator()) {
    progress.checkCanceled()
    @Suppress("UNCHECKED_CAST")
    if (!runQueryRequest(query as Query<Any>, transforms as Collection<XTransformation<Any, R>>, processor)) {
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

private data class RequestAndProcessors(
  val request: WordRequestInfo,
  val processors: RequestProcessors
)

internal class RequestProcessors(
  val hostProcessors: Collection<OccurrenceProcessor>,
  val injectionProcessors: Map<LanguageInfo, Collection<OccurrenceProcessor>>
)
