// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.advertiser

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.plugins.*
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
private class OnDemandDependencyFeatureCollector : AppLifecycleListener {

  init {
    if (!IdeaPluginDescriptorImpl.isOnDemandEnabled) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun appFrameCreated(commandLineArgs: List<String>) {
    val featureMap = LinkedHashMap<String, FeaturePluginData>()

    PluginManagerCore.getPluginSet()
      .allPlugins // todo assert dependencySupport is in the main descriptors only
      .filter { it.pluginClassLoader == null }
      .filter { it.isOnDemand }
      .associateWith { it.epNameToExtensions?.get(DependencySupportBean.EP_NAME.name) ?: emptyList() }
      .forEach { (pluginDescriptor, extensionDescriptors) ->
        val pluginData = PluginData(pluginDescriptor)

        featureMap += extensionDescriptors.mapNotNull { it.element }
          .map { DependencySupportBean(it.attributes) }
          .associate { bean ->
            bean.id to FeaturePluginData(bean.displayNameOrId, pluginData)
          }
      }

    PluginFeatureService.instance.updateFeatureMapping(DEPENDENCY_SUPPORT_FEATURE, featureMap)
  }
}
