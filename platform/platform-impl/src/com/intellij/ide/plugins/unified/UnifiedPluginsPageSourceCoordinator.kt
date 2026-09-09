// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.InstalledPluginsTabSearchResultPanel
import com.intellij.ide.plugins.MarketplaceTabSearchSortByOptions
import com.intellij.ide.plugins.newui.MyPluginModel
import com.intellij.ide.plugins.newui.PluginPreparedUpdateState
import com.intellij.ide.plugins.newui.PluginProgressState
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.SearchQueryParser
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

internal data class UnifiedPluginMarketplaceSourceState(
  val queryRevision: Long,
  val section: PluginSectionState,
  val listModelData: PluginListModelData,
  val suggestedFacetItems: List<PluginItemState> = emptyList(),
  val suggestedFacetsLoading: Boolean = false,
  val popularTags: List<String> = emptyList(),
  val popularTagsLoading: Boolean = false,
)

internal data class UnifiedPluginsPageSourceState(
  val query: PluginsQueryState,
  val sections: List<PluginSectionState>,
  val listModelData: PluginListModelData,
  val repositoryPlugins: List<PluginUiModel>?,
  val settledRepositoryPlugins: Map<String, List<PluginUiModel>> = emptyMap(),
  val repositoryContentRevision: Long = 0,
  val mayEstablishSelection: Boolean,
  val searchControls: UnifiedPluginsSearchControlsState = UnifiedPluginsSearchControlsState(),
  val internalDescriptorSettled: Boolean = true,
)

internal data class UnifiedPluginSourceProjection(
  val eligible: Boolean,
  val query: String = "",
)

internal data class UnifiedPluginsSourceRoute(
  val local: UnifiedPluginSourceProjection,
  val internal: UnifiedPluginSourceProjection,
  val marketplace: UnifiedPluginSourceProjection,
  val repositories: UnifiedPluginSourceProjection,
  val selectedRepositoryIds: Set<String>,
  val marketplaceMode: UnifiedPluginMarketplaceSourceMode,
  val sortVisible: Boolean,
)

/**
 * Combines all plugin source states into one page source state.
 *
 * The lifecycle owner serializes [start], [setQuery], other request methods, and [close].
 * The internal lock coordinates those calls with concurrent source collectors and projection jobs.
 * The coordinator has no EDT requirement.
 * [state] can be collected from any coroutine context.
 */
