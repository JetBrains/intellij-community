// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.ide.SearchTopHitProvider
import com.intellij.ide.actions.ApplyIntentionAction
import com.intellij.ide.ui.OptionsSearchTopHitProvider
import com.intellij.ide.ui.OptionsTopHitProvider.ProjectLevelProvidersAdapter
import com.intellij.ide.ui.search.ActionFromOptionDescriptorProvider
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.ui.search.SearchableOptionsRegistrar
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl
import com.intellij.ide.util.gotoByName.GotoActionModel.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.platform.util.coroutines.mapConcurrent
import com.intellij.platform.util.coroutines.mapNotNullConcurrent
import com.intellij.platform.util.coroutines.transformConcurrent
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.switcher.QuickActionProvider
import com.intellij.util.CollectConsumer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.toList
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.sequences.forEach

private val LOG = logger<ActionAsyncProvider>()

@OptIn(ExperimentalCoroutinesApi::class)
internal class ActionAsyncProvider(private val model: GotoActionModel) {
  private val actionManager: ActionManager = ActionManager.getInstance()
  private val intentions = ConcurrentHashMap<String, ApplyIntentionAction>()
  private val MATCHED_VALUE_COMPARATOR = Comparator<MatchedValue> { o1, o2 -> o1.compareWeights(o2) }

  fun processActions(
    scope: CoroutineScope,
    presentationProvider: suspend (AnAction) -> Presentation,
    pattern: String,
    ids: Set<String>,
    consumer: suspend (MatchedValue) -> Boolean,
  ) {
    scope.launch {
      val nonMatchedIdsChannel = Channel<String>(capacity = Channel.UNLIMITED)
      val matchedStubsJob = processMatchedActionsAndStubs(pattern, ids, presentationProvider, consumer, nonMatchedIdsChannel, null)
      processUnmatchedStubs(nonMatchedIdsChannel, pattern, presentationProvider, consumer, matchedStubsJob)
    }
  }

  fun filterElements(
    scope: CoroutineScope,
    presentationProvider: suspend (AnAction) -> Presentation,
    pattern: String,
    consumer: suspend (MatchedValue) -> Boolean,
  ) {
    if (pattern.isEmpty()) return

    LOG.debug { "Start actions searching ($pattern)" }

    val actionIds = (actionManager as ActionManagerImpl).actionIds

    scope.launch {
      val abbreviationsJob = processAbbreviations(pattern, presentationProvider, consumer)

      val nonMatchedIdsChannel = Channel<String>(capacity = Channel.UNLIMITED)
      val matchedStubsJob = processMatchedActionsAndStubs(pattern, actionIds, presentationProvider, consumer, nonMatchedIdsChannel, abbreviationsJob)
      val unmatchedStubsJob = processUnmatchedStubs(nonMatchedIdsChannel, pattern, presentationProvider, consumer, matchedStubsJob)

      val topHitsJob = processTopHits(pattern, presentationProvider, consumer, unmatchedStubsJob)
      val intentionsJob = processIntentions(pattern, presentationProvider, consumer, topHitsJob)
      processOptions(pattern, presentationProvider, consumer, intentionsJob)
    }
  }

  private fun CoroutineScope.processAbbreviations(pattern: String,
                                                  presentationProvider: suspend (AnAction) -> Presentation,
                                                  consumer: suspend (MatchedValue) -> Boolean
  ): Job = launch {
    LOG.debug { "Process abbreviations for \"$pattern\"" }
    val matcher = buildWeightMatcher(pattern)
    val actionIds = serviceAsync<AbbreviationManager>().findActions(pattern)

    actionIds.forEachConcurrent { id ->
      val action = loadAction(id) ?: return@forEachConcurrent
      val wrapper = wrapAnAction(action, presentationProvider)
      val degree = matcher.matchingDegree(pattern)
      val matchedValue = abbreviationMatchedValue(wrapper, pattern, degree)
      if (!consumer(matchedValue)) cancel()
    }
  }

