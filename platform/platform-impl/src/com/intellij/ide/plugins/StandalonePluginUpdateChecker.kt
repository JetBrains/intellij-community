// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ShowLogAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.eventLog.fus.MachineIdManager
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.Alarm
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.io.HttpRequests
import com.intellij.util.text.VersionComparatorUtil
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon

sealed class PluginUpdateStatus {
  val timestamp: Long = System.currentTimeMillis()

  object LatestVersionInstalled : PluginUpdateStatus()

  class Update(
    val pluginDescriptor: IdeaPluginDescriptor,
    val hostToInstallFrom: String?
  ) : PluginUpdateStatus()

  class CheckFailed(val message: String, val detail: String? = null) : PluginUpdateStatus()

  class Unverified(val verifierName: String, val reason: String?, val updateStatus: Update) : PluginUpdateStatus()

  fun mergeWith(other: PluginUpdateStatus): PluginUpdateStatus {
    if (other is Update) {
      when (this) {
        is LatestVersionInstalled -> return other
        is Update -> {
          if (VersionComparatorUtil.compare(other.pluginDescriptor.version, pluginDescriptor.version) > 0) {
            return other
          }
        }

        is CheckFailed, is Unverified -> {
          // proceed to return this
        }
      }
    }

    return this
  }

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
  private val notificationIcon: Icon?
): Disposable {

  private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

  @Volatile
  protected var lastUpdateStatus: PluginUpdateStatus? = null

  @Volatile
  private var updateDelay = INITIAL_UPDATE_DELAY
  private val checkQueued = AtomicBoolean(false)

  open val currentVersion: String
    get() = PluginManagerCore.getPlugin(pluginId)!!.version

  open fun skipUpdateCheck(): Boolean = false

  open fun verifyUpdate(status: PluginUpdateStatus.Update): PluginUpdateStatus = status

  fun pluginUsed() {
    if (!UpdateSettings.getInstance().isCheckNeeded) return
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
          } finally {
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
    } else {
      try {
        updateStatus = checkUpdatesInMainRepository()
        for (host in RepositoryHelper.getCustomPluginRepositoryHosts()) {
          val customUpdateStatus = checkUpdatesInCustomRepository(host)
          updateStatus = updateStatus.mergeWith(customUpdateStatus)
        }
      } catch (e: Exception) {
        updateStatus = PluginUpdateStatus.fromException(IdeBundle.message("plugin.updater.error.check.failed"), e)
      }
    }

    lastUpdateStatus = updateStatus
    if (updateStatus is PluginUpdateStatus.Update) {
      updateStatus = verifyUpdate(updateStatus)
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

  private fun findPluginDescriptor() = PluginManagerCore.getPlugin(pluginId) ?: error("Plugin ID $pluginId not found when checking updates")

  private fun checkUpdatesInMainRepository(): PluginUpdateStatus {
    val buildNumber = ApplicationInfo.getInstance().apiVersion
    val os = URLEncoder.encode(SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION, CharsetToolkit.UTF8)
    val uid = PermanentInstallationID.get()
    val pluginId = pluginId.idString
    var url =
      "https://plugins.jetbrains.com/plugins/list?pluginId=$pluginId&build=$buildNumber&pluginVersion=$currentVersion&os=$os&uuid=$uid"

    if (!PropertiesComponent.getInstance().getBoolean(UpdateChecker.MACHINE_ID_DISABLED_PROPERTY, false)) {
      val machineId = MachineIdManager.getAnonymizedMachineId("JetBrainsUpdates")
      if (machineId != null) {
        url += "&${UpdateChecker.MACHINE_ID_PARAMETER}=$machineId"
      }
    }

    val responseDoc = HttpRequests.request(url).connect { JDOMUtil.load(it.inputStream) }
    if (responseDoc.name != "plugin-repository") {
      return PluginUpdateStatus.CheckFailed(
        IdeBundle.message("plugin.updater.error.unexpected.repository.response"),
        JDOMUtil.writeElement(responseDoc, "\n")
      )
    }

    if (responseDoc.children.isEmpty()) {
      // No plugin version compatible with current IDEA build; don't retry updates
      return PluginUpdateStatus.LatestVersionInstalled
    }

    val newVersion = responseDoc.getChild("category")
                       ?.getChild("idea-plugin")
                       ?.getChild("version")
                       ?.text
                     ?: return PluginUpdateStatus.CheckFailed(
                       IdeBundle.message("plugin.updater.error.cant.find.plugin.version"),
                       JDOMUtil.writeElement(responseDoc, "\n"),
                     )

    val pluginDescriptor = initPluginDescriptor(newVersion)
    return updateIfNotLatest(pluginDescriptor, null)
  }

  private fun checkUpdatesInCustomRepository(host: String): PluginUpdateStatus {
    val plugins = try {
      RepositoryHelper.loadPlugins(/* repositoryUrl = */ host, /* build = */ null, /* indicator = */ null)
    } catch (e: Exception) {
      return PluginUpdateStatus.fromException(IdeBundle.message("plugin.updater.error.custom.repository", host), e)
    }

    val newPlugin = plugins.find { pluginDescriptor ->
      pluginDescriptor.pluginId == pluginId && PluginManagerCore.isCompatible(pluginDescriptor)
    } ?: return PluginUpdateStatus.LatestVersionInstalled

    return updateIfNotLatest(newPlugin, host)
  }

  private fun updateIfNotLatest(newPlugin: IdeaPluginDescriptor, host: String?): PluginUpdateStatus {
    if (VersionComparatorUtil.compare(newPlugin.version, currentVersion) <= 0) {
      return PluginUpdateStatus.LatestVersionInstalled
    }

    return PluginUpdateStatus.Update(newPlugin, host)
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
          installPluginUpdate(update) {
            notifyPluginUpdateAvailable(update)
          }
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
    val descriptor = update.pluginDescriptor
    val pluginDownloader = PluginDownloader.createDownloader(descriptor, update.hostToInstallFrom, null)
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
        } catch (e: IOException) {
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
          } else {
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
