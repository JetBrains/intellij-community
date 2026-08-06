// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.filters.ExceptionFilters
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.ui.layout.impl.RunnerContentUi
import com.intellij.ide.actions.RevealFileAction
import com.intellij.java.debugger.impl.shared.SharedDebuggerUtils
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.threadDumpParser.ThreadDumpParser
import com.intellij.threadDumpParser.ThreadState
import com.intellij.ui.content.Content
import com.intellij.unscramble.DumpItem
import com.intellij.unscramble.InfoDumpItem
import com.intellij.unscramble.parseJcmdJsonThreadDump
import com.intellij.unscramble.toDumpItems
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

internal object ThreadDumpExecutor {
  private val THREAD_DUMP_STATE_KEY = Key.create<ThreadDumpState>("java.run.thread.dump.controller")
  private val FULL_DUMP_PREVIEW_DELAY = 500.milliseconds
  private const val THREAD_DUMP_NOTIFICATION_GROUP = "Thread dump capture"
  private const val STICKY_THREAD_DUMP_NOTIFICATION_GROUP = "Thread dump capture sticky"
  private val nonModalEdtContext: CoroutineContext
    get() = Dispatchers.EDT + ModalityState.nonModal().asContextElement()

  @JvmStatic
  fun dump(
    project: Project,
    pid: String,
    processHandler: ProcessHandler,
    runnerContentUi: RunnerContentUi,
    scope: GlobalSearchScope,
    fallbackDump: Runnable,
  ) {
    val state = getThreadDumpState(processHandler)
    state.scope.launch {
      // We cannot start the next dump, while the previous dump is still in progress
      if (state.isFullDumpInProgress()) {
        focusLastDumpAndShowFullDumpInProgressNotification(state, runnerContentUi, project)
        return@launch
      }
      // Compute platform thread dump right away, so that it's immediately available for the preview in case full dump takes too long
      val platformThreadDump = withContext(Dispatchers.Default) {
        computePlatformThreadDump(pid)
      }
      if (platformThreadDump == null) {
        fallbackDump.run()
        return@launch
      }
      if (ThreadDumpProvider.isVirtualThreadsDumpAvailable(pid)) {
        if (!state.isReadyToStartFullThreadDump()) {
          focusLastDumpAndShowFullDumpInProgressNotification(state, runnerContentUi, project)
          return@launch
        }
        try {
          startFullThreadDump(state, project, pid, runnerContentUi, scope, platformThreadDump)
        }
        finally {
          state.finishFullDump()
        }
      }
      else {
        showPlatformThreadDump(project, runnerContentUi, scope, platformThreadDump).also {
          state.rememberDumpContent(it)
        }
      }
    }
  }

  private fun getThreadDumpState(processHandler: ProcessHandler): ThreadDumpState {
    processHandler.getUserData(THREAD_DUMP_STATE_KEY)?.let { return it }

    val state = ThreadDumpState()
    val result = processHandler.putUserDataIfAbsent(THREAD_DUMP_STATE_KEY, state)
    if (result === state) {
      processHandler.addProcessListener(
        object : ProcessListener {
          override fun processTerminated(event: ProcessEvent) {
            processHandler.removeProcessListener(this)
            processHandler.putUserData(THREAD_DUMP_STATE_KEY, null)
            state.dispose()
          }
        }
      )
    }
    else {
      state.dispose()
    }
    return result
  }

