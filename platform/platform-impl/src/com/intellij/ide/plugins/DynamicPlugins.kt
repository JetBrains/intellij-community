// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.fasterxml.jackson.databind.type.TypeFactory
import com.intellij.configurationStore.StoreUtil.Companion.saveDocumentsAndProjectsAndApp
import com.intellij.configurationStore.jdomSerializer
import com.intellij.configurationStore.runInAutoSaveDisabledMode
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.ui.TopHitCache
import com.intellij.ide.ui.UIThemeProvider
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.lang.Language
import com.intellij.model.psi.impl.ReferenceProviders
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.ExtensionsArea
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.keymap.impl.BundledKeymapBean
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.PotemkinProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.util.CachedValuesManager
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.serviceContainer.ComponentManagerImpl.DescriptorToLoad
import com.intellij.util.CachedValuesManagerImpl
import com.intellij.util.MemoryDumpHelper
import com.intellij.util.SystemProperties
import com.intellij.util.io.URLUtil
import com.intellij.util.messages.Topic
import com.intellij.util.messages.impl.MessageBusImpl
import com.intellij.util.xmlb.BeanBinding
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JComponent

internal class CannotUnloadPluginException(value: String) : ProcessCanceledException(RuntimeException(value))

interface DynamicPluginListener {
  @JvmDefault
  fun beforePluginLoaded(pluginDescriptor: IdeaPluginDescriptor) { }

  @JvmDefault
  fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) { }

  /**
   * @param isUpdate true if the plugin is being unloaded as part of an update installation and a new version will be loaded afterwards
   */
  @JvmDefault
  fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) { }

  @JvmDefault
  fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) { }

  /**
   * Checks if the plugin can be dynamically unloaded at this moment.
   * Method should throw {@link CannotUnloadPluginException} if it isn't possible by some reason
   */
  @Throws(CannotUnloadPluginException::class)
  @JvmDefault
  fun checkUnloadPlugin(pluginDescriptor: IdeaPluginDescriptor) { }

  companion object {
    @JvmField val TOPIC = Topic.create("DynamicPluginListener", DynamicPluginListener::class.java)
  }
}

object DynamicPlugins {
  private val LOG = Logger.getInstance(DynamicPlugins::class.java)
  private val GROUP = NotificationGroup("Dynamic plugin installation", NotificationDisplayType.BALLOON, false)

