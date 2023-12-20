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
import com.intellij.openapi.actionSystem.impl.Utils.runUpdateSessionForActionSearch
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.Strings
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.ui.switcher.QuickActionProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.function.Predicate

private val LOG = logger<ActionAsyncProvider>()

private const val DEFAULT_CHANNEL_CAPACITY = 30

class ActionAsyncProvider(private val myModel: GotoActionModel) {

  private val myActionManager: ActionManager = ActionManager.getInstance()
  private val myIntentions = ConcurrentHashMap<String, ApplyIntentionAction>()

  fun processActions(pattern: String, ids: Set<String>, consumer: Predicate<in MatchedValue>): Unit = runBlockingCancellable {
    runUpdateSessionForActionSearch(myModel.getUpdateSession()) { presentationProvider ->
      myModel.buildGroupMappings()

      val matchedActionsFlowDeferred = async { matchedActionsAndStubsFlow(pattern, ids, presentationProvider) }
      val unmatchedStubsFlowDeferred = async { unmatchedStubsFlow(pattern, ids, presentationProvider) }

      launch {
        sendResults(matchedActionsFlowDeferred.await(), consumer)
        sendResults(unmatchedStubsFlowDeferred.await(), consumer)
      }
    }
  }

  fun filterElements(pattern: String, consumer: Predicate<in MatchedValue>) {
    if (pattern.isEmpty()) return

    try {
      runBlockingCancellable {
        runUpdateSessionForActionSearch(myModel.getUpdateSession()) { presentationProvider ->
          LOG.debug("Start actions searching ($pattern)")

          myModel.buildGroupMappings()
          val actionIds = (myActionManager as ActionManagerImpl).actionIds

          val comparator: Comparator<MatchedValue> = Comparator { o1, o2 -> o1.compareWeights(o2) }
          val abbreviationsPromise = async { abbreviationsFlow(pattern, presentationProvider).let { collectAndSort(it, comparator) } }
          val matchedActionsFlowDeferred = async { matchedActionsAndStubsFlow(pattern, actionIds, presentationProvider) }
          val unmatchedStubsFlowDeferred = async { unmatchedStubsFlow(pattern, actionIds, presentationProvider) }
          val topHitsPromise = async { topHitsFlow(pattern, presentationProvider).let { collectAndSort(it, comparator) } }
          val intentionsPromise = async { intentionsFlow(pattern, presentationProvider).let { collectAndSort(it, comparator) } }
          val optionsPromise = async { optionsFlow(pattern, presentationProvider).let { collectAndSort(it, comparator) } }

          launch {
            sendResults(abbreviationsPromise.await(), consumer, "abbreviations")
            sendResults(matchedActionsFlowDeferred.await(), consumer)
            sendResults(unmatchedStubsFlowDeferred.await(), consumer)
            sendResults(topHitsPromise.await(), consumer, "topHits")
            sendResults(intentionsPromise.await(), consumer, "intentions")
            sendResults(optionsPromise.await(), consumer, "options")
          }
        }
      }
      LOG.debug("Finish actions processing")
    }
    catch (e: SearchFinishedException) {
      LOG.debug("Search finished with reason:", e)
    }
  }

  private suspend fun <T> collectAndSort(flow: Flow<T>, comparator: Comparator<in T>): List<T> {
    val list = flow
      .catch { e -> LOG.error("Error while collecting actions.", e) }
      .toSet()
      .toMutableList()
    list.sortWith(comparator)
    return list
  }

  private suspend fun CoroutineScope.sendResults(list: List<MatchedValue>, consumer: Predicate<in MatchedValue>, name: String) {
    LOG.debug("Sending results ($name)")
    for (value in list) {
      if (!isActive) {
        LOG.debug("Sending results: coroutine is not active any more")
        return
      }

      if (!consumer.test(value)) {
        LOG.debug("Sending results: consumer returned false")
        throw SearchFinishedException()
      }
    }
    LOG.debug("Sending results ($name) - done")
  }

  private suspend fun sendResults(flow: Flow<MatchedValue>, consumer: Predicate<in MatchedValue>) {
    flow.onEach {
      val collected = consumer.test(it)
      if (!collected) {
        LOG.debug("Sending results: consumer returned false")
        throw SearchFinishedException()
      }
    }.collect()
  }

