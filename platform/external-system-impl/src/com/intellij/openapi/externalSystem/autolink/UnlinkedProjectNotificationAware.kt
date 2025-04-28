// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.CommonBundle
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.ide.environment.EnvironmentService
import com.intellij.ide.environment.description
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction.createSimpleExpiring
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.importing.ExternalSystemKeyProvider
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.ui.ExternalSystemTextProvider
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
@State(name = "UnlinkedProjectNotification", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
class UnlinkedProjectNotificationAware(
  private val project: Project
) : PersistentStateComponent<UnlinkedProjectNotificationAware.State> {

  private val disabledNotifications = ConcurrentCollectionFactory.createConcurrentSet<String>()

  private val notifiedNotifications = ConcurrentHashMap<ExternalSystemProjectId, Notification>()

  fun notificationNotify(projectId: ExternalSystemProjectId, callback: () -> Unit) {
    if (projectId.systemId.id in disabledNotifications) {
      LOG.debug("$projectId: notification has been disabled")
      return
    }
    if (projectId in notifiedNotifications.keys) {
      LOG.debug("$projectId: notification has been already notified")
      return
    }

    val textProvider = ExternalSystemTextProvider.getExtension(projectId.systemId)
    val notificationManager = NotificationGroupManager.getInstance()
    val notificationGroup = notificationManager.getNotificationGroup(NOTIFICATION_GROUP_ID)
    val notificationContent = textProvider.getUPNText(projectId.projectName)
    if (checkEnvironment(projectId, callback)) {
      return
    }
    notifiedNotifications.computeIfAbsent(projectId) {
      notificationGroup.createNotification(notificationContent, NotificationType.INFORMATION)
        .setDisplayId(UNLINKED_NOTIFICATION_ID)
        .setSuggestionType(true)
        .setNotificationHelp(textProvider.getUPNHelpText())
        .addAction(createSimpleExpiring(textProvider.getUPNLinkActionText()) { callback() })
        .addAction(createSimpleExpiring(textProvider.getUPNSkipActionText()) { disableNotification(projectId) })
        .whenExpired {
          notifiedNotifications.remove(projectId)
          LOG.debug("$projectId: notification is expired")
        }.apply {
          notify(project)
          LOG.debug("$projectId: notification is notified")
        }
    }
  }

  private fun checkEnvironment(projectId: ExternalSystemProjectId, callback: () -> Unit) : Boolean {
    if (ApplicationManager.getApplication().isHeadlessEnvironment && !ApplicationManager.getApplication().isUnitTestMode) {
      LOG.warn("Unlinked project notification is shown; Consider specifying '${ExternalSystemKeyProvider.Keys.LINK_UNLINKED_PROJECT.id}' to import the necessary build scripts.")
    }
    val environmentService = service<EnvironmentService>()
    val linkChoice: String = runBlockingCancellable {
      environmentService.getEnvironmentValue(ExternalSystemKeyProvider.Keys.LINK_UNLINKED_PROJECT, "")
    }
    if (linkChoice != "") {
      if (linkChoice == "all") {
        callback()
        return true
      }
      val allChoices = linkChoice.split(",")
      val choiceForThisBuildSystem = allChoices.find { it.startsWith(projectId.systemId.readableName) }?.substringAfter(":")
      when (choiceForThisBuildSystem) {
        "import" -> {
          callback()
          return true
        }
        "skip" -> {
          disableNotification(projectId)
          return true
        }
        null -> {
          // no decision for this build system, proceed with UI notification (though it is useless here)
        }
        else -> {
          LOG.error("""Invalid value for the key ${ExternalSystemKeyProvider.Keys.LINK_UNLINKED_PROJECT}. 
The correct usage:
 ${ExternalSystemKeyProvider.Keys.LINK_UNLINKED_PROJECT.description}""")
        }
      }
    }
    return false
  }

  private fun disableNotification(projectId: ExternalSystemProjectId) {
    disabledNotifications.add(projectId.systemId.id)
    LOG.debug("$projectId: notification is disabled")
  }

  fun notificationExpire(projectId: ExternalSystemProjectId) {
    notifiedNotifications.remove(projectId)?.expire()
  }

  @TestOnly
  fun getProjectsWithNotification(): Set<ExternalSystemProjectId> {
    return notifiedNotifications.keys
  }

  override fun getState(): State {
    return State(disabledNotifications)
  }

  override fun loadState(state: State) {
    disabledNotifications.clear()
    disabledNotifications.addAll(state.disabledNotifications)
  }

  data class State(var disabledNotifications: Set<String> = emptySet())

  companion object {

    private val LOG = Logger.getInstance("#com.intellij.openapi.externalSystem.autolink")

    private const val NOTIFICATION_GROUP_ID = "External System Auto-Link Notification Group"
    private const val UNLINKED_NOTIFICATION_ID = "external.system.autolink.unlinked.project.notification"

    @JvmStatic
    fun getInstance(project: Project): UnlinkedProjectNotificationAware {
      return project.getService(UnlinkedProjectNotificationAware::class.java)
    }

    @JvmStatic
    fun enableNotifications(project: Project, systemId: ProjectSystemId) {
      getInstance(project).disabledNotifications.remove(systemId.id)
    }

    private fun Notification.setNotificationHelp(help: @NlsActions.ActionDescription String) = apply {
      contextHelpAction = object : DumbAwareAction(CommonBundle.getHelpButtonText(), help, null) {
        override fun actionPerformed(e: AnActionEvent) {}
      }
    }
  }
}