  @JvmStatic
  @JvmOverloads
  fun allowLoadUnloadWithoutRestart(descriptor: IdeaPluginDescriptorImpl, baseDescriptor: IdeaPluginDescriptorImpl? = null): Boolean {
    if (InstalledPluginsState.getInstance().isRestartRequired) {
      return false
    }

    if (!Registry.`is`("ide.plugins.allow.unload")) {
      return allowLoadUnloadSynchronously(descriptor)
    }

    val projectManager = ProjectManager.getInstance()
    val anyProject = projectManager.openProjects.firstOrNull() ?: projectManager.defaultProject

    val loadedPluginDescriptor = if (descriptor.pluginId != null) PluginManagerCore.getPlugin(descriptor.pluginId) as? IdeaPluginDescriptorImpl else null

    try {
      ApplicationManager.getApplication().messageBus.syncPublisher(DynamicPluginListener.TOPIC).checkUnloadPlugin(descriptor)
    }
    catch (e: CannotUnloadPluginException) {
      val localizedMessage = e.cause?.localizedMessage
      LOG.info(localizedMessage)
      return false
    }

    if (!Registry.`is`("ide.plugins.allow.unload.from.sources")) {
      if (loadedPluginDescriptor != null && isPluginOrModuleLoaded(loadedPluginDescriptor.pluginId)) {
        if (!descriptor.useIdeaClassLoader) {
          val pluginClassLoader = loadedPluginDescriptor.pluginClassLoader
          if (pluginClassLoader !is PluginClassLoader && !ApplicationManager.getApplication().isUnitTestMode) {
            val loader = baseDescriptor ?: descriptor
            LOG.info("Plugin ${loader.pluginId} is not unload-safe because of use of ${pluginClassLoader.javaClass.name} as the default class loader. " +
                     "For example, the IDE is started from the sources with the plugin.")
            return false
          }
        }
      }
    }

    val extensions = descriptor.extensions
    if (extensions != null) {
      for (epName in extensions.keys) {
        val pluginExtensionPoint = findPluginExtensionPoint(baseDescriptor ?: descriptor, epName)
        if (pluginExtensionPoint != null) {
          // descriptor.pluginId is null when we check the optional dependencies of the plugin which is being loaded
          // if an optional dependency of a plugin extends a non-dynamic EP of that plugin, it shouldn't prevent plugin loading
          if (baseDescriptor != null && descriptor.pluginId != null && !pluginExtensionPoint.isDynamic) {
            LOG.info("Plugin ${baseDescriptor.pluginId} is not unload-safe because of use of non-dynamic EP $epName" +
                     " in optional dependency on it: ${descriptor.pluginId}")
            return false
          }
          continue
        }

        @Suppress("RemoveExplicitTypeArguments") val ep =
          Extensions.getRootArea().getExtensionPointIfRegistered<Any>(epName)
          ?: anyProject.extensionArea.getExtensionPointIfRegistered<Any>(epName)
        if (ep != null) {
          if (!ep.isDynamic) {
            if (baseDescriptor != null) {
              LOG.info(
                "Plugin ${baseDescriptor.pluginId} is not unload-safe because of use of non-dynamic EP $epName in optional dependencies on it")
            }
            else {
              LOG.info("Plugin ${descriptor.pluginId} is not unload-safe because of extension to non-dynamic EP $epName")
            }
            return false
          }
          continue
        }

        if (baseDescriptor != null) {
          val baseEP = findPluginExtensionPoint(baseDescriptor, epName)
          if (baseEP != null) {
            if (!baseEP.isDynamic) {
              LOG.info(
                "Plugin ${baseDescriptor.pluginId} is not unload-safe because of use of non-dynamic EP $epName in optional dependencies on it")
              return false
            }
            continue
          }
        }

        LOG.info("Plugin ${descriptor.pluginId} is not unload-safe because of unresolved extension $epName")
        return false
      }
    }

    if (!hasNoComponentsOrServiceOverrides(descriptor)) return false
    if (!((ActionManager.getInstance() as ActionManagerImpl).canUnloadActions(descriptor))) return false

    descriptor.optionalConfigs?.forEach { (pluginId, optionalDescriptors) ->
      if (isPluginOrModuleLoaded(pluginId) && optionalDescriptors.any { !allowLoadUnloadWithoutRestart(it, descriptor) }) return false
    }

    var canUnload = true
    processOptionalDependenciesOnPlugin(descriptor) { _, fullyLoadedDescriptor ->
      if (!allowLoadUnloadWithoutRestart(fullyLoadedDescriptor, descriptor)) {
        canUnload = false
      }
      canUnload
    }

    return canUnload
  }

  private fun processOptionalDependenciesOnPlugin(rootDescriptor: IdeaPluginDescriptorImpl,
                                                  callback: (loadedDescriptorOfDependency: IdeaPluginDescriptorImpl, fullDescriptor: IdeaPluginDescriptorImpl) -> Boolean) {
    val pluginXmlFactory = PluginXmlFactory()
    val listContext = DescriptorListLoadingContext.createSingleDescriptorContext(PluginManagerCore.disabledPlugins())
    for (descriptor in PluginManagerCore.getLoadedPlugins()) {
      if (!descriptor.optionalDependentPluginIds.contains(rootDescriptor.pluginId)) {
        continue
      }

      descriptor as IdeaPluginDescriptorImpl

      val dependencyConfigFile = descriptor.findOptionalDependencyConfigFile(rootDescriptor.pluginId) ?: continue

      val pathResolver = PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER
      val context = DescriptorLoadingContext(listContext, false, false, pathResolver)
      val element = try {
        val baseUri = descriptor.basePath.toUri()
        val jarPair = URLUtil.splitJarUrl(baseUri.toString())
        val newBasePath = if (jarPair != null) {
          val (jarPath, pathInsideJar) = jarPair
          val fs = context.open(Paths.get(jarPath))
          fs.getPath(pathInsideJar)
        }
        else {
          Paths.get(baseUri)
        }
        pathResolver.resolvePath(newBasePath, dependencyConfigFile, pluginXmlFactory)
      }
      catch (e: Exception) {
        LOG.info("Can't resolve optional dependency on plugin being loaded/unloaded: config file $dependencyConfigFile", e)
        continue
      }
      finally {
        context.close()
      }

      val fullyLoadedDescriptor = IdeaPluginDescriptorImpl(descriptor.pluginPath, false)
      if (!fullyLoadedDescriptor.readExternal(element, descriptor.basePath, pathResolver, context, descriptor)) {
        LOG.info("Can't read descriptor $dependencyConfigFile for optional dependency of plugin being loaded/unloaded")
        continue
      }
      if (!callback(descriptor, fullyLoadedDescriptor)) {
        break
      }
    }
  }

