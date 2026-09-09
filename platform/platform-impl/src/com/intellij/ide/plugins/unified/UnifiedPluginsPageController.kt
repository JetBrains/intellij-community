// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
  private var searchControls = UnifiedPluginsSearchControlsState()
  private var selectedOccurrences: List<PluginOccurrenceId> = emptyList()
  private var initialSelectionPending = true
  private var pendingQuerySelectionRevision: Long? = null
  private val priorityBundledCategories = priorityBundledCategories.toSet()

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
      publish(establishSelection = true)
    }
  }

  fun setSectionExpanded(sectionId: PluginSectionId, expanded: Boolean) {
    synchronized(lock) {
      if (sectionId == PluginSectionId.Installing) {
        installingExpansionDefaultHandled = true
      }
      val expansionChanged = if (expanded) {
        expandedSections.add(sectionId)
      }
      else {
        expandedSections.remove(sectionId)
      }
      if (!expansionChanged) return
      selectedOccurrences = emptyList()
      initialSelectionPending = false
      pendingQuerySelectionRevision = null
      publish(establishSelection = false)
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
          expandedSections.add(section.id)
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
      expanded = section.id in expandedSections,
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
    if (normalizedQueryChanged) {
      selectedOccurrences = emptyList()
      initialSelectionPending = true
      pendingQuerySelectionRevision = updatedQuery.revision
    }
    return true
  }

  private fun publish(establishSelection: Boolean) {
    val visibleSections = visibleSections().map { section ->
      val expanded = section.id in expandedSections
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
    val retainedSelection = currentSelection.filter { containsOccurrence(visibleSections, it) }
    if (retainedSelection.isNotEmpty()) {
      selectedOccurrences = retainedSelection
      initialSelectionPending = false
      pendingQuerySelectionRevision = null
    }
    else {
      if (currentSelection.isNotEmpty()) {
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
      .sortedWith(compareBy({ section: PluginSectionState -> sectionRank(section.id) }, { sectionInsertionOrder.getValue(it.id) }))
      .toList()
  }

  private fun orderBundledItems(section: PluginSectionState): PluginSectionState {
    if (section.id != PluginSectionId.Bundled || query.normalizedQuery.isNotEmpty()) return section
    val collapsedOrder = orderCollapsedBundledItems(section.items)
    if (section.id !in expandedSections) return section.copy(items = collapsedOrder)

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
  ): Boolean {
    return visibleSections.any { section ->
      section.id == occurrenceId.sectionId && section.displayItems.any { it.pluginId == occurrenceId.pluginId }
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
    val pluginIds = HashSet<com.intellij.openapi.extensions.PluginId>()
    return occurrenceIds.filter { occurrenceId ->
      pluginDetailsMode(occurrenceId.sectionId) == preferredMode && pluginIds.add(occurrenceId.pluginId)
    }
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
