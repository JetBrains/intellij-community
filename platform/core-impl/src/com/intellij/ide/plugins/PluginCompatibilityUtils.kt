// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PluginCompatibilityUtils {
  // skip our plugins as expected to be up to date whether bundled or not
  fun isLegacyPluginWithoutPlatformAliasDependencies(descriptor: IdeaPluginDescriptorImpl): Boolean {
    return !descriptor.isBundled &&
           descriptor.packagePrefix == null &&
           !descriptor.isImplementationDetail &&
           descriptor.content.modules.isEmpty() &&
           descriptor.moduleDependencies.modules.isEmpty() &&
           descriptor.moduleDependencies.plugins.isEmpty() &&
           descriptor.pluginId != PluginManagerCore.CORE_ID &&
           descriptor.pluginId != PluginManagerCore.JAVA_PLUGIN_ID &&
           !hasJavaOrPlatformAliasDependency(descriptor)
  }

  private fun hasJavaOrPlatformAliasDependency(descriptor: IdeaPluginDescriptorImpl): Boolean {
    for (dependency in descriptor.dependencies) {
      val dependencyPluginId = dependency.pluginId
      if (PluginManagerCore.JAVA_PLUGIN_ID == dependencyPluginId ||
          PluginManagerCore.JAVA_MODULE_ID == dependencyPluginId ||
          PluginManagerCore.looksLikePlatformPluginAlias(dependencyPluginId)) {
        return true
      }
    }
    return false
  }
}