// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.providers

import com.intellij.ide.customize.transferSettings.models.PluginFeature
import com.intellij.ide.customize.transferSettings.models.Settings
import com.intellij.ide.plugins.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiserDialogPluginInstaller
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.getInstallAndEnableTask
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class PluginInstallationState {
  NoPlugins, Done, RestartRequired
}

interface ImportPerformer {

  fun collectAllRequiredPlugins(settings: Settings): Set<PluginId>
  suspend fun installPlugins(project: Project?, pluginIds: Set<PluginId>, pi: ProgressIndicator): PluginInstallationState
  fun patchSettingsAfterPluginInstallation(settings: Settings, successPluginIds: Set<String>): Settings

  /**
   * Heavy tasks should be performed there (on pooled thread)
   */
  fun perform(project: Project?, settings: Settings, pi: ProgressIndicator)

  /**
   * Quick tasks that will be performed on EDT after perform() is finished
   */
  fun performEdt(project: Project?, settings: Settings)
}

private val logger = logger<DefaultImportPerformer>()

class DefaultImportPerformer(private val partials: Collection<PartialImportPerformer>) : ImportPerformer {
  constructor() : this(arrayListOf(LookAndFeelImportPerformer(),
                                   SyntaxSchemeImportPerformer(),
                                   KeymapSchemeImportPerformer(),
                                   RecentProjectsImportPerformer()))

  private fun onlyRequiredPartials(settings: Settings) = partials.filter { p -> p.willPerform(settings) }

  override fun collectAllRequiredPlugins(settings: Settings): Set<PluginId> {
    logger.info("collectAllRequiredPlugins")
    val explicitlyAskedPlugins =
      if (settings.preferences.plugins)
        settings.plugins
      else emptyList()
    val ids = explicitlyAskedPlugins.filterIsInstance<PluginFeature>().map { PluginId.getId(it.pluginId) }.toMutableSet()

    ids.addAll(onlyRequiredPartials(settings).flatMap { it.collectAllRequiredPlugins(settings) })

    return ids
  }

  override suspend fun installPlugins(project: Project?, pluginIds: Set<PluginId>, pi: ProgressIndicator): PluginInstallationState {
    logger.info("Installing plugins")
    val installedPlugins = PluginManagerCore.plugins.map { it.pluginId.idString }.toSet()
    val pluginsToInstall = pluginIds.filter { !installedPlugins.contains(it.idString) }.toSet()

    val installAndEnableTask = getInstallAndEnableTask(project, pluginsToInstall, false, false, pi.modalityState) {}
    installAndEnableTask.run(pi)

    if (installAndEnableTask.plugins.isEmpty()) return PluginInstallationState.NoPlugins
    val cp = installAndEnableTask.customPlugins ?: return PluginInstallationState.NoPlugins
    val restartRequiringPlugins = AtomicInteger()
    val installStatus = suspendCoroutine { cont ->
      val a = object : PluginsAdvertiserDialogPluginInstaller(project, installAndEnableTask.plugins, cp, { status ->
        cont.resume(status)
      }) {
        override fun downloadPlugins(plugins: MutableList<PluginNode>,
                                     customPlugins: MutableCollection<PluginNode>,
                                     onSuccess: Runnable?,
                                     modalityState: ModalityState,
                                     function: Consumer<Boolean>?) {
          var success = true
          try {
            val operation = PluginInstallOperation(plugins, customPlugins, PluginEnabler.HEADLESS, pi)
            operation.setAllowInstallWithoutRestart(true)
            operation.run()
            success = operation.isSuccess
            if (operation.isRestartRequired) {
              restartRequiringPlugins.incrementAndGet()
            }
            if (success) {
              ApplicationManager.getApplication().invokeLater(Runnable {
                for ((file, pluginDescriptor) in operation.pendingDynamicPluginInstalls) {
                  success = success and PluginInstaller.installAndLoadDynamicPlugin(file, pluginDescriptor)
                }
              }, modalityState)
            }
          }
          finally {
            ApplicationManager.getApplication().invokeLater({ function?.accept(success) }, pi.modalityState)
          }
        }
      }
      a.doInstallPlugins({ true }, pi.modalityState)
    }

    logger.info("Finished installing plugins, result: $installStatus")
    return if (restartRequiringPlugins.get() > 0) PluginInstallationState.RestartRequired else PluginInstallationState.Done
  }

  override fun patchSettingsAfterPluginInstallation(settings: Settings, successPluginIds: Set<String>): Settings {
    onlyRequiredPartials(settings).forEach {
      logger.info("patchSettingsAfterPluginInstallation: ${it.javaClass.simpleName}")
      it.patchSettingsAfterPluginInstallation(settings, successPluginIds)
    }

    return settings
  }

  override fun perform(project: Project?, settings: Settings, pi: ProgressIndicator) {
    onlyRequiredPartials(settings).forEach {
      logger.info("perform: ${it.javaClass.simpleName}")
      it.perform(project, settings, pi)
    }
  }

  override fun performEdt(project: Project?, settings: Settings) {
    onlyRequiredPartials(settings).forEach {
      logger.info("performEdt: ${it.javaClass.simpleName}")
      it.performEdt(project, settings)
    }
  }
}