  private fun CoroutineScope.processMatchedActionsAndStubs(pattern: String,
                                                           allIds: Collection<String>,
                                                           presentationProvider: suspend (AnAction) -> Presentation,
                                                           consumer: suspend (MatchedValue) -> Boolean,
                                                           unmatchedIdsChannel: SendChannel<String>,
                                                           awaitJob: Job?
  ): Job = launch {
    val weightMatcher = buildWeightMatcher(pattern)

    val list = collectMatchedActions(pattern, allIds, weightMatcher, unmatchedIdsChannel)
    LOG.debug { "Matched actions list is collected" }

    awaitJob?.join() //wait until all items from previous step are processed
    LOG.debug { "Process matched actions for \"$pattern\"" }
    list.forEachConcurrentOrdered { matchedActionOrStub, awaitMyTurn ->
      val action = matchedActionOrStub.action
      val matchedAction = if (action is ActionStubBase) loadAction(action.id)?.let { MatchedAction(it, matchedActionOrStub.mode, matchedActionOrStub.weight) } else matchedActionOrStub
      if (matchedAction == null) return@forEachConcurrentOrdered
      val matchedValue = matchItem(
        item = wrapAnAction(action = matchedAction.action, presentationProvider = presentationProvider, matchMode = matchedAction.mode),
        matcher = weightMatcher,
        pattern = pattern,
        matchType = MatchedValueType.ACTION,
      )
      awaitMyTurn()
      if (!consumer(matchedValue)) cancel()
    }
  }

  private suspend fun <T> Collection<T>.forEachConcurrentOrdered(action: suspend (T, suspend () -> Unit) -> Unit) {
    suspend fun runConcurrent(item: T, jobToAwait: Job?): Job = coroutineScope {
      launch { action(item) { jobToAwait?.join() } }
    }

    var prevItemJob: Job? = null
    forEach { prevItemJob = runConcurrent(it, prevItemJob) }
  }

  private suspend fun collectMatchedActions(pattern: String, allIds: Collection<String>, weightMatcher: MinusculeMatcher, unmatchedIdsChannel: SendChannel<String>): List<MatchedAction> = coroutineScope {
    val matcher = buildMatcher(pattern)

    val mainActions: Sequence<AnAction> = allIds.asSequence().mapNotNull {
      val action = actionManager.getActionOrStub(it) ?: return@mapNotNull null
      if (action is ActionGroup && !action.isSearchable) return@mapNotNull null

      return@mapNotNull action
    }
    val extendedActions: Sequence<AnAction> = model.dataContext.getData(QuickActionProvider.KEY)?.getActions(true)?.asSequence() ?: emptySequence<AnAction>()
    val allActions: Sequence<AnAction> = mainActions + extendedActions + extendedActions.flatMap { (it as? ActionGroup)?.let { model.updateSession.children(it) } ?: emptyList() }
    val matchedActions = produce(capacity = Channel.UNLIMITED) {
      allActions.forEach { action ->
        launch {
          runCatching {
            val mode = model.actionMatches(pattern, matcher, action)
            if (mode != MatchMode.NONE) {
              val weight = calcElementWeight(action, pattern, weightMatcher)
              send(MatchedAction(action, mode, weight))
            }
            else {
              if (action is ActionStubBase) actionManager.getId(action)?.let { unmatchedIdsChannel.send(it) }
            }
          }.getOrLogException(LOG)
        }
      }
    }.toList()
    unmatchedIdsChannel.close()

    val comparator = Comparator.comparing<MatchedAction, Int> { it.weight ?: 0 }.reversed()
    return@coroutineScope matchedActions.sortedWith(comparator)
  }

  private fun CoroutineScope.processUnmatchedStubs(nonMatchedIds: ReceiveChannel<String>,
                                                   pattern: String,
                                                   presentationProvider: suspend (AnAction) -> Presentation,
                                                   consumer: suspend (MatchedValue) -> Boolean,
                                                   awaitJob: Job
  ): Job = launch {
    val matcher = buildMatcher(pattern)
    val weightMatcher = buildWeightMatcher(pattern)

    val matchedActions = nonMatchedIds.toList().mapNotNullConcurrent { id ->
      runCatching {
        val action = loadAction(id) ?: return@runCatching null
        if (action is ActionGroup && !action.isSearchable) return@runCatching null

        val mode = model.actionMatches(pattern, matcher, action)
        if (mode == MatchMode.NONE) return@runCatching null

        val weight = calcElementWeight(element = action, pattern = pattern, matcher = weightMatcher)
        val matchedAction = MatchedAction(action = action, mode = mode, weight = weight)
        val item = wrapAnAction(matchedAction.action, presentationProvider, matchedAction.mode)
        val matchedValue = matchItem(item = item, matcher = weightMatcher, pattern = pattern, matchType = MatchedValueType.ACTION)
        return@runCatching matchedValue
      }.getOrLogException(LOG)
    }.sortedWith(MATCHED_VALUE_COMPARATOR)

    awaitJob.join() //wait until all items from previous step are processed
    LOG.debug { "Process unmatched stubs for \"$pattern\"" }
    matchedActions.forEach {
      if (!consumer(it)) cancel()
    }
  }