internal class UnifiedPluginsPageSourceCoordinator(
  private val scope: CoroutineScope,
  initialQuery: String,
  private val localSource: UnifiedPluginLocalSourceCoordinator,
  private val internalSource: UnifiedPluginInternalSourceCoordinator,
  private val marketplaceSource: UnifiedPluginMarketplaceSourceCoordinator,
  private val repositorySource: UnifiedPluginRepositorySourceCoordinator,
  private val onSearchReset: () -> Unit = {},
  private val projectionDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {
  private val lock = Any()
  private var query = initialPluginsQueryState(initialQuery)
  private var localState = localSource.state.value
  private var internalState = internalSource.state.value
  private var marketplaceState = marketplaceSource.state.value
  private var repositoryState = repositorySource.state.value
  private val mutableState = MutableStateFlow(
    composeUnifiedPluginsPageSourceState(query, localState, marketplaceState, repositoryState, internalState = internalState)
  )
  private var localCollectionJob: Job? = null
  private var internalCollectionJob: Job? = null
  private var marketplaceCollectionJob: Job? = null
  private var repositoryCollectionJob: Job? = null
  private var projectionJob: Job? = null
  private var projectionCache = UnifiedPluginsPageProjectionCache.EMPTY
  private var projectionToken = 0L
  private var started = false
  private var closed = false

  val state: StateFlow<UnifiedPluginsPageSourceState> = mutableState.asStateFlow()

  fun start() {
    check(!started) { "Unified plugins page source coordinator is already started" }
    check(!closed) { "Unified plugins page source coordinator is closed" }
    started = true
    localCollectionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
      localSource.state.drop(1).collect(::acceptLocalState)
    }
    internalCollectionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
      internalSource.state.drop(1).collect(::acceptInternalState)
    }
    marketplaceCollectionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
      marketplaceSource.state.drop(1).collect(::acceptMarketplaceState)
    }
    repositoryCollectionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
      repositorySource.state.drop(1).collect(::acceptRepositoryState)
    }
    localSource.start()
    internalSource.start()
    repositorySource.start()
    marketplaceSource.start()
  }

  fun setQuery(rawQuery: String, scope: PluginsQueryScope = PluginsQueryScope.Unified) {
    if (synchronized(lock) {
        if (closed) return
        val updatedQuery = advancePluginsQueryState(query, rawQuery, scope)
        if (updatedQuery == query) return
        val reset = query.normalizedQuery.isNotEmpty() && updatedQuery.normalizedQuery.isEmpty()
        val normalizedQueryChanged = updatedQuery.revision != query.revision
        query = updatedQuery
        if (normalizedQueryChanged) {
          mutableState.value = transitionUnifiedPluginsPageQuery(mutableState.value, updatedQuery, localState.mayEstablishSelection)
          scheduleProjectionLocked()
        }
        else {
          mutableState.value = mutableState.value.copy(query = updatedQuery)
        }
        marketplaceSource.setQuery(updatedQuery)
        reset
      }) onSearchReset()
  }

  fun applySearchControl(intent: UnifiedPluginSearchControlIntent) {
    val updatedQuery = synchronized(lock) {
      if (closed) return
      val parsed = UnifiedPluginsQuery.parse(query.rawQuery)
      when (intent) {
        is UnifiedPluginSearchControlIntent.ToggleAttribute ->
          parsed.withAttribute(intent.attribute, intent.value, intent.selected)
        is UnifiedPluginSearchControlIntent.ToggleInstalledFilter ->
          parsed.withInstalledFilter(intent.filter, intent.selected)
        is UnifiedPluginSearchControlIntent.SelectSort -> parsed.withSort(intent.sort)
      }
    }
    setQuery(updatedQuery)
  }

  fun refresh() {
    synchronized(lock) {
      if (!closed) {
        localSource.refresh()
        internalSource.refreshEnrichment()
        repositorySource.refresh()
        if (query.usesMarketplaceSearch()) marketplaceSource.retry()
      }
    }
  }

  fun retry(sectionId: PluginSectionId) {
    synchronized(lock) {
      if (closed) return
      when (sectionId) {
        PluginSectionId.Installed, PluginSectionId.Bundled -> localSource.refresh()
        PluginSectionId.Internal -> internalSource.retry()
        PluginSectionId.CustomRepositoryCatalog -> repositorySource.refresh()
        is PluginSectionId.CustomRepository -> repositorySource.retry(sectionId.repositoryId)
        else -> if (sectionId == marketplaceSectionId(query)) marketplaceSource.retry()
      }
    }
  }

  override fun close() {
    synchronized(lock) {
      if (closed) return
      closed = true
      localCollectionJob?.cancel()
      internalCollectionJob?.cancel()
      marketplaceCollectionJob?.cancel()
      repositoryCollectionJob?.cancel()
      projectionJob?.cancel()
      localSource.close()
      internalSource.close()
      marketplaceSource.close()
      repositorySource.close()
    }
  }

  private fun acceptLocalState(state: UnifiedPluginLocalSourceState) {
    synchronized(lock) {
      if (closed) return
      val sharedFactsChanged = state.listModelData != localState.listModelData
      localState = state
      scheduleProjectionLocked()
      if (sharedFactsChanged) {
        internalSource.refreshEnrichment()
        marketplaceSource.refreshEnrichment()
        repositorySource.refreshEnrichment()
      }
    }
  }

  private fun acceptInternalState(state: UnifiedPluginInternalSourceState) {
    synchronized(lock) {
      if (closed) return
      internalState = state
      scheduleProjectionLocked()
    }
  }

  private fun acceptMarketplaceState(state: UnifiedPluginMarketplaceSourceState) {
    synchronized(lock) {
      if (closed || state.queryRevision != query.revision) return
      marketplaceState = state
      scheduleProjectionLocked()
    }
  }

  private fun acceptRepositoryState(state: UnifiedPluginRepositorySourceState) {
    synchronized(lock) {
      if (closed) return
      val suggestionsMustRefresh = state.suggestionsRefreshRevision != repositoryState.suggestionsRefreshRevision
      repositoryState = state
      scheduleProjectionLocked()
      if (suggestionsMustRefresh && query.usesSuggestedSource()) marketplaceSource.retry()
    }
  }

  private fun scheduleProjectionLocked() {
    projectionJob?.cancel()
    val token = ++projectionToken
    val requestedQuery = query
    val requestedLocalState = localState
    val requestedInternalState = internalState
    val requestedMarketplaceState = marketplaceState
    val requestedRepositoryState = repositoryState
    val requestedProjectionCache = projectionCache
    projectionJob = scope.launch(projectionDispatcher) {
      val projectionContext = coroutineContext
      val projection = projectUnifiedPluginsPageSourceState(
        query = requestedQuery,
        localState = requestedLocalState,
        marketplaceState = requestedMarketplaceState,
        repositoryState = requestedRepositoryState,
        internalState = requestedInternalState,
        previousCache = requestedProjectionCache,
        cancellationCheck = projectionContext::ensureActive,
      )
      synchronized(lock) {
        if (closed || token != projectionToken || query.revision != requestedQuery.revision) return@synchronized
        projectionJob = null
        projectionCache = projection.cache
        mutableState.value = projection.state.copy(query = query)
      }
    }
  }
}

