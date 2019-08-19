// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.serviceContainer.ServiceManagerImpl
import com.intellij.util.ReflectionUtil

object DynamicPlugins {
  private val LOG = Logger.getInstance(DynamicPlugins::class.java)

  @JvmStatic
  fun isUnloadSafe(pluginDescriptor: IdeaPluginDescriptor): Boolean {
    if (!ApplicationManager.getApplication().isInternal) return false

    if (pluginDescriptor !is IdeaPluginDescriptorImpl) return false

    val anyProject = ProjectManager.getInstance().openProjects.firstOrNull() ?:
                     ProjectManager.getInstance().defaultProject

    val extensions = pluginDescriptor.extensions
    if (extensions != null) {
      for (epName in extensions.keySet()) {
        val ep = Extensions.getRootArea().getExtensionPointIfRegistered<Any>(epName) ?:
          anyProject.extensionArea.getExtensionPointIfRegistered<Any>(epName)
        if (ep == null || !ep.isDynamic) {
          LOG.info("Plugin ${pluginDescriptor.pluginId} is not unload-safe because of extension $epName")
          return false
        }
      }
    }

    return isUnloadSafe(pluginDescriptor.appContainerDescriptor) &&
           isUnloadSafe(pluginDescriptor.projectContainerDescriptor) &&
           isUnloadSafe(pluginDescriptor.moduleContainerDescriptor) &&
           (ActionManager.getInstance() as ActionManagerImpl).canUnloadActions(pluginDescriptor)
  }

  private fun isUnloadSafe(containerDescriptor: ContainerDescriptor): Boolean {
    return containerDescriptor.components.isNullOrEmpty()
  }

  @JvmStatic
  fun unloadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl): Boolean {
    (ActionManager.getInstance() as ActionManagerImpl).unloadActions(pluginDescriptor)

    val openProjects = ProjectManager.getInstance().openProjects

    pluginDescriptor.extensions?.let { extensions ->
      for (epName in extensions.keySet()) {
        val appEp = Extensions.getRootArea().getExtensionPointIfRegistered<Any>(epName)
        if (appEp != null) {
          appEp.unregisterExtensions({ _, adapter -> adapter.pluginDescriptor != pluginDescriptor }, false)
        }
        else {
          for (openProject in openProjects) {
            val projectEp = openProject.extensionArea.getExtensionPointIfRegistered<Any>(epName)
            projectEp?.unregisterExtensions({ _, adapter -> adapter.pluginDescriptor != pluginDescriptor }, false)
          }
        }
      }
    }

    pluginDescriptor.app.extensionsPoints?.let {
      for (extensionPointElement in it) {
        Extensions.getRootArea().unregisterExtensionPoint(ExtensionsAreaImpl.getExtensionPointName(extensionPointElement, pluginDescriptor))
      }
    }
    pluginDescriptor.project.extensionsPoints?.let {
      for (extensionPointElement in it) {
        val extensionPointName = ExtensionsAreaImpl.getExtensionPointName(extensionPointElement, pluginDescriptor)
        for (openProject in openProjects) {
          openProject.extensionArea.unregisterExtensionPoint(extensionPointName)
        }
      }
    }

    val application = ApplicationManager.getApplication() as ApplicationImpl
    val appServiceInstances = ServiceManagerImpl.unloadServices(pluginDescriptor.app, application)
    for (appServiceInstance in appServiceInstances) {
      application.stateStore.unloadComponent(appServiceInstance)
    }

    for (project in openProjects) {
      val projectServiceInstances = ServiceManagerImpl.unloadServices(pluginDescriptor.project, project as ProjectImpl)
      for (projectServiceInstance in projectServiceInstances) {
        project.stateStore.unloadComponent(projectServiceInstance)
      }
    }

    return pluginDescriptor.unloadClassLoader()
  }

  @JvmStatic
  fun loadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl) {
    val idToDescriptorMap = PluginManagerCore.getPlugins().associateBy { it.pluginId }
    val coreLoader = ReflectionUtil.findCallerClass(1)!!.classLoader
    PluginManagerCore.initClassLoader(coreLoader, idToDescriptorMap, pluginDescriptor)

    (ApplicationManager.getApplication() as ApplicationImpl).registerComponents(listOf(pluginDescriptor))
    for (openProject in ProjectManager.getInstance().openProjects) {
      (openProject as ProjectImpl).registerComponents(listOf(pluginDescriptor))
    }
    (ActionManager.getInstance() as ActionManagerImpl).registerPluginActions(pluginDescriptor)
  }
}
