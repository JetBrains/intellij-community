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
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.IjentSessionState
import com.intellij.platform.ijent.community.impl.nio.IjentUnavailableHandler
import com.intellij.platform.ijent.community.impl.nio.IjentUnavailableUserDecisionException
import com.intellij.platform.ijent.community.impl.nio.ReconnectUiDialogImpl
import com.intellij.platform.ijent.community.impl.nio.ReconnectUiHandleImpl
import com.intellij.platform.ijent.community.ui.actions.IjentImplBundle
import com.intellij.platform.ijent.community.ui.actions.dashboard.IjentStatDashboard
import com.intellij.platform.ijent.community.ui.actions.dashboard.printTable
import com.intellij.platform.ijent.runningIjentSessionOrNull
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

private class EdtOnceTask : OnceTask<Nothing, ReconnectUiDialogImpl>(
  shouldCacheFailure = { it is IjentUnavailableUserDecisionException },
) {
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
@ApiStatus.Internal
@VisibleForTesting
class NotRespondingFilesystemDialogService {
  private val pendingRequests = ConcurrentHashMap<EelDescriptor, Pair<List<Project>, EdtOnceTask>>()
  suspend fun doOnceOrWait(dialogParams: IjentUnavailableDialogHandler.DialogParams, onComputing: (Deferred<ReconnectUiDialogImpl>) -> Unit, f: suspend (CompletableDeferred<ReconnectUiDialogImpl>) -> Nothing): Nothing {
    val onceTask = pendingRequests.compute(dialogParams.eelDescriptor) { _, v ->
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
    onceTask.getOrCompute(onComputing, f)
  }

  companion object {
    fun getInstance(): NotRespondingFilesystemDialogService = service()
  }
}

@ApiStatus.Internal
@VisibleForTesting
class IjentUnavailableDialogHandler : IjentUnavailableHandler {
  override suspend fun showModalDialog(eelDescriptor: EelDescriptor, uiHandle: ReconnectUiHandleImpl): Nothing {
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
    val sessionState = eelDescriptor.getResolvedEelMachine().asSafely<IjentMachine>()?.ijentSessionState
    val capturedInitialSession = (sessionState?.value as? IjentSessionState.Deployed)?.session
    LOG.warn("IJent for ${eelDescriptor.name} is unavailable (${sessionState?.value.debugString(eelDescriptor)}). Modal dialog will be shown.")
    NotRespondingFilesystemDialogService.getInstance().doOnceOrWait(dialogParams, uiHandle::setDialogSession) { dialogSession ->
      coroutineScope {
        launch(Dispatchers.IO) {
          logIjentDiagnostics(eelDescriptor, sessionState, capturedInitialSession)
        }
        showCloseProjectDialog(dialogSession, dialogParams, sessionState, capturedInitialSession)
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

  private suspend fun showCloseProjectDialog(
    dialogSession: CompletableDeferred<ReconnectUiDialogImpl>,
    dialogParams: DialogParams,
    sessionState: StateFlow<IjentSessionState>?,
    capturedSession: IjentSession?,
  ): Nothing {
    val coroutineContext = currentCoroutineContext()
    suspendCancellableCoroutine<Nothing> { cont ->
      val builder = DialogBuilder(dialogParams.projectList.first()).apply {
        setTitle(IjentImplBundle.message("dialog.title.ijent.unavailable"))
        setCenterPanel(createCenterPanel(dialogParams, sessionState, capturedSession))
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
            dialogParams.eelDescriptor.getResolvedEelMachine().asSafely<IjentMachine>()?.ijentSessionState?.value?.runningIjentSessionOrNull?.close()
            throw IjentUnavailableUserDecisionException(
              "The user chose to close the project instead of waiting for IJent ${dialogParams.eelDescriptor}."
            )
          }
          is DialogParams.UnrelatedIjent -> {
            dialogParams.eelDescriptor.getResolvedEelMachine().asSafely<IjentMachine>()?.ijentSessionState?.value?.runningIjentSessionOrNull?.close()
            throw IjentUnavailableUserDecisionException(
              "The user chose to stop IJent ${dialogParams.eelDescriptor}, which has no open projects."
            )
          }
        }
      }
      else {
        cont.resumeWithException(IllegalStateException("Unexpected exit code: $exitCode"))
      }
    }
  }

  private fun Panel.createDefaultPanel(dialogParams: DialogParams, sessionState: StateFlow<IjentSessionState>?) {
    row {
      icon(AllIcons.General.WarningDialog)
        .align(AlignY.TOP)
        .customize(UnscaledGaps(right = 12))
      panel {
        bindIjentSessionState(dialogParams, sessionState)
        when (dialogParams) {
          is DialogParams.ProjectIjent -> {
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
              @NonNls val ijentName = dialogParams.eelDescriptor.name
              label(ijentName).bold()
            }
          }
        }
      }.align(AlignY.TOP)
    }
  }

  private fun createCenterPanel(
    dialogParams: DialogParams,
    sessionState: StateFlow<IjentSessionState>?,
    capturedSession: IjentSession?,
  ): JComponent {
    val statTab = capturedSession?.let { IjentStatDashboard(it.eventBus.counter) }
    val preferredWidth = maxOf(480, statTab?.component?.preferredSize?.width ?: 0)
    return panel {
      createDefaultPanel(dialogParams, sessionState)
      if (statTab != null) {
        createStatPanel(statTab, capturedSession, dialogParams.eelDescriptor)
      }
    }
      .withBorder(JBUI.Borders.empty(16, 12, 8, 12))
      .withPreferredWidth(preferredWidth)
      .withMinimumWidth(200)
  }

  private fun Panel.createStatPanel(statDashboard: IjentStatDashboard, capturedSession: IjentSession, eelDescriptor: EelDescriptor) {
    statDashboard.component.launchOnShow("ping request") {
      makePingRequest(capturedSession.getIjentInstance(eelDescriptor))
    }
    collapsibleGroup(IjentImplBundle.message("tab.title.ijent.dashboard.stat.for.session", capturedSession.debugString(eelDescriptor))) {
      row {
        cell(statDashboard.component)
      }
    }
  }

  private suspend fun makePingRequest(eelApi: EelApi) {
    eelApi.fs.stat(eelApi.userInfo.home).eelIt()
  }

  private fun Panel.bindIjentSessionState(dialogParams: DialogParams, sessionState: StateFlow<IjentSessionState>?) {
    row {
      val description = when (dialogParams) {
        is DialogParams.ProjectIjent -> IjentImplBundle.message("label.projects.below.should.be.closed")
        is DialogParams.UnrelatedIjent -> IjentImplBundle.message("label.ijent.should.be.stopped")
      }
      text(description)
    }
    row {
      val statusText = label("")
        .customize(UnscaledGaps(bottom = 12))
        .component
      statusText.launchOnShow("monitor IJent session") {
        sessionState?.collectLatest { state ->
          val sessionDebugString = (state as? IjentSessionState.Deployed)?.session?.debugString(dialogParams.eelDescriptor)
          while (true) {
            statusText.text = when (state) {
              is IjentSessionState.Deployed if state.session.isRunning ->
                IjentImplBundle.message("label.ijent.status.running", sessionDebugString!!)
              is IjentSessionState.Deployed -> IjentImplBundle.message("label.ijent.status.stopped", sessionDebugString!!)
              is IjentSessionState.Deploying -> IjentImplBundle.message("label.ijent.status.deploying")
              is IjentSessionState.Failed -> IjentImplBundle.message("label.ijent.status.failed")
              is IjentSessionState.NotDeployed -> IjentImplBundle.message("label.ijent.status.none")
            }
            delay(1.seconds)
          }
        }
      }
    }
  }

  private suspend fun logIjentDiagnostics(
    eelDescriptor: EelDescriptor,
    sessionState: StateFlow<IjentSessionState>?,
    capturedSession: IjentSession?,
  ) {
    var backOff = 2.seconds
    while (true) {
      val statTable = capturedSession?.eventBus?.counter?.snapshot()?.printTable()
      val path = PerformanceWatcher.getInstance().dumpThreads("ijent", true, true)
      LOG.warn(
        "IJent for ${eelDescriptor.name} is unavailable (${sessionState?.value.debugString(eelDescriptor)}). Thread dump saved to $path."
      )
      if (statTable != null) {
        LOG.warn("Calls statistics for captured ${capturedSession.debugString(eelDescriptor)}:\n\n$statTable")
      }
      delay(backOff)
      backOff *= 2
    }
  }
}

private fun IjentSession.debugString(eelDescriptor: EelDescriptor): String {
  val objectId = System.identityHashCode(this).toString(16)
  val remotePid = getIjentInstance(eelDescriptor).ijentProcessInfo.remotePid.value
  return "IjentSession@$objectId(remotePid=$remotePid)"
}

private fun IjentSessionState?.debugString(eelDescriptor: EelDescriptor): String = when (this) {
  is IjentSessionState.Deployed -> "${session.debugString(eelDescriptor)}, ${if (session.isRunning) "running" else "stopped"}"
  is IjentSessionState.Deploying -> "deploying"
  is IjentSessionState.Failed -> "deployment failed: ${cause.message ?: cause.javaClass.name}"
  is IjentSessionState.NotDeployed -> "not deployed"
  null -> "session state unavailable"
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
              var markerReached = false
              loop.dispatch(EmptyCoroutineContext) {
                markerReached = true
              }
              while (!markerReached) {
                if (loop.processNextEvent() > 0L) break
              }
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