  private  fun CoroutineScope.processTopHits(pattern: String,
                                             presentationProvider: suspend (AnAction) -> Presentation,
                                             consumer: suspend (MatchedValue) -> Boolean,
                                             awaitJob: Job
  ): Job = launch {
    val project = model.project
    val commandAccelerator = SearchTopHitProvider.getTopHitAccelerator()
    val matcher = buildWeightMatcher(pattern)
    val collector = CollectConsumer<Any>()

    for (provider in SearchTopHitProvider.EP_NAME.extensionList) {
      @Suppress("DEPRECATION")
      if (provider is com.intellij.ide.ui.OptionsTopHitProvider.CoveredByToggleActions) {
        continue
      }

      if (provider is OptionsSearchTopHitProvider && !pattern.startsWith(commandAccelerator)) {
        val prefix = commandAccelerator + provider.getId() + " "
        provider.consumeTopHits(pattern = prefix + pattern, collector = collector, project = project)
      }
      else if (project != null && provider is ProjectLevelProvidersAdapter) {
        provider.consumeAllTopHits(
          pattern = pattern,
          collector = { collector.accept(it) },
          project = project,
        )
      }
      provider.consumeTopHits(pattern, collector, project)
    }

    val matchedValues = collector.result.mapConcurrent { item ->
      val obj = (item as? AnAction)?.let { wrapAnAction(action = it, presentationProvider = presentationProvider) } ?: item
      matchItem(item = obj, matcher = matcher, pattern = pattern, matchType = MatchedValueType.TOP_HIT)
    }.sortedWith(MATCHED_VALUE_COMPARATOR)

    awaitJob.join() //wait until all items from previous step are processed
    LOG.debug { "Process top hits for \"$pattern\"" }
    matchedValues.forEach { if (!consumer(it)) cancel() }
  }

  private fun CoroutineScope.processIntentions(pattern: String,
                                               presentationProvider: suspend (AnAction) -> Presentation,
                                               consumer: suspend (MatchedValue) -> Boolean,
                                               awaitJob: Job
  ): Job = launch {
    val matcher = buildMatcher(pattern)
    val weightMatcher = buildWeightMatcher(pattern)

    val matchedValues = getIntentionsMap().entries
      .mapNotNullConcurrent { (text, action) ->
        if (model.actionMatches(pattern, matcher, action) == MatchMode.NONE) return@mapNotNullConcurrent null

        val groupMapping = GroupMapping.createFromText(text, false)
        val wrapper = ActionWrapper(action, groupMapping, MatchMode.INTENTION, presentationProvider(action))
        matchItem(wrapper, weightMatcher, pattern, MatchedValueType.INTENTION)
      }
      .sortedWith(MATCHED_VALUE_COMPARATOR)

    awaitJob.join()
    LOG.debug { "Process intentions for \"$pattern\""}
    matchedValues.forEach { if (!consumer(it)) cancel() }
  }

  private suspend fun getIntentionsMap(): Map<String, ApplyIntentionAction> {
    if (intentions.isEmpty()) {
      val intentions = readAction {
        model.availableIntentions
      }

      this.intentions.putAll(intentions)
    }
    return intentions
  }

