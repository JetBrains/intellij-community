// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems", "ReplaceGetOrSet")
package com.intellij.ide.plugins

import com.intellij.diagnostic.PluginException
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.stubs.StubElementTypeHolderEP
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.util.getErrorsAsString
import io.github.classgraph.*
import java.lang.reflect.Constructor
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

      val application = ApplicationManager.getApplication() as ComponentManagerImpl
      checkContainer(application, "app", indicator, taskExecutor)

      val project = ProjectUtil.getOpenProjects().firstOrNull() as? ComponentManagerImpl
      if (project != null) {
        checkContainer(project, "project", indicator, taskExecutor)
        val module = ModuleManager.getInstance(project as Project).modules.firstOrNull() as? ComponentManagerImpl
        if (module != null) {
          checkContainer(module, "module", indicator, taskExecutor)
        }
      }

      indicator.text2 = "Checking light services..."
      for (mainDescriptor in PluginManagerCore.getPluginSet().enabledPlugins) {
        // we don't check classloader for sub descriptors because url set is the same
        val pluginClassLoader = mainDescriptor.pluginClassLoader as? PluginClassLoader
                                ?: continue

        scanClassLoader(pluginClassLoader).use { scanResult ->
          for (classInfo in scanResult.getClassesWithAnnotation(Service::class.java.name)) {
            checkLightServices(classInfo, mainDescriptor, application, project) {
              val error = when (it) {
                is ProcessCanceledException -> throw it
                is PluginException -> it
                else -> PluginException("Cannot create ${classInfo.name}", it, mainDescriptor.pluginId)
              }

              errors.add(error)
            }
          }
        }
      }
    }

    if (errors.isNotEmpty()) {
      logger<ComponentManagerImpl>().error(getErrorsAsString(errors).toString())
    }
    // some errors are not thrown but logged
    val message = (if (errors.isEmpty()) "No errors" else "${errors.size} errors were logged") + ". Check also that no logged errors."
    Notification("Error Report", "", message, NotificationType.INFORMATION).notify(null)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
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
const val ACTION_ID: String = "CreateAllServicesAndExtensions"

/**
 * If service instance is obtained on Event Dispatch Thread only, it may expect that its constructor is called on EDT as well, so we must
 * honor this in the action.
 */
@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
private val servicesWhichRequireEdt = java.util.Set.of(
  "com.intellij.usageView.impl.UsageViewContentManagerImpl",
  "com.jetbrains.python.scientific.figures.PyPlotToolWindow",
  "com.intellij.analysis.pwa.analyser.PwaServiceImpl",
  "com.intellij.analysis.pwa.view.toolwindow.PwaProblemsViewImpl",
)

/**
 * If service instance is obtained under read action only, it may expect that its constructor is called with read access, so we must honor
 * this in the action.
 */
private val servicesWhichRequireReadAction = setOf(
  "org.jetbrains.plugins.grails.lang.gsp.psi.gsp.impl.gtag.GspTagDescriptorService",
  "com.intellij.database.psi.DbFindUsagesOptionsProvider",
  "com.jetbrains.python.findUsages.PyFindUsagesOptions"
)

@Suppress("HardCodedStringLiteral")
private fun checkContainer(container: ComponentManagerImpl, levelDescription: String?, indicator: ProgressIndicator,
                           taskExecutor: (task: () -> Unit) -> Unit) {
  indicator.text2 = "Checking ${levelDescription} services..."
  ComponentManagerImpl.createAllServices(container, servicesWhichRequireEdt, servicesWhichRequireReadAction)
  indicator.text2 = "Checking ${levelDescription} extensions..."
  container.extensionArea.processExtensionPoints { extensionPoint ->
    // requires a read action
    if (extensionPoint.name == "com.intellij.favoritesListProvider" ||
        extensionPoint.name == "com.intellij.postStartupActivity" ||
        extensionPoint.name == "com.intellij.backgroundPostStartupActivity" ||
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

private fun scanClassLoader(pluginClassLoader: PluginClassLoader): ScanResult {
  return ClassGraph()
    .enableAnnotationInfo()
    .ignoreParentClassLoaders()
    .overrideClassLoaders(pluginClassLoader)
    .scan()
}

private fun checkLightServices(
  classInfo: ClassInfo,
  mainDescriptor: IdeaPluginDescriptorImpl,
  application: ComponentManagerImpl,
  project: ComponentManagerImpl?,
  onThrowable: (Throwable) -> Unit,
) {
  try {
    val lightServiceClass = when (val className = classInfo.name) {
                              // wants EDT/read action in constructor
                              "org.jetbrains.plugins.grails.runner.GrailsConsole",
                              "com.jetbrains.rdserver.editors.MultiUserCaretSynchronizerProjectService",
                              "com.intellij.javascript.web.webTypes.nodejs.WebTypesNpmLoader" -> null
                              // not clear - from what classloader light service will be loaded in reality
                              else -> loadLightServiceClass(className, mainDescriptor)
                            } ?: return

    val (isAppLevel, isProjectLevel) = classInfo.getAnnotationInfo(Service::class.java.name)
                                         .parameterValues
                                         .find { it.name == "value" }
                                         ?.let { levelsByAnnotations(it) }
                                       ?: levelsByConstructors(lightServiceClass.declaredConstructors)

    val components = listOfNotNull(
      if (isAppLevel) application else null,
      if (isProjectLevel) project else null,
    )

    for (component in components) {
      try {
        component.getService(lightServiceClass)
      }
      catch (e: Throwable) {
        onThrowable(e)
      }
    }
  }
  catch (e: Throwable) {
    onThrowable(e)
  }
}

private data class Levels(
  val isAppLevel: Boolean,
  val isProjectLevel: Boolean,
)

private fun levelsByConstructors(constructors: Array<Constructor<*>>): Levels {
  return Levels(
    isAppLevel = constructors.any { it.parameterCount == 0 },
    isProjectLevel = constructors.any { constructor ->
      constructor.parameterCount == 1
      && constructor.parameterTypes.get(0) == Project::class.java
    },
  )
}

private fun levelsByAnnotations(annotationParameterValue: AnnotationParameterValue): Levels {
  fun hasLevel(level: Service.Level) =
    (annotationParameterValue.value as Array<*>).asSequence()
      .map { it as AnnotationEnumValue }
      .any { it.name == level.name }

  return Levels(
    isAppLevel = hasLevel(Service.Level.APP),
    isProjectLevel = hasLevel(Service.Level.PROJECT),
  )
}

private fun loadLightServiceClass(
  className: String,
  mainDescriptor: IdeaPluginDescriptorImpl,
): Class<*>? {
  fun loadClass(descriptor: IdeaPluginDescriptorImpl) =
    (descriptor.pluginClassLoader as? PluginClassLoader)?.loadClass(className, true)

  for (moduleItem in mainDescriptor.content.modules) {
    try {
      // module is not loaded - dependency is not provided
      return loadClass(moduleItem.requireDescriptor())
    }
    catch (_: PluginException) {
    }
    catch (_: ClassNotFoundException) {
    }
  }

  // ok, or no plugin dependencies at all, or all are disabled, resolve from main
  return loadClass(mainDescriptor)
}
