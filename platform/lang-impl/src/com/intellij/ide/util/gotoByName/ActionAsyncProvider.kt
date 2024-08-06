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
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.switcher.QuickActionProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel.Factory.RENDEZVOUS
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import kotlin.coroutines.coroutineContext

private val LOG = logger<ActionAsyncProvider>()

@OptIn(ExperimentalCoroutinesApi::class)
internal class ActionAsyncProvider(private val model: GotoActionModel) {
  private val actionManager: ActionManager = ActionManager.getInstance()
  private val intentions = ConcurrentHashMap<String, ApplyIntentionAction>()

  fun processActions(
    scope: CoroutineScope,
    presentationProvider: suspend (AnAction) -> Presentation,
    pattern: String,
    ids: Set<String>,
    consumer: suspend (MatchedValue) -> Boolean,
  ) {

    scope.launch {
      val channel = produce {
        flattenConcat(listOf(
          matchedActionsAndStubsFlow(pattern = pattern, allIds = ids, presentationProvider = presentationProvider),
          unmatchedStubsFlow(pattern = pattern, allIds = ids, presentationProvider = presentationProvider),
        )).collect { send(it) }
      }

      try {
        for (i in channel) {
          if (!consumer(i)) return@launch
        }
      }
      finally {
        channel.cancel()
      }
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

    val comparator: Comparator<MatchedValue> = Comparator { o1, o2 -> o1.compareWeights(o2) }

    scope.launch {
      val channel = produce {
        flattenConcat(listOf(
          abbreviationsFlow(pattern, presentationProvider).sorted(comparator),
          matchedActionsAndStubsFlow(pattern, actionIds, presentationProvider),
          unmatchedStubsFlow(pattern, actionIds, presentationProvider),
          topHitsFlow(pattern, presentationProvider).sorted(comparator),
          intentionsFlow(pattern, presentationProvider).sorted(comparator),
          optionsFlow(pattern, presentationProvider).sorted(comparator)
        )).collect { send(it) }
      }

      try {
        for (i in channel) {
          if (!consumer(i)) return@launch
        }
      }
      finally {
        channel.cancel()
      }
    }
  }

  private fun <T> Flow<T>.sorted(comparator: Comparator<in T>): Flow<T> {
    val sourceFlow = this
    return flow {
      val list = sourceFlow.catch { e -> LOG.error("Error while collecting actions.", e) }
        .toSet()
        .toMutableList()
      list.sortWith(comparator)
      list.forEach { emit(it) }
    }
  }

  private suspend fun abbreviationsFlow(pattern: String, presentationProvider: suspend (AnAction) -> Presentation): Flow<MatchedValue> {
    LOG.debug { "Create abbreviations flow ($pattern)" }
    val matcher = buildWeightMatcher(pattern)
    val actionIds = serviceAsync<AbbreviationManager>().findActions(pattern)

    return actionIds.asFlow()
      .mapNotNull { loadAction(it) }
      .buffer(RENDEZVOUS)
      .map {
        val presentation = presentationProvider(it)
        val wrapper = wrapAnAction(it, presentation)
        val degree = matcher.matchingDegree(pattern)
        abbreviationMatchedValue(wrapper, pattern, degree)
      }
  }

  private fun matchedActionsAndStubsFlow(
    pattern: String,
    allIds: Collection<String>,
    presentationProvider: suspend (AnAction) -> Presentation,
  ): Flow<MatchedValue> {
    val weightMatcher = buildWeightMatcher(pattern)

    return flow {
      val list = collectMatchedActions(pattern, allIds, weightMatcher)
      LOG.debug { "List is collected" }

      for (matchedAction in list) {
        val action = matchedAction.action
        if (action is ActionStubBase) {
          loadAction(action.id)?.let { loaded -> emit(MatchedAction(loaded, matchedAction.mode, matchedAction.weight)) }
        }
        else {
          emit(matchedAction)
        }
      }
    }
      .buffer(RENDEZVOUS)
      .map { matchedAction ->
        val presentation = presentationProvider(matchedAction.action)
        matchItem(
          item = wrapAnAction(action = matchedAction.action, presentation = presentation, matchMode = matchedAction.mode),
          matcher = weightMatcher,
          pattern = pattern,
          matchType = MatchedValueType.ACTION,
        )
      }
  }

  private suspend fun collectMatchedActions(pattern: String, allIds: Collection<String>, weightMatcher: MinusculeMatcher): List<MatchedAction> = coroutineScope {
    val matcher = buildMatcher(pattern)

    val mainActions: List<AnAction> = allIds.mapNotNull {
      val action = actionManager.getActionOrStub(it) ?: return@mapNotNull null
      if (action is ActionGroup && !action.isSearchable) return@mapNotNull null

      return@mapNotNull action
    }
    val extendedActions = model.dataContext.getData(QuickActionProvider.KEY)?.getActions(true) ?: emptyList()
    val actions = (mainActions + extendedActions).mapNotNull {
      runCatching {
        val mode = model.actionMatches(pattern, matcher, it)
        if (mode != MatchMode.NONE) {
          val weight = calcElementWeight(it, pattern, weightMatcher)
          return@runCatching(MatchedAction(it, mode, weight))
        }
        return@runCatching null
      }.getOrLogException(LOG)
    }

    val comparator = Comparator.comparing<MatchedAction, Int> { it.weight ?: 0 }.reversed()
    return@coroutineScope actions.sortedWith(comparator)
  }

  private fun unmatchedStubsFlow(pattern: String, allIds: Collection<String>,
                                 presentationProvider: suspend (AnAction) -> Presentation): Flow<MatchedValue> {
    val matcher = buildMatcher(pattern)
    val weightMatcher = buildWeightMatcher(pattern)

    return allIds.asFlow()
      .mapNotNull {
        val action = actionManager.getActionOrStub(it) ?: return@mapNotNull null
        if (action is ActionGroup && !action.isSearchable) return@mapNotNull null
        action
      }
      .filter {
        runCatching { (it is ActionStubBase) && model.actionMatches(pattern, matcher, it) == MatchMode.NONE }
          .getOrLogException(LOG) == true
      }
      .transform {
        runCatching {
          val action = loadAction((it as ActionStubBase).id) ?: return@runCatching
          val mode = model.actionMatches(pattern, matcher, action)
          if (mode != MatchMode.NONE) {
            val weight = calcElementWeight(element = action, pattern = pattern, matcher = weightMatcher)
            emit(MatchedAction(action = action, mode = mode, weight = weight))
          }
        }.getOrLogException(LOG)
      }
      .buffer(RENDEZVOUS)
      .map { matchedAction ->
        val presentation = presentationProvider(matchedAction.action)
        val item = wrapAnAction(matchedAction.action, presentation, matchedAction.mode)
        matchItem(item = item, matcher = weightMatcher, pattern = pattern, matchType = MatchedValueType.ACTION)
      }
  }

  private fun topHitsFlow(
    pattern: String,
    presentationProvider: suspend (AnAction) -> Presentation,
  ): Flow<MatchedValue> {
    LOG.debug { "Create TopHits flow ($pattern)" }

    val project = model.project
    val commandAccelerator = SearchTopHitProvider.getTopHitAccelerator()
    val matcher = buildWeightMatcher(pattern)

    return channelFlow {
      val collector = Consumer<Any> { item ->
        launch {
          val obj = (item as? AnAction)?.let { wrapAnAction(action = it, presentation = presentationProvider(it)) } ?: item
          val matchedValue = matchItem(item = obj, matcher = matcher, pattern = pattern, matchType = MatchedValueType.TOP_HIT)
          send(matchedValue)
        }
      }

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
            collector = {
              send(matchItem(item = it, matcher = matcher, pattern = pattern, matchType = MatchedValueType.TOP_HIT))
            },
            project = project,
          )
        }
        provider.consumeTopHits(pattern, collector, project)
      }
    }
  }

  private fun intentionsFlow(
    pattern: String,
    presentationProvider: suspend (AnAction) -> Presentation,
  ): Flow<MatchedValue> {
    LOG.debug { "Create intentions flow ($pattern)" }
    val matcher = buildMatcher(pattern)
    val weightMatcher = buildWeightMatcher(pattern)

    return channelFlow {
      launch {
        for ((text, action) in getIntentionsMap()) {
          if (model.actionMatches(pattern, matcher, action) != MatchMode.NONE) {
            val groupMapping = GroupMapping.createFromText(text, false)
            send(ActionWrapper(action, groupMapping, MatchMode.INTENTION, presentationProvider(action)))
          }
        }
      }
    }
      .map { matchItem(it, weightMatcher, pattern, MatchedValueType.INTENTION) }
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

  private suspend fun optionsFlow(
    pattern: String,
    presentationProvider: suspend (AnAction) -> Presentation,
  ): Flow<MatchedValue> {
    LOG.debug { "Create options flow ($pattern)" }

    val weightMatcher = buildWeightMatcher(pattern)

    val map = model.configurablesNames
    val registrar = serviceAsync<SearchableOptionsRegistrar>() as SearchableOptionsRegistrarImpl

    val words = registrar.getProcessedWords(pattern)
    val filterOutInspections = Registry.`is`("go.to.action.filter.out.inspections", true)

    @Suppress("RemoveExplicitTypeArguments")
    return channelFlow<Any> {
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
        for (description in optionDescriptions) {
          for (converter in ActionFromOptionDescriptorProvider.EP.extensionList) {
            val action = converter.provide(description) ?: continue
            send(ActionWrapper(action, null, MatchMode.NAME, presentationProvider(action)))
          }
          send(description)
        }
      }
    }
      .map { matchItem(item = it, matcher = weightMatcher, pattern = pattern, matchType = MatchedValueType.TOP_HIT) }
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

  private fun wrapAnAction(action: AnAction, presentation: Presentation, matchMode: MatchMode = MatchMode.NAME): ActionWrapper {
    val groupMapping = model.getGroupMapping(action)
    groupMapping?.updateBeforeShow(model.updateSession)
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

/**
 * Creates a unified [Flow] by efficiently combining multiple [Flow]s in a flattened, concatenated format.
 * The conventional {code flattenConcat} method from the standard coroutines library isn't suitable in this context for the reasons below:
 *
 *  - Initialization of the combined flows is resource-intensive, leading to a significant delay between the invocation of {code collect()}
 *  and the emission of the first item.
 *
 *  - The standard {code flattenConcat} method generates subsequent flows only after prior data has been sequentially collected. This mechanism forces us to
 *  wait for completion of each flow's initialization process during the transition between flows.
 *
 *  - Contrastingly, this function initiates a collection of all the flows concurrently using parallel coroutines, thereby accomplishing flow initialization in parallel,
 *  significantly enhancing the process's efficiency.
 * @param flows The list of [Flow]s to flatten and concatenate.
 * @return The flattened and concatenated [Flow].
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> flattenConcat(flows: List<Flow<T>>): Flow<T> = channelFlow {
  flows.map { flow -> produce { flow.collect { send(it) } } }
    .forEach { ch -> for (i in ch) send(i) }
}

