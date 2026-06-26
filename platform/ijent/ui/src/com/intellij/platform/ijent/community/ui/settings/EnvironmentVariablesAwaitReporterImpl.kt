// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.ui.settings

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelExecApi.EnvironmentVariablesOptions.Mode
import com.intellij.platform.eel.EnvironmentVariablesAwaitReporter
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ijent.community.ui.actions.IjentImplBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

private const val NOTIFICATION_GROUP_ID: String = "IJent.EnvironmentVariables"

/**
 * Forwards events to the application-level [EnvironmentVariablesAwaitNotifier] service.
 *
 * Registered as idea application service in
 * `intellij.platform.ijent.community.ui.xml`. The SPI-side thin forwarder in `eel-impl` looks up
 * this service through IntelliJ's service container, which crosses plugin-classloader boundaries
 * (unlike `ServiceLoader`).
 */
internal class EnvironmentVariablesAwaitReporterImpl : EnvironmentVariablesAwaitReporter {
  override fun finished(
    descriptor: EelDescriptor,
    mode: Mode,
    duration: Duration,
    result: Result<Map<String, String>>,
  ) {
    val app = ApplicationManager.getApplication() ?: return
    val notifier = app.serviceIfCreated<EnvironmentVariablesAwaitNotifier>()
                   ?: app.service<EnvironmentVariablesAwaitNotifier>()
    notifier.finished(descriptor, mode, duration, result)
  }
}

@Service(Service.Level.APP)
internal class EnvironmentVariablesAwaitNotifier(private val cs: CoroutineScope) {
  private data class Key(val descriptor: EelDescriptor, val mode: Mode)

  private val active = ConcurrentHashMap<Key, Notification>()

  fun finished(
    descriptor: EelDescriptor,
    mode: Mode,
    duration: Duration,
    result: Result<Map<String, String>>,
  ) {
    if (result.isSuccess) return
    if (mode == Mode.MINIMAL) return
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) return

    val key = Key(descriptor, mode)
    if (active.containsKey(key)) return

    val cause = result.exceptionOrNull() ?: return

    cs.launch(Dispatchers.Default) {
      showBalloon(key, descriptor, mode, duration, cause)
    }
  }

  private fun showBalloon(key: Key, descriptor: EelDescriptor, mode: Mode, duration: Duration, cause: Throwable) {
    val title = IjentImplBundle.message(
      "notification.ijent.env.vars.failed.title",
      descriptor.name,
    )
    val message = cause.message ?: cause.javaClass.simpleName
    val content = IjentImplBundle.message(
      "notification.ijent.env.vars.failed.content",
      message,
      mode.name,
      duration.toString(),
    )

    val notification = NotificationGroupManager.getInstance()
      .getNotificationGroup(NOTIFICATION_GROUP_ID)
      .createNotification(title, content, NotificationType.WARNING)

    notification.addAction(NotificationAction.createSimple(
      IjentImplBundle.message("notification.ijent.env.vars.action.open.dashboard"),
    ) {
      openDashboard(descriptor)
      notification.expire()
    })

    notification.whenExpired { active.remove(key) }

    if (active.putIfAbsent(key, notification) != null) {
      // Lost the race to another finished() call — let the existing balloon stand.
      return
    }

    notification.notify(null)
  }

  private fun openDashboard(descriptor: EelDescriptor) {
    val project = ProjectManager.getInstance().openProjects
                    .firstOrNull { it.getEelDescriptor() == descriptor }
                  ?: ProjectManager.getInstance().openProjects.firstOrNull()
                  ?: return
    ShowSettingsUtil.getInstance().showSettingsDialog(project, IjentDashboardConfigurable::class.java)
  }
}