internal fun initialPluginsQueryState(
  rawQuery: String,
  scope: PluginsQueryScope = PluginsQueryScope.Unified,
): PluginsQueryState {
  return PluginsQueryState(rawQuery = rawQuery, normalizedQuery = rawQuery.trim(), scope = scope)
}

internal fun advancePluginsQueryState(
  current: PluginsQueryState,
  rawQuery: String,
  scope: PluginsQueryScope = PluginsQueryScope.Unified,
): PluginsQueryState {
  val normalizedQuery = rawQuery.trim()
  if (rawQuery == current.rawQuery && normalizedQuery == current.normalizedQuery && scope == current.scope) return current
  return PluginsQueryState(
    rawQuery = rawQuery,
    normalizedQuery = normalizedQuery,
    revision = if (normalizedQuery == current.normalizedQuery && scope == current.scope) current.revision else current.revision + 1,
    scope = scope,
  )
}

internal fun transitionUnifiedPluginsPageQuery(
  current: UnifiedPluginsPageSourceState,
  query: PluginsQueryState,
  mayEstablishSelection: Boolean,
): UnifiedPluginsPageSourceState {
  val marketplaceFamilyId = marketplaceSectionId(query)
  val marketplaceSection = PluginSectionState(
    marketplaceFamilyId,
    status = initialMarketplaceSectionStatus(query),
  )
  return current.copy(
    query = query,
    sections = current.sections.map { section ->
      when (section.id) {
        PluginSectionId.Suggested, PluginSectionId.Marketplace -> marketplaceSection
        else -> section
      }
    },
    mayEstablishSelection = mayEstablishSelection,
  )
}

internal fun composeUnifiedPluginsPageSourceState(
  query: PluginsQueryState,
  localState: UnifiedPluginLocalSourceState,
  marketplaceState: UnifiedPluginMarketplaceSourceState? = null,
  repositoryState: UnifiedPluginRepositorySourceState? = null,
  mayEstablishSelection: Boolean = localState.mayEstablishSelection,
  internalState: UnifiedPluginInternalSourceState? = null,
  cancellationCheck: () -> Unit = {},
): UnifiedPluginsPageSourceState = projectUnifiedPluginsPageSourceState(
  query = query,
  localState = localState,
  marketplaceState = marketplaceState,
  repositoryState = repositoryState,
  mayEstablishSelection = mayEstablishSelection,
  internalState = internalState,
  cancellationCheck = cancellationCheck,
).state

