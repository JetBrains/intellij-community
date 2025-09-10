// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.plugins.parser.impl.isKotlinPlugin
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

private val pluginIdsToIgnoreK2KotlinCompatibility: Set<String> = buildSet {
  System.getProperty("idea.kotlin.plugin.plugin.ids.to.ignore.k2.compatibility")?.split(',')?.mapTo(this) { it.trim() }
  addAll(listOf("fleet.backend.kotlin", "fleet.backend.mercury"))

  try {
    // KTIJ-30545
    javaClass.getResource("/pluginsCompatibleWithK2Mode.txt")
      ?.openStream()?.use { it.reader().readLines() }
      ?.map { it.trim() }
      ?.filterTo(this) { it.isNotEmpty() }
  }
  catch (e: IOException) {
    PluginManagerCore.logger.error("Cannot load pluginsCompatibleWithK2Mode.txt", e)
  }
}


/**
 * See KTIJ-30474 for semantic
 */
internal fun pluginCanWorkInK2Mode(plugin: IdeaPluginDescriptorImpl): Boolean {
  val supportKotlinPluginModeEPs = getSupportKotlinPluginModeEPs(plugin)

  return when {
    // explicitly disabled
    supportKotlinPluginModeEPs.any { it.element?.attributes.orEmpty()[SUPPORTS_K2_ATTRIBUTE_NAME] == "false" } -> false
    plugin.pluginId.idString in pluginIdsToIgnoreK2KotlinCompatibility -> true
    // by default, the K2 mode is not supported
    else -> supportKotlinPluginModeEPs.any { it.element?.attributes.orEmpty()[SUPPORTS_K2_ATTRIBUTE_NAME] == "true" }
  }
}


/**
 * See KTIJ-30474 for semantic
 */
internal fun pluginCanWorkInK1Mode(plugin: IdeaPluginDescriptorImpl): Boolean {
  val supportKotlinPluginModeEPs = getSupportKotlinPluginModeEPs(plugin)

  return when {
    // explicitly disabled
    supportKotlinPluginModeEPs.any { it.element?.attributes.orEmpty()[SUPPORTS_K1_ATTRIBUTE_NAME] == "false" } -> false
    // by default, the K1 mode is supported
    else -> true
  }
}



private fun getSupportKotlinPluginModeEPs(plugin: IdeaPluginDescriptorImpl): List<ExtensionDescriptor> {
  return plugin.extensions[SUPPORTS_KOTLIN_PLUGIN_MODE_EP_NAME] ?: emptyList()
}


internal fun isKotlinPluginK2Mode(): Boolean {
  return System.getProperty("idea.kotlin.plugin.use.k2", "false").toBoolean()
}

@ApiStatus.Internal
fun isKotlinPlugin(pluginId: PluginId): Boolean = isKotlinPlugin(pluginId.idString)

@ApiStatus.Internal
fun isKotlinPluginK1Mode(): Boolean {
  return !isKotlinPluginK2Mode()
}

internal fun isIncompatibleWithKotlinPlugin(plugin: IdeaPluginDescriptorImpl): Boolean {
  if (isKotlinPluginK1Mode() && !pluginCanWorkInK1Mode(plugin)) {
    return true
  }

  if (isKotlinPluginK2Mode() && !pluginCanWorkInK2Mode(plugin)) {
    return true
  }

  return false
}


@ApiStatus.Internal
fun isPluginWhichDependsOnKotlinPluginAndItsIncompatibleWithIt(plugin: IdeaPluginDescriptorImpl): Boolean {
  if (isKotlinPlugin(plugin.pluginId)) return false
  if (!nonOptionallyDependsOnKotlinPlugin(plugin)) return false

  return isIncompatibleWithKotlinPlugin(plugin)
}

@ApiStatus.Internal
fun isPluginWhichDependsOnKotlinPluginInK2ModeAndItDoesNotSupportK2Mode(plugin: IdeaPluginDescriptorImpl): Boolean {
  if (isKotlinPlugin(plugin.pluginId)) return false
  if (!nonOptionallyDependsOnKotlinPlugin(plugin)) return false
  return !pluginCanWorkInK2Mode(plugin)
}

private fun nonOptionallyDependsOnKotlinPlugin(plugin: IdeaPluginDescriptorImpl): Boolean {
  return plugin.dependencies.any { (isKotlinPlugin(it.pluginId)) && !it.isOptional } ||
         plugin.moduleDependencies.plugins.any { isKotlinPlugin(it) }
}

private const val SUPPORTS_KOTLIN_PLUGIN_MODE_EP_NAME = "org.jetbrains.kotlin.supportsKotlinPluginMode"
private const val SUPPORTS_K1_ATTRIBUTE_NAME = "supportsK1"
private const val SUPPORTS_K2_ATTRIBUTE_NAME = "supportsK2"

