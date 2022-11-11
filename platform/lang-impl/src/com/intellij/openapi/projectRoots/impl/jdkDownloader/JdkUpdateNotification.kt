// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.ide.actions.SettingsEntryPointAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.io.systemIndependentPath
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOG = logger<JdkUpdateNotification>()

/**
 * There are the following possibilities for a given JDK:
 *  - we have no notification (this object must be tracked)
 *  - we show update suggestion
 *  - the JDK update is running
 *  - a error notification is shown
 *
 *  This class keeps track of all such possibilities
 *
 *  The [whenComplete] callback is executed when we reach the final state, which is:
 *    - the error notification is dismissed
 *    - the update notification is dismissed (or rejected)
 *    - the JDK update is completed
 */
class JdkUpdateNotification(val jdk: Sdk,
                            val oldItem: JdkItem,
                            val newItem: JdkItem,
                            private val whenComplete: (JdkUpdateNotification) -> Unit
) {
  private val lock = ReentrantLock()

  private val jdkVersion = jdk.versionString
  private val jdkHome = jdk.homePath

  private var myIsTerminated = false
  private var myIsUpdateRunning = false

  /**
   * Can be either suggestion or error notification
   */
  private var myRetryNotification : Notification? = null

  val persistentId = "${jdk.name}-${oldItem.fullPresentationText}-${newItem.fullPresentationText}-${jdk.homePath}"

  private fun Notification.bindNextNotificationAndShow() {
    bindNextNotification(this)
    notify(null)
  }

  private fun bindNextNotification(notification: Notification) {
    lock.withLock {
      myRetryNotification?.expire()

      notification.whenExpired {
        lock.withLock {
          if (myRetryNotification === notification) {
            myRetryNotification = null
          }
        }
      }

      myRetryNotification = notification
    }
  }

  fun tryReplaceWithNewerNotification(other: JdkUpdateNotification? = null): Boolean = lock.withLock {
    //nothing to do if update is already running
    if (myIsUpdateRunning) return false

    //the pending notification is the same as before
    if (other != null && isSameNotification(other)) return false

    myRetryNotification?.expire()
    myRetryNotification = null
    reachTerminalState()

    return true
  }

  private fun isSameNotification(other: JdkUpdateNotification): Boolean {
    if (this.jdkVersion != other.jdkVersion) return false
    if (this.jdkHome != other.jdkHome) return false
    if (this.newItem != other.newItem) return false
    return true
  }

  /**
   * The state-machine reached it's end
   */
  fun reachTerminalState(): Unit = lock.withLock {
    if (myIsTerminated) return
    myIsTerminated = true
    whenComplete(this)
  }

  fun isTerminated() = lock.withLock { myIsTerminated }

  inner class InstallUpdateNotification : NotificationAction(ProjectBundle.message("notification.link.jdk.update.retry")) {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
      performUpdateAction(e)
      notification.expire()
    }
  }

  val updateAction = JdkUpdateSuggestionAction()

  val isUpdateActionVisible get() = !myIsUpdateRunning && !myIsTerminated

  inner class JdkUpdateSuggestionAction : SettingsEntryPointAction.UpdateAction() {
    val jdkUpdateNotification = this@JdkUpdateNotification

    init {
      templatePresentation.text = ProjectBundle.message("action.title.jdk.update.found", jdk.name, newItem.fullPresentationText, oldItem.versionPresentationText)
      templatePresentation.description = ProjectBundle.message("action.description.jdk.update.found", jdk.name, newItem.fullPresentationText, oldItem.versionPresentationText)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = isUpdateActionVisible
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
      performUpdateAction(e)
    }
  }

  private fun showUpdateErrorNotification(feedItem: JdkItem) : Unit = lock.withLock {
    NotificationGroupManager.getInstance().getNotificationGroup("JDK Update Error")
      .createNotification(
        ProjectBundle.message("progress.title.updating.jdk.0.to.1", jdk.name, feedItem.fullPresentationText),
        ProjectBundle.message("progress.title.updating.jdk.failed", feedItem.fullPresentationText),
        NotificationType.ERROR)
      .addAction(InstallUpdateNotification())
      .bindNextNotificationAndShow()
  }

  private fun performUpdateAction(e: AnActionEvent) {
    myRetryNotification?.expire()

    lock.withLock {
      if (myIsUpdateRunning) return
      myIsUpdateRunning = true
    }

    val title = ProjectBundle.message("progress.title.updating.jdk.0.to.1", jdk.name, newItem.fullPresentationText)
    ProgressManager.getInstance().run(
      object : Task.Backgroundable(null /*progress should be global*/, title, true, ALWAYS_BACKGROUND) {
        override fun run(indicator: ProgressIndicator) {
          val newJdkHome = try {
            val installer = JdkInstaller.getInstance()

            val request = installer.prepareJdkInstallation(newItem, installer.defaultInstallDir(newItem))
            installer.installJdk(request, indicator, e.project)

            //make sure VFS sees the files and sets up the JDK correctly
            indicator.text = ProjectBundle.message("progress.text.updating.jdk.setting.up")
            VfsUtil.markDirtyAndRefresh(false, true, true, request.installDir.toFile())
            request.javaHome
          }
          catch (t: Throwable) {
            if (t is ControlFlowException) {
              reachTerminalState()
              throw t
            }

            LOG.warn("Failed to update $jdk to $newItem. ${t.message}", t)
            showUpdateErrorNotification(newItem)
            lock.withLock { myIsUpdateRunning = false }
            return
          }

          invokeLater {
            try {
              runWriteAction {
                jdk.sdkModificator.apply {
                  removeAllRoots()
                  homePath = newJdkHome.systemIndependentPath
                  versionString = newItem.versionString
                }.commitChanges()

                (jdk.sdkType as? SdkType)?.setupSdkPaths(jdk)
                reachTerminalState()
              }
            }
            catch (t: Throwable) {
              if (t is ControlFlowException) {
                reachTerminalState()
                throw t
              }

              LOG.warn("Failed to apply downloaded JDK update for $jdk from $newItem at $newJdkHome. ${t.message}", t)
              showUpdateErrorNotification(newItem)
              lock.withLock { myIsUpdateRunning = false }
            }
          }
        }
      }
    )
  }
}
