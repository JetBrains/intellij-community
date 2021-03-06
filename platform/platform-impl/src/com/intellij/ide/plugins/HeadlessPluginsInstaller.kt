// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser
import java.io.IOException

internal class HeadlessPluginsInstaller : ApplicationStarter {
  override fun getCommandName(): String = "installPlugins"

  override fun getRequiredModality(): Int = ApplicationStarter.NOT_IN_EDT

  override fun main(args: List<String>) {
    try {
      val ids = args.subList(1, args.size - 1).map { PluginId.getId(it) }.toSet()

      PluginsAdvertiser.installAndEnable(null, ids, false) {}
    }
    catch (e: IOException) {
      e.printStackTrace(System.err)
      System.exit(1)
    }
    System.exit(0)
  }

}