// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import java.io.File

/**
 * @author yole
 */
data class PluginInstallCallbackData(
  val file: File,
  val pluginDescriptor: IdeaPluginDescriptorImpl,
  val restartNeeded: Boolean
)

data class PendingDynamicPluginInstall(
  val file: File,
  val pluginDescriptor: IdeaPluginDescriptorImpl
)

fun installPluginFromCallbackData(callbackData: PluginInstallCallbackData) {
  if (callbackData.restartNeeded) {
    PluginManagerConfigurable.shutdownOrRestartAppAfterInstall(callbackData.pluginDescriptor.name)
  }
  else {
    if (!PluginInstaller.installAndLoadDynamicPlugin(callbackData.file, null, callbackData.pluginDescriptor)) {
      PluginManagerConfigurable.shutdownOrRestartAppAfterInstall(callbackData.pluginDescriptor.name)
    }
  }
}