  private suspend fun startFullThreadDump(
    state: ThreadDumpState,
    project: Project,
    pid: String,
    runnerContentUi: RunnerContentUi,
    scope: GlobalSearchScope,
    platformThreadDump: List<ThreadState>,
  ) {
    val previewShown = AtomicBoolean()

    suspend fun showPlatformDump(additionalDumpItem: DumpItem? = null) {
      showPlatformThreadDump(project, runnerContentUi, scope, platformThreadDump, additionalDumpItem).also {
        state.rememberDumpContent(it)
      }
    }
    withBackgroundProgress(project, ExecutionBundle.message("run.configuration.full.thread.dump.progress.title")) {
      val previewJob = launch {
        delay(FULL_DUMP_PREVIEW_DELAY)
        if (previewShown.compareAndSet(false, true)) {
          showPlatformDump(createFullDumpInProgressPreviewItem())
        }
      }
      try {
        val result = computeFullOrPlatformDumpItems(pid, platformThreadDump)
        previewJob.cancelAndJoin()
        when (result) {
          is FullThreadDumpResult.Success -> {
            showDumpItems(project, result.dumpItems, runnerContentUi, scope).also {
              state.rememberDumpContent(it)
            }
          }
          is FullThreadDumpResult.SavedToFile -> {
            focusLastDumpAndShowFullDumpTooLargeNotification(state, runnerContentUi, project, result.file)
          }
          is FullThreadDumpResult.NoVirtualThreads -> {
            previewJob.cancelAndJoin()
            // In case there are no virtual threads, make sure we do not show the platform dump twice
            if (previewShown.compareAndSet(false, true)) {
              showPlatformDump()
            }
          }
          is FullThreadDumpResult.Failed -> {
            showPlatformDump(createFullDumpUnavailableItem())
          }
        }
      }
      catch (e: CancellationException) {
        withContext(NonCancellable) {
          previewJob.cancelAndJoin()
        }
        throw e
      }
    }
  }

  private suspend fun computeFullOrPlatformDumpItems(
    pid: String,
    platformThreadDump: List<ThreadState>,
  ): FullThreadDumpResult = withContext(Dispatchers.Default) {
    val dump = coroutineToIndicator { indicator ->
      ThreadDumpProvider.dumpAllThreads(pid, indicator)
    }

    currentCoroutineContext().ensureActive()
    val dumpText = when (dump) {
      is ThreadDumpProvider.FullThreadDump.SavedToFile -> return@withContext FullThreadDumpResult.SavedToFile(dump.file())
      is ThreadDumpProvider.FullThreadDump.Text -> dump.text()
      else -> return@withContext FullThreadDumpResult.Failed
    }
    val fullThreadDumpState = parseJcmdJsonThreadDump(dumpText, platformThreadDump)
                              ?: return@withContext FullThreadDumpResult.Failed

    if (fullThreadDumpState.threadStates.any { it.isVirtual }) {
      FullThreadDumpResult.Success(toDumpItems(fullThreadDumpState))
    }
    else {
      FullThreadDumpResult.NoVirtualThreads
    }
  }

  private sealed interface FullThreadDumpResult {
    data class SavedToFile(val file: Path) : FullThreadDumpResult
    data class Success(val dumpItems: List<DumpItem>) : FullThreadDumpResult
    data object Failed : FullThreadDumpResult
    data object NoVirtualThreads : FullThreadDumpResult
  }

  private suspend fun showPlatformThreadDump(
    project: Project,
    runnerContentUi: RunnerContentUi,
    scope: GlobalSearchScope,
    platformThreadDump: List<ThreadState>,
    additionalDumpItem: DumpItem? = null,
  ): Content? {
    val platformThreadDumpItems = withContext(Dispatchers.Default) {
      toDumpItems(platformThreadDump) + listOfNotNull(additionalDumpItem)
    }
    return showDumpItems(project, platformThreadDumpItems, runnerContentUi, scope)
  }

  private suspend fun showDumpItems(
    project: Project,
    dumpItems: List<DumpItem>,
    runnerContentUi: RunnerContentUi,
    scope: GlobalSearchScope,
  ): Content? {
    val filters = ExceptionFilters.getFilters(scope)
    return withContext(nonModalEdtContext) {
      val layoutUi = runnerContentUi.runnerLayoutUi
      if (layoutUi.isDisposed) return@withContext null
      val panel = SharedDebuggerUtils.createThreadDumpPanel(project, dumpItems, layoutUi, filters)
      layoutUi.contents.firstOrNull { it.component === panel }
    }
  }

  private fun createFullDumpInProgressPreviewItem(): InfoDumpItem =
    InfoDumpItem(
      ExecutionBundle.message("run.configuration.thread.dump.preview.title"),
      ExecutionBundle.message("run.configuration.thread.dump.preview.full.dump.in.progress"),
    )

  private fun createFullDumpUnavailableItem(): InfoDumpItem =
    InfoDumpItem(
      ExecutionBundle.message("run.configuration.full.thread.dump.unavailable.title"),
      ExecutionBundle.message("run.configuration.full.thread.dump.unavailable.message"),
    )

