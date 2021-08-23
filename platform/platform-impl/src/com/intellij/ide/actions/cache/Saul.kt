// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.cache

import com.intellij.ide.IdeBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.concurrent.CompletableFuture
import javax.swing.event.HyperlinkEvent

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

  private val actionSeq: Iterator<RecoveryAction> = actions.iterator()
  @Volatile
  private var next: RecoveryAction? = null

  fun start(project: Project) {
    // we expect that at least one action recovery exist: cache invalidation
    perform(nextRecoveryAction(project), project)
  }

  fun perform(recoveryAction: RecoveryAction, project: Project) {
    recoveryAction.performUnderProgress(project, true) { p ->
      if (hasNextRecoveryAction(p)) {
        askUserToContinue(p)
      }
    }
  }

  private fun askUserToContinue(project: Project) {
    if (!hasNextRecoveryAction(project)) return
    val recoveryAction = actionSeq.next()

    NotificationGroupManager.getInstance().getNotificationGroup("IDE Caches")
      .createNotification(IdeBundle.message("notification.cache.diagnostic.helper.title"),
                          IdeBundle.message("notification.cache.diagnostic.helper.text", recoveryAction.presentableName),
                          NotificationType.INFORMATION)
      .setListener(object : NotificationListener.Adapter() {
        override fun hyperlinkActivated(notification: Notification, e: HyperlinkEvent) {
          notification.expire()
          if (e.description == "next") {
            perform(recoveryAction, project)
          }
        }
      })
      .notify(project)
  }

  private fun hasNextRecoveryAction(project: Project): Boolean {
    if (next != null) return true
    while (actionSeq.hasNext()) {
      next = actionSeq.next()
      if (next!!.canBeApplied(project)) {
        return true
      }
    }
    next = null
    return false
  }

  private fun nextRecoveryAction(project: Project): RecoveryAction {
    assert(hasNextRecoveryAction(project))
    return next!!
  }
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
        for (problem in res.problems) {
          RecoveryWorker.LOG.error("${recoveryAction.actionKey} found and fixed a problem: ${problem.message}")
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
