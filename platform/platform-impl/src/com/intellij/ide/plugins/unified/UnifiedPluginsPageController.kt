// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// @spec platform/platform-impl/spec/plugin-manager/unified-plugin-manager-ui.spec.md
/**
 * Owns the page interaction state.
 *
 * The internal lock serializes all changes, so callers can update this controller from any thread.
 * [state] can be collected from any coroutine context.
 * The Swing view still renders collected state on the EDT.
 */
internal class UnifiedPluginsPageController(
  initialSections: List<PluginSectionState> = emptyList(),
  initialQuery: PluginsQueryState = PluginsQueryState(),
  private val collapsedItemLimit: Int = PluginSectionState.COLLAPSED_ITEM_LIMIT,
  priorityBundledCategories: Set<String> = emptySet(),
) {
  private val lock = Any()
  private val sections = LinkedHashMap<PluginSectionId, PluginSectionState>()
  private val sectionInsertionOrder = HashMap<PluginSectionId, Long>()
  private val expandedSections = hashSetOf(PluginSectionId.Suggested, PluginSectionId.Marketplace)
  private var installingExpansionDefaultHandled = false
  private var nextInsertionOrder = 0L
  private var query = initialQuery
  private var automaticExpansionContext = query.automaticExpansionContext()
  private val automaticallyCollapsedSections = HashSet<PluginSectionId>()
  private var searchControls = UnifiedPluginsSearchControlsState()
  private var selectedOccurrences: List<PluginOccurrenceId> = emptyList()
  private var initialSelectionPending = true
  private var pendingQuerySelectionRevision: Long? = null
  private val priorityBundledCategories = priorityBundledCategories.toSet()
  private val sectionItemOrders = HashMap<PluginSectionItemOrderKey, PluginSectionItemOrder>()

  private val mutableState = MutableStateFlow(
    UnifiedPluginsPageState(
      query = query,
      sections = emptyList(),
      selectedOccurrences = emptyList(),
      searchControls = searchControls,
    )
  )

  val state: StateFlow<UnifiedPluginsPageState> = mutableState.asStateFlow()

  init {
    replaceSections(initialSections)
  }

  fun setQuery(query: PluginsQueryState) {
    synchronized(lock) {
      if (!storeQuery(query)) return
      publish(establishSelection = false)
    }
  }

  fun replaceSourceState(
    query: PluginsQueryState,
    updatedSections: List<PluginSectionState>,
    mayEstablishSelection: Boolean,
    updatedSearchControls: UnifiedPluginsSearchControlsState = searchControls,
  ) {
    synchronized(lock) {
      storeQuery(query)
      searchControls = updatedSearchControls
      replaceStoredSections(updatedSections)
      val querySelectionPending = pendingQuerySelectionRevision == query.revision
      if (!mayEstablishSelection && !querySelectionPending && selectedOccurrences.isEmpty()) {
        initialSelectionPending = false
      }
      publish(establishSelection = mayEstablishSelection || querySelectionPending)
    }
  }

  fun replaceSections(newSections: List<PluginSectionState>) {
    synchronized(lock) {
      replaceStoredSections(newSections)
      publish(establishSelection = true)
    }
  }

  fun updateSections(updatedSections: List<PluginSectionState>) {
    synchronized(lock) {
      updatedSections.forEach(::storeSection)
      publish(establishSelection = true)
    }
  }

  fun updateSection(section: PluginSectionState) {
    updateSections(listOf(section))
  }

  fun removeSection(sectionId: PluginSectionId) {
    synchronized(lock) {
      if (sectionId in REQUIRED_SECTION_IDS || sections.remove(sectionId) == null) {
        return
      }
      sectionInsertionOrder.remove(sectionId)
      sectionItemOrders.keys.removeAll { it.sectionId == sectionId }
      publish(establishSelection = true)
    }
  }

  fun setSectionExpanded(sectionId: PluginSectionId, expanded: Boolean) {
    synchronized(lock) {
      if (sectionId == PluginSectionId.Installing) {
        installingExpansionDefaultHandled = true
      }
      val expansionChanged = if (automaticExpansionContext.targets(sectionId)) {
        if (expanded) {
          automaticallyCollapsedSections.remove(sectionId)
        }
        else {
          automaticallyCollapsedSections.add(sectionId)
        }
      }
      else {
        if (expanded) {
          expandedSections.add(sectionId)
        }
        else {
          expandedSections.remove(sectionId)
        }
      }
      if (!expansionChanged) return
      initialSelectionPending = false
      pendingQuerySelectionRevision = null
      publish(establishSelection = false, collapsedSectionId = sectionId.takeUnless { expanded })
    }
  }

  fun selectOccurrence(occurrenceId: PluginOccurrenceId?) {
    selectOccurrences(listOfNotNull(occurrenceId))
  }

  fun selectOccurrences(occurrenceIds: List<PluginOccurrenceId>) {
    synchronized(lock) {
      val visibleSections = visibleSections()
      if (occurrenceIds.any { !containsOccurrence(visibleSections, it) }) return
      selectedOccurrences = normalizeSelection(occurrenceIds)
      initialSelectionPending = false
      pendingQuerySelectionRevision = null
      publish(establishSelection = false)
    }
  }

  fun selectAndRevealOccurrence(occurrenceId: PluginOccurrenceId): Boolean {
    return selectAndRevealOccurrences(listOf(occurrenceId))
  }

  fun selectAndRevealOccurrences(occurrenceIds: List<PluginOccurrenceId>): Boolean {
    synchronized(lock) {
      if (occurrenceIds.isEmpty()) return false
      val visibleSections = visibleSections()
      for ((sectionId, pluginId) in occurrenceIds) {
        val section = visibleSections.firstOrNull { it.id == sectionId } ?: return false
        val itemIndex = section.displayItems.indexOfFirst { it.pluginId == pluginId }
        if (itemIndex < 0) return false
        if (itemIndex >= section.collapsedItemLimit) {
          expandSectionForReveal(section.id)
        }
      }
      selectedOccurrences = normalizeSelection(occurrenceIds)
      initialSelectionPending = false
      pendingQuerySelectionRevision = null
      publish(establishSelection = false)
      return true
    }
  }

  private fun storeSection(section: PluginSectionState) {
    if (section.id == PluginSectionId.Installing && section.items.size > collapsedItemLimit && !installingExpansionDefaultHandled) {
      installingExpansionDefaultHandled = true
      expandedSections.add(section.id)
    }
    sectionInsertionOrder.computeIfAbsent(section.id) { nextInsertionOrder++ }
    sections[section.id] = section.copy(
      expanded = isSectionExpanded(section.id),
      categoryGroups = emptyList(),
      collapsedItemLimit = collapsedItemLimit,
    )
  }

  private fun replaceStoredSections(newSections: List<PluginSectionState>) {
    sections.clear()
    sectionInsertionOrder.clear()
    nextInsertionOrder = 0
    DEFAULT_SECTIONS.forEach(::storeSection)
    newSections.forEach(::storeSection)
  }

  private fun storeQuery(updatedQuery: PluginsQueryState): Boolean {
    require(updatedQuery.revision >= query.revision) { "Plugin query revision must not move backwards" }
    if (updatedQuery == query) return false
    val normalizedQueryChanged = updatedQuery.revision != query.revision
    if (!normalizedQueryChanged) {
      require(updatedQuery.normalizedQuery == query.normalizedQuery) {
        "Normalized plugin query must change its revision"
      }
    }
    query = updatedQuery
    updateAutomaticExpansionContext(updatedQuery.automaticExpansionContext())
    if (normalizedQueryChanged) {
      sectionItemOrders.clear()
      selectedOccurrences = emptyList()
      initialSelectionPending = true
      pendingQuerySelectionRevision = updatedQuery.revision
    }
    return true
  }

  private fun publish(establishSelection: Boolean, collapsedSectionId: PluginSectionId? = null) {
    val visibleSections = visibleSections().map { section ->
      val expanded = isSectionExpanded(section.id)
      section.copy(
        expanded = expanded,
        categoryGroups = if (section.id == PluginSectionId.Bundled && expanded && query.normalizedQuery.isEmpty()) {
          bundledCategoryGroups(section.items)
        }
        else {
          emptyList()
        },
      )
    }

    val currentSelection = selectedOccurrences
    val retainedSelection = currentSelection.filter { containsOccurrence(visibleSections, it, collapsedSectionId) }
    if (retainedSelection.isNotEmpty()) {
      selectedOccurrences = retainedSelection
      initialSelectionPending = false
      pendingQuerySelectionRevision = null
    }
    else {
      if (currentSelection.isNotEmpty() && collapsedSectionId == null) {
        initialSelectionPending = true
      }
      selectedOccurrences = emptyList()
      if (establishSelection && initialSelectionPending) {
        firstOccurrence(visibleSections)?.let { selectedOccurrences = listOf(it) }
        if (selectedOccurrences.isNotEmpty()) {
          initialSelectionPending = false
          pendingQuerySelectionRevision = null
        }
      }
    }

    mutableState.value = UnifiedPluginsPageState(
      query = query,
      sections = visibleSections,
      selectedOccurrences = selectedOccurrences,
      searchControls = searchControls,
    )
  }

  private fun visibleSections(): List<PluginSectionState> {
    val route = query.sourceRoute()
    val selectedRepositoryIds = if (route.repositories.eligible) route.selectedRepositoryIds else emptySet()
    return sections.values
      .asSequence()
      .filter { section -> isVisibleForCurrentQuery(section, selectedRepositoryIds) }
      .map(::orderBundledItems)
      .map(::stabilizeItemOrder)
      .sortedWith(compareBy({ section: PluginSectionState -> sectionRank(section.id) }, { sectionInsertionOrder.getValue(it.id) }))
      .toList()
  }

  private fun stabilizeItemOrder(section: PluginSectionState): PluginSectionState {
    val itemsById = LinkedHashMap<PluginId, PluginItemState>()
    for (item in section.items) {
      if (itemsById.put(item.pluginId, item) != null) {
        sectionItemOrders.keys.removeAll { it.sectionId == section.id }
        return section
      }
    }
    val mode = if (section.id == PluginSectionId.Bundled && query.normalizedQuery.isEmpty() && isSectionExpanded(section.id)) {
      PluginSectionItemOrderMode.ExpandedBundled
    }
    else {
      PluginSectionItemOrderMode.Default
    }
    val orderKey = PluginSectionItemOrderKey(section.id, mode)
    val categories = if (mode == PluginSectionItemOrderMode.ExpandedBundled) {
      section.items.associate { item -> item.pluginId to bundledPluginCategory(item.searchCategory) }
    }
    else {
      emptyMap()
    }
    val previousOrder = sectionItemOrders[orderKey]
    if (previousOrder == null || previousOrder.hasChangedCategory(categories)) {
      sectionItemOrders[orderKey] = PluginSectionItemOrder(section.items.map(PluginItemState::pluginId), categories)
      return section
    }

    val previousIds = previousOrder.pluginIds.toHashSet()
    val newItems = section.items.filter { it.pluginId !in previousIds }
    val updatedCategories = previousOrder.bundledCategories + categories
    val updatedPluginIds = if (mode == PluginSectionItemOrderMode.ExpandedBundled) {
      appendNewBundledPluginIds(previousOrder.pluginIds, newItems.map(PluginItemState::pluginId), updatedCategories)
    }
    else {
      previousOrder.pluginIds + newItems.map(PluginItemState::pluginId)
    }
    val orderedItems = updatedPluginIds.mapNotNull(itemsById::get)
    sectionItemOrders[orderKey] = PluginSectionItemOrder(
      pluginIds = updatedPluginIds,
      bundledCategories = updatedCategories,
    )
    return if (orderedItems == section.items) section else section.copy(items = orderedItems)
  }

  private fun orderBundledItems(section: PluginSectionState): PluginSectionState {
    if (section.id != PluginSectionId.Bundled || query.normalizedQuery.isNotEmpty()) return section
    val collapsedOrder = orderCollapsedBundledItems(section.items)
    if (!isSectionExpanded(section.id)) return section.copy(items = collapsedOrder)

    val itemsByCategory = LinkedHashMap<String, MutableList<PluginItemState>>()
    for (item in collapsedOrder) {
      itemsByCategory.getOrPut(bundledPluginCategory(item.searchCategory)) { ArrayList() }.add(item)
    }
    return section.copy(items = itemsByCategory.values.flatten())
  }

  private fun orderCollapsedBundledItems(items: List<PluginItemState>): List<PluginItemState> {
    val (errorItems, healthyItems) = items.partition(::hasErrors)
    return prioritizeBundledCategories(errorItems) + prioritizeBundledCategories(healthyItems)
  }

  private fun prioritizeBundledCategories(items: List<PluginItemState>): List<PluginItemState> {
    if (priorityBundledCategories.isEmpty()) return items
    val (priorityItems, otherItems) = items.partition { item ->
      bundledPluginCategory(item.searchCategory) in priorityBundledCategories
    }
    return priorityItems + otherItems
  }

  private fun isVisibleForCurrentQuery(
    section: PluginSectionState,
    selectedRepositoryIds: Set<String>,
  ): Boolean {
    val marketplaceFamilyId = marketplaceSectionId(query)
    if (section.id is PluginSectionId.CustomRepository && query.normalizedQuery.isEmpty()) {
      return section.status is PluginSectionStatus.Failed
    }
    if (section.id is PluginSectionId.CustomRepository && section.id.repositoryId in selectedRepositoryIds) {
      return true
    }
    if (section.id == PluginSectionId.Suggested || section.id == PluginSectionId.Marketplace) {
      if (section.id != marketplaceFamilyId) return false
    }
    return section.items.isNotEmpty() || section.status !is PluginSectionStatus.Ready
  }

  private fun containsOccurrence(
    visibleSections: List<PluginSectionState>,
    occurrenceId: PluginOccurrenceId,
    visibleOnlyInSection: PluginSectionId? = null,
  ): Boolean {
    return visibleSections.any { section ->
      section.id == occurrenceId.sectionId &&
      (if (section.id == visibleOnlyInSection) section.visibleItems else section.displayItems)
        .any { it.pluginId == occurrenceId.pluginId }
    }
  }

  private fun firstOccurrence(visibleSections: List<PluginSectionState>): PluginOccurrenceId? {
    for (section in visibleSections) {
      val firstItem = section.items.firstOrNull() ?: continue
      return section.occurrenceId(firstItem.pluginId)
    }
    return null
  }

  private fun normalizeSelection(occurrenceIds: List<PluginOccurrenceId>): List<PluginOccurrenceId> {
    val preferredMode = occurrenceIds.lastOrNull()?.let { pluginDetailsMode(it.sectionId) } ?: return emptyList()
    val pluginIds = HashSet<PluginId>()
    return occurrenceIds.filter { occurrenceId ->
      pluginDetailsMode(occurrenceId.sectionId) == preferredMode && pluginIds.add(occurrenceId.pluginId)
    }
  }

  private fun isSectionExpanded(sectionId: PluginSectionId): Boolean {
    return if (automaticExpansionContext.targets(sectionId)) {
      sectionId !in automaticallyCollapsedSections
    }
    else {
      sectionId in expandedSections
    }
  }

  private fun expandSectionForReveal(sectionId: PluginSectionId) {
    if (automaticExpansionContext.targets(sectionId)) {
      automaticallyCollapsedSections.remove(sectionId)
    }
    else {
      expandedSections.add(sectionId)
    }
  }

  private fun updateAutomaticExpansionContext(updatedContext: AutomaticExpansionContext) {
    val previousContext = automaticExpansionContext
    if (previousContext is AutomaticExpansionContext.Repositories &&
        updatedContext is AutomaticExpansionContext.Repositories) {
      automaticallyCollapsedSections.retainAll(updatedContext.sectionIds)
    }
    else if (previousContext != updatedContext) {
      automaticallyCollapsedSections.clear()
    }
    automaticExpansionContext = updatedContext
  }

  private fun sectionRank(sectionId: PluginSectionId): Int {
    return when (sectionId) {
      PluginSectionId.Installing -> 0
      PluginSectionId.Installed -> 1
      PluginSectionId.Bundled -> 2
      PluginSectionId.Suggested, PluginSectionId.Marketplace -> 3
      PluginSectionId.Internal -> 4
      PluginSectionId.CustomRepositoryCatalog, is PluginSectionId.CustomRepository -> 5
    }
  }

  private companion object {
    val DEFAULT_SECTIONS: List<PluginSectionState> = listOf(
      PluginSectionState(PluginSectionId.Installed),
      PluginSectionState(PluginSectionId.Bundled),
      PluginSectionState(PluginSectionId.Suggested),
      PluginSectionState(PluginSectionId.Marketplace),
    )

    val REQUIRED_SECTION_IDS: Set<PluginSectionId> = DEFAULT_SECTIONS.mapTo(HashSet(), PluginSectionState::id)
  }
}

