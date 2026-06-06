// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.ijent.community.ui.actions.unavailable

import com.intellij.icons.AllIcons
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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
import com.intellij.platform.ijent.IjentEventBus
import com.intellij.platform.ijent.IjentMachine
import com.intellij.platform.ijent.community.impl.nio.IjentUnavailableHandler
import com.intellij.platform.ijent.community.impl.nio.IjentUnavailableHandlerResult
import com.intellij.platform.ijent.community.impl.nio.IjentUnavailableHandlerResult.ProjectCloseDecision
import com.intellij.platform.ijent.community.impl.nio.IjentUnavailableHandlerResult.UnrelatedIjent
import com.intellij.platform.ijent.community.ui.actions.IjentImplBundle
import com.intellij.platform.ijent.community.ui.actions.dashboard.IjentStatCounter
import com.intellij.platform.ijent.community.ui.actions.dashboard.IjentStatDashboard
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.asSafely
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.computeDetached
import kotlinx.coroutines.DelicateCoroutinesApi
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

private class EdtOnceTask : OnceTask<ProjectCloseDecision>() {
  override suspend fun <R> executeUnderLockIfNotAlreadyAcquired(f: suspend () -> R): R {
    return if (IjentCallerContext.getSaved()?.isDispatchThread == true) {
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

@Service
internal class NotRespondingFilesystemDialogService {
  private val pendingRequests = ConcurrentHashMap<EelDescriptor, Pair<List<Project>, OnceTask<ProjectCloseDecision>>>()
  suspend fun doOnceOrWait(ijentId: EelDescriptor, projects: List<Project>, f: suspend () -> ProjectCloseDecision): ProjectCloseDecision {
    val onceTask = pendingRequests.compute(ijentId) { _, v ->
      when {
        v == null -> projects to EdtOnceTask()
        v.first.containsAll(projects) -> v
        v.second.computedValue() != null -> projects to EdtOnceTask()
        else -> (v.first + projects).distinct() to v.second
      }
    }!!.second
    return onceTask.getOrCompute {
      f()
    }
  }

  companion object {
    fun getInstance(): NotRespondingFilesystemDialogService = service()
  }
}

internal class IjentUnavailableDialogHandler : IjentUnavailableHandler {
  override suspend fun showModalDialog(eelDescriptor: EelDescriptor): IjentUnavailableHandlerResult {
    val activeProject = ProjectUtil.getActiveProject()
    val projectsToClose = ProjectManager.getInstance().openProjects.filter {
      it.getEelDescriptor() == eelDescriptor
    }.sortedByDescending {
      activeProject == it
    }
    if (projectsToClose.isEmpty()) {
      eelDescriptor.getResolvedEelMachine().asSafely<IjentMachine>()?.getCachedIjentSession()?.close()
      return UnrelatedIjent(eelDescriptor)
    }
    return NotRespondingFilesystemDialogService.getInstance().doOnceOrWait(eelDescriptor, projectsToClose) {
      showCloseProjectDialog(eelDescriptor, projectsToClose).also {
        eelDescriptor.getResolvedEelMachine().asSafely<IjentMachine>()?.getCachedIjentSession()?.close()
      }
    }
  }

  private suspend fun showCloseProjectDialog(eelDescriptor: EelDescriptor, projects: List<Project>): ProjectCloseDecision {
    val coroutineContext = currentCoroutineContext()
    val closeDecision = suspendCancellableCoroutine { cont ->
      val builder = DialogBuilder(projects.first()).apply {
        setTitle(IjentImplBundle.message("dialog.title.ijent.unavailable"))
        setCenterPanel(createCenterPanel(eelDescriptor, projects))
        addCancelAction().setText(IjentImplBundle.message("action.close.projects.text", projects.size))
        dialogWrapper.setShouldUseWriteIntentReadAction(false)
      }

      cont.invokeOnCancellation {
        ApplicationManager.getApplication().invokeLater(
          { builder.dialogWrapper.close(DialogWrapper.OK_EXIT_CODE) },
          ModalityState.any(),
        )
      }

      // It's crucial here to pump coroutine event loop while the dialog is shown
      // because otherwise canceling the dialog would not even be dispatched,
      // and the dialog (created to visualize the freeze) becomes a cause of the freeze to continue.
      val exitCode = builder.showWithPump(coroutineContext)

      if (exitCode == DialogWrapper.CANCEL_EXIT_CODE) {
        ApplicationManager.getApplication().invokeLater {
          WriteIntentReadAction.run {
            for (projectToClose in projects) {
              ProjectManager.getInstance().closeAndDispose(projectToClose)
            }
          }
          WelcomeFrame.showIfNoProjectOpened()
        }
        cont.resume(ProjectCloseDecision(eelDescriptor))
      }
      else {
        cont.resume(null)
      }
    }
    return checkNotNull(closeDecision)
  }

  private fun Panel.createDefaultPanel(projects: List<Project>) {
    row {
      icon(AllIcons.General.WarningDialog)
        .align(AlignY.TOP)
        .customize(UnscaledGaps(right = 12))
      panel {
        row {
          text(IjentImplBundle.message("label.projects.below.should.be.closed"))
            .customize(UnscaledGaps(bottom = 12))
        }
        for (project in projects) {
          row {
            icon(AllIcons.Nodes.Project)
              .customize(UnscaledGaps(right = 4))
            label(project.name).bold()
          }
        }
      }.align(AlignY.TOP)
    }
  }

  private fun createCenterPanel(eelDescriptor: EelDescriptor, projects: List<Project>): JComponent {
    return panel {
      createDefaultPanel(projects)
      val session = eelDescriptor.getResolvedEelMachine().asSafely<IjentMachine>()?.getCachedIjentSession()
      if (session != null) {
        createStatPanel(session.eventBus, session.getIjentInstance(eelDescriptor))
      }
    }
      .withBorder(JBUI.Borders.empty(16, 12, 8, 12))
      .withPreferredWidth(480)
      .withMinimumWidth(480)
      .withMaximumWidth(480)
  }

  private fun Panel.createStatPanel(eventBus: IjentEventBus, eelApi: EelApi) {
    val stat = IjentStatCounter()
    val statTab = IjentStatDashboard(stat).launchOnShow()
    statTab.launchOnShow("ijent stats") {
      stat.process(eventBus) {
        launch { makePingRequest(eelApi) }
        awaitCancellation()
      }
    }
    row {
      cell(statTab)
    }
  }

  private suspend fun makePingRequest(eelApi: EelApi) {
    eelApi.fs.stat(eelApi.userInfo.home).eelIt()
  }
}

private fun DialogBuilder.showWithPump(coroutineContext: CoroutineContext): Int {
  @Suppress("INVISIBLE_REFERENCE")
  return when (val loop = coroutineContext[ContinuationInterceptor]) {
    is MainCoroutineDispatcher -> show()
    is kotlinx.coroutines.EventLoop -> {
      // Use active waiting since it's the simplest way. Listening for dispatched events is more complex.
      val future = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
        {
          ApplicationManager.getApplication().invokeLater(
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