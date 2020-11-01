// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.ComponentManagerImpl
import io.github.classgraph.AnnotationEnumValue
import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import java.util.function.BiConsumer

@Suppress("HardCodedStringLiteral")
private class CreateAllServicesAndExtensionsAction : AnAction("Create All Services And Extensions"), DumbAware {
  companion object {
    @JvmStatic
    fun createAllServicesAndExtensions() {
      val errors = mutableListOf<Throwable>()
      runModalTask("Creating All Services And Extensions", cancellable = true) { indicator ->
        val logger = logger<ComponentManagerImpl>()
        val taskExecutor: (task: () -> Unit) -> Unit = { task ->
          try {
            task()
          }
          catch (e: ProcessCanceledException) {
            throw e
          }
          catch (e: Throwable) {
            logger.error(e)
            errors.add(e)
          }
        }

        checkContainer(ApplicationManager.getApplication() as ComponentManagerImpl, indicator, taskExecutor)
        ProjectUtil.getOpenProjects().firstOrNull()?.let {
          checkContainer(it as ComponentManagerImpl, indicator, taskExecutor)
        }

        indicator.text2 = "Checking light services..."
        checkLightServices(taskExecutor)
      }

      Notification("Error Report", null, "", if (errors.isEmpty()) "No errors" else "${errors.size} errors were logged", NotificationType.INFORMATION, null)
        .notify(null)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    createAllServicesAndExtensions()
  }
}

@Suppress("HardCodedStringLiteral")
private fun checkContainer(container: ComponentManagerImpl, indicator: ProgressIndicator, taskExecutor: (task: () -> Unit) -> Unit) {
  indicator.text2 = "Checking ${container.activityNamePrefix()}services..."
  ComponentManagerImpl.createAllServices(container)
  indicator.text2 = "Checking ${container.activityNamePrefix()}extensions..."
  container.extensionArea.processExtensionPoints { extensionPoint ->
    // requires read action
    if (extensionPoint.name != "com.intellij.favoritesListProvider" && extensionPoint.name != "com.intellij.favoritesListProvider") {
      extensionPoint.processImplementations(false, BiConsumer { supplier, _ ->
        taskExecutor {
          try {
            supplier.get()
          }
          catch (ignore: ExtensionNotApplicableException) {
          }
        }
      })
    }
  }
}

private fun checkLightServices(taskExecutor: (task: () -> Unit) -> Unit) {
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
        val lightServices = scanResult.getClassesWithAnnotation(Service::class.java.name)
        for (lightService in lightServices) {
          // not clear - from what classloader light service will be loaded in reality
          val lightServiceClass = loadLightServiceClass(lightService, plugin)

          val isProjectLevel: Boolean
          val isAppLevel: Boolean
          val annotationParameterValue = lightService.getAnnotationInfo(Service::class.java.name).parameterValues.find { it.name == "value" }
          if (annotationParameterValue == null) {
            isAppLevel = lightServiceClass.declaredConstructors.any { it.parameterCount == 0 }
            isProjectLevel = lightServiceClass.declaredConstructors.any { it.parameterCount == 1 && it.parameterTypes.get(0) == Project::class.java }
          }
          else {
            val list = annotationParameterValue.value as Array<*>
            isAppLevel = list.any { v -> (v as AnnotationEnumValue).valueName == Service.Level.APP.name }
            isProjectLevel = list.any { v -> (v as AnnotationEnumValue).valueName == Service.Level.PROJECT.name }
          }

          if (isAppLevel) {
            taskExecutor {
              ApplicationManager.getApplication().getService(lightServiceClass)
            }
          }
          if (isProjectLevel) {
            taskExecutor {
              ProjectUtil.getOpenProjects().firstOrNull()?.getService(lightServiceClass)
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