  private fun findPluginExtensionPoint(pluginDescriptor: IdeaPluginDescriptorImpl, epName: String): ExtensionPointImpl<*>? =
    findContainerExtensionPoint(pluginDescriptor.app, epName) ?:
    findContainerExtensionPoint(pluginDescriptor.project, epName) ?:
    findContainerExtensionPoint(pluginDescriptor.module, epName)

  private fun findContainerExtensionPoint(containerDescriptor: ContainerDescriptor, epName: String): ExtensionPointImpl<*>? =
    containerDescriptor.extensionPoints?.find { it.name == epName }

  /**
   * Checks if the plugin can be loaded/unloaded immediately when the corresponding action is invoked in the
   * plugins settings, without pressing the Apply button.
   */
  @JvmStatic
  fun allowLoadUnloadSynchronously(pluginDescriptor: IdeaPluginDescriptorImpl): Boolean {
    val extensions = pluginDescriptor.extensions
    if (extensions != null && !extensions.all {
        it.key == UIThemeProvider.EP_NAME.name ||
        it.key == BundledKeymapBean.EP_NAME.name}) {
      return false
    }
    return hasNoComponentsOrServiceOverrides(pluginDescriptor) && pluginDescriptor.actionDescriptionElements.isNullOrEmpty()
  }

  private fun hasNoComponentsOrServiceOverrides(pluginDescriptor: IdeaPluginDescriptorImpl): Boolean =
    hasNoComponentsOrServiceOverrides(pluginDescriptor.appContainerDescriptor) &&
    hasNoComponentsOrServiceOverrides(pluginDescriptor.projectContainerDescriptor) &&
    hasNoComponentsOrServiceOverrides(pluginDescriptor.moduleContainerDescriptor)

  private fun hasNoComponentsOrServiceOverrides(containerDescriptor: ContainerDescriptor): Boolean {
    if (!containerDescriptor.components.isNullOrEmpty()) {
      LOG.info("Plugin is not unload-safe because it declares components")
      return false
    }
    if (containerDescriptor.services?.any { it.overrides } == true) {
      LOG.info("Plugin is not unload-safe because it overrides services")
      return false
    }
    return true
  }

  @JvmStatic
  @JvmOverloads
  fun unloadPluginWithProgress(parentComponent: JComponent?,
                               pluginDescriptor: IdeaPluginDescriptorImpl,
                               disable: Boolean = false,
                               isUpdate: Boolean = false): Boolean {
    var result = false
    if (!allowLoadUnloadSynchronously(pluginDescriptor)) {
      runInAutoSaveDisabledMode {
        val saveAndSyncHandler = SaveAndSyncHandler.getInstance()
        saveAndSyncHandler.saveSettingsUnderModalProgress(ApplicationManager.getApplication())
        for (openProject in ProjectManager.getInstance().openProjects) {
          saveAndSyncHandler.saveSettingsUnderModalProgress(openProject)
        }
      }
    }
    val indicator = PotemkinProgress("Unloading plugin ${pluginDescriptor.name}", null, parentComponent, null)
    indicator.runInSwingThread {
      result = unloadPlugin(pluginDescriptor, disable, isUpdate, save = false)
    }
    return result
  }

