// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.model.search.SearchParameters
import com.intellij.model.search.TextOccurrence
import com.intellij.model.search.impl.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.TextOccurenceProcessor
import com.intellij.util.Processor
import com.intellij.util.Query
import com.intellij.util.SmartList
import com.intellij.util.containers.cancellable
import com.intellij.util.text.StringSearcher
import gnu.trove.THashMap
import java.util.*
import java.util.function.Consumer

private val LOG: Logger = logger(::LOG)

fun <R> runSearch(parameters: SearchParameters<R>, processor: Processor<in R>): Boolean {
  val initialQueries = paramsQueries(parameters)
  return runSearch(parameters.project, initialQueries, processor)
}

private fun <R> runSearch(project: Project, queries: Collection<Query<out R>>, processor: Processor<in R>): Boolean {
  val progress = indicatorOrEmpty
  var currentQueries = queries
  while (currentQueries.isNotEmpty()) {
    progress.checkCanceled()
    val layer = buildLayer(progress, project, currentQueries)
    val layerResult = layer.runLayer(processor)
    when (layerResult) {
      is Result.Ok -> currentQueries = layerResult.subqueries
      is Result.Stop -> return false
    }
  }
  return true
}

private fun <R> buildLayer(progress: ProgressIndicator, project: Project, queries: Collection<Query<out R>>): Layer<out R> {
  val queue = LinkedList<Query<out R>>()
  queue.addAll(queries)

  val queryRequests = HashMap<Query<*>, MutableCollection<Transformation<*, R>>>()
  val wordRequests = LinkedList<WordRequest<out R>>()
  val subqueryRequests = LinkedList<SubqueryRequest<*, *, out R>>()

  while (queue.isNotEmpty()) {
    progress.checkCanceled()
    val query = queue.remove()
    val flatRequests = decompose(query)
    for (queryRequest in flatRequests.queryRequests) {
      queryRequests.getOrPut(queryRequest.query) { SmartList() }.add(queryRequest.transformation)
    }
    wordRequests.addAll(flatRequests.wordRequests)
    subqueryRequests.addAll(flatRequests.subqueryRequests)
    for (parametersRequest: ParametersRequest<*, out R> in flatRequests.parametersRequests.cancellable(progress)) {
      handleParamRequest(progress, parametersRequest) { queue.offer(it) }
    }
  }

  return Layer(project, progress, queryRequests, wordRequests, subqueryRequests)
}

private fun <B, R> handleParamRequest(progress: ProgressIndicator,
                                      request: ParametersRequest<B, out R>,
                                      queue: (Query<out R>) -> Unit) {
  val parameters: SearchParameters<B> = request.params
  val paramsQueries: Collection<Query<out B>> = paramsQueries(parameters)
  for (paramsQuery in paramsQueries.cancellable(progress)) {
    queue(TransformingQuery(paramsQuery, request.transformation))
  }
}

private fun <R> paramsQueries(parameters: SearchParameters<R>): Collection<Query<out R>> {
  val subQueries = LinkedList<Query<out R>>()
  DumbService.getInstance(parameters.project).runReadActionInSmartMode {
    SearchRequestors.collectSearchRequests(parameters, Consumer {
      subQueries.add(it)
    })
  }
  return subQueries
}

private sealed class Result<out T> {
  object Stop : Result<Nothing>()
  class Ok<T>(val subqueries: Collection<Query<out T>>) : Result<T>()
}

private class Layer<T>(
  project: Project,
  private val progress: ProgressIndicator,
  private val queryRequests: Map<Query<*>, Collection<Transformation<*, T>>>,
  private val wordRequests: Collection<WordRequest<out T>>,
  private val subqueryRequests: Collection<SubqueryRequest<*, *, out T>>
) {

  private val myHelper = PsiSearchHelper.getInstance(project) as PsiSearchHelperImpl

  fun runLayer(processor: Processor<in T>): Result<T> {
    if (!processQueryRequests(progress, queryRequests, processor)) {
      return Result.Stop
    }
    if (!processWordRequests(processor)) {
      return Result.Stop
    }
    val subQueries = SmartList<Query<out T>>()
    runSubqueryRequests(subqueryRequests.cancellable(progress)) {
      subQueries.add(it)
    }
    return Result.Ok(subQueries)
  }

  private fun processWordRequests(processor: Processor<in T>): Boolean {
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

    val globalsIds: Map<Set<IdIndexEntry>, List<SearchWordRequest>> = globals.keys.groupBy {
      PsiSearchHelperImpl.getWordEntries(it.word, it.caseSensitive).toSet()
    }
    return myHelper.processGlobalRequests(globalsIds, progress, buildLocalProcessors(progress, globals))
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
    val options = EnumSet.of(PsiSearchHelperImpl.Options.PROCESS_ONLY_JAVA_IDENTIFIERS_IF_POSSIBLE)
    if (request.caseSensitive) options.add(PsiSearchHelperImpl.Options.CASE_SENSITIVE_SEARCH)
    if (request.shouldProcessInjectedPsi()) options.add(PsiSearchHelperImpl.Options.PROCESS_INJECTED_PSI)

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

private typealias OccurrenceProcessor = Processor<in TextOccurrence>

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

private fun buildLocalProcessors(progress: ProgressIndicator,
                                 globals: Map<SearchWordRequest, Collection<Processor<in TextOccurrence>>>): Map<SearchWordRequest, Processor<PsiElement>> {
  val result = THashMap<SearchWordRequest, Processor<PsiElement>>()
  globals.forEach { request, processors ->
    progress.checkCanceled()

    val searcher = StringSearcher(request.word, request.caseSensitive, true, false)
    val adapted = adaptProcessors(request.shouldProcessInjectedPsi(), processors)
    val localProcessor = PsiSearchHelperImpl.localProcessor(progress, searcher, adapted)

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