  private suspend fun focusLastDumpAndShowFullDumpInProgressNotification(state: ThreadDumpState, runnerContentUi: RunnerContentUi, project: Project) {
    withContext(nonModalEdtContext) {
      focusLastDumpContent(state, runnerContentUi)
      threadDumpNotificationGroup
        .createNotification(
          fullDumpInProgressTitle(),
          ExecutionBundle.message("run.configuration.full.thread.dump.already.in.progress.message"),
          NotificationType.INFORMATION,
        )
        .notify(project)
    }
  }

  private suspend fun focusLastDumpAndShowFullDumpTooLargeNotification(state: ThreadDumpState, runnerContentUi: RunnerContentUi, project: Project, file: Path) {
    withContext(nonModalEdtContext) {
      focusLastDumpContent(state, runnerContentUi)
      val notification = stickyThreadDumpNotificationGroup
        .createNotification(
          fullDumpTooLargeTitle(),
          fullDumpTooLargeMessage(file),
          NotificationType.INFORMATION,
        )
        .setImportant(true)
        .setRemoveWhenExpired(false)
      if (RevealFileAction.isSupported()) {
        notification.addAction(NotificationAction.createSimple(revealFullDumpActionText()) {
          RevealFileAction.openFile(file)
        })
      }
      notification.notify(project)
    }
  }

  private suspend fun focusLastDumpContent(state: ThreadDumpState, runnerContentUi: RunnerContentUi) {
    withContext(nonModalEdtContext) {
      val layoutUi = runnerContentUi.runnerLayoutUi
      if (layoutUi.isDisposed) return@withContext
      val content = state.lastDumpContent()
      if (content != null && layoutUi.contents.contains(content)) {
        layoutUi.selectAndFocus(content, true, true)
      }
    }
  }

  private fun fullDumpInProgressTitle(): @NlsContexts.NotificationTitle String =
    ExecutionBundle.message("run.configuration.full.thread.dump.already.in.progress.title")

  private fun fullDumpTooLargeTitle(): @NlsContexts.NotificationTitle String =
    ExecutionBundle.message("run.configuration.full.thread.dump.too.large.title")

  private fun revealFullDumpActionText(): @NlsContexts.NotificationContent String =
    ExecutionBundle.message("run.configuration.full.thread.dump.reveal.action", RevealFileAction.getFileManagerName())

  private fun fullDumpTooLargeMessage(file: Path): @NlsContexts.NotificationContent String =
    ExecutionBundle.message(
      "run.configuration.full.thread.dump.too.large.message",
      file.parent ?: file,
    )

  private val threadDumpNotificationGroup: NotificationGroup
    get() = NotificationGroupManager.getInstance().getNotificationGroup(THREAD_DUMP_NOTIFICATION_GROUP)

  private val stickyThreadDumpNotificationGroup: NotificationGroup
    get() = NotificationGroupManager.getInstance().getNotificationGroup(STICKY_THREAD_DUMP_NOTIFICATION_GROUP)

  private fun computePlatformThreadDump(pid: String): List<ThreadState>? {
    val dump = ThreadDumpProvider.dumpThreads(pid) ?: return null
    val threadStates = ThreadDumpParser.parse(dump)
    return threadStates
  }

  private class ThreadDumpState {
    val scope = service<RunThreadDumpCoroutineScopeService>().coroutineScope.childScope("Run thread dump", Dispatchers.Default)
    private val fullDumpInProgress = AtomicBoolean()
    private val lastDumpContent = AtomicReference<Content?>()

    fun isFullDumpInProgress(): Boolean = fullDumpInProgress.get()

    fun isReadyToStartFullThreadDump(): Boolean = fullDumpInProgress.compareAndSet(false, true)

    fun finishFullDump() {
      fullDumpInProgress.set(false)
    }

    fun rememberDumpContent(content: Content?) {
      if (content != null) {
        lastDumpContent.set(content)
      }
    }

    fun lastDumpContent(): Content? {
      val content = lastDumpContent.get() ?: return null
      if (!content.isValid || content.manager == null) {
        lastDumpContent.compareAndSet(content, null)
        return null
      }
      return content
    }

    fun dispose() {
      scope.cancel()
    }
  }

  @Service(Service.Level.APP)
  private class RunThreadDumpCoroutineScopeService(val coroutineScope: CoroutineScope)
}
