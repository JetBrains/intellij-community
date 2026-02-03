// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.history.core.HistoryPathFilter
import com.intellij.history.integration.IdeaGateway
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.lvcs.impl.*
import com.intellij.platform.lvcs.impl.actions.isShowSystemLabelsEnabled
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

@OptIn(FlowPreview::class)
internal class ActivityViewModel(private val project: Project, gateway: IdeaGateway, internal val activityScope: ActivityScope,
                                 diffMode: DirectoryDiffMode, coroutineScope: CoroutineScope) {
  private val eventDispatcher = EventDispatcher.create(ActivityModelListener::class.java)

  internal val activityProvider: ActivityProvider = LocalHistoryActivityProvider(project, gateway)

  private val activityItemsFlow = MutableStateFlow(ActivityData.EMPTY)
  private val selectionFlow = MutableStateFlow<ActivitySelection?>(null)
  private val diffModeFlow = MutableStateFlow(diffMode)

  private val filterFlow = MutableStateFlow<String?>(null)

  private val filterSystemLabelsFlow = MutableStateFlow(isShowSystemLabelsEnabled())

  private val isVisibleFlow = MutableStateFlow(true)

  init {
    coroutineScope.launch {
      combine(activityProvider.getActivityItemsChanged(activityScope).debounce(500),
              if (filterKind == FilterKind.FILE) filterFlow else flowOf(null),
              isVisibleFlow,
              filterSystemLabelsFlow.debounce(100)) { _, filter, isVisible, showSystemLabels -> Triple(filter, isVisible, showSystemLabels) }
        .filter { (_, isVisible, _) -> isVisible }
        .map { it.first to it.third }
        .collect { (filter, showSystemLabels) ->
          thisLogger<ActivityViewModel>().debug("Loading activity items for $activityScope and filter $filter")
          withContext(Dispatchers.EDT) { eventDispatcher.multicaster.onItemsLoadingStarted() }
          val activityData = withContext(Dispatchers.Default) {
            LocalHistoryCounter.logLoadItems(project, activityScope) {
              val pathFilter = HistoryPathFilter.create(filter, project)
              activityProvider.loadActivityList(activityScope, ActivityFilter(pathFilter, null, showSystemLabels))
            }
          }
          withContext(Dispatchers.EDT) {
            activityItemsFlow.value = activityData
            eventDispatcher.multicaster.onItemsLoadingStopped(activityData)
          }
        }
    }
    if (!isSingleDiffSupported) {
      coroutineScope.launch {
        combine(selectionFlow, diffModeFlow) { s, d -> s to d }.collectLatest { (selection, diffMode) ->
          thisLogger<ActivityViewModel>().debug("Loading diff data for $activityScope diff mode $diffMode")
          withContext(Dispatchers.EDT) {
            eventDispatcher.multicaster.onDiffDataLoadingStarted()
          }
          val diffData = selection?.let {
            withContext(Dispatchers.Default) {
              LocalHistoryCounter.logLoadDiff(project, activityScope) {
                activityProvider.loadDiffData(activityScope, selection, diffMode)
              }
            }
          }
          withContext(Dispatchers.EDT) {
            eventDispatcher.multicaster.onDiffDataLoadingStopped(diffData)
          }
        }
      }
    }

    if (filterKind == FilterKind.CONTENT) {
      coroutineScope.launch {
        combine(filterFlow.debounce(100),
                filterSystemLabelsFlow.debounce(100),
                activityItemsFlow) { ff, fs, r -> Triple(ff, fs, r) }
          .collect { (filter, showSystemLabels, data) ->
            thisLogger<ActivityViewModel>().debug("Filtering activity items for $activityScope by $filter")
            withContext(Dispatchers.EDT) { eventDispatcher.multicaster.onFilteringStarted() }
            val result = LocalHistoryCounter.logFilter(project, activityScope) {
              activityProvider.filterActivityList(activityScope, data, ActivityFilter(null, filter, showSystemLabels))
            }
            withContext(Dispatchers.EDT) { eventDispatcher.multicaster.onFilteringStopped(result) }
          }
      }
    }
  }

  internal val selection get() = selectionFlow.value

  internal var diffMode get() = diffModeFlow.value
    set(value) {
      diffModeFlow.value = value
    }

  internal val isSingleDiffSupported get() = !activityScope.hasMultipleFiles

  internal val filterKind get() = activityProvider.getSupportedFilterKindFor(activityScope)
  val isFilterSet: Boolean get() = !filterFlow.value.isNullOrEmpty()
  fun setFilter(pattern: String?) {
    filterFlow.value = pattern
  }

  fun setSystemLabelsFiltered(filtered: Boolean) {
    filterSystemLabelsFlow.value = filtered
  }

  @RequiresEdt
  fun setSelection(selection: ActivitySelection?) {
    selectionFlow.value = selection
    eventDispatcher.multicaster.onSelectionChanged(selection)
  }

  fun setVisible(isVisible: Boolean) {
    isVisibleFlow.value = isVisible
  }

  fun addListener(listener: ActivityModelListener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
  }
}

internal interface ActivityModelListener : EventListener {
  fun onItemsLoadingStarted() = Unit
  fun onItemsLoadingStopped(data: ActivityData) = Unit
  fun onSelectionChanged(selection: ActivitySelection?) = Unit
  fun onDiffDataLoadingStarted() = Unit
  fun onDiffDataLoadingStopped(diffData: ActivityDiffData?) = Unit
  fun onFilteringStarted() = Unit
  fun onFilteringStopped(result: Set<ActivityItem>?) = Unit
}
