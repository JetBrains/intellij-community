// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.ijent.community.ui.actions.unavailable

import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.icons.AllIcons
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.fs.stat
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.getResolvedEelMachine
import com.intellij.platform.ijent.IjentCallerContext
import com.intellij.platform.ijent.IjentMachine
import com.intellij.platform.ijent.community.impl.nio.IjentUnavailableHandler
import com.intellij.platform.ijent.community.impl.nio.IjentUnavailableHandlerResult
import com.intellij.platform.ijent.community.impl.nio.IjentUnavailableHandlerResult.ProjectCloseDecision
import com.intellij.platform.ijent.community.impl.nio.ReconnectUiDialogImpl
import com.intellij.platform.ijent.community.impl.nio.ReconnectUiHandleImpl
import com.intellij.platform.ijent.community.ui.actions.IjentImplBundle
import com.intellij.platform.ijent.community.ui.actions.dashboard.IjentStatDashboard
import com.intellij.platform.ijent.community.ui.actions.dashboard.printTable
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.application
import com.intellij.util.asSafely
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.computeDetached
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

private class EdtOnceTask : OnceTask<IjentUnavailableHandlerResult, ReconnectUiDialogImpl>() {
  override suspend fun <R> executeUnderLockIfNotAlreadyAcquired(f: suspend () -> R): R {
    return if (checkNotNull(IjentCallerContext.getSaved()).isDispatchThread) {
      check(ApplicationManager.getApplication().isDispatchThread)
      f()
    }
    else {
      // computeDetached is crucial here for immediate cancellation in case EDT is not available
      // (e.g., waiting for fsBlocking inside DiskQueryRelay)
      @OptIn(DelicateCoroutinesApi::class)
      computeDetached {
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          f()
        }
      }
    }
  }
}

/**
 * Reuses the same [EdtOnceTask] mutex for a generation of projects, replacing it only after
 * that generation is closed and new projects are opened on the same IJent.
 */
@Service
private class NotRespondingFilesystemDialogService {
  private val pendingRequests = ConcurrentHashMap<EelDescriptor, Pair<List<Project>, EdtOnceTask>>()
  suspend fun doOnceOrWait(ijentId: EelDescriptor, dialogParams: IjentUnavailableDialogHandler.DialogParams, onComputing: (Deferred<ReconnectUiDialogImpl>) -> Unit, f: suspend (CompletableDeferred<ReconnectUiDialogImpl>) -> IjentUnavailableHandlerResult): IjentUnavailableHandlerResult {
    val onceTask = pendingRequests.compute(ijentId) { _, v ->
      when (dialogParams) {
        is IjentUnavailableDialogHandler.DialogParams.ProjectIjent -> {
          when {
            v == null -> dialogParams.projects to EdtOnceTask()
            v.first.containsAll(dialogParams.projects) -> v
            v.second.computedValue() != null -> dialogParams.projects to EdtOnceTask()
            else -> (v.first + dialogParams.projects).distinct() to v.second
          }
        }
        is IjentUnavailableDialogHandler.DialogParams.UnrelatedIjent -> {
          when {
            v == null -> dialogParams.projectList to EdtOnceTask()
            v.second.computedValue() != null -> dialogParams.projectList to EdtOnceTask()
            else -> v
          }
        }
      }
    }!!.second
    return onceTask.getOrCompute(onComputing, f)
  }

  companion object {
    fun getInstance(): NotRespondingFilesystemDialogService = service()
  }
}

