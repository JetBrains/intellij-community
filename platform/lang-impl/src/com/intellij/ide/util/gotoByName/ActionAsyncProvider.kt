// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.ide.SearchTopHitProvider
import com.intellij.ide.actions.ApplyIntentionAction
import com.intellij.ide.ui.OptionsSearchTopHitProvider
import com.intellij.ide.ui.OptionsTopHitProvider.CoveredByToggleActions
import com.intellij.ide.ui.OptionsTopHitProvider.ProjectLevelProvidersAdapter
import com.intellij.ide.ui.search.ActionFromOptionDescriptorProvider
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.ui.search.SearchableOptionsRegistrar
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl
import com.intellij.ide.util.gotoByName.GotoActionModel.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.Strings
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

class ActionAsyncProvider(private val myModel: GotoActionModel) {

  private val myActionManager: ActionManager = ActionManager.getInstance()
  private val myIntentions = ConcurrentHashMap<String, ApplyIntentionAction>()

  fun processActions(scope: CoroutineScope, presentationProvider: suspend (AnAction) -> Presentation, pattern: String,
                     ids: Set<String>, consumer: suspend (MatchedValue) -> Boolean) {
    scope.flowCollector(matchedActionsAndStubsFlow(pattern, ids, presentationProvider))
      .then(unmatchedStubsFlow(pattern, ids, presentationProvider))
      .collect(consumer)
  }

  fun filterElements(scope: CoroutineScope, presentationProvider: suspend (AnAction) -> Presentation,
                     pattern: String, consumer: suspend (MatchedValue) -> Boolean) {
    if (pattern.isEmpty()) return

    LOG.debug("Start actions searching ($pattern)")

    val actionIds = (myActionManager as ActionManagerImpl).actionIds

    val comparator: Comparator<MatchedValue> = Comparator { o1, o2 -> o1.compareWeights(o2) }

    scope.flowCollector(abbreviationsFlow(pattern, presentationProvider).sorted(comparator))
      .then(matchedActionsAndStubsFlow(pattern, actionIds, presentationProvider))
      .then(unmatchedStubsFlow(pattern, actionIds, presentationProvider))
      .then(topHitsFlow(pattern, presentationProvider).sorted(comparator))
      .then(intentionsFlow(pattern, presentationProvider).sorted(comparator))
      .then(optionsFlow(pattern, presentationProvider).sorted(comparator))
      .collect(consumer)
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

  private fun abbreviationsFlow(pattern: String, presentationProvider: suspend (AnAction) -> Presentation): Flow<MatchedValue> {
    LOG.debug("Create abbreviations flow ($pattern)")
    val matcher = buildWeightMatcher(pattern)
    val actionIds = AbbreviationManager.getInstance().findActions(pattern)

    return actionIds.asFlow()
      .mapNotNull { loadAction(it) }
      .buffer(RENDEZVOUS)
      .map {
        val presentation = presentationProvider(it)
        val wrapper: ActionWrapper = wrapAnAction(it, presentation)
        val degree = matcher.matchingDegree(pattern)
        abbreviationMatchedValue(wrapper, pattern, degree)
      }
  }

  private fun matchedActionsAndStubsFlow(pattern: String, allIds: Collection<String>,
                                                 presentationProvider: suspend (AnAction) -> Presentation): Flow<MatchedValue> {
    val weightMatcher = buildWeightMatcher(pattern)

    return flow {
      val list = collectMatchedActions(pattern, allIds, weightMatcher)
      LOG.debug("List is collected")

      list.forEach {
        val action = it.action
        if (action is ActionStubBase) {
          loadAction(action.id)?.let { loaded -> emit(MatchedAction(loaded, it.mode, it.weight)) }
        }
        else {
          emit(it)
        }
      }
    }
      .buffer(RENDEZVOUS)
      .map {
        val presentation = presentationProvider(it.action)
        wrapAnAction(it.action, presentation, it.mode)
      }
      .map { matchItem(it, weightMatcher, pattern, MatchedValueType.ACTION) }
  }

  private suspend fun collectMatchedActions(pattern: String, allIds: Collection<String>, weightMatcher: MinusculeMatcher): List<MatchedAction> = coroutineScope {
    val matcher = buildMatcher(pattern)

    val mainActions: List<AnAction> = allIds.mapNotNull {
      val action = myActionManager.getActionOrStub(it) ?: return@mapNotNull null
      if (action is ActionGroup && !action.isSearchable) return@mapNotNull null

      return@mapNotNull action
    }
    val extendedActions: List<AnAction> = myModel.dataContext.getData(QuickActionProvider.KEY)?.getActions(true) ?: emptyList<AnAction>()
    val actions = (mainActions + extendedActions).mapNotNull {
      runCatching {
        val mode = myModel.actionMatches(pattern, matcher, it)
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
        val action = myActionManager.getActionOrStub(it) ?: return@mapNotNull null
        if (action is ActionGroup && !action.isSearchable) return@mapNotNull null
        action
      }
      .filter {
        runCatching { (it is ActionStubBase) && myModel.actionMatches(pattern, matcher, it) == MatchMode.NONE }
          .getOrLogException(LOG) == true
      }
      .transform {
        runCatching {
          val action = loadAction((it as ActionStubBase).id) ?: return@runCatching
          val mode = myModel.actionMatches(pattern, matcher, action)
          if (mode != MatchMode.NONE) {
            val weight = calcElementWeight(action, pattern, weightMatcher)
            emit(MatchedAction(action, mode, weight))
          }
        }.getOrLogException(LOG)
      }
      .buffer(RENDEZVOUS)
      .map {
        val presentation = presentationProvider(it.action)
        wrapAnAction(it.action, presentation, it.mode)
      }
      .map { matchItem(it, weightMatcher, pattern, MatchedValueType.ACTION) }
  }

  private fun topHitsFlow(pattern: String,
                          presentationProvider: suspend (AnAction) -> Presentation): Flow<MatchedValue> {
    LOG.debug("Create TopHits flow ($pattern)")
    val project = myModel.project
    val commandAccelerator = SearchTopHitProvider.getTopHitAccelerator()
    val matcher = buildWeightMatcher(pattern)

    return channelFlow<Any> {
      val collector = Consumer<Any> { item ->
        launch {
          val obj = (item as? AnAction)?.let { wrapAnAction(it, presentationProvider(it)) } ?: item
          send(obj)
        }
      }
      for (provider in SearchTopHitProvider.EP_NAME.extensionList) {
        if (provider is CoveredByToggleActions) {
          continue
        }

        if (provider is OptionsSearchTopHitProvider && !pattern.startsWith(commandAccelerator)) {
          val prefix = commandAccelerator + (provider as OptionsSearchTopHitProvider).getId() + " "
          provider.consumeTopHits(prefix + pattern, collector, project)
        }
        else if (project != null && provider is ProjectLevelProvidersAdapter) {
          provider.consumeAllTopHits(pattern, collector, project)
        }
        provider.consumeTopHits(pattern, collector, project)
      }
    }
      .map { matchItem(it, matcher, pattern, MatchedValueType.TOP_HIT) }
  }

  private fun intentionsFlow(pattern: String,
                             presentationProvider: suspend (AnAction) -> Presentation): Flow<MatchedValue> {
    LOG.debug("Create intentions flow ($pattern)")
    val matcher = buildMatcher(pattern)
    val weightMatcher = buildWeightMatcher(pattern)

    return channelFlow {
      launch {
        for ((text, action) in getIntentionsMap()) {
          if (myModel.actionMatches(pattern, matcher, action) != MatchMode.NONE) {
            val groupMapping = GroupMapping.createFromText(text, false)
            send(ActionWrapper(action, groupMapping, MatchMode.INTENTION, myModel, presentationProvider(action)))
          }
        }
      }
    }
      .map { matchItem(it, weightMatcher, pattern, MatchedValueType.INTENTION) }
  }

  private suspend fun getIntentionsMap(): Map<String, ApplyIntentionAction> {
    if (myIntentions.isEmpty()) {
      val intentions = readAction {
        myModel.availableIntentions
      }

      myIntentions.putAll(intentions)
    }
    return myIntentions
  }

  private fun optionsFlow(pattern: String,
                          presentationProvider: suspend (AnAction) -> Presentation): Flow<MatchedValue> {
    LOG.debug("Create options flow ($pattern)")

    val weightMatcher = buildWeightMatcher(pattern)

    val map = myModel.configurablesNames
    val registrar = SearchableOptionsRegistrar.getInstance() as SearchableOptionsRegistrarImpl

    val words = registrar.getProcessedWords(pattern)
    val filterOutInspections = Registry.`is`("go.to.action.filter.out.inspections", true)

    return channelFlow<Any> {
      var optionDescriptions: MutableSet<OptionDescription>? = null
      for (word in words) {
        val descriptions = Objects.requireNonNullElse(registrar.getAcceptableDescriptions(word), hashSetOf())
        descriptions.removeIf { "ActionManager" == it.path || filterOutInspections && "Inspections" == it.groupName }

        if (!descriptions.isEmpty()) {
          if (optionDescriptions == null) {
            optionDescriptions = descriptions
          }
          else {
            optionDescriptions.retainAll(descriptions)
          }
        }
        else {
          optionDescriptions = null
          break
        }
      }
      if (!Strings.isEmptyOrSpaces(pattern)) {
        val matcher = buildMatcher(pattern)
        if (optionDescriptions == null) {
          optionDescriptions = HashSet()
        }
        for ((key, value) in map) {
          if (matcher.matches(value)) {
            optionDescriptions.add(OptionDescription(null, key, value, null, value))
          }
        }
      }
      if (!optionDescriptions.isNullOrEmpty()) {
        val currentHits: MutableSet<String> = hashSetOf()
        val iterator = optionDescriptions.iterator()
        for (description in iterator) {
          val hit = description.hit
          if (hit == null || !currentHits.add(hit.trim { it <= ' ' })) {
            iterator.remove()
          }
        }
        for (description in optionDescriptions) {
          for (converter in ActionFromOptionDescriptorProvider.EP.extensionList) {
            val action = converter.provide(description)
            if (action != null) {
              send(ActionWrapper(action, null, MatchMode.NAME, myModel, presentationProvider(action)))
            }
          }
          send(description)
        }
      }
    }
      .map { matchItem(it, weightMatcher, pattern, MatchedValueType.TOP_HIT) }
  }

  private suspend fun loadAction(id: String): AnAction? = withContext(coroutineContext) {
     async { myActionManager.getAction(id) }.await()
  }

  private fun matchItem(item: Any, matcher: MinusculeMatcher, pattern: String, matchType: MatchedValueType): MatchedValue {
    val weight = calcElementWeight(item, pattern, matcher)
    return if (weight == null) MatchedValue(item, pattern, matchType) else MatchedValue(item, pattern, weight, matchType)
  }

  private fun wrapAnAction(action: AnAction, presentation: Presentation, matchMode: MatchMode = MatchMode.NAME): ActionWrapper {
    return ActionWrapper(action, myModel.getGroupMapping(action), matchMode, myModel, presentation)
  }

  private fun buildWeightMatcher(pattern: String): MinusculeMatcher = NameUtil.buildMatcher("*$pattern")
    .withCaseSensitivity(NameUtil.MatchingCaseSensitivity.NONE)
    .preferringStartMatches()
    .build()

  fun clearIntentions() {
    myIntentions.clear()
  }
}

private fun abbreviationMatchedValue(wrapper: ActionWrapper, pattern: String, degree: Int) =
  object : MatchedValue(wrapper, pattern, degree, MatchedValueType.ABBREVIATION) {
    override fun getValueText(): String {
      return pattern
    }
  }

private data class MatchedAction(val action: AnAction, val mode: MatchMode, val weight: Int?)

private fun <I> CoroutineScope.flowCollector(flow: Flow<I>): FlowsSequenceCollector<I> = FlowsSequenceCollector<I>(this).apply { then(flow) }

private class FlowsSequenceCollector<I>(private val scope: CoroutineScope) {

  private val flows = mutableListOf<Flow<I>>()

  fun then(flow: Flow<I>): FlowsSequenceCollector<I> {
    flows.add(flow)
    return this
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun collect(collector: suspend (I) -> Boolean) {
    scope.launch {
      val channel = scope.produce {
        var prevJob: Job? = null
        for (flow in flows) {
          val waitForJob = prevJob
          prevJob = launch {
            flow.collect {
              waitForJob?.join()
              send(it)
            }
          }
        }
      }

      try {
        for (i in channel) {
          if (!collector(i)) return@launch
        }
      }
      finally {
        channel.cancel()
      }
    }
  }
}