private fun projectUnifiedPluginsPageSourceState(
  query: PluginsQueryState,
  localState: UnifiedPluginLocalSourceState,
  marketplaceState: UnifiedPluginMarketplaceSourceState? = null,
  repositoryState: UnifiedPluginRepositorySourceState? = null,
  mayEstablishSelection: Boolean = localState.mayEstablishSelection,
  internalState: UnifiedPluginInternalSourceState? = null,
  previousCache: UnifiedPluginsPageProjectionCache = UnifiedPluginsPageProjectionCache.EMPTY,
  cancellationCheck: () -> Unit = {},
): UnifiedPluginsPageProjection {
  cancellationCheck()
  val route = query.sourceRoute()
  val effectiveSort = UnifiedPluginsQuery.parse(query.rawQuery).effectiveSort
  val marketplaceFamilyId = marketplaceSectionId(query)
  val marketplaceSection = marketplaceState
                             ?.takeIf { it.queryRevision == query.revision && it.section.id == marketplaceFamilyId }
                             ?.section
                           ?: PluginSectionState(marketplaceFamilyId, status = initialMarketplaceSectionStatus(query))
  val cachedProjectionApplies = previousCache.query == query
  val localSections = previousCache.localSections.takeIf {
    cachedProjectionApplies && previousCache.localState === localState
  } ?: localState.sections.map { section ->
    cancellationCheck()
    when (section.id) {
      PluginSectionId.Installed, PluginSectionId.Bundled -> if (route.local.eligible) {
        section.copy(
          items = filterPluginItems(
            section.items,
            route.local.query,
            effectiveSort,
            categoryRelevance = section.id == PluginSectionId.Bundled,
            cancellationCheck = cancellationCheck,
          )
        )
      }
      else {
        section.copy(items = emptyList(), status = PluginSectionStatus.Ready)
      }
      else -> section
    }
  }
  cancellationCheck()
  val internalSection = if (cachedProjectionApplies && previousCache.internalState === internalState) {
    previousCache.internalSection
  }
  else internalState?.section?.let { section ->
    if (route.internal.eligible) {
      section.copy(items = filterInternalPluginItems(section.items, route.internal.query, effectiveSort, cancellationCheck))
    }
    else {
      section.copy(items = emptyList(), status = PluginSectionStatus.Ready)
    }
  }
  cancellationCheck()
  val repositorySections = previousCache.repositorySections.takeIf {
    cachedProjectionApplies && previousCache.repositoryState === repositoryState
  } ?: repositoryState?.sections.orEmpty().map { section ->
    cancellationCheck()
    if (section.id == PluginSectionId.CustomRepositoryCatalog) return@map section
    val repositoryId = (section.id as? PluginSectionId.CustomRepository)?.repositoryId
    if (!route.repositories.eligible || route.selectedRepositoryIds.isNotEmpty() && repositoryId !in route.selectedRepositoryIds) {
      section.copy(items = emptyList(), status = PluginSectionStatus.Ready)
    }
    else {
      section.copy(items = filterRepositoryPluginItems(section.items, route.repositories.query, cancellationCheck))
    }
  }
  cancellationCheck()
  val marketplaceListModelData = marketplaceState?.takeIf { it.queryRevision == query.revision }?.listModelData
                                 ?: PluginListModelData.EMPTY
  val remoteListModelData = mergePluginListModelData(
    internalState?.listModelData ?: PluginListModelData.EMPTY,
    mergePluginListModelData(
      marketplaceListModelData,
      repositoryState?.listModelData ?: PluginListModelData.EMPTY,
    ),
  )
  val sections = buildList {
    addAll(localSections)
    internalSection?.let(::add)
    add(marketplaceSection)
    addAll(repositorySections)
  }
  val projectedState = UnifiedPluginsPageSourceState(
    query = query,
    sections = applyManualUpdatePresentations(sections, localState.manualUpdates, cancellationCheck),
    listModelData = mergePluginListModelData(localState.listModelData, remoteListModelData),
    repositoryPlugins = repositoryState?.repositoryPlugins,
    settledRepositoryPlugins = repositoryState?.settledRepositoryPlugins.orEmpty(),
    repositoryContentRevision = repositoryState?.repositoryContentRevision ?: 0,
    mayEstablishSelection = mayEstablishSelection,
    internalDescriptorSettled = internalState?.descriptorRequestSettled ?: true,
    searchControls = composeUnifiedPluginsSearchControls(
      query,
      localState,
      marketplaceState,
      repositoryState,
      route,
      internalState,
      cancellationCheck,
    ),
  )
  return UnifiedPluginsPageProjection(
    state = projectedState,
    cache = UnifiedPluginsPageProjectionCache(
      query = query,
      localState = localState,
      localSections = localSections,
      internalState = internalState,
      internalSection = internalSection,
      repositoryState = repositoryState,
      repositorySections = repositorySections,
    ),
  )
}

private data class UnifiedPluginsPageProjection(
  val state: UnifiedPluginsPageSourceState,
  val cache: UnifiedPluginsPageProjectionCache,
)

private class UnifiedPluginsPageProjectionCache(
  val query: PluginsQueryState?,
  val localState: UnifiedPluginLocalSourceState?,
  val localSections: List<PluginSectionState>,
  val internalState: UnifiedPluginInternalSourceState?,
  val internalSection: PluginSectionState?,
  val repositoryState: UnifiedPluginRepositorySourceState?,
  val repositorySections: List<PluginSectionState>,
) {
  companion object {
    val EMPTY = UnifiedPluginsPageProjectionCache(null, null, emptyList(), null, null, null, emptyList())
  }
}

internal fun applyManualUpdatePresentations(
  sections: List<PluginSectionState>,
  manualUpdates: Map<PluginId, UnifiedPluginManualUpdateState>,
  cancellationCheck: () -> Unit = {},
): List<PluginSectionState> {
  if (manualUpdates.isEmpty()) return sections
  return sections.map { section ->
    cancellationCheck()
    val items = section.items.map { item ->
      cancellationCheck()
      val input = item.rowInput
      val manualUpdate = manualUpdates[item.pluginId]
      if (input == null || manualUpdate == null) {
        item
      }
      else {
        val updatedInput = when (val presentation = manualUpdate.presentation) {
          UnifiedPluginManualUpdatePresentation.Downloading -> input.copy(
            operationInProgress = true,
            detailsProgress = input.detailsProgress ?: PluginProgressState.Indeterminate,
            preparedUpdate = null,
          )
          is UnifiedPluginManualUpdatePresentation.Prepared -> input.copy(
            operationInProgress = false,
            detailsProgress = null,
            preparedUpdate = PluginPreparedUpdateState(presentation.restartRequired),
          )
        }
        item.copy(rowInput = updatedInput)
      }
    }
    if (items == section.items) section else section.copy(items = items)
  }
}