internal class IjentUnavailableDialogHandler : IjentUnavailableHandler {
  override suspend fun showModalDialog(eelDescriptor: EelDescriptor, uiHandle: ReconnectUiHandleImpl): IjentUnavailableHandlerResult {
    val activeProject = ProjectUtil.getActiveProject()
    val dialogParams = ProjectManager.getInstance().openProjects.filter {
      it.getEelDescriptor() == eelDescriptor
    }.sortedByDescending {
      activeProject == it
    }.takeIf {
      it.isNotEmpty()
    }?.let {
      DialogParams.ProjectIjent(eelDescriptor, it)
    } ?: DialogParams.UnrelatedIjent(eelDescriptor, ProjectManager.getInstance().defaultProject)
    LOG.warn("Ijent is unavailable. Modal dialog will be shown.")
    return NotRespondingFilesystemDialogService.getInstance().doOnceOrWait(eelDescriptor, dialogParams, uiHandle::setDialogSession) { dialogSession ->
      coroutineScope {
        val logJob = launch(Dispatchers.IO) {
          val ijentSession = eelDescriptor.getResolvedEelMachine().asSafely<IjentMachine>()?.getCachedIjentSession()
          var backOff = 2.seconds
          while (true) {
            val statTable = ijentSession?.eventBus?.counter?.snapshot()?.printTable()
            val path = PerformanceWatcher.getInstance().dumpThreads("ijent", true, true)
            LOG.warn("Ijent is unavailable. Thread dump saved to $path.")
            if (statTable != null) {
              LOG.warn("Calls statistics:\n\n$statTable")
            }
            delay(backOff)
            backOff *= 2
          }
        }
        try {
          showCloseProjectDialog(dialogSession, dialogParams)
        }
        finally {
          logJob.cancel()
        }
      }
    }
  }

  sealed class DialogParams {
    abstract val eelDescriptor: EelDescriptor
    val projectList: List<Project>
      get() = when (this) {
        is ProjectIjent -> projects
        is UnrelatedIjent -> listOf(defaultProject)
      }
    class ProjectIjent(override val eelDescriptor: EelDescriptor, val projects: List<Project>) : DialogParams()
    class UnrelatedIjent(override val eelDescriptor: EelDescriptor, val defaultProject: Project) : DialogParams()
  }

  private suspend fun showCloseProjectDialog(dialogSession: CompletableDeferred<ReconnectUiDialogImpl>, dialogParams: DialogParams): IjentUnavailableHandlerResult {
    val coroutineContext = currentCoroutineContext()
    val closeDecision = suspendCancellableCoroutine { cont ->
      val builder = DialogBuilder(dialogParams.projectList.first()).apply {
        setTitle(IjentImplBundle.message("dialog.title.ijent.unavailable"))
        setCenterPanel(createCenterPanel(dialogParams))
        DialogBuilder.CancelActionDescriptor().getAction(dialogWrapper).isEnabled = false
        when (dialogParams) {
          is DialogParams.ProjectIjent -> {
            addOkAction().setText(IjentImplBundle.message("action.close.projects.text", dialogParams.projects.size))
          }
          is DialogParams.UnrelatedIjent -> {
            addOkAction().setText(IjentImplBundle.message("action.stop.ijent.text"))
          }
        }
        dialogWrapper.setShouldUseWriteIntentReadAction(false)
      }

      cont.invokeOnCancellation {
        ApplicationManager.getApplication().invokeLater(
          { builder.dialogWrapper.close(DialogWrapper.CANCEL_EXIT_CODE) },
          ModalityState.any(),
        )
      }

      // It's crucial here to pump coroutine event loop while the dialog is shown
      // because otherwise canceling the dialog would not even be dispatched,
      // and the dialog (created to visualize the freeze) becomes a cause of the freeze to continue.
      builder.dialogWrapper.registerWhenShowing(dialogSession)
      val exitCode = builder.showWithPump(coroutineContext)

      if (exitCode == DialogWrapper.OK_EXIT_CODE) {
        when (dialogParams) {
          is DialogParams.ProjectIjent -> {
            ApplicationManager.getApplication().invokeLater {
              WriteIntentReadAction.run {
                for (projectToClose in dialogParams.projectList) {
                  ProjectManager.getInstance().closeAndDispose(projectToClose)
                }
              }
              WelcomeFrame.showIfNoProjectOpened()
            }
            dialogParams.eelDescriptor.getResolvedEelMachine().asSafely<IjentMachine>()?.getCachedIjentSession()?.close()
            cont.resume(ProjectCloseDecision(dialogParams.eelDescriptor))
          }
          is DialogParams.UnrelatedIjent -> {
            dialogParams.eelDescriptor.getResolvedEelMachine().asSafely<IjentMachine>()?.getCachedIjentSession()?.close()
            cont.resume(IjentUnavailableHandlerResult.UnrelatedIjent(dialogParams.eelDescriptor))
          }
        }
      }
      else {
        cont.resumeWithException(IllegalStateException("Unexpected exit code: $exitCode"))
      }
    }
    return closeDecision
  }