  private fun CoroutineScope.processOptions(pattern: String,
                                            presentationProvider: suspend (AnAction) -> Presentation,
                                            consumer: suspend (MatchedValue) -> Boolean,
                                            awaitJob: Job): Job = launch {
    val weightMatcher = buildWeightMatcher(pattern)

    val map = model.configurablesNames
    val registrar = serviceAsync<SearchableOptionsRegistrar>() as SearchableOptionsRegistrarImpl

    val words = registrar.getProcessedWords(pattern)
    val filterOutInspections = Registry.`is`("go.to.action.filter.out.inspections", true)

    // Use LinkedHashSet to preserve the order of the elements to iterate through them later
    val optionDescriptions = LinkedHashSet<OptionDescription>()

    if (pattern.isNotBlank()) {
      val matcher = buildMatcher(pattern)
      for ((key, value) in map) {
        if (matcher.matches(value)) {
          optionDescriptions.add(OptionDescription(_option = null, configurableId = key, hit = value, path = null, groupName = value))
        }
      }
    }

    var registrarDescriptions: MutableSet<OptionDescription>? = null
    registrar.initialize()
    for (word in words) {
      val descriptions = registrar.findAcceptableDescriptions(word)
        ?.filter {
          @Suppress("HardCodedStringLiteral")
          !(it.path == "ActionManager" || filterOutInspections && it.groupName == "Inspections")
        }
        ?.toHashSet()
      if (descriptions.isNullOrEmpty()) {
        registrarDescriptions = null
        break
      }

      if (registrarDescriptions == null) {
        registrarDescriptions = descriptions
      }
      else {
        registrarDescriptions.retainAll(descriptions)
      }
    }

    // Add registrar's options to the end of the `LinkedHashSet`
    // to guarantee that options from the `map` are going to be processed first
    if (registrarDescriptions != null) {
      optionDescriptions.addAll(registrarDescriptions)
    }

    if (optionDescriptions.isNotEmpty()) {
      val currentHits = HashSet<String>()
      val iterator = optionDescriptions.iterator()
      for (description in iterator) {
        val hit = description.hit
        if (hit == null || !currentHits.add(hit.trim())) {
          iterator.remove()
        }
      }


      val matchedValues = optionDescriptions.transformConcurrent { description ->
        for (converter in ActionFromOptionDescriptorProvider.EP.extensionList) {
          val action = converter.provide(description) ?: continue
          val actionWrapper = ActionWrapper(action, null, MatchMode.NAME, presentationProvider(action))
          out(matchItem(item = actionWrapper, matcher = weightMatcher, pattern = pattern, matchType = MatchedValueType.TOP_HIT))
        }
        out(matchItem(item = description, matcher = weightMatcher, pattern = pattern, matchType = MatchedValueType.TOP_HIT))
      }.sortedWith(MATCHED_VALUE_COMPARATOR)

      awaitJob.join()
      LOG.debug { "Process options for \"$pattern\""}
      matchedValues.forEach { if (!consumer(it)) cancel() }
    }
  }

  private suspend fun loadAction(id: String): AnAction? {
    return withContext(coroutineContext) {
      actionManager.getAction(id)
    }
  }

  private fun matchItem(item: Any, matcher: MinusculeMatcher, pattern: String, matchType: MatchedValueType): MatchedValue {
    val weight = calcElementWeight(element = item, pattern = pattern, matcher = matcher)
    return if (weight == null) MatchedValue(item, pattern, matchType) else MatchedValue(item, pattern, weight, matchType)
  }

  private suspend fun wrapAnAction(action: AnAction, presentationProvider: suspend (AnAction) -> Presentation, matchMode: MatchMode = MatchMode.NAME): ActionWrapper {
    val groupMapping = model.getGroupMapping(action)
    groupMapping?.updateBeforeShowSuspend(presentationProvider)
    val presentation = presentationProvider(action)
    return ActionWrapper(action, groupMapping, matchMode, presentation)
  }

  private fun buildWeightMatcher(pattern: String): MinusculeMatcher {
    return NameUtil.buildMatcher("*$pattern")
      .withCaseSensitivity(NameUtil.MatchingCaseSensitivity.NONE)
      .preferringStartMatches()
      .build()
  }

  fun clearIntentions() {
    intentions.clear()
  }
}

private fun abbreviationMatchedValue(wrapper: ActionWrapper, pattern: String, degree: Int): MatchedValue {
  return object : MatchedValue(wrapper, pattern, degree, MatchedValueType.ABBREVIATION) {
    override fun getValueText(): String = pattern
  }
}

private data class MatchedAction(@JvmField val action: AnAction, @JvmField val mode: MatchMode, @JvmField val weight: Int?)
