// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.project.DumbAware
import com.intellij.serviceContainer.ComponentManagerImpl
import io.github.classgraph.AnnotationEnumValue
import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo

@Suppress("HardCodedStringLiteral")
private class CreateAllServicesAndExtensionsAction : AnAction("Create All Services And Extensions"), DumbAware {
  companion object {
    @JvmStatic
    fun createAllServicesAndExtensions() {
      runModalTask("Creating All Services And Extensions", cancellable = true) { indicator ->
        checkContainer(ApplicationManager.getApplication() as ComponentManagerImpl, indicator)
        ProjectUtil.getOpenProjects().firstOrNull()?.let {
          checkContainer(it as ComponentManagerImpl, indicator)
        }

        indicator.text2 = "Checking light services..."
        checkLightServices()
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    createAllServicesAndExtensions()
  }
}

private val LOG = logger<ComponentManagerImpl>()

@Suppress("HardCodedStringLiteral")
private fun checkContainer(container: ComponentManagerImpl, indicator: ProgressIndicator) {
  indicator.text2 = "Checking ${container.activityNamePrefix()}services..."
  ComponentManagerImpl.createAllServices(container)
  indicator.text2 = "Checking ${container.activityNamePrefix()}extensions..."
  container.extensionArea.processExtensionPoints {
    // requires read action
    if (it.name != "com.intellij.favoritesListProvider" && it.name != "com.intellij.favoritesListProvider") {
      LOG.runAndLogException {
        it.extensionList
      }
    }
  }
}

private fun checkLightServices() {

  for (plugin in PluginManagerCore.getLoadedPlugins(null)) {
    // we don't check classloader for sub descriptors because url set is the same
    if (plugin.classLoader !is PluginClassLoader || plugin.pluginDependencies == null) {
      continue
    }

    ClassGraph()
      .enableAnnotationInfo()
      .ignoreParentClassLoaders()
      .overrideClassLoaders(plugin.classLoader)
      .scan()
      .use { scanResult ->
        val lightServices = scanResult.getClassesWithAnnotation("com.intellij.openapi.components.Service")
        for (lightService in lightServices) {
          // not clear - from what classloader light service will be loaded in reality
          val lightServiceClass = loadLightServiceClass(lightService, plugin)
          // check only app-level light services for now
          if (lightService.getAnnotationInfo("com.intellij.openapi.components.Service").parameterValues.find { it.name == "value" }?.let {
              (it.value as Array<*>).any { v -> (v as AnnotationEnumValue).valueName == Service.Level.PROJECT.name }
          } == true) {
            continue
          }

          LOG.runAndLogException {
            lightServiceClass.declaredConstructors.find { it.parameterCount == 0 }?.let {
              ApplicationManager.getApplication().getService(lightServiceClass)
            }
          }
        }
      }
  }
}

private fun loadLightServiceClass(lightService: ClassInfo, mainDescriptor: IdeaPluginDescriptorImpl): Class<*> {
  for (pluginDependency in mainDescriptor.pluginDependencies!!) {
    val subPluginClassLoader = pluginDependency.subDescriptor?.classLoader as? PluginClassLoader ?: continue
    val clazz = subPluginClassLoader.loadClass(lightService.name, true)
    if (clazz != null && clazz.classLoader === subPluginClassLoader) {
      // light class is resolved from this sub plugin classloader - check successful
      return clazz
    }
  }

  // ok, or no plugin dependencies at all, or all are disabled, resolve from main
  return (mainDescriptor.classLoader as PluginClassLoader).loadClass(lightService.name, true)
}