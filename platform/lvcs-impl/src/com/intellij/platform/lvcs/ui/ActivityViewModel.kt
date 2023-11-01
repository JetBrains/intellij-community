// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.ui

import com.intellij.history.integration.IdeaGateway
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.lvcs.*
import com.intellij.platform.lvcs.impl.LocalHistoryActivityProvider
import com.intellij.util.EventDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import java.util.*

@OptIn(FlowPreview::class)
class ActivityViewModel(project: Project, gateway: IdeaGateway, private val activityScope: ActivityScope, coroutineScope: CoroutineScope) {
  private val eventDispatcher = EventDispatcher.create(ActivityModelListener::class.java)

  internal val activityProvider: ActivityProvider = LocalHistoryActivityProvider(project, gateway)

  private val activityItemsFlow = MutableStateFlow<List<ActivityItem>>(emptyList())
  private val selectionFlow = MutableStateFlow<ActivitySelection?>(null)

  private val scopeFilterFlow = MutableStateFlow<String?>(null)
  private val activityFilterFlow = MutableStateFlow<String?>(null)

  init {
    coroutineScope.launch {
      combine(activityProvider.activityItemsChanged.debounce(500), scopeFilterFlow) { _, filter -> filter }.collect { filter ->
        val activityItems = withContext(Dispatchers.Default) {
          activityProvider.loadActivityList(activityScope, filter)
        }
        withContext(Dispatchers.EDT) {
          activityItemsFlow.value = activityItems
          eventDispatcher.multicaster.onItemsLoaded(activityItems)
        }
      }
    }
    coroutineScope.launch {
      selectionFlow.collectLatest { selection ->
        val diffData = selection?.let { withContext(Dispatchers.Default) { activityProvider.loadDiffData(activityScope, selection) } }
        withContext(Dispatchers.EDT) {
          eventDispatcher.multicaster.onDiffDataLoaded(diffData)
        }
      }
    }

    if (activityProvider.isActivityFilterSupported(activityScope)) {
      coroutineScope.launch {
        combine(activityFilterFlow.debounce(100), activityItemsFlow) { f, r -> f to r }.collect { (filter, items) ->
          withContext(Dispatchers.EDT) { eventDispatcher.multicaster.onFilteringStarted() }
          val result = activityProvider.filterActivityList(activityScope, items, filter)
          withContext(Dispatchers.EDT) { eventDispatcher.multicaster.onFilteringStopped(result) }
        }
      }
    }
  }

  val isFilterSupported: Boolean
    get() = activityProvider.isScopeFilterSupported(activityScope) || activityProvider.isActivityFilterSupported(activityScope)

  fun setFilter(pattern: String?) {
    if (activityProvider.isScopeFilterSupported(activityScope)) {
      scopeFilterFlow.value = pattern
    }
    if (activityProvider.isActivityFilterSupported(activityScope)) {
      activityFilterFlow.value = pattern
    }
  }

  fun setSelection(selection: ActivitySelection?) {
    selectionFlow.value = selection
  }

  fun addListener(listener: ActivityModelListener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
  }
}

interface ActivityModelListener : EventListener {
  fun onItemsLoaded(items: List<ActivityItem>)
  fun onDiffDataLoaded(diffData: ActivityDiffData?)
  fun onFilteringStarted()
  fun onFilteringStopped(result: Set<Long>?)
}