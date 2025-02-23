// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.util.NlsContexts.NotificationContent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
class CompileDriverNotifications(
  private val project: Project
) : Disposable {
  private val currentNotification = AtomicReference<Notification>(null)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): CompileDriverNotifications = project.service<CompileDriverNotifications>()
  }

  override fun dispose() {
    currentNotification.set(null)
  }

  fun createCannotStartNotification() : LightNotification {
    return LightNotification()
  }

  inner class LightNotification {
    private val isShown = AtomicBoolean()
    private val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("jps configuration error")

    private val baseNotification = notificationGroup
      .createNotification(JavaCompilerBundle.message("notification.title.jps.cannot.start.compiler"), NotificationType.ERROR)
      .setImportant(true)

    fun withExpiringAction(@NotificationContent title : String,
                           handler: () -> Unit): LightNotification {
      return apply {
        baseNotification.addAction(NotificationAction.createSimpleExpiring(title, handler))
      }
    }

    @JvmOverloads
    fun withOpenSettingsAction(moduleNameToSelect: String? = null, tabNameToSelect: String? = null): LightNotification {
      return withExpiringAction(JavaCompilerBundle.message("notification.action.jps.open.configuration.dialog")) {
        val service = ProjectSettingsService.getInstance(project)
        if (moduleNameToSelect != null) {
          service.showModuleConfigurationDialog(moduleNameToSelect, tabNameToSelect)
        }
        else {
          service.openProjectSettings()
        }
      }
    }

    fun withContent(@NotificationContent content: String): LightNotification = apply {
      baseNotification.setContent(content)
    }

    /**
     * This wrapper helps to make sure we have only one active unresolved notification per project
     */
    fun showNotification() {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        thisLogger().error("" + baseNotification.content)
        return
      }

      if (!isShown.compareAndSet(false, true)) return

      val showNotification = Runnable {
        baseNotification.whenExpired {
          currentNotification.compareAndExchange(baseNotification, null)
        }

        currentNotification.getAndSet(baseNotification)?.expire()
        baseNotification.notify(project)
      }

      showNotification.run()
    }
  }
}
