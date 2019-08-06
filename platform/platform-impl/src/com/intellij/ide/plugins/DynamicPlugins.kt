// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.extensions.Extensions
import com.intellij.util.ReflectionUtil

object DynamicPlugins {
  @JvmStatic
  fun isUnloadSafe(pluginDescriptor: IdeaPluginDescriptor): Boolean {
    if (!ApplicationManager.getApplication().isInternal) return false

    if (pluginDescriptor !is IdeaPluginDescriptorImpl) return false

    val extensions = pluginDescriptor.extensions
    if (extensions != null) {
      for (epName in extensions.keySet()) {
        val ep = Extensions.getRootArea().getExtensionPointIfRegistered<Any>(epName)
        if (ep == null || !ep.isUnloadSafe) return false
      }
    }

    return isUnloadSafe(pluginDescriptor.appContainerDescriptor) &&
           isUnloadSafe(pluginDescriptor.projectContainerDescriptor) &&
           isUnloadSafe(pluginDescriptor.moduleContainerDescriptor) &&
           (ActionManager.getInstance() as ActionManagerImpl).canUnloadActions(pluginDescriptor)
  }

  private fun isUnloadSafe(containerDescriptor: ContainerDescriptor): Boolean {
    return containerDescriptor.components.isNullOrEmpty() &&
           containerDescriptor.extensionsPoints.isNullOrEmpty()
  }

  @JvmStatic
  fun unloadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl): Boolean {
    val extensions = pluginDescriptor.extensions
    if (extensions != null) {
      for (epName in extensions.keySet()) {
        val ep = Extensions.getRootArea().getExtensionPointIfRegistered<Any>(epName) ?: continue
        ep.unregisterExtensions({ _, adapter -> adapter.pluginDescriptor != pluginDescriptor }, false)
      }
    }

    (ActionManager.getInstance() as ActionManagerImpl).unloadActions(pluginDescriptor)
    return pluginDescriptor.unloadClassLoader()
  }

  @JvmStatic
  fun loadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl) {
    val idToDescriptorMap = PluginManagerCore.getPlugins().associateBy { it.pluginId }
    val coreLoader = ReflectionUtil.findCallerClass(1)!!.classLoader
    PluginManagerCore.initClassLoader(coreLoader, idToDescriptorMap, pluginDescriptor)

    (ApplicationManager.getApplication() as ApplicationImpl).registerComponents(listOf(pluginDescriptor))
    (ActionManager.getInstance() as ActionManagerImpl).registerPluginActions(pluginDescriptor)
  }
}
