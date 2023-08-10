// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.externalDependencies.DependencyOnPlugin
import com.intellij.externalDependencies.ExternalDependenciesManager
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.InstallAndEnableTaskHeadlessImpl
import com.intellij.util.io.URLUtil
import java.nio.file.Path
import kotlin.system.exitProcess

internal class HeadlessPluginsInstaller : ApplicationStarter {
  @Suppress("SSBasedInspection")
  private val LOG = logger<HeadlessPluginsInstaller>()

  override val commandName: String
    get() = "installPlugins"

  override val requiredModality: Int
    get() = ApplicationStarter.NOT_IN_EDT

  override fun main(args: List<String>) {
    try {
      val pluginIds = LinkedHashSet<PluginId>()
      val customRepositories = LinkedHashSet<String>()
      val projectPaths : MutableList<String> = mutableListOf()
      for (i in 1 until args.size) {
        val arg = args[i]
        when {
          (arg == "-h") || (arg == "--help") -> {
            println("""
              Usage: installPlugins pluginId* repository* (--for-project=<project-path>)*
              
              Installs plugins with `pluginId` from the Marketplace or provided `repository`-es.
              If `--for-project` is specified, also installs the required plugins for a project located at <project-path>. 
            """.trimIndent())
          }
          arg.startsWith("--for-project") -> projectPaths += arg.substringAfter("--for-project=")
          arg.contains(URLUtil.SCHEME_SEPARATOR) -> customRepositories += arg
          else -> pluginIds += PluginId.getId(arg)
        }
      }

      collectProjectRequiredPlugins(pluginIds, projectPaths)

      if (customRepositories.isNotEmpty()) {
        val hosts = System.getProperty("idea.plugin.hosts")
        val newHosts = customRepositories.joinToString(separator = ";", prefix = if (hosts.isNullOrBlank()) "" else "${hosts};")
        System.setProperty("idea.plugin.hosts", newHosts)
        LOG.info("plugin hosts: $newHosts")
      }
      LOG.info("installing: $pluginIds")

      ProgressManager.getInstance().run(object : InstallAndEnableTaskHeadlessImpl(pluginIds, {}) {
        override fun onThrowable(error: Throwable) {
          LOG.error("Failed to install plugins:", error)
          exitProcess(1)
        }
      })
      exitProcess(0)
    }
    catch (t: Throwable) {
      LOG.error(t)
      exitProcess(1)
    }
  }

  private fun collectProjectRequiredPlugins(collector : MutableCollection<in PluginId>, projectPaths: List<String>) {
    for (path in projectPaths) {
      val project = ProjectUtil.openOrImport(Path.of(path), OpenProjectTask {
        showWelcomeScreen = false
        runConversionBeforeOpen = false
      })
      if (project == null) {
        LOG.error("Error on opening the project")
        exitProcess(1)
      }

      val externalDependenciesService = ExternalDependenciesManager.getInstance(project)
      val pluginDependencies = externalDependenciesService.getDependencies(DependencyOnPlugin::class.java)
      collector += pluginDependencies.map { PluginId.getId(it.pluginId) }
    }
  }
}
