// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.cache

import com.intellij.ide.IdeBundle
import com.intellij.notification.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue

@Service
internal class Saul {
  companion object {
    @JvmStatic
    private val RECOVERY_ACTION_EP_NAME = ExtensionPointName.create<RecoveryAction>("com.intellij.recoveryAction")
  }

  @Volatile
  private var recoveryActionModificationCounter = 0L

  init {
    val listener: () -> Unit = {
      recoveryActionModificationCounter++
    }
    RECOVERY_ACTION_EP_NAME.addChangeListener(listener, ApplicationManager.getApplication())
  }

  val sortedActions: List<RecoveryAction>
    get() = RECOVERY_ACTION_EP_NAME.extensionList.sortedByDescending { it.performanceRate }

  val modificationRecoveryActionTracker = ModificationTracker { recoveryActionModificationCounter }

  fun sortThingsOut(project: Project) = RecoveryWorker(sortedActions).start(project)
}

private class RecoveryWorker(val actions: Collection<RecoveryAction>) {
  companion object {
    val LOG = logger<RecoveryWorker>()
  }

  private val actionSeq = ConcurrentLinkedQueue(actions)

  fun start(project: Project) {
    // we expect that at least one action recovery exist: cache invalidation
    perform(nextRecoveryAction(project), project, 0)
  }

  fun perform(recoveryAction: RecoveryAction, project: Project, idx: Int) {
    recoveryAction.performUnderProgress(project, true) { p ->
      if (hasNextRecoveryAction(p)) {
        askUserToContinue(p, recoveryAction, idx)
      }
    }
  }

  private fun askUserToContinue(project: Project, previousRecoveryAction: RecoveryAction, idx: Int) {
    if (!hasNextRecoveryAction(project)) return
    val recoveryAction = nextRecoveryAction(project)
    val next = idx + 1

    val notification = NotificationGroupManager.getInstance().getNotificationGroup("Cache Recovery")
      .createNotification(
        IdeBundle.message("notification.cache.diagnostic.helper.title"),
        IdeBundle.message("notification.cache.diagnostic.helper.text",
          previousRecoveryAction.presentableName,
          next,
          service<Saul>().sortedActions.filter { it.canBeApplied(project) }.size),
        NotificationType.WARNING
      )
    notification
      .addAction(DumbAwareAction.create(IdeBundle.message("notification.cache.diagnostic.stop.text")) {
        notification.expire()
        reportStoppedToFus(project)
      })
      .addAction(DumbAwareAction.create(recoveryAction.presentableName) {
        notification.expire()
        perform(recoveryAction, project, next)
      })
      .setImportant(true)
      .notify(project)
  }

  private fun hasNextRecoveryAction(project: Project): Boolean {
    while (actionSeq.isNotEmpty()) {
      if (actionSeq.peek().canBeApplied(project)) {
        return true
      }
      actionSeq.poll()
    }
    return false
  }

  private fun nextRecoveryAction(project: Project): RecoveryAction {
    assert(hasNextRecoveryAction(project))
    return actionSeq.poll()
  }

  private fun reportStoppedToFus(project: Project) = CacheRecoveryUsageCollector.recordGuideStoppedEvent(project)
}

internal fun RecoveryAction.performUnderProgress(project: Project, fromGuide: Boolean, onComplete: (Project) -> Unit = {}) {
  val recoveryAction = this
  object : Task.Backgroundable(project, IdeBundle.message("recovery.progress.title", recoveryAction.presentableName)) {
    override fun run(indicator: ProgressIndicator) {
      CacheRecoveryUsageCollector.recordRecoveryPerformedEvent(recoveryAction, fromGuide, project)
      recoveryAction.perform(project).handle { res, err ->
        if (err != null) {
          RecoveryWorker.LOG.error(err)
          return@handle
        }

        if (res.problems.isNotEmpty()) {
          RecoveryWorker.LOG.error("${recoveryAction.actionKey} found and fixed ${res.problems.size} problems, samples: " +
                                   res.problems.take(10).joinToString(", ") { it.message })
        }

        onComplete(res.project)
      }
    }
  }.queue()
}

@ApiStatus.Internal
interface RecoveryAction {
  val performanceRate: Int

  val presentableName: @Nls(capitalization = Nls.Capitalization.Title) String

  val actionKey: String

  fun perform(project: Project): CompletableFuture<AsyncRecoveryResult> {
    try {
      return CompletableFuture.completedFuture(AsyncRecoveryResult(project, performSync(project)))
    }
    catch (e: Exception) {
      return CompletableFuture.failedFuture(e)
    }
  }

  fun performSync(project: Project): List<CacheInconsistencyProblem> =
    throw NotImplementedError()

  fun canBeApplied(project: Project): Boolean = true
}

data class AsyncRecoveryResult(val project: Project, val problems: List<CacheInconsistencyProblem>)

interface CacheInconsistencyProblem {
  val message: String
}

class ExceptionalCompletionProblem(private val e: Throwable): CacheInconsistencyProblem {
  override val message: String
    get() = """Exception: ${e.javaClass} with message ${e.message}"""
}
