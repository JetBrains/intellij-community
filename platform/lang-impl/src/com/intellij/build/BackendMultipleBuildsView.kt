// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build

import com.intellij.build.events.*
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.deleteValueById
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.util.SmartList
import com.intellij.util.concurrency.EdtExecutorService
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.swing.Icon
import kotlin.concurrent.Volatile

private val LOG = fileLogger()

private const val STATUS_UPDATE_THROTTLING_TIME_MS = 300

@ApiStatus.Internal
class BackendMultipleBuildsView(
  private val project: Project,
  private val buildContentManager: BuildContentManager,
  internal val viewManager: AbstractViewManager,
) : AbstractMultipleBuildsView() {
  companion object {
    fun getById(buildContentId: BuildContentId): BackendMultipleBuildsView? {
      return findValueById(buildContentId, BackendMultipleBuildsViewIdType)
    }

    fun getToolWindowActivationCallback(buildId: BuildId): Runnable? {
      return findValueById(buildId, BuildDataIdType)?.activationCallback
    }
  }

  internal val id = storeValueGlobally(this, BackendMultipleBuildsViewIdType)
  private val viewModel = BuildViewViewModel.getInstance(project)
  private val buildsMap = ConcurrentHashMap<Any, BuildInfo>()
  private val viewMap = ConcurrentHashMap<BuildInfo, BuildView>()
  private val buildsList = mutableListOf<BuildInfo>()
  @Volatile
  private var disposed = false
  @Volatile
  var pinned: Boolean = false
  private var isFirstErrorShown = false

  init {
    LOG.debug { "$this created" }
  }

  override fun onEvent(buildId: Any, event: BuildEvent) {
    LOG.trace { "$this event: [$buildId] $event" }
    val buildInfo: BuildInfo?
    if (event is StartBuildEvent) {
      buildInfo = BuildInfo(event.buildDescriptor)
      buildsMap[buildId] = buildInfo
    }
    else {
      buildInfo = buildsMap[buildId]
    }
    if (buildInfo == null) {
      LOG.warn("Build can not be found for buildId: '$buildId', event: '$event'")
      return
    }
    EdtExecutorService.getInstance().execute {
      if (disposed) return@execute
      buildContentManager.orCreateToolWindow
      if (event is StartBuildEvent) {
        LOG.debug { "$this build started, buildId: $buildId" }
        clearOldBuilds(event)

        buildsList.singleOrNull()?.let {
          // Since we don't send status for the single build, when a second build appears, we need to catch up
          updateStatusMessage(it)
        }

        buildsList.add(buildInfo)

        val contentDescriptor = buildInfo.contentDescriptorSupplier?.get()
        val activationCallback = if (contentDescriptor != null) {
          LOG.debug { "$this using settings from run content descriptor: " +
                      "activateToolWindowWhenAdded=${contentDescriptor.isActivateToolWindowWhenAdded}, " +
                      "autoFocusContent=${contentDescriptor.isAutoFocusContent}" }
          buildInfo.isActivateToolWindowWhenAdded = contentDescriptor.isActivateToolWindowWhenAdded
          buildInfo.isAutoFocusContent = contentDescriptor.isAutoFocusContent
          if (contentDescriptor is BuildContentDescriptor) {
            LOG.debug { "$this using settings from build content descriptor: " +
                        "navigateToError=${contentDescriptor.isNavigateToError}, " +
                        "activateToolWindowWhenFailed=${contentDescriptor.isActivateToolWindowWhenFailed}" }
            buildInfo.isNavigateToError = contentDescriptor.isNavigateToError
            buildInfo.isActivateToolWindowWhenFailed = contentDescriptor.isActivateToolWindowWhenFailed
          }
          contentDescriptor.activationCallback
        }
        else {
          null
        }
        val buildData = BuildData(activationCallback)
        val id = storeValueGlobally(buildData, BuildDataIdType)
        buildInfo.buildId = id

        val buildView = BuildView(project, buildInfo, "build.toolwindow." + viewManager.viewName + ".selection.state", viewManager)
        Disposer.register(this, buildView)
        buildView.whenDisposed {
          LOG.debug { "$this build removed, buildId: $buildId" }
          deleteValueById(id, BuildDataIdType)
          viewModel.onBuildRemoved(this, id)
        }
        viewMap[buildInfo] = buildView
        if (contentDescriptor != null) {
          Disposer.register(buildView, contentDescriptor)
        }

        buildView.onEvent(buildId, event)

        viewManager.onBuildStart(buildInfo)
        viewModel.onBuildStarted(this, id, buildInfo.title, buildInfo.startTime, event.message,
                                 buildInfo.isAutoFocusContent, buildInfo.isActivateToolWindowWhenAdded)
      }
      else {
        if (!isFirstErrorShown &&
            ((event as? FinishEvent)?.result is FailureResult ||
            (event as? MessageEvent)?.result?.kind == MessageEvent.Kind.ERROR)) {
          LOG.debug { "$this selecting build on first error, buildId: $buildId" }
          isFirstErrorShown = true
          viewModel.onBuildSelected(this, buildInfo.buildId)
        }
        val view = viewMap[buildInfo]
        view?.onEvent(buildId, event)
        if (event is FinishBuildEvent) {
          LOG.debug { "$this build finished, buildId: $buildId" }
          val result = event.result
          buildInfo.result = result
          buildInfo.endTime = event.eventTime
          val select: Boolean
          val activate: Boolean
          val notification: BuildNotification?
          if (result is FailureResult) {
            select = true
            activate = buildInfo.isActivateToolWindowWhenFailed
            notification = result.getFailures().firstOrNull()?.notification?.let {
              BuildNotification(it.title, it.content)
            }
          }
          else {
            select = false
            activate = false
            notification = null
          }
          viewManager.onBuildFinish(buildInfo)
          viewModel.onBuildFinished(this, buildInfo.buildId, event.message, buildInfo.icon, select, activate, notification)
        }
        else {
          val message = event.getMessage()
          if (message != buildInfo.statusMessage) {
            buildInfo.statusMessage = message
            // Performance optimization: if only one build is in progress, status won't be displayed by the frontend anyway
            if (buildsList.size > 1) {
              updateStatusMessage(buildInfo)
            }
          }
        }
      }
    }
  }

  private fun updateStatusMessage(buildInfo: BuildInfo) {
    val tillNextUpdateMs = STATUS_UPDATE_THROTTLING_TIME_MS - (System.currentTimeMillis() - buildInfo.lastStatusUpdateTime)
    if (tillNextUpdateMs <= 0) {
      doUpdateStatusMessage(buildInfo)
    }
    else if (!buildInfo.statusUpdateScheduled) {
      buildInfo.statusUpdateScheduled = true
      EdtExecutorService.getScheduledExecutorInstance().schedule(
        {
          buildInfo.statusUpdateScheduled = false
          doUpdateStatusMessage(buildInfo)
        },
        tillNextUpdateMs, TimeUnit.MILLISECONDS)
    }
  }

  private fun doUpdateStatusMessage(buildInfo: BuildInfo) {
    val statusMessage = buildInfo.statusMessage ?: return
    buildInfo.lastStatusUpdateTime = System.currentTimeMillis()
    viewModel.onBuildStatusChanged(this, buildInfo.buildId, statusMessage)
  }

  private fun clearOldBuilds(startBuildEvent: StartBuildEvent) {
    val currentTime = System.currentTimeMillis()
    val buildDescriptor = startBuildEvent.buildDescriptor
    var clearAll = !buildsList.isEmpty()
    val sameBuildsToClear = SmartList<BuildInfo>()
    for (build in buildsList) {
      val sameBuildKind = build.workingDir == buildDescriptor.workingDir
      val differentBuildsFromSameBuildGroup = build.id != buildDescriptor.id &&
                                              build.groupId != null &&
                                              build.groupId == buildDescriptor.groupId
      if (!build.isRunning && sameBuildKind && !differentBuildsFromSameBuildGroup) {
        sameBuildsToClear.add(build)
      }
      val buildFinishedRecently = currentTime - build.endTime < TimeUnit.SECONDS.toMillis(1)
      if (build.isRunning || !sameBuildKind && buildFinishedRecently || differentBuildsFromSameBuildGroup) {
        clearAll = false
      }
    }
    val toClear = if (clearAll) {
      isFirstErrorShown = false
      buildsList.toList()
    }
    else {
      sameBuildsToClear
    }
    toClear.forEach { info ->
      buildsMap.values.remove(info)
      buildsList.remove(info)
      viewMap.remove(info)?.let {
        Disposer.dispose(it)
      }
    }
  }

  override fun dispose() {
    LOG.debug { "$this disposed" }
    disposed = true
    deleteValueById(id, BackendMultipleBuildsViewIdType)
    viewManager.onBuildsViewRemove(this)
    viewModel.onBuildViewDisposed(this)
  }

  override fun getBuildsMap(): Map<BuildDescriptor, BuildView> = Collections.unmodifiableMap(viewMap)

  override fun getBuildView(buildId: Any): BuildView? {
    val buildInfo = buildsMap[buildId] ?: return null
    return viewMap[buildInfo]
  }

  override fun shouldConsume(buildId: Any): Boolean = buildsMap.containsKey(buildId)

  override fun isPinned(): Boolean = pinned

  override fun lockContent() {} // frontend implementation will automatically lock previous views

  private object BackendMultipleBuildsViewIdType : BackendValueIdType<BuildContentId, BackendMultipleBuildsView>(::BuildContentId)
  private object BuildDataIdType : BackendValueIdType<BuildId, BuildData>(::BuildId)

  private class BuildData(val activationCallback: Runnable?)

  private class BuildInfo(descriptor: BuildDescriptor) : DefaultBuildDescriptor(descriptor) {
    var statusMessage: @BuildEventsNls.Message String? = null
    var lastStatusUpdateTime: Long = -1
    var statusUpdateScheduled: Boolean = false

    var endTime: Long = -1

    var result: EventResult? = null

    lateinit var buildId: BuildId

    val icon: Icon
      get() = ExecutionNode.getEventResultIcon(result)

    val isRunning: Boolean
      get() = endTime == -1L
  }
}