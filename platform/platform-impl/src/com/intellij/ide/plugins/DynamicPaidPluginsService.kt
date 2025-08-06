// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerCore.ULTIMATE_PLUGIN_ID
import com.intellij.ide.plugins.PluginManagerCore.processAllNonOptionalDependencies
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.IntellijInternalApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.nio.file.FileVisitResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.measureTimedValue

@ApiStatus.Internal
@IntellijInternalApi
@Service(Service.Level.APP)
class DynamicPaidPluginsService(private val cs: CoroutineScope) {
  internal class LoadPaidPluginsProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
      if (shouldLoadOnProjectOpening.compareAndSet(true, false)) {
        getInstance().loadPaidPlugins(project)
      }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): DynamicPaidPluginsService = service<DynamicPaidPluginsService>()

    private val logger = logger<DynamicPaidPluginsService>()
    private val shouldLoadOnProjectOpening: AtomicBoolean = AtomicBoolean(false)
  }

  @ApiStatus.Internal
  fun loadPaidPluginsWhenProjectIsOpened() {
    val project = ProjectManager.getInstance().openProjects.firstOrNull()
    if (project != null) {
      loadPaidPlugins(project)
    }
    else {
      logger.debug("No open projects found. Scheduling paid plugins loading on project opening.")
      shouldLoadOnProjectOpening.set(true)
    }
  }

  @JvmOverloads
  fun loadPaidPlugins(project: Project? = null) {
    cs.launch(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
      logger.debug("loadPaidPlugins called")
      doLoadPaidPlugins(project)
    }
  }

  private fun doLoadPaidPlugins(project: Project?) {
    if (PluginEnabler.getInstance().isDisabled(ULTIMATE_PLUGIN_ID)) {
      logger.info("Ultimate plugin is disabled. Paid plugins will not be enabled.")
      return
    }

    val disabledPlugins = DisabledPluginsState.getDisabledIds()
    val pluginSet = PluginManagerCore.getPluginSet()
    val pluginIdMap = PluginManagerCore.buildPluginIdMap()
    val contentModuleIdMap = pluginSet.buildContentModuleIdMap()
    val loadedPlugins = pluginSet.enabledPlugins.toSet()

    val pluginsToEnable = pluginSet.allPlugins.filter {
      !disabledPlugins.contains(it.pluginId) &&
      !loadedPlugins.contains(it) &&
      pluginRequiresUltimatePlugin(it.pluginId, pluginIdMap, contentModuleIdMap) &&
      !pluginRequiresDisabledPlugin(it.pluginId, pluginIdMap, contentModuleIdMap, disabledPlugins) &&
      PluginManagerCore.isCompatible(it)
    }

    if (pluginsToEnable.isEmpty()) {
      logger.debug("No plugins found to be enabled.")
      return
    }

    val (loadablePlugins, requireRestartPlugins) = pluginsToEnable.splitPlugins()
    val pluginEnabler = PluginEnabler.getInstance()

    if (loadablePlugins.isNotEmpty()) {
      enablePlugins(pluginEnabler, loadablePlugins, restart = false, project = project,
                    progressTitle = IdeBundle.message("progress.title.loading.paid.plugins"))
    }
    else {
      logger.debug("No plugins loadable without restart plugins found to be enabled.")
    }

    if (requireRestartPlugins.isNotEmpty()) {
      notifyNotLoadedWithoutRestart(pluginEnabler, requireRestartPlugins)
    }
    else {
      logger.debug("No plugins that require restart found to be enabled.")
    }
  }

  private fun notifyNotLoadedWithoutRestart(pluginEnabler: PluginEnabler, plugins: List<IdeaPluginDescriptorImpl>) {
    val (loadableAfterRestart, missingDependencies) = plugins.partition { plugin ->
      plugin.dependencies.all { PluginManagerCore.isPluginInstalled(it.pluginId) || it.isOptional }
    }

    if (missingDependencies.isNotEmpty()) {
      logger.info("Plugins cannot be loaded even with restart because of missing dependencies: ${missingDependencies.map { it.pluginId }}")
    }

    if (loadableAfterRestart.isNotEmpty()) {
      val notificationTitle: String = IdeBundle.message("notification.title.paid.plugins.not.loaded")
      val pluginNames = loadableAfterRestart.map { it.name }.sorted()

      @Suppress("HardCodedStringLiteral")
      val notificationContent = IdeBundle.message("notification.content.paid.plugins.not.loaded") +
                                pluginNames.joinToString(separator = "<br>")

      NotificationGroupManager.getInstance()
        .getNotificationGroup("Paid Plugins")
        .createNotification(notificationTitle, notificationContent, NotificationType.INFORMATION)
        .addAction(object : NotificationAction(IdeBundle.message("notification.action.load.paid.plugins.and.restart")) {
          override fun actionPerformed(e: AnActionEvent, notification: Notification) {
            enablePlugins(pluginEnabler, loadableAfterRestart, restart = true)
          }
        })
        .notify(null)
    }
  }

  private fun enablePlugins(
    pluginEnabler: PluginEnabler,
    descriptors: List<IdeaPluginDescriptor>,
    restart: Boolean,
    project: Project? = null,
    progressTitle: @Nls String? = null,
  ) {
    logger.info("Plugins to enable: [${descriptors.joinToString(separator = ", ") { it.pluginId.idString }}]")
    val (result, elapsedTime) = measureTimedValue {
      if (pluginEnabler is DynamicPluginEnabler) {
        pluginEnabler.enable(descriptors, progressTitle, project)
      }
      else {
        pluginEnabler.enable(descriptors)
      }
    }
    val pluginsCount = descriptors.size
    DynamicPluginsUsagesCollector.logPaidPluginsLoaded(elapsedTime, pluginsCount, restart)
    logger.info("Loaded $pluginsCount plugins in ${elapsedTime.inWholeMilliseconds} ms. Enabled: $result. Restart requested: $restart")

    if (restart) {
      ApplicationManagerEx.getApplicationEx().restart(true)
    }
  }

  /**
   * Splits a list of plugins into two categories: those that can be loaded dynamically without requiring a restart
   * and those that require a restart to be loaded.
   * Tries including transitive dependencies as loadable without a restart when possible.
   *
   * @param this The list of plugins to analyze for determining loadability without requiring a restart.
   * Acts as a context in [DynamicPlugins.allowLoadUnloadWithoutRestart]
   */
  private fun List<IdeaPluginDescriptorImpl>.splitPlugins(): Pair<List<IdeaPluginDescriptorImpl>, List<IdeaPluginDescriptorImpl>> {
    tailrec fun doSplit(
      pluginsToLoad: List<IdeaPluginDescriptorImpl>,
      loadablePlugins: List<IdeaPluginDescriptorImpl>,
      requireRestartPlugins: List<IdeaPluginDescriptorImpl>,
    ): Pair<List<IdeaPluginDescriptorImpl>, List<IdeaPluginDescriptorImpl>> {
      if (pluginsToLoad.isEmpty()) return loadablePlugins to requireRestartPlugins

      val (loadable, requireRestart) = pluginsToLoad.partition {
        DynamicPlugins.allowLoadUnloadWithoutRestart(it, context = pluginsToLoad)
      }

      return if (requireRestart.isEmpty()) {
        // loadablePlugins.all { allowLoadUnloadWithoutRestart(it, context = loadablePlugins) } == true at this point
        // we can load all of them safely
        return loadablePlugins + loadable to requireRestartPlugins
      }
      else doSplit(pluginsToLoad = loadable, loadablePlugins = loadablePlugins, requireRestartPlugins = requireRestartPlugins + requireRestart)
    }

    return doSplit(this, emptyList(), emptyList())
  }
}

private fun pluginRequiresDisabledPlugin(
  plugin: PluginId, pluginMap: Map<PluginId, IdeaPluginDescriptorImpl>,
  contentModuleIdMap: Map<String, ContentModuleDescriptor>, disabledPluginIds: Set<PluginId>,
): Boolean {
  if (disabledPluginIds.isEmpty()) return false
  val rootDescriptor = pluginMap[plugin] ?: return false
  return !processAllNonOptionalDependencies(rootDescriptor, pluginMap, contentModuleIdMap) { descriptorImpl ->
    if (disabledPluginIds.contains(descriptorImpl.pluginId)) FileVisitResult.TERMINATE
    else FileVisitResult.CONTINUE
  }
}