internal fun composeUnifiedPluginsSearchControls(
  query: PluginsQueryState,
  localState: UnifiedPluginLocalSourceState,
  marketplaceState: UnifiedPluginMarketplaceSourceState?,
  repositoryState: UnifiedPluginRepositorySourceState?,
  route: UnifiedPluginsSourceRoute = query.sourceRoute(),
  internalState: UnifiedPluginInternalSourceState? = null,
  cancellationCheck: () -> Unit = {},
): UnifiedPluginsSearchControlsState {
  val parsedQuery = UnifiedPluginsQuery.parse(query.rawQuery)
  val facetItems = buildList {
    localState.sections.asSequence()
      .filter { it.id == PluginSectionId.Installed || it.id == PluginSectionId.Bundled }
      .flatMap(PluginSectionState::items)
      .forEach { item ->
        cancellationCheck()
        add(item)
      }
    internalState?.section?.items.orEmpty().forEach { item ->
      cancellationCheck()
      add(item)
    }
    marketplaceState?.suggestedFacetItems.orEmpty().forEach { item ->
      cancellationCheck()
      add(item)
    }
    repositoryState?.sections.orEmpty().forEach { section ->
      section.items.forEach { item ->
        cancellationCheck()
        add(item)
      }
    }
  }
  val vendors = facetItems.asSequence().mapNotNull(PluginItemState::searchVendor)
    .plus(parsedQuery.vendors.asSequence())
    .normalizedFacetValues()
  val categories = facetItems.asSequence().mapNotNull(PluginItemState::searchCategory)
    .plus(parsedQuery.categories.asSequence())
    .normalizedFacetValues()
  val tags = mergePopularTagFacetValues(
    marketplaceState?.popularTags.orEmpty(),
    facetItems.asSequence().flatMap { it.searchTags.asSequence() }.plus(parsedQuery.tags.asSequence()),
  )
  val repositories = buildList {
    repositoryState?.sections.orEmpty().forEach { section ->
      (section.id as? PluginSectionId.CustomRepository)?.repositoryId?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
    }
    parsedQuery.repositories.asSequence().map(String::trim).filter(String::isNotEmpty).forEach(::add)
  }.distinct()
  return UnifiedPluginsSearchControlsState(
    options = UnifiedPluginFilterOptions(
      vendors = vendors,
      categories = categories,
      tags = tags,
      repositories = repositories,
      facetValuesLoading = localState.facetsLoading ||
                           internalState?.facetsLoading == true ||
                           marketplaceState?.suggestedFacetsLoading == true ||
                           marketplaceState?.popularTagsLoading == true ||
                           repositoryState?.facetsLoading == true,
      repositoriesLoading = repositoryState?.catalogLoading == true,
    ),
    selectedVendors = parsedQuery.vendors,
    selectedCategories = parsedQuery.categories,
    selectedTags = parsedQuery.tags,
    selectedRepositories = parsedQuery.repositories,
    selectedInstalledFilter = parsedQuery.effectiveInstalledFilter,
    effectiveSort = parsedQuery.effectiveSort,
    sortVisible = route.sortVisible,
  )
}

private fun Sequence<String>.normalizedFacetValues(): List<String> {
  return map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()
    .sortedWith(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()))
    .toList()
}

private fun mergePopularTagFacetValues(popularTags: List<String>, otherTags: Sequence<String>): List<String> {
  val result = LinkedHashSet<String>()
  popularTags.asSequence().map(String::trim).filter(String::isNotEmpty).forEach(result::add)
  otherTags.normalizedFacetValues().forEach(result::add)
  return result.toList()
}

internal fun mergePluginListModelData(
  local: PluginListModelData,
  remote: PluginListModelData,
): PluginListModelData {
  return PluginListModelData(
    installedModels = remote.installedModels + local.installedModels,
    errors = remote.errors + local.errors,
    installationStates = remote.installationStates + local.installationStates,
  )
}

