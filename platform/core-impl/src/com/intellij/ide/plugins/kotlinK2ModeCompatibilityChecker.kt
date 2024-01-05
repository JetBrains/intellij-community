// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

internal fun pluginCanWorkInK2Mode(plugin: IdeaPluginDescriptorImpl): Boolean {
  return plugin.epNameToExtensions["org.jetbrains.kotlin.supportsKotlinK2Mode"]?.isNotEmpty() == true
}

internal fun isKotlinPluginK2Mode(): Boolean {
  return System.getProperty("idea.kotlin.plugin.use.k2", "false").toBoolean()
}


@ApiStatus.Internal
fun isPluginWhichDependsOnKotlinPluginInK2ModeAndItDoesNotSupportK2Mode(plugin: IdeaPluginDescriptorImpl): Boolean {
  fun nonOptionallyDependsOnKotlinPlugin(): Boolean {
    return plugin.pluginDependencies.any { (isKotlinPlugin(it.pluginId)) && !it.isOptional } ||
           plugin.dependencies.plugins.any { isKotlinPlugin(it.id) }
  }

  if (isKotlinPluginK2Mode()) {
    if (!isKotlinPlugin(plugin.pluginId) && nonOptionallyDependsOnKotlinPlugin()) {
      if (!pluginCanWorkInK2Mode(plugin)) {
       return true
      }
    }
  }

  return false
}