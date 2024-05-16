// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.history.integration.IdeaGateway
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.lvcs.impl.*
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

@OptIn(FlowPreview::class)
internal class ActivityViewModel(private val project: Project, gateway: IdeaGateway, internal val activityScope: ActivityScope, coroutineScope: CoroutineScope) {
  private val eventDispatcher = EventDispatcher.create(ActivityModelListener::class.java)

  internal val activityProvider: ActivityProvider = LocalHistoryActivityProvider(project, gateway)

  private val activityItemsFlow = MutableStateFlow(ActivityData.EMPTY)
  private val selectionFlow = MutableStateFlow<ActivitySelection?>(null)

  private val scopeFilterFlow = MutableStateFlow<String?>(null)
  private val activityFilterFlow = MutableStateFlow<String?>(null)

  private val isVisibleFlow = MutableStateFlow(true)

  init {
    coroutineScope.launch {
      combine(activityProvider.getActivityItemsChanged(activityScope).debounce(500), scopeFilterFlow,
              isVisibleFlow) { _, filter, isVisible -> filter to isVisible }
        .filter { (_, isVisible) -> isVisible }
        .map { it.first }
        .collect { filter ->
          thisLogger<ActivityViewModel>().debug("Loading activity items for $activityScope and filter $filter")
          withContext(Dispatchers.EDT) { eventDispatcher.multicaster.onItemsLoadingStarted() }
          val activityData = withContext(Dispatchers.Default) {
            LocalHistoryCounter.logLoadItems(project, activityScope) {
              activityProvider.loadActivityList(activityScope, filter)
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
        selectionFlow.collectLatest { selection ->
          thisLogger<ActivityViewModel>().debug("Loading diff data for $activityScope")
          withContext(Dispatchers.EDT) {
            eventDispatcher.multicaster.onDiffDataLoadingStarted()
          }
          val diffData = selection?.let {
            withContext(Dispatchers.Default) {
              LocalHistoryCounter.logLoadDiff(project, activityScope) {
                activityProvider.loadDiffData(activityScope, selection)
              }
            }
          }
          withContext(Dispatchers.EDT) {
            eventDispatcher.multicaster.onDiffDataLoadingStopped(diffData)
          }
        }
      }
    }

    if (activityProvider.isActivityFilterSupported(activityScope)) {
      coroutineScope.launch {
        combine(activityFilterFlow.debounce(100), activityItemsFlow) { f, r -> f to r }.collect { (filter, data) ->
          if (filter.isNullOrEmpty()) {
            withContext(Dispatchers.EDT) { eventDispatcher.multicaster.onFilteringStopped(null) }
            return@collect
          }

          thisLogger<ActivityViewModel>().debug("Filtering activity items for $activityScope by $filter")
          withContext(Dispatchers.EDT) { eventDispatcher.multicaster.onFilteringStarted() }
          val result = LocalHistoryCounter.logFilter(project, activityScope) {
            activityProvider.filterActivityList(activityScope, data, filter)
          }
          withContext(Dispatchers.EDT) { eventDispatcher.multicaster.onFilteringStopped(result) }
        }
      }
    }
  }

  internal val selection get() = selectionFlow.value

  internal val isSingleDiffSupported get() = !activityScope.hasMultipleFiles
  internal val isScopeFilterSupported get() = activityProvider.isScopeFilterSupported(activityScope)
  internal val isActivityFilterSupported get() = activityProvider.isActivityFilterSupported(activityScope)

  val isFilterSet: Boolean get() = !scopeFilterFlow.value.isNullOrEmpty() || !activityFilterFlow.value.isNullOrEmpty()

  fun setFilter(pattern: String?) {
    if (isScopeFilterSupported) {
      scopeFilterFlow.value = pattern
    }
    if (isActivityFilterSupported) {
      activityFilterFlow.value = pattern
    }
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

interface ActivityModelListener : EventListener {
  fun onItemsLoadingStarted() = Unit
  fun onItemsLoadingStopped(data: ActivityData) = Unit
  fun onSelectionChanged(selection: ActivitySelection?) = Unit
  fun onDiffDataLoadingStarted() = Unit
  fun onDiffDataLoadingStopped(diffData: ActivityDiffData?) = Unit
  fun onFilteringStarted() = Unit
  fun onFilteringStopped(result: Set<ActivityItem>?) = Unit
}