private fun filterPluginItems(
  items: List<PluginItemState>,
  normalizedQuery: String,
  sortBy: MarketplaceTabSearchSortByOptions,
  categoryRelevance: Boolean,
  cancellationCheck: () -> Unit = {},
): List<PluginItemState> {
  if (!categoryRelevance && normalizedQuery.isEmpty() && sortBy == MarketplaceTabSearchSortByOptions.RELEVANCE) return items
  val unifiedQuery = UnifiedPluginsQuery.parse(normalizedQuery)
  val parser = SearchQueryParser.Installed(unifiedQuery.withoutCategoryFilters())
  val matches = items.asSequence()
    .filter { item ->
      cancellationCheck()
      val model = item.modelHandle?.model ?: return@filter false
      val input = item.rowInput
      (parser.vendors.isEmpty() || MyPluginModel.isVendor(model, parser.vendors)) &&
      item.matchesCategories(unifiedQuery.categories) &&
      (parser.tags.isEmpty() || item.searchTags.any(parser.tags::contains)) &&
      (!parser.enabled || input != null && input.enabled && input.errors.isEmpty()) &&
      (!parser.disabled || input != null && !input.enabled && input.errors.isEmpty()) &&
      (!parser.bundled || model.isBundled || model.isBundledUpdate) &&
      (!parser.updatedBundled || model.isBundledUpdate) &&
      (!parser.userInstalled || !model.isBundled && !model.isBundledUpdate) &&
      (!parser.invalid || input?.errors?.isNotEmpty() == true) &&
      (!parser.needUpdate || input?.updateDescriptor != null)
    }
    .mapNotNull { item ->
      val searchQuery = parser.searchQuery ?: return@mapNotNull LocalPluginSearchMatch(item, 0.0)
      val model = item.modelHandle?.model ?: return@mapNotNull null
      InstalledPluginsTabSearchResultPanel.computeSearchQueryRelevance(model, searchQuery)
        ?.let { LocalPluginSearchMatch(item, it) }
    }
    .toList()
  cancellationCheck()
  return matches.sortedWith(localPluginComparator(sortBy, categoryRelevance)).map(LocalPluginSearchMatch::item)
}

private data class LocalPluginSearchMatch(
  val item: PluginItemState,
  val relevance: Double,
)

private fun localPluginComparator(
  sortBy: MarketplaceTabSearchSortByOptions,
  categoryRelevance: Boolean = false,
): Comparator<LocalPluginSearchMatch> {
  return when (sortBy) {
    MarketplaceTabSearchSortByOptions.RELEVANCE -> Comparator { first, second ->
      val firstHasErrors = first.item.rowInput?.errors?.isNotEmpty() == true
      val secondHasErrors = second.item.rowInput?.errors?.isNotEmpty() == true
      when {
        firstHasErrors != secondHasErrors -> if (firstHasErrors) -1 else 1
        first.relevance != second.relevance -> second.relevance.compareTo(first.relevance)
        categoryRelevance -> compareBundledItems(first.item, second.item)
        else -> 0
      }
    }
    MarketplaceTabSearchSortByOptions.NAME -> Comparator { first, second -> comparePluginNames(first.item, second.item) }
    MarketplaceTabSearchSortByOptions.DOWNLOADS -> localPluginMetadataComparator { it.downloads?.toLongOrNull() }
    MarketplaceTabSearchSortByOptions.RATING -> localPluginMetadataComparator { it.rating?.toDoubleOrNull() }
    MarketplaceTabSearchSortByOptions.UPDATE_DATE -> localPluginMetadataComparator { model ->
      model.date.takeUnless { it <= 0 || it == Long.MAX_VALUE } ?: model.releaseDate
    }
  }
}

private fun compareBundledItems(first: PluginItemState, second: PluginItemState): Int {
  val firstCategory = bundledPluginCategory(first.searchCategory)
  val secondCategory = bundledPluginCategory(second.searchCategory)
  val otherCategory = bundledPluginCategory(null)
  val categoryComparison = when {
    firstCategory == secondCategory -> 0
    firstCategory == otherCategory -> 1
    secondCategory == otherCategory -> -1
    else -> StringUtil.compare(firstCategory, secondCategory, false)
  }
  return if (categoryComparison != 0) categoryComparison else comparePluginNames(first, second)
}

private fun <T : Comparable<T>> localPluginMetadataComparator(
  value: (PluginUiModel) -> T?,
): Comparator<LocalPluginSearchMatch> {
  return Comparator { first, second ->
    val valueComparison = compareDescendingNullLast(
      first.item.modelHandle?.model?.let(value),
      second.item.modelHandle?.model?.let(value),
    )
    if (valueComparison != 0) valueComparison else comparePluginNames(first.item, second.item)
  }
}

private fun <T : Comparable<T>> compareDescendingNullLast(first: T?, second: T?): Int {
  if (first == null) return if (second == null) 0 else 1
  if (second == null) return -1
  return second.compareTo(first)
}

private fun comparePluginNames(first: PluginItemState, second: PluginItemState): Int {
  return StringUtil.compare(first.name, second.name, true)
}

