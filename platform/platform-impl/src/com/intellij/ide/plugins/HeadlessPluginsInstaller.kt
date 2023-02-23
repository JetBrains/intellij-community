// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.InstallAndEnableTaskHeadlessImpl
import com.intellij.util.io.URLUtil
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
      for (i in 1 until args.size) {
        val arg = args[i]
        when {
          arg.contains(URLUtil.SCHEME_SEPARATOR) -> customRepositories += arg
          else -> pluginIds += PluginId.getId(arg)
        }
      }

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

        override fun onFinished() {
          val allPluginIds = PluginManager.getPlugins().map { it.pluginId }.toSet()
          for (pluginId in pluginIds) {
            if (pluginId !in allPluginIds) {
              println("Unable to install plugin '$pluginId'")
              exitProcess(1)
            }
          }
          exitProcess(0)
        }
      })
      exitProcess(0)
    }
    catch (t: Throwable) {
      LOG.error(t)
      exitProcess(1)
    }
  }
}
