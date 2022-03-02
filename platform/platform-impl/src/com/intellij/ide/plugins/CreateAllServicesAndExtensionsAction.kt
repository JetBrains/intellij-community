// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems", "ReplaceGetOrSet")
package com.intellij.ide.plugins

import com.intellij.diagnostic.PluginException
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.stubs.StubElementTypeHolderEP
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.util.getErrorsAsString
import io.github.classgraph.AnnotationEnumValue
import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import kotlin.properties.Delegates.notNull

@Suppress("HardCodedStringLiteral")
private class CreateAllServicesAndExtensionsAction : AnAction("Create All Services And Extensions"), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val errors = mutableListOf<Throwable>()
    runModalTask("Creating All Services And Extensions", cancellable = true) { indicator ->
      val taskExecutor: (task: () -> Unit) -> Unit = { task ->
        try {
          task()
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (e: Throwable) {
          errors.add(e)
        }
      }

      // check first
      checkExtensionPoint(StubElementTypeHolderEP.EP_NAME.point as ExtensionPointImpl<*>, taskExecutor)

      checkContainer(ApplicationManager.getApplication() as ComponentManagerImpl, indicator, taskExecutor)
      ProjectUtil.getOpenProjects().firstOrNull()?.let {
        checkContainer(it as ComponentManagerImpl, indicator, taskExecutor)
      }

      indicator.text2 = "Checking light services..."
      checkLightServices(taskExecutor, errors)
    }

    if (errors.isNotEmpty()) {
      logger<ComponentManagerImpl>().error(getErrorsAsString(errors))
    }
    // some errors are not thrown but logged
    val message = (if (errors.isEmpty()) "No errors" else "${errors.size} errors were logged") + ". Check also that no logged errors."
    Notification("Error Report", "", message, NotificationType.INFORMATION).notify(null)
  }
}

private class CreateAllServicesAndExtensionsActivity : AppLifecycleListener {

  init {
    if (!ApplicationManager.getApplication().isInternal ||
        !java.lang.Boolean.getBoolean("ide.plugins.create.all.services.and.extensions")) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun appStarted() = ApplicationManager.getApplication().invokeLater {
    performAction()
  }
}

fun performAction() {
  val actionManager = ActionManager.getInstance()
  actionManager.tryToExecute(
    actionManager.getAction(ACTION_ID),
    null,
    null,
    ActionPlaces.UNKNOWN,
    true,
  )
}

// external usage in [src/com/jetbrains/performancePlugin/commands/chain/generalCommandChain.kt]
const val ACTION_ID = "CreateAllServicesAndExtensions"

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
private val badServices = java.util.Set.of(
  "com.intellij.usageView.impl.UsageViewContentManagerImpl",
  "com.jetbrains.python.scientific.figures.PyPlotToolWindow",
  "com.intellij.analysis.pwa.analyser.PwaServiceImpl",
  "com.intellij.analysis.pwa.view.toolwindow.PwaProblemsViewImpl",
)

@Suppress("HardCodedStringLiteral")
private fun checkContainer(container: ComponentManagerImpl, indicator: ProgressIndicator, taskExecutor: (task: () -> Unit) -> Unit) {
  indicator.text2 = "Checking ${container.activityNamePrefix()}services..."
  ComponentManagerImpl.createAllServices(container, badServices)
  indicator.text2 = "Checking ${container.activityNamePrefix()}extensions..."
  container.extensionArea.processExtensionPoints { extensionPoint ->
    // requires a read action
    if (extensionPoint.name == "com.intellij.favoritesListProvider" ||
        extensionPoint.name == "org.jetbrains.kotlin.defaultErrorMessages") {
      return@processExtensionPoints
    }

    checkExtensionPoint(extensionPoint, taskExecutor)
  }
}

private fun checkExtensionPoint(extensionPoint: ExtensionPointImpl<*>, taskExecutor: (task: () -> Unit) -> Unit) {
  var extensionClass: Class<out Any> by notNull()
  taskExecutor {
    extensionClass = extensionPoint.extensionClass
  }

  extensionPoint.checkImplementations { extension ->
    taskExecutor {
      val extensionInstance: Any
      try {
        extensionInstance = (extension.createInstance(extensionPoint.componentManager) ?: return@taskExecutor)
      }
      catch (e: Exception) {
        throw PluginException("Failed to instantiate extension (extension=$extension, pointName=${extensionPoint.name})",
                              e, extension.pluginDescriptor.pluginId)
      }

      if (!extensionClass.isInstance(extensionInstance)) {
        throw PluginException("$extension does not implement $extensionClass", extension.pluginDescriptor.pluginId)
      }
    }
  }
}

private fun checkLightServices(taskExecutor: (task: () -> Unit) -> Unit, errors: MutableList<Throwable>) {
  for (plugin in PluginManagerCore.getPluginSet().enabledPlugins) {
    // we don't check classloader for sub descriptors because url set is the same
    val pluginClassLoader = plugin.pluginClassLoader
    if (pluginClassLoader !is PluginClassLoader) {
      continue
    }

    ClassGraph()
      .enableAnnotationInfo()
      .ignoreParentClassLoaders()
      .overrideClassLoaders(pluginClassLoader)
      .scan()
      .use { scanResult ->
        val lightServices = scanResult.getClassesWithAnnotation(Service::class.java.name)
        for (lightService in lightServices) {
          if (lightService.name == "org.jetbrains.plugins.grails.runner.GrailsConsole" ||
              lightService.name == "com.jetbrains.rdserver.editors.MultiUserCaretSynchronizerProjectService" ||
              lightService.name == "com.intellij.javascript.web.webTypes.nodejs.WebTypesNpmLoader") {
            // wants EDT/read action in constructor
             continue
          }

          // not clear - from what classloader light service will be loaded in reality
          val lightServiceClass = try {
            loadLightServiceClass(lightService, plugin)
          }
          catch (e: Throwable) {
            errors.add(e)
            continue
          }

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
              try {
                ApplicationManager.getApplication().getService(lightServiceClass)
              }
              catch (e: Throwable) {
                errors.add(RuntimeException("Cannot create $lightServiceClass", e))
              }
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
  for (item in mainDescriptor.content.modules) {
    val classLoader = item.requireDescriptor().pluginClassLoader as? PluginClassLoader ?: continue
    if (lightService.name.startsWith(classLoader.packagePrefix!!)) {
      return classLoader.loadClass(lightService.name, true)
    }
  }

  // ok, or no plugin dependencies at all, or all are disabled, resolve from main
  return (mainDescriptor.pluginClassLoader as PluginClassLoader).loadClass(lightService.name, true)
}
