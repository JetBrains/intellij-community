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
import com.intellij.platform.eel.EnvironmentVariablesOptionsBuilder
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.resolveEelMachine
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.ijent.LoginShellEnvVarMode
import com.intellij.platform.ijent.LoginShellEnvVarModeProvider
import com.intellij.platform.ijent.community.ui.actions.IjentImplBundle
import com.intellij.platform.ijent.loginShellEnvVarMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val NOTIFICATION_GROUP_ID: String = "IJent.EnvironmentVariables"

/**
 * Decides whether a failed environment-variables fetch deserves a user-facing notification, and
 * forwards the decision to the UI-only [EnvironmentVariablesAwaitNotifier] service.
 *
 * A notification is shown only if **all** of the following hold (see [finished]):
 *  1. The result is a failure — successes are silent.
 *  2. The application is neither in unit-test nor headless mode — there is no UI to show into.
 *  3. The notifier reports no live balloon for the same `(descriptor, mode)` — see
 *     [EnvironmentVariablesAwaitNotifier.isAlreadyActive].
 *  4. Probing the user-selected mode (per [LoginShellEnvVarModeProvider]) does NOT return a
 *     successful response shortly.
 */
internal class EnvironmentVariablesAwaitReporterImpl(
  private val cs: CoroutineScope,
) : EnvironmentVariablesAwaitReporter {
  override fun finished(
    descriptor: EelDescriptor,
    mode: Mode,
    duration: Duration,
    result: Result<Map<String, String>>,
  ) {
    val app = ApplicationManager.getApplication() ?: return
    if (app.isUnitTestMode || app.isHeadlessEnvironment) return
    val cause = result.exceptionOrNull() ?: return

    val notifier = app.serviceIfCreated<EnvironmentVariablesAwaitNotifier>()
                   ?: app.service<EnvironmentVariablesAwaitNotifier>()
    if (notifier.isAlreadyActive(descriptor, mode)) return

    cs.launch(Dispatchers.Default) {
      if (userSelectedModeAnsweredQuickly(descriptor)) return@launch
      notifier.show(descriptor, mode, duration, cause)
    }
  }

  private suspend fun userSelectedModeAnsweredQuickly(descriptor: EelDescriptor): Boolean {
    return withTimeoutOrNull(1.seconds) {
      try {
        val userEelMode = loginShellEnvVarMode(descriptor.resolveEelMachine()).toEelMode()
        val eelApi = descriptor.toEelApi()
        val opts = EnvironmentVariablesOptionsBuilder().mode(userEelMode).build()
        eelApi.exec.environmentVariables(opts).await()
        true
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (_: Throwable) {
        false
      }
    } ?: false
  }

  @OptIn(EelDelicateApi::class)
  private fun LoginShellEnvVarMode.toEelMode(): Mode = when (this) {
    LoginShellEnvVarMode.LOGIN_INTERACTIVE -> Mode.LOGIN_INTERACTIVE
    LoginShellEnvVarMode.LOGIN_NON_INTERACTIVE -> Mode.LOGIN_NON_INTERACTIVE
    LoginShellEnvVarMode.LOGIN_INTERACTIVE_SHELL -> Mode.LOGIN_INTERACTIVE_VIA_SHELL
  }
}

@Service(Service.Level.APP)
internal class EnvironmentVariablesAwaitNotifier {
  private data class Key(val descriptor: EelDescriptor, val mode: Mode)

  private val active = ConcurrentHashMap<Key, Notification>()

  fun isAlreadyActive(descriptor: EelDescriptor, mode: Mode): Boolean =
    active.containsKey(Key(descriptor, mode))

  fun show(descriptor: EelDescriptor, mode: Mode, duration: Duration, cause: Throwable) {
    val key = Key(descriptor, mode)
    if (active.containsKey(key)) return

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
      // Lost the race to another show() call — let the existing balloon stand.
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
