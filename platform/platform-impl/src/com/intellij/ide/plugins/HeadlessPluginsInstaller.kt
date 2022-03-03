// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.util.io.URLUtil
import kotlin.system.exitProcess

internal class HeadlessPluginsInstaller : ApplicationStarter {
  @Suppress("SSBasedInspection")
  private val LOG = logger<HeadlessPluginsInstaller>()

  override fun getCommandName(): String = "installPlugins"

  override fun getRequiredModality(): Int = ApplicationStarter.NOT_IN_EDT

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
        println("plugin hosts: ${newHosts}")
        LOG.info("plugin hosts: ${newHosts}")
      }

      println("installing: ${pluginIds}")
      LOG.info("installing: ${pluginIds}")
      installAndEnable(null, pluginIds) {}
      exitProcess(0)
    }
    catch (t: Throwable) {
      LOG.error(t)
      exitProcess(1)
    }
  }
}