  @JvmStatic
  @JvmOverloads
  fun unloadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl, disable: Boolean = false, isUpdate: Boolean = false, save: Boolean = true): Boolean {
    val application = ApplicationManager.getApplication() as ApplicationImpl

    // The descriptor passed to `unloadPlugin` is the full descriptor loaded from disk, it does not have a classloader.
    // We need to find the real plugin loaded into the current instance and unload its classloader.
    val loadedPluginDescriptor = PluginManagerCore.getPlugin(pluginDescriptor.pluginId) as? IdeaPluginDescriptorImpl
                                 ?: return false

    try {
      if (save) {
        saveDocumentsAndProjectsAndApp(true)
      }
      application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).beforePluginUnload(pluginDescriptor, isUpdate)
      IdeEventQueue.getInstance().flushQueue()

      application.runWriteAction {
        try {
          processOptionalDependenciesOnPlugin(pluginDescriptor) { loadedDescriptorOfDependency, dependencyDescriptor ->
            unloadPluginDescriptor(dependencyDescriptor, loadedDescriptorOfDependency, dependencyDescriptor)
            var detached: Boolean? = false
            if (loadedPluginDescriptor.pluginClassLoader is PluginClassLoader) {
              detached = (loadedDescriptorOfDependency.pluginClassLoader as? PluginClassLoader)?.detachParent(loadedPluginDescriptor.pluginClassLoader)
            }
            if (detached != true) {
              LOG.info("Failed to detach classloader of ${loadedPluginDescriptor.pluginId} from classloader of ${loadedDescriptorOfDependency.pluginId}")
            }
            true
          }

          if (!pluginDescriptor.useIdeaClassLoader) {
            if (loadedPluginDescriptor.pluginClassLoader is PluginClassLoader) {
              IconLoader.detachClassLoader(loadedPluginDescriptor.pluginClassLoader)
              Language.unregisterLanguages(loadedPluginDescriptor.pluginClassLoader)
            }
          }

          pluginDescriptor.optionalConfigs?.forEach { (pluginId, optionalDescriptors) ->
            if (isPluginOrModuleLoaded(pluginId)) {
              for (optionalDescriptor in optionalDescriptors) {
                unloadPluginDescriptor(optionalDescriptor, loadedPluginDescriptor)
              }
            }
          }
          unloadPluginDescriptor(pluginDescriptor, loadedPluginDescriptor)

          for (project in ProjectManager.getInstance().openProjects) {
            (CachedValuesManager.getManager(project) as CachedValuesManagerImpl).clearCachedValues()
          }
          ReferenceProviders.getInstance().clearCaches()
          jdomSerializer.clearSerializationCaches()
          BeanBinding.clearSerializationCaches()
          TypeFactory.defaultInstance().clearCache()
          DefaultJDOMExternalizer.clearFieldCache()
          TopHitCache.getInstance().clear()
          Disposer.clearDisposalTraces()  // ensure we don't have references to plugin classes in disposal backtraces

          if (disable) {
            // Update list of disabled plugins
            PluginManager.getInstance().setPlugins(PluginManagerCore.getPlugins().asList())
          }
          else {
            PluginManager.getInstance().setPlugins(PluginManagerCore.getPlugins().asSequence().minus(loadedPluginDescriptor).toList())
          }
        }
        finally {
          val disposable = PluginManager.pluginDisposables.remove(pluginDescriptor)
          if (disposable != null) {
            Disposer.dispose(disposable)
          }
          application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).pluginUnloaded(pluginDescriptor, isUpdate)
        }
      }
    }
    catch (e: Exception) {
      Logger.getInstance(DynamicPlugins.javaClass).error(e)
    }
    finally {
      IdeEventQueue.getInstance().flushQueue()

      if (ApplicationManager.getApplication().isUnitTestMode && loadedPluginDescriptor.pluginClassLoader !is PluginClassLoader) {
        return true
      }

      val classLoaderUnloaded = loadedPluginDescriptor.unloadClassLoader()
      if (!classLoaderUnloaded) {
        InstalledPluginsState.getInstance().isRestartRequired = true

        if (Registry.`is`("ide.plugins.snapshot.on.unload.fail") && MemoryDumpHelper.memoryDumpAvailable() && !ApplicationManager.getApplication().isUnitTestMode) {
          val snapshotFolder = System.getProperty("snapshots.path", SystemProperties.getUserHome())
          val snapshotDate = SimpleDateFormat("dd.MM.yyyy_HH.mm.ss").format(Date())
          val snapshotPath = "$snapshotFolder/unload-${pluginDescriptor.pluginId}-$snapshotDate.hprof"
          MemoryDumpHelper.captureMemoryDump(snapshotPath)
          notify("Captured memory snapshot on plugin unload fail: $snapshotPath", NotificationType.WARNING)
        }
        LOG.info("Plugin ${pluginDescriptor.pluginId} is not unload-safe because class loader cannot be unloaded")
      }

      val eventId = if (classLoaderUnloaded) "unload.success" else "unload.fail"
      val fuData = FeatureUsageData().addPluginInfo(getPluginInfoByDescriptor(loadedPluginDescriptor))
      FUCounterUsageLogger.getInstance().logEvent("plugins.dynamic", eventId, fuData)

      return classLoaderUnloaded
    }
  }

  internal fun notify(text: String, notificationType: NotificationType) {
    GROUP.createNotification(text, notificationType).notify(null)
  }

  private fun unloadPluginDescriptor(
    pluginDescriptor: IdeaPluginDescriptorImpl,
    loadedPluginDescriptor: IdeaPluginDescriptorImpl,
    descriptorToUnloadListeners: IdeaPluginDescriptorImpl = loadedPluginDescriptor
  ) {
    val application = ApplicationManager.getApplication() as ApplicationImpl
    (ActionManager.getInstance() as ActionManagerImpl).unloadActions(pluginDescriptor)

    val openProjects = ProjectManager.getInstance().openProjects

    val unloadListeners = mutableListOf<Runnable>()
    pluginDescriptor.extensions?.let { extensions ->
      for ((epName, epExtensions) in extensions) {
        val appEp = Extensions.getRootArea().getExtensionPointIfRegistered<Any>(epName) as ExtensionPointImpl<*>?
        if (appEp != null) {
          appEp.unregisterExtensions(application, loadedPluginDescriptor, epExtensions, unloadListeners)
        }
        else {
          for (openProject in openProjects) {
            val projectEp = openProject.extensionArea.getExtensionPointIfRegistered<Any>(epName) as ExtensionPointImpl<*>?
            projectEp?.unregisterExtensions(openProject, loadedPluginDescriptor, epExtensions, unloadListeners)
          }
        }
      }
    }
    for (unloadListener in unloadListeners) {
      unloadListener.run()
    }

    processExtensionPoints(pluginDescriptor, openProjects) { areaInstance, name ->
      areaInstance.getExtensionPoint<Any>(name).reset()
    }
    processExtensionPoints(pluginDescriptor, openProjects) { areaInstance, name ->
      areaInstance.unregisterExtensionPoint(name)
    }
    pluginDescriptor.app.extensionPoints?.forEach(ExtensionPointImpl<*>::clearExtensionClass)
    pluginDescriptor.project.extensionPoints?.forEach(ExtensionPointImpl<*>::clearExtensionClass)
    loadedPluginDescriptor.app.extensionPoints?.forEach(ExtensionPointImpl<*>::clearExtensionClass)
    loadedPluginDescriptor.project.extensionPoints?.forEach(ExtensionPointImpl<*>::clearExtensionClass)

    val appServiceInstances = application.unloadServices(pluginDescriptor.app)
    for (appServiceInstance in appServiceInstances) {
      application.stateStore.unloadComponent(appServiceInstance)
    }
    (application.messageBus as MessageBusImpl).unsubscribePluginListeners(descriptorToUnloadListeners)

    for (project in openProjects) {
      val projectServiceInstances = (project as ProjectImpl).unloadServices(pluginDescriptor.project)
      for (projectServiceInstance in projectServiceInstances) {
        project.stateStore.unloadComponent(projectServiceInstance)
      }

      for (module in ModuleManager.getInstance(project).modules) {
        val moduleServiceInstances = (module as ComponentManagerImpl).unloadServices(pluginDescriptor.module)
        for (moduleServiceInstance in moduleServiceInstances) {
          module.stateStore.unloadComponent(moduleServiceInstance)
        }
      }
      (project.messageBus as MessageBusImpl).unsubscribePluginListeners(descriptorToUnloadListeners)
    }
  }

  private fun processExtensionPoints(pluginDescriptor: IdeaPluginDescriptorImpl,
                                     openProjects: Array<Project>,
                                     callback: (ExtensionsArea, String) -> Unit) {
    pluginDescriptor.app.extensionPoints?.let {
      val rootArea = ApplicationManager.getApplication().extensionArea
      for (point in it) {
        callback(rootArea, point.name)
      }
    }
    pluginDescriptor.project.extensionPoints?.let {
      for (point in it) {
        val extensionPointName = point.name
        for (openProject in openProjects) {
          callback(openProject.extensionArea, extensionPointName)
        }
      }
    }
  }

  @JvmStatic
  fun loadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl, wasDisabled: Boolean) {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      PluginManagerCore.initClassLoader(pluginDescriptor)
    }
    val application = ApplicationManager.getApplication() as ApplicationImpl
    application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).beforePluginLoaded(pluginDescriptor)
    application.runWriteAction {

      try {
        loadPluginDescriptor(pluginDescriptor, pluginDescriptor)

        processOptionalDependenciesOnPlugin(pluginDescriptor) { loadedDescriptorOfDependency, fullDescriptor ->
          if (pluginDescriptor.pluginClassLoader is PluginClassLoader) {
            (loadedDescriptorOfDependency.pluginClassLoader as? PluginClassLoader)?.attachParent(pluginDescriptor.pluginClassLoader)
          }

          loadPluginDescriptor(loadedDescriptorOfDependency, fullDescriptor)
          true
        }

        for (openProject in ProjectManager.getInstance().openProjects) {
          (CachedValuesManager.getManager(openProject) as CachedValuesManagerImpl).clearCachedValues()
        }

        if (wasDisabled) {
          val newPlugins = PluginManagerCore.getPlugins()
            .map { if (it.pluginId == pluginDescriptor.pluginId) pluginDescriptor else it }
            .toList()
          PluginManager.getInstance().setPlugins(newPlugins)
        }
        else {
          PluginManager.getInstance().setPlugins(PluginManagerCore.getPlugins().asSequence().plus(pluginDescriptor).toList())
        }
        val fuData = FeatureUsageData().addPluginInfo(getPluginInfoByDescriptor(pluginDescriptor))
        FUCounterUsageLogger.getInstance().logEvent("plugins.dynamic", "load", fuData)
      }
      finally {
        application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).pluginLoaded(pluginDescriptor)
      }
    }
  }

  private fun loadPluginDescriptor(baseDescriptor: IdeaPluginDescriptorImpl, fullyLoadedBaseDescriptor: IdeaPluginDescriptorImpl) {
    val application = ApplicationManager.getApplication() as ApplicationImpl
    val listenerCallbacks = arrayListOf<Runnable>()
    val pluginsToLoad = mutableListOf(DescriptorToLoad(fullyLoadedBaseDescriptor, baseDescriptor))
    fullyLoadedBaseDescriptor.optionalConfigs?.forEach { (pluginId, optionalDescriptors) ->
      if (isPluginOrModuleLoaded(pluginId)) {
        optionalDescriptors.mapTo(pluginsToLoad) {
          DescriptorToLoad(it, baseDescriptor)
        }
      }
    }
    application.registerComponents(pluginsToLoad, listenerCallbacks)
    for (openProject in ProjectManager.getInstance().openProjects) {
      (openProject as ProjectImpl).registerComponents(pluginsToLoad, listenerCallbacks)
      for (module in ModuleManager.getInstance(openProject).modules) {
        (module as ComponentManagerImpl).registerComponents(pluginsToLoad, listenerCallbacks)
      }
    }
    for (descriptorToLoad in pluginsToLoad) {
      (ActionManager.getInstance() as ActionManagerImpl).registerPluginActions(baseDescriptor, descriptorToLoad.descriptor.actionDescriptionElements, false)
    }
    listenerCallbacks.forEach(Runnable::run)
  }

  private fun isPluginOrModuleLoaded(pluginId: PluginId?): Boolean {
    if (pluginId != null && PluginManagerCore.isModuleDependency(pluginId)) {
      return PluginManagerCore.findPluginByModuleDependency(pluginId) != null
    }
    return PluginManagerCore.getLoadedPlugins().any { it.pluginId == pluginId }
  }

  @JvmStatic
  fun pluginDisposable(clazz: Class<*>): Disposable? {
    val classLoader = clazz.classLoader
    if (classLoader is PluginClassLoader) {
      val pluginDescriptor = classLoader.pluginDescriptor
      if (pluginDescriptor != null) {
        return PluginManager.pluginDisposables[pluginDescriptor]
      }
    }
    return null
  }

  @JvmStatic
  fun pluginDisposable(clazz: Class<*>, defaultValue: Disposable): Disposable {
    val pluginDisposable = pluginDisposable(clazz)
    if (pluginDisposable != null) {
      val result = Disposer.newDisposable()
      Disposer.register(pluginDisposable, result)
      Disposer.register(defaultValue, Disposable { Disposer.dispose(result) })
      return result
    }
    return defaultValue
  }

  @JvmStatic
  fun onPluginUnload(parentDisposable: Disposable, callback: Runnable) {
    ApplicationManager.getApplication().messageBus.connect(parentDisposable).subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        callback.run()
      }
    })
  }
}