  private fun Panel.createDefaultPanel(dialogParams: DialogParams) {
    row {
      icon(AllIcons.General.WarningDialog)
        .align(AlignY.TOP)
        .customize(UnscaledGaps(right = 12))
      panel {
        when (dialogParams) {
          is DialogParams.ProjectIjent -> {
            row {
              text(IjentImplBundle.message("label.projects.below.should.be.closed"))
                .customize(UnscaledGaps(bottom = 12))
            }
            for (project in dialogParams.projects) {
              row {
                icon(AllIcons.Nodes.Project)
                  .customize(UnscaledGaps(right = 4))
                label(project.name).bold()
              }
            }
          }
          is DialogParams.UnrelatedIjent -> {
            row {
              text(IjentImplBundle.message("label.ijent.should.be.stopped"))
                .customize(UnscaledGaps(bottom = 12))
            }
            row {
              @NonNls val ijentName = dialogParams.eelDescriptor.name
              label(ijentName).bold()
            }
          }
        }
      }.align(AlignY.TOP)
    }
  }

  private fun createCenterPanel(dialogParams: DialogParams): JComponent {
    val session = dialogParams.eelDescriptor.getResolvedEelMachine().asSafely<IjentMachine>()?.getCachedIjentSession()
    val statTab = session?.let { IjentStatDashboard(session.eventBus.counter) }
    val preferredWidth = maxOf(480, statTab?.component?.preferredSize?.width ?: 0)
    return panel {
      createDefaultPanel(dialogParams)
      if (statTab != null) {
        createStatPanel(statTab, session.getIjentInstance(dialogParams.eelDescriptor))
      }
    }
      .withBorder(JBUI.Borders.empty(16, 12, 8, 12))
      .withPreferredWidth(preferredWidth)
      .withMinimumWidth(200)
  }

  private fun Panel.createStatPanel(statDashboard: IjentStatDashboard, eelApi: EelApi) {
    statDashboard.component.launchOnShow("ping request") {
      makePingRequest(eelApi)
    }
    collapsibleGroup(IjentImplBundle.message("tab.title.ijent.dashboard.stat")) {
      row {
        cell(statDashboard.component)
      }
    }
  }

  private suspend fun makePingRequest(eelApi: EelApi) {
    eelApi.fs.stat(eelApi.userInfo.home).eelIt()
  }
}

private fun DialogWrapper.registerWhenShowing(dialogSession: CompletableDeferred<ReconnectUiDialogImpl>) {
  application.invokeLater(
    {
      if (contentPane.isShowing) {
        dialogSession.complete(ReconnectUiDialogImpl(ModalityState.stateForComponent(contentPane), contentPane))
      }
      else if (dialogSession.isActive && !isDisposed) {
        registerWhenShowing(dialogSession)
      }
    },
    ModalityState.any(),
  )
}

private fun DialogBuilder.showWithPump(coroutineContext: CoroutineContext): Int {
  @Suppress("INVISIBLE_REFERENCE")
  return when (val loop = coroutineContext[ContinuationInterceptor]) {
    is MainCoroutineDispatcher -> show()
    is kotlinx.coroutines.EventLoop -> {
      // Use active waiting since it's the simplest way. Listening for dispatched events is more complex.
      val future = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
        {
          application.invokeLater(
            {
              @Suppress("RAW_RUN_BLOCKING")
              runBlocking(loop) { }
            },
            ModalityState.any(),
          )
        },
        0L, 50L, TimeUnit.MILLISECONDS,
      )

      try {
        return show()
      }
      finally {
        future.cancel(false)
      }
    }
    else -> error("Unknown loop type: $loop")
  }
}

private val LOG = logger<IjentUnavailableDialogHandler>()