private sealed interface AutomaticExpansionContext {
  fun targets(sectionId: PluginSectionId): Boolean

  data object None : AutomaticExpansionContext {
    override fun targets(sectionId: PluginSectionId): Boolean = false
  }

  data class Local(val key: LocalAutomaticExpansionKey) : AutomaticExpansionContext {
    override fun targets(sectionId: PluginSectionId): Boolean {
      return sectionId == PluginSectionId.Installed || sectionId == PluginSectionId.Bundled
    }
  }

  data class Repositories(val repositoryIds: Set<String>) : AutomaticExpansionContext {
    val sectionIds: Set<PluginSectionId> = repositoryIds.mapTo(HashSet()) { PluginSectionId.CustomRepository(it) }

    override fun targets(sectionId: PluginSectionId): Boolean = sectionId in sectionIds
  }
}

private data class LocalAutomaticExpansionKey(
  val installedFilter: UnifiedPluginInstalledFilter?,
  val installedOnlyCommands: Set<UnifiedPluginQueryCommand>,
  val updateSources: Set<String>,
)

private fun PluginsQueryState.automaticExpansionContext(): AutomaticExpansionContext {
  val route = sourceRoute()
  if (route.local.eligible && !route.internal.eligible && !route.marketplace.eligible && !route.repositories.eligible) {
    val parsedQuery = UnifiedPluginsQuery.parse(normalizedQuery)
    return AutomaticExpansionContext.Local(
      LocalAutomaticExpansionKey(
        installedFilter = parsedQuery.effectiveInstalledFilter,
        installedOnlyCommands = parsedQuery.installedOnlyCommands,
        updateSources = parsedQuery.updateSources,
      )
    )
  }
  if (route.repositories.eligible && route.selectedRepositoryIds.isNotEmpty()) {
    return AutomaticExpansionContext.Repositories(route.selectedRepositoryIds)
  }
  return AutomaticExpansionContext.None
}

