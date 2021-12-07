// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.cache

import com.intellij.ide.IdeBundle
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
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
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
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

  fun sortThingsOut(recoveryScope: RecoveryScope) = RecoveryWorker(sortedActions).start(recoveryScope)
}

private class RecoveryWorker(val actions: Collection<RecoveryAction>) {
  companion object {
    val LOG = logger<RecoveryWorker>()
  }

  private val actionSeq = ConcurrentLinkedQueue(actions)

  fun start(recoveryScope: RecoveryScope) {
    // we expect that at least one action recovery exist: cache invalidation
    perform(nextRecoveryAction(recoveryScope), recoveryScope, 0)
  }

  fun perform(recoveryAction: RecoveryAction, recoveryScope: RecoveryScope, idx: Int) {
    recoveryAction.performUnderProgress(recoveryScope, true) { scope ->
      if (hasNextRecoveryAction(scope)) {
        askUserToContinue(scope, recoveryAction, idx)
      }
    }
  }

  private fun askUserToContinue(recoveryScope: RecoveryScope, previousRecoveryAction: RecoveryAction, idx: Int) {
    if (!hasNextRecoveryAction(recoveryScope)) return
    val recoveryAction = nextRecoveryAction(recoveryScope)
    val next = idx + 1

    val notification = NotificationGroupManager.getInstance().getNotificationGroup("Cache Recovery")
      .createNotification(
        IdeBundle.message("notification.cache.diagnostic.helper.title"),
        IdeBundle.message("notification.cache.diagnostic.helper.text",
          previousRecoveryAction.presentableName,
          next,
          service<Saul>().sortedActions.filter { it.canBeApplied(recoveryScope) }.size),
        NotificationType.WARNING
      )
    notification
      .addAction(DumbAwareAction.create(IdeBundle.message("notification.cache.diagnostic.stop.text")) {
        notification.expire()
        reportStoppedToFus(recoveryScope.project)
      })
      .addAction(DumbAwareAction.create(recoveryAction.presentableName) {
        notification.expire()
        perform(recoveryAction, recoveryScope, next)
      })
      .setImportant(true)
      .notify(recoveryScope.project)
  }

  private fun hasNextRecoveryAction(recoveryScope: RecoveryScope): Boolean {
    while (actionSeq.isNotEmpty()) {
      if (actionSeq.peek().canBeApplied(recoveryScope)) {
        return true
      }
      actionSeq.poll()
    }
    return false
  }

  private fun nextRecoveryAction(recoveryScope: RecoveryScope): RecoveryAction {
    assert(hasNextRecoveryAction(recoveryScope))
    return actionSeq.poll()
  }

  private fun reportStoppedToFus(project: Project) = CacheRecoveryUsageCollector.recordGuideStoppedEvent(project)
}

internal fun RecoveryAction.performUnderProgress(recoveryScope: RecoveryScope, fromGuide: Boolean, onComplete: (RecoveryScope) -> Unit = {}) {
  val recoveryAction = this
  val project = recoveryScope.project
  object : Task.Backgroundable(project, IdeBundle.message("recovery.progress.title", recoveryAction.presentableName)) {
    override fun run(indicator: ProgressIndicator) {
      CacheRecoveryUsageCollector.recordRecoveryPerformedEvent(recoveryAction, fromGuide, project)
      recoveryAction.perform(recoveryScope).handle { res, err ->
        if (err != null) {
          RecoveryWorker.LOG.error(err)
          return@handle
        }

        if (res.problems.isNotEmpty()) {
          RecoveryWorker.LOG.error("${recoveryAction.actionKey} found and fixed ${res.problems.size} problems, samples: " +
                                   res.problems.take(10).joinToString(", ") { it.message })
        }

        onComplete(res.scope)
      }
    }
  }.queue()
}

@ApiStatus.Internal
interface RecoveryAction {
  val performanceRate: Int

  val presentableName: @Nls(capitalization = Nls.Capitalization.Title) String

  val actionKey: String

  fun perform(recoveryScope: RecoveryScope): CompletableFuture<AsyncRecoveryResult> {
    try {
      return CompletableFuture.completedFuture(AsyncRecoveryResult(recoveryScope, performSync(recoveryScope)))
    }
    catch (e: Exception) {
      return CompletableFuture.failedFuture(e)
    }
  }

  fun performSync(recoveryScope: RecoveryScope): List<CacheInconsistencyProblem> =
    throw NotImplementedError()

  fun canBeApplied(recoveryScope: RecoveryScope): Boolean = true
}

@ApiStatus.Internal
sealed interface RecoveryScope {
  val project: Project

  companion object {
    fun createInstance(e: AnActionEvent): RecoveryScope {
      val project = e.project!!
      if (e.place == ActionPlaces.PROJECT_VIEW_POPUP) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: emptyArray()
        return FilesRecoveryScope(project, files.toSet())
      }
      return ProjectRecoveryScope(project)
    }
  }
}

data class ProjectRecoveryScope(override val project: Project) : RecoveryScope

data class FilesRecoveryScope(override val project: Project, val files: Set<VirtualFile>) : RecoveryScope

data class AsyncRecoveryResult(val scope: RecoveryScope, val problems: List<CacheInconsistencyProblem>)

interface CacheInconsistencyProblem {
  val message: String
}

class ExceptionalCompletionProblem(private val e: Throwable): CacheInconsistencyProblem {
  override val message: String
    get() = """Exception: ${e.javaClass} with message ${e.message}"""
}