private fun filterRepositoryPluginItems(
  items: List<PluginItemState>,
  normalizedQuery: String,
  cancellationCheck: () -> Unit = {},
): List<PluginItemState> {
  if (normalizedQuery.isEmpty()) return items
  val unifiedQuery = UnifiedPluginsQuery.parse(normalizedQuery)
  val parser = SearchQueryParser.Marketplace(unifiedQuery.withoutCategoryFilters())
  return items.asSequence()
    .filter { item ->
      cancellationCheck()
      val model = item.modelHandle?.model ?: return@filter false
      (parser.vendors.isEmpty() || MyPluginModel.isVendor(model, parser.vendors)) &&
      item.matchesCategories(unifiedQuery.categories) &&
      (parser.tags.isEmpty() || item.searchTags.any(parser.tags::contains))
    }
    .mapNotNull { item ->
      val searchQuery = parser.searchQuery ?: return@mapNotNull 0.0 to item
      val model = item.modelHandle?.model ?: return@mapNotNull null
      InstalledPluginsTabSearchResultPanel.computeSearchQueryRelevance(model, searchQuery)?.let { it to item }
    }
    .sortedByDescending(Pair<Double, PluginItemState>::first)
    .map(Pair<Double, PluginItemState>::second)
    .toList()
}

private fun filterInternalPluginItems(
  items: List<PluginItemState>,
  normalizedQuery: String,
  sortBy: MarketplaceTabSearchSortByOptions,
  cancellationCheck: () -> Unit = {},
): List<PluginItemState> {
  if (normalizedQuery.isEmpty() && sortBy == MarketplaceTabSearchSortByOptions.RELEVANCE) return items
  val unifiedQuery = UnifiedPluginsQuery.parse(normalizedQuery)
  val parser = SearchQueryParser.Marketplace(unifiedQuery.withoutCategoryFilters())
  val matches = items.asSequence()
    .filter { item ->
      cancellationCheck()
      val model = item.modelHandle?.model ?: return@filter false
      (parser.vendors.isEmpty() || MyPluginModel.isVendor(model, parser.vendors)) &&
      item.matchesCategories(unifiedQuery.categories) &&
      (parser.tags.isEmpty() || item.searchTags.any(parser.tags::contains))
    }
    .mapNotNull { item ->
      val searchQuery = parser.searchQuery ?: return@mapNotNull LocalPluginSearchMatch(item, 0.0)
      val model = item.modelHandle?.model ?: return@mapNotNull null
      InstalledPluginsTabSearchResultPanel.computeSearchQueryRelevance(model, searchQuery)
        ?.let { LocalPluginSearchMatch(item, it) }
    }
    .toList()
  cancellationCheck()
  val comparator = when (sortBy) {
    MarketplaceTabSearchSortByOptions.RELEVANCE ->
      compareByDescending(LocalPluginSearchMatch::relevance)
    MarketplaceTabSearchSortByOptions.NAME ->
      Comparator { first, second -> comparePluginNames(first.item, second.item) }
    MarketplaceTabSearchSortByOptions.DOWNLOADS -> localPluginMetadataComparator { it.downloads?.toLongOrNull() }
    MarketplaceTabSearchSortByOptions.RATING -> localPluginMetadataComparator { it.rating?.toDoubleOrNull() }
    MarketplaceTabSearchSortByOptions.UPDATE_DATE -> localPluginMetadataComparator { model ->
      model.date.takeUnless { it <= 0 || it == Long.MAX_VALUE } ?: model.releaseDate
    }
  }
  return matches.sortedWith(comparator).map(LocalPluginSearchMatch::item)
}

private fun UnifiedPluginsQuery.withoutCategoryFilters(): String {
  return renderExcluding { part ->
    part is UnifiedPluginQueryPart.Attribute && part.attribute == UnifiedPluginQueryAttribute.Category
  }
}

private fun PluginItemState.matchesCategories(categories: Set<String>): Boolean {
  return categories.isEmpty() || searchCategory in categories
}

internal enum class UnifiedPluginMarketplaceSourceMode {
  Suggested,
  Search,
  Inactive,
}

internal fun PluginsQueryState.marketplaceSourceMode(): UnifiedPluginMarketplaceSourceMode {
  return sourceRoute().marketplaceMode
}

internal fun PluginsQueryState.usesMarketplaceSearch(): Boolean =
  marketplaceSourceMode() == UnifiedPluginMarketplaceSourceMode.Search

internal fun PluginsQueryState.usesSuggestedSource(): Boolean =
  marketplaceSourceMode() == UnifiedPluginMarketplaceSourceMode.Suggested

internal fun marketplaceSectionId(query: PluginsQueryState): PluginSectionId {
  return if (query.usesSuggestedSource()) {
    PluginSectionId.Suggested
  }
  else {
    PluginSectionId.Marketplace
  }
}

internal fun initialMarketplaceSectionStatus(query: PluginsQueryState): PluginSectionStatus {
  return if (query.marketplaceSourceMode() == UnifiedPluginMarketplaceSourceMode.Inactive) {
    PluginSectionStatus.Ready
  }
  else {
    PluginSectionStatus.Loading(showingStaleContent = false)
  }
}

