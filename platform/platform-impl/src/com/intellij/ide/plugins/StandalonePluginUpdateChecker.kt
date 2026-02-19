// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ShowLogAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.updateSettings.PluginUpdateCheckService
import com.intellij.openapi.updateSettings.PluginUpdateInfo
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.util.Alarm
import com.intellij.util.concurrency.ThreadingAssertions
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon

sealed class PluginUpdateStatus {
  val timestamp: Long = System.currentTimeMillis()

  object LatestVersionInstalled : PluginUpdateStatus()

  class Update(
    val pluginDescriptor: IdeaPluginDescriptor,
    val pluginDownloader: PluginDownloader,
  ) : PluginUpdateStatus()

  class CheckFailed(val message: String, val detail: String? = null) : PluginUpdateStatus()

  companion object {
    fun fromException(message: String, e: Exception): PluginUpdateStatus {
      val writer = StringWriter()
      e.printStackTrace(PrintWriter(writer))
      return CheckFailed(message, writer.toString())
    }
  }
}

/**
 * When [notificationGroup] is null, [StandalonePluginUpdateChecker] doesn't create any notifications about available plugin updates.
 */
open class StandalonePluginUpdateChecker(
  val pluginId: PluginId,
  private val updateTimestampProperty: String,
  private val notificationGroup: NotificationGroup?,
  private val notificationIcon: Icon?,
) : Disposable {

  private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

  @Volatile
  private var updateDelay = INITIAL_UPDATE_DELAY
  private val checkQueued = AtomicBoolean(false)

  open val currentVersion: String
    get() = PluginManagerCore.getPlugin(pluginId)!!.version

  open fun skipUpdateCheck(): Boolean = false

  fun pluginUsed() {
    if (!UpdateSettings.getInstance().isPluginsCheckNeeded) return
    if (ApplicationManager.getApplication().isHeadlessEnvironment) return

    val lastUpdateTime = PropertiesComponent.getInstance().getLong(updateTimestampProperty, 0L)
    if (lastUpdateTime == 0L || System.currentTimeMillis() - lastUpdateTime > CACHED_REQUEST_DELAY) {
      queueUpdateCheck { updateStatus ->
        when (updateStatus) {
          is PluginUpdateStatus.Update -> notifyPluginUpdateAvailable(updateStatus)
          is PluginUpdateStatus.CheckFailed -> LOG.info("Plugin update check failed: ${updateStatus.message}, details: ${updateStatus.detail}")
          else -> Unit
        }

        true
      }
    }
  }

  protected fun queueUpdateCheck(callback: (PluginUpdateStatus) -> Boolean) {
    ThreadingAssertions.assertEventDispatchThread()
    if (checkQueued.compareAndSet(/* expectedValue = */ false, /* newValue = */ true)) {
      alarm.addRequest(
        {
          try {
            updateCheck(callback)
          }
          finally {
            checkQueued.set(false)
          }
        },
        updateDelay,
      )

      updateDelay *= 2 // exponential backoff
    }
  }

  protected fun updateCheck(callback: (PluginUpdateStatus) -> Boolean) {
    var updateStatus: PluginUpdateStatus

    if (skipUpdateCheck()) {
      updateStatus = PluginUpdateStatus.LatestVersionInstalled
    }
    else {
      val checkResult = PluginUpdateCheckService.getInstance().getPluginUpdate(pluginId)

      updateStatus = when (checkResult) {
        is PluginUpdateInfo.CheckFailed ->
          PluginUpdateStatus.fromException(IdeBundle.message("plugin.updater.error.check.failed"),
                                           checkResult.errors.values.first())

        is PluginUpdateInfo.UpdateAvailable ->
          PluginUpdateStatus.Update(initPluginDescriptor(checkResult.update.pluginVersion), checkResult.update)

        else -> PluginUpdateStatus.LatestVersionInstalled
      }
    }

    if (updateStatus !is PluginUpdateStatus.CheckFailed) {
      recordSuccessfulUpdateCheck()
    }

    ApplicationManager.getApplication().invokeLater(
      /* runnable = */ { callback(updateStatus) },
      /* state = */ ModalityState.any(),
    )
  }

  private fun initPluginDescriptor(newVersion: String): IdeaPluginDescriptor {
    val originalPlugin = findPluginDescriptor()
    return PluginNode(pluginId).apply {
      version = newVersion
      name = originalPlugin.name
      description = originalPlugin.description
    }
  }

  private fun findPluginDescriptor(): IdeaPluginDescriptor {
    return PluginManagerCore.getPlugin(pluginId) ?: error("Plugin ID $pluginId not found when checking updates")
  }

  private fun recordSuccessfulUpdateCheck() {
    PropertiesComponent.getInstance().setValue(updateTimestampProperty, System.currentTimeMillis().toString())
    updateDelay = INITIAL_UPDATE_DELAY
  }

  private fun notifyPluginUpdateAvailable(update: PluginUpdateStatus.Update) {
    if (notificationGroup == null) return

    val pluginName = findPluginDescriptor().name
    notificationGroup
      .createNotification(
        pluginName,
        IdeBundle.message("plugin.updater.notification.message", update.pluginDescriptor.version, pluginName),
        NotificationType.INFORMATION,
      )
      .setSuggestionType(true)
      .addAction(
        NotificationAction.createSimpleExpiring(IdeBundle.message("plugin.updater.install")) {
          installPluginUpdate(update)
        }
      )
      .setIcon(notificationIcon)
      .notify(null)
  }

  fun installPluginUpdate(
    update: PluginUpdateStatus.Update,
    successCallback: () -> Unit = {},
    cancelCallback: () -> Unit = {},
    errorCallback: () -> Unit = {},
  ) {
    val pluginDownloader = update.pluginDownloader
    ProgressManager.getInstance().run(object : Task.Backgroundable(
      /* project = */ null,
      /* title = */ IdeBundle.message("plugin.updater.downloading"),
      /* canBeCancelled = */ true,
      /* backgroundOption = */ PluginManagerUISettings.getInstance(),
    ) {
      override fun run(indicator: ProgressIndicator) {
        var installed = false
        var message: String? = null
        val prepareResult = try {
          pluginDownloader.prepareToInstall(indicator)
        }
        catch (e: IOException) {
          LOG.info(e)
          message = e.message
          false
        }

        if (prepareResult) {
          installed = true
          pluginDownloader.install()

          ApplicationManager.getApplication().invokeLater {
            PluginManagerMain.notifyPluginsUpdated(null)
          }
        }

        ApplicationManager.getApplication().invokeLater {
          if (!installed) {
            errorCallback()
            notifyNotInstalled(message)
          }
          else {
            successCallback()
          }
        }
      }

      override fun onCancel() {
        cancelCallback()
      }
    })
  }

  private fun notifyNotInstalled(message: String?) {
    if (notificationGroup == null) return

    val content = when (message) {
      null -> IdeBundle.message("plugin.updater.not.installed")
      else -> IdeBundle.message("plugin.updater.not.installed.misc", message)
    }

    notificationGroup
      .createNotification(findPluginDescriptor().name, content, NotificationType.INFORMATION)
      .addAction(
        NotificationAction.createSimpleExpiring(IdeBundle.message("plugin.updater.not.installed.see.log.action")) {
          ShowLogAction.showLog()
        }
      )
      .notify(null)
  }

  override fun dispose(): Unit = Unit

  companion object {
    private const val INITIAL_UPDATE_DELAY = 2000L

    @JvmStatic
    protected val CACHED_REQUEST_DELAY: Long = TimeUnit.DAYS.toMillis(1)
    private val LOG = Logger.getInstance(StandalonePluginUpdateChecker::class.java)
  }
}
