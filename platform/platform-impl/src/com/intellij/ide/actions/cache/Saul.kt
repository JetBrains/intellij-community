// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.cache

import com.intellij.ide.IdeBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
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

  fun sortThingsOut(project: Project?) = RecoveryWorker(sortedActions, project).start()
}

private class RecoveryWorker(val actions: Collection<RecoveryAction>, private val project: Project?) {
  private val actionSeq: Iterator<RecoveryAction> = actions
    .iterator()
    .asSequence()
    .filter { it.canBeApplied(project) }
    .iterator()

  fun start() {
    // we expect that at least one action recovery exist: cache invalidation
    perform(actionSeq.next())
  }

  fun perform(recoveryAction: RecoveryAction) {
    object : Task.Backgroundable(project, IdeBundle.message("recovery.progress.title", recoveryAction.presentableName)) {
      override fun run(indicator: ProgressIndicator) {
        recoveryAction.perform(project)
        if (actionSeq.hasNext()) {
          askUserToContinue()
        }
      }
    }.queue()
  }

  private fun askUserToContinue() {
    if (!actionSeq.hasNext()) return
    val recoveryAction = actionSeq.next()

    NotificationGroupManager.getInstance().getNotificationGroup("IDE Caches")
      .createNotification(IdeBundle.message("notification.cache.diagnostic.helper.title"),
                          IdeBundle.message("notification.cache.diagnostic.helper.text", recoveryAction.presentableName),
                          NotificationType.INFORMATION)
      .setListener(object : NotificationListener.Adapter() {
        override fun hyperlinkActivated(notification: Notification, e: HyperlinkEvent) {
          notification.expire()
          if (e.description == "next") {
            perform(recoveryAction)
          }
        }
      })
      .notify(null)
  }
}

@ApiStatus.Internal
interface RecoveryAction {
  val performanceRate: Int

  val presentableName: @Nls(capitalization = Nls.Capitalization.Title) String

  val actionKey: String

  fun perform(project: Project?)

  fun canBeApplied(project: Project?): Boolean
}