internal fun PluginsQueryState.sourceRoute(): UnifiedPluginsSourceRoute {
  if (normalizedQuery.isEmpty()) {
    return UnifiedPluginsSourceRoute(
      local = UnifiedPluginSourceProjection(eligible = true),
      internal = UnifiedPluginSourceProjection(eligible = true),
      marketplace = UnifiedPluginSourceProjection(eligible = true),
      repositories = UnifiedPluginSourceProjection(eligible = true),
      selectedRepositoryIds = emptySet(),
      marketplaceMode = UnifiedPluginMarketplaceSourceMode.Suggested,
      sortVisible = false,
    )
  }

  val parsed = UnifiedPluginsQuery.parse(normalizedQuery)
  val repositoryConstraint = parsed.repositories.isNotEmpty()
  val localQuery = parsed.renderExcluding { part ->
    part.isSupersededInstalledFilter(parsed.effectiveInstalledFilter) ||
    part is UnifiedPluginQueryPart.Attribute &&
    (part.attribute == UnifiedPluginQueryAttribute.Sort || part.attribute == UnifiedPluginQueryAttribute.Repository) ||
    part is UnifiedPluginQueryPart.Command && part.command in setOf(
      UnifiedPluginQueryCommand.Suggested,
      UnifiedPluginQueryCommand.StaffPicks,
      UnifiedPluginQueryCommand.Internal,
    )
  }
  val repositoryQuery = parsed.renderExcluding { part ->
    part is UnifiedPluginQueryPart.Attribute &&
    (part.attribute == UnifiedPluginQueryAttribute.Sort || part.attribute == UnifiedPluginQueryAttribute.Repository)
  }

  if (scope == PluginsQueryScope.Installed) {
    return UnifiedPluginsSourceRoute(
      local = UnifiedPluginSourceProjection(
        eligible = true,
        query = parsed.renderExcluding { it.isSupersededInstalledFilter(parsed.effectiveInstalledFilter) },
      ),
      internal = UnifiedPluginSourceProjection(eligible = false),
      marketplace = UnifiedPluginSourceProjection(eligible = false),
      repositories = UnifiedPluginSourceProjection(eligible = false),
      selectedRepositoryIds = parsed.repositories,
      marketplaceMode = UnifiedPluginMarketplaceSourceMode.Inactive,
      sortVisible = true,
    )
  }

  val marketplaceSpecialMode = parsed.hasPrimaryMarketplaceMode
  val internalEligible = !repositoryConstraint && !parsed.hasInstalledConstraint &&
                         (!marketplaceSpecialMode || parsed.requestsInternal)
  val internalQuery = parsed.renderExcluding { part ->
    part is UnifiedPluginQueryPart.Attribute &&
    (part.attribute == UnifiedPluginQueryAttribute.Sort || part.attribute == UnifiedPluginQueryAttribute.Repository) ||
    part is UnifiedPluginQueryPart.Command && part.command in setOf(
      UnifiedPluginQueryCommand.Suggested,
      UnifiedPluginQueryCommand.StaffPicks,
      UnifiedPluginQueryCommand.Internal,
    )
  }
  val localEligible = scope == PluginsQueryScope.Marketplace ||
                      !repositoryConstraint && !marketplaceSpecialMode
  val localProjection = if (scope == PluginsQueryScope.Marketplace) "" else localQuery
  val marketplaceEligible = !repositoryConstraint && !parsed.hasInstalledConstraint && !parsed.requestsInternal
  val repositoryEligible = !parsed.hasInstalledConstraint && !marketplaceSpecialMode
  val marketplaceMode = when {
    !marketplaceEligible -> UnifiedPluginMarketplaceSourceMode.Inactive
    parsed.requestsSuggested -> UnifiedPluginMarketplaceSourceMode.Suggested
    else -> UnifiedPluginMarketplaceSourceMode.Search
  }
  return UnifiedPluginsSourceRoute(
    local = UnifiedPluginSourceProjection(localEligible, localProjection),
    internal = UnifiedPluginSourceProjection(internalEligible, internalQuery),
    marketplace = UnifiedPluginSourceProjection(marketplaceEligible, normalizedQuery),
    repositories = UnifiedPluginSourceProjection(repositoryEligible, repositoryQuery),
    selectedRepositoryIds = parsed.repositories,
    marketplaceMode = marketplaceMode,
    sortVisible = localEligible && localProjection.isNotEmpty() ||
                  internalEligible ||
                  marketplaceMode == UnifiedPluginMarketplaceSourceMode.Search && !marketplaceSpecialMode,
  )
}

private fun UnifiedPluginQueryPart.isSupersededInstalledFilter(
  effectiveFilter: UnifiedPluginInstalledFilter?,
): Boolean {
  return this is UnifiedPluginQueryPart.Command &&
         command.installedFilter?.let { it != effectiveFilter } == true
}
