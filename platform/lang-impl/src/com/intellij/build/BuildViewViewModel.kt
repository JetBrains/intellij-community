// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build

import com.intellij.build.events.BuildEventsNls
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class BuildViewViewModel(scope: CoroutineScope) : FlowWithHistory<BuildViewEvent>(scope) {
  companion object {
    fun getInstance(project: Project): BuildViewViewModel = project.service()
  }

  private val viewStates = mutableMapOf<BackendMultipleBuildsView, ViewState>()

  fun onBuildStarted(
    view: BackendMultipleBuildsView,
    buildId: BuildId,
    title: @BuildEventsNls.Title String,
    startTime: Long,
    message: @BuildEventsNls.Message String,
    requestFocus: Boolean,
    activateToolWindow: Boolean,
  ) {
    updateHistoryAndEmit {
      val viewState = viewStates.computeIfAbsent(view) { ViewState() }
      BuildViewEvent.BuildStarted(view.toDto(), buildId, title, startTime, message, requestFocus, activateToolWindow).also {
        viewState.buildMap[buildId] = BuildState(it.copy(requestFocus = false, activateToolWindow = false))
      }
    }
  }

  fun onBuildFinished(
    view: BackendMultipleBuildsView,
    buildId: BuildId,
    message: String,
    icon: Icon,
    selectContent: Boolean,
    activateToolWindow: Boolean,
    notification: BuildNotification?,
  )  {
    updateHistoryAndEmit {
      val viewState = viewStates[view] ?: return@updateHistoryAndEmit null
      val buildState = viewState.buildMap[buildId] ?: return@updateHistoryAndEmit null
      BuildViewEvent.BuildFinished(buildId, message, icon.rpcId(), selectContent, activateToolWindow, notification).also {
        buildState.finishEvent = it.copy(activateToolWindow = false, notification = null)
      }
    }
  }

  fun onBuildStatusChanged(
    view: BackendMultipleBuildsView,
    buildId: BuildId,
    statusMessage: String
  )  {
    updateHistoryAndEmit {
      val viewState = viewStates[view] ?: return@updateHistoryAndEmit null
      val buildState = viewState.buildMap[buildId] ?: return@updateHistoryAndEmit null
      BuildViewEvent.BuildStatusChanged(buildId, statusMessage).also {
        buildState.statusEvent = it
      }
    }
  }

  fun onBuildSelected(
    view: BackendMultipleBuildsView,
    buildId: BuildId
  ) {
    updateHistoryAndEmit {
      val viewState = viewStates[view] ?: return@updateHistoryAndEmit null
      viewState.buildMap[buildId] ?: return@updateHistoryAndEmit null
      BuildViewEvent.BuildSelected(buildId).also {
        viewState.selectEvent = it
      }
    }
  }

  fun onBuildRemoved(
    view: BackendMultipleBuildsView,
    buildId: BuildId
  )  {
    updateHistoryAndEmit {
      val viewState = viewStates[view] ?: return@updateHistoryAndEmit null
      viewState.buildMap.remove(buildId) ?: return@updateHistoryAndEmit null
      if (viewState.selectEvent?.buildId == buildId) {
        viewState.selectEvent = null
      }
      BuildViewEvent.BuildRemoved(buildId)
    }
  }

  fun onBuildViewDisposed(
    view: BackendMultipleBuildsView
  ) {
    updateHistoryAndEmit {
      viewStates.remove(view)
      null
    }
  }

  override fun getHistory(): List<BuildViewEvent> = buildList {
    viewStates.forEach { (view, viewState) ->
      val contentDto = view.toDto()  // send up-to date pinned status
      viewState.buildMap.values.forEach { buildState ->
        add(buildState.startEvent.copy(content = contentDto))
        buildState.statusEvent?.let {
          add(it)
        }
        buildState.finishEvent?.let {
          add(it)
        }
      }
      viewState.selectEvent?.let {
        add(it)
      }
    }
  }

  private fun BackendMultipleBuildsView.toDto() = BuildContent(id, viewManager.myId, viewManager.viewName, pinned)

  private class ViewState {
    val buildMap = mutableMapOf<BuildId, BuildState>()
    var selectEvent: BuildViewEvent.BuildSelected? = null
  }

  private class BuildState(val startEvent: BuildViewEvent.BuildStarted) {
    var statusEvent: BuildViewEvent.BuildStatusChanged? = null
    var finishEvent: BuildViewEvent.BuildFinished? = null
  }
}