  private fun abbreviationsFlow(pattern: String, presentationProvider: suspend (AnAction) -> Presentation): Flow<MatchedValue> {
    LOG.debug("Create abbreviations flow ($pattern)")
    val matcher = buildWeightMatcher(pattern)
    val actionIds = AbbreviationManager.getInstance().findActions(pattern)

    return actionIds.asFlow()
      .mapNotNull { myActionManager.getAction(it) }
      .map {
        val presentation = presentationProvider(it)
        val wrapper: ActionWrapper = wrapAnAction(it, presentation)
        val degree = matcher.matchingDegree(pattern)
        abbreviationMatchedValue(wrapper, pattern, degree)
      }
      .buffer(actionIds.size)
  }

  private suspend fun matchedActionsAndStubsFlow(pattern: String, allIds: Collection<String>,
                                                 presentationProvider: suspend (AnAction) -> Presentation): Flow<MatchedValue> {
    val matcher = buildMatcher(pattern)
    val weightMatcher = buildWeightMatcher(pattern)

    val fromIdsFlow = allIds.asFlow().mapNotNull {
      val action = myActionManager.getActionOrStub(it) ?: return@mapNotNull null
      if (action is ActionGroup && !action.isSearchable) return@mapNotNull null

      return@mapNotNull action
    }

    val additionalActionsFlow = myModel.dataContext.getData(QuickActionProvider.KEY)?.getActions(true)?.asFlow() ?: emptyFlow<AnAction>()

    val actionsWithWeightsFlow = merge(fromIdsFlow, additionalActionsFlow).transform {
      runCatching {
        val mode = myModel.actionMatches(pattern, matcher, it)
        if (mode != MatchMode.NONE) {
          val weight = calcElementWeight(it, pattern, weightMatcher)
          emit(MatchedAction(it, mode, weight))
        }
      }.getOrLogException(LOG)
    }

    val comparator = Comparator.comparing<MatchedAction, Int> { it.weight ?: 0 }.reversed()

    val list = collectAndSort(actionsWithWeightsFlow, comparator)
    LOG.debug("List is collected")
    return list.asFlow().buffer(list.size)
      .transform {
        val action = it.action
        if (action is ActionStubBase) {
          myActionManager.getAction(action.id)?.let { loaded -> emit(MatchedAction(loaded, it.mode, it.weight)) }
        } else {
          emit(it)
        }
      }
      .map {
        val presentation = presentationProvider(it.action)
        wrapAnAction(it.action, presentation, it.mode)
      }
      .map { matchItem(it, weightMatcher, pattern, MatchedValueType.ACTION) }
  }

  private fun unmatchedStubsFlow(pattern: String, allIds: Collection<String>,
                        presentationProvider: suspend (AnAction) -> Presentation): Flow<MatchedValue> {
    val matcher = buildMatcher(pattern)
    val weightMatcher = buildWeightMatcher(pattern)

    return allIds.asFlow().buffer(allIds.size)
      .mapNotNull {
        val action = myActionManager.getActionOrStub(it) ?: return@mapNotNull null
        if (action is ActionGroup && !action.isSearchable) return@mapNotNull null
        action
      }
      .filter {
        runCatching { (it is ActionStubBase) && myModel.actionMatches(pattern, matcher, it) == MatchMode.NONE }.getOrLogException(LOG) == true
      }
      .transform {
        runCatching {
          val action = myActionManager.getAction((it as ActionStubBase).id)
          val mode = myModel.actionMatches(pattern, matcher, action)
          if (mode != MatchMode.NONE) {
            val weight = calcElementWeight(action, pattern, weightMatcher)
            emit(MatchedAction(action, mode, weight))
          }
        }.getOrLogException(LOG)
      }
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
      .buffer(DEFAULT_CHANNEL_CAPACITY)
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
      .buffer(DEFAULT_CHANNEL_CAPACITY)
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
    }.buffer(DEFAULT_CHANNEL_CAPACITY)
      .map { matchItem(it, weightMatcher, pattern, MatchedValueType.TOP_HIT) }
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

private class SearchFinishedException : Exception("Found items limit reached")