private enum class PluginSectionItemOrderMode {
  Default,
  ExpandedBundled,
}

private data class PluginSectionItemOrderKey(
  val sectionId: PluginSectionId,
  val mode: PluginSectionItemOrderMode,
)

private data class PluginSectionItemOrder(
  val pluginIds: List<PluginId>,
  val bundledCategories: Map<PluginId, String>,
) {
  fun hasChangedCategory(updatedCategories: Map<PluginId, String>): Boolean {
    return bundledCategories.any { (pluginId, category) ->
      updatedCategories[pluginId]?.let { it != category } == true
    }
  }
}

private fun appendNewBundledPluginIds(
  retainedPluginIds: List<PluginId>,
  newPluginIds: List<PluginId>,
  categories: Map<PluginId, String>,
): List<PluginId> {
  val newPluginIdsByCategory = LinkedHashMap<String, MutableList<PluginId>>()
  for (pluginId in newPluginIds) {
    newPluginIdsByCategory.getOrPut(categories.getValue(pluginId)) { ArrayList() }.add(pluginId)
  }
  return buildList(retainedPluginIds.size + newPluginIds.size) {
    retainedPluginIds.forEachIndexed { index, pluginId ->
      add(pluginId)
      val category = categories.getValue(pluginId)
      val nextCategory = retainedPluginIds.getOrNull(index + 1)?.let(categories::getValue)
      if (category != nextCategory) {
        newPluginIdsByCategory.remove(category)?.let(::addAll)
      }
    }
    newPluginIdsByCategory.values.forEach(::addAll)
  }
}

private fun bundledCategoryGroups(items: List<PluginItemState>): List<BundledPluginCategoryGroupState> {
  val itemsByCategory = LinkedHashMap<String, MutableList<PluginItemState>>()
  for (item in items) {
    itemsByCategory.getOrPut(bundledPluginCategory(item.searchCategory)) { ArrayList() }.add(item)
  }
  return itemsByCategory.map { (category, categoryItems) ->
    BundledPluginCategoryGroupState(
      category = category,
      pluginIds = categoryItems.map(PluginItemState::pluginId),
      action = if (categoryItems.any { it.rowInput?.enabled == true }) {
        BundledPluginCategoryAction.DisableAll
      }
      else {
        BundledPluginCategoryAction.EnableAll
      },
    )
  }
}

private fun hasErrors(item: PluginItemState): Boolean = item.rowInput?.errors?.isNotEmpty() == true
