// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.history.integration.IdeaGateway
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.lvcs.impl.*
import com.intellij.util.EventDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

@OptIn(FlowPreview::class)
internal class ActivityViewModel(project: Project, gateway: IdeaGateway, private val activityScope: ActivityScope, coroutineScope: CoroutineScope) {
  private val eventDispatcher = EventDispatcher.create(ActivityModelListener::class.java)

  internal val activityProvider: ActivityProvider = LocalHistoryActivityProvider(project, gateway)

  private val activityItemsFlow = MutableStateFlow(ActivityData.EMPTY)
  private val selectionFlow = MutableStateFlow<ActivitySelection?>(null)

  private val scopeFilterFlow = MutableStateFlow<String?>(null)
  private val activityFilterFlow = MutableStateFlow<String?>(null)

  private val isVisibleFlow = MutableStateFlow(true)

  init {
    coroutineScope.launch {
      combine(activityProvider.activityItemsChanged.debounce(500), scopeFilterFlow,
              isVisibleFlow) { _, filter, isVisible -> filter to isVisible }
        .filter { (_, isVisible) -> isVisible }
        .map { it.first }
        .collect { filter ->
          thisLogger<ActivityViewModel>().debug("Loading activity items for $activityScope and filter $filter")
          withContext(Dispatchers.EDT) { eventDispatcher.multicaster.onItemsLoadingStarted() }
          val activityItems = withContext(Dispatchers.Default) {
            activityProvider.loadActivityList(activityScope, filter)
          }
          withContext(Dispatchers.EDT) {
            val activityData = ActivityData(activityItems)
            activityItemsFlow.value = activityData
            eventDispatcher.multicaster.onItemsLoadingStopped(activityData)
          }
        }
    }
    coroutineScope.launch {
      selectionFlow.collectLatest { selection ->
        thisLogger<ActivityViewModel>().debug("Loading diff data for $activityScope")
        val diffData = selection?.let { withContext(Dispatchers.Default) { activityProvider.loadDiffData(activityScope, selection) } }
        withContext(Dispatchers.EDT) {
          eventDispatcher.multicaster.onDiffDataLoaded(diffData)
        }
      }
    }

    if (activityProvider.isActivityFilterSupported(activityScope)) {
      coroutineScope.launch {
        combine(activityFilterFlow.debounce(100), activityItemsFlow) { f, r -> f to r }.collect { (filter, data) ->
          thisLogger<ActivityViewModel>().debug("Filtering activity items for $activityScope by $filter")
          withContext(Dispatchers.EDT) { eventDispatcher.multicaster.onFilteringStarted() }
          val result = activityProvider.filterActivityList(activityScope, data, filter)
          withContext(Dispatchers.EDT) { eventDispatcher.multicaster.onFilteringStopped(result) }
        }
      }
    }
  }

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

  fun setSelection(selection: ActivitySelection?) {
    selectionFlow.value = selection
  }

  fun setVisible(isVisible: Boolean) {
    isVisibleFlow.value = isVisible
  }

  fun addListener(listener: ActivityModelListener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
  }
}

interface ActivityModelListener : EventListener {
  fun onItemsLoadingStarted()
  fun onItemsLoadingStopped(data: ActivityData)
  fun onDiffDataLoaded(diffData: ActivityDiffData?)
  fun onFilteringStarted()
  fun onFilteringStopped(result: Set<ActivityItem>?)
}