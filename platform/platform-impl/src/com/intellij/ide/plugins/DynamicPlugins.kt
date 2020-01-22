// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.configurationStore.StoreUtil.Companion.saveDocumentsAndProjectsAndApp
import com.intellij.configurationStore.jdomSerializer
import com.intellij.configurationStore.runInAutoSaveDisabledMode
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.ui.UIThemeProvider
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
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.keymap.impl.BundledKeymapBean
import com.intellij.openapi.keymap.impl.BundledKeymapProvider
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.PotemkinProgress
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.util.CachedValuesManager
import com.intellij.serviceContainer.PlatformComponentManagerImpl
import com.intellij.util.ArrayUtil
import com.intellij.util.CachedValuesManagerImpl
import com.intellij.util.MemoryDumpHelper
import com.intellij.util.SystemProperties
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.util.messages.Topic
import com.intellij.util.messages.impl.MessageBusImpl
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.BeanBinding
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JComponent


class CannotUnloadPluginException(value: String) : ProcessCanceledException(RuntimeException(value))

interface DynamicPluginListener {
  @JvmDefault
  fun beforePluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
  }

  @JvmDefault
  fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
  }

  /**
   * @param isUpdate true if the plugin is being unloaded as part of an update installation and a new version will be loaded afterwards
   */
  @JvmDefault
  fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
  }

  @JvmDefault
  fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
  }


  /**
   * Checks if the plugin can be dynamically unloaded at this moment. 
   * Method should throw {@link CannotUnloadPluginException} if it isn't possible by some reason
   */
  @Throws(CannotUnloadPluginException::class)
  @JvmDefault 
  fun checkUnloadPlugin(pluginDescriptor: IdeaPluginDescriptor) {
  }

  companion object {
    @JvmField val TOPIC = Topic.create("DynamicPluginListener", DynamicPluginListener::class.java)
  }
}

object DynamicPlugins {
  private val LOG = Logger.getInstance(DynamicPlugins::class.java)
  private val GROUP = NotificationGroup("Dynamic plugin installation", NotificationDisplayType.BALLOON, false)

  val pluginDisposables = ConcurrentFactoryMap.createWeakMap<PluginDescriptor, Disposable> {
    plugin -> Disposer.newDisposable("Plugin disposable [${plugin.name}]")
  }

  @JvmStatic
  @JvmOverloads
  fun allowLoadUnloadWithoutRestart(pluginDescriptor: IdeaPluginDescriptorImpl, baseDescriptor: IdeaPluginDescriptorImpl? = null): Boolean {
    val anyProject = ProjectManager.getInstance().openProjects.firstOrNull() ?:
                     ProjectManager.getInstance().defaultProject

    val loadedPluginDescriptor = if (pluginDescriptor.pluginId != null) PluginManagerCore.getPlugin(pluginDescriptor.pluginId) as? IdeaPluginDescriptorImpl else null

    try {
      ApplicationManager.getApplication().messageBus.syncPublisher(DynamicPluginListener.TOPIC).checkUnloadPlugin(pluginDescriptor)
    } catch (e: CannotUnloadPluginException) {
      val localizedMessage = e.cause?.localizedMessage
      LOG.info(localizedMessage)
      return false
    }
    
    if (loadedPluginDescriptor != null && isPluginLoaded(loadedPluginDescriptor.pluginId)) {
      if (!pluginDescriptor.useIdeaClassLoader) {
        val pluginClassLoader = loadedPluginDescriptor.pluginClassLoader
        if (pluginClassLoader !is PluginClassLoader && !ApplicationManager.getApplication().isUnitTestMode) {
          val loader = baseDescriptor ?: pluginDescriptor
          LOG.info("Plugin ${loader.pluginId} is not unload-safe because of use of UrlClassLoader as the default class loader. " +
                   "For example, the IDE is started from the sources with the plugin.")
          return false
        }
      }
    }
    
    val extensions = pluginDescriptor.extensions
    if (extensions != null) {
      for (epName in extensions.keys) {
        val pluginExtensionPoint = findPluginExtensionPoint(pluginDescriptor, epName)
        if (pluginExtensionPoint != null) {
          if (baseDescriptor != null && !pluginExtensionPoint.isDynamic) {
            LOG.info("Plugin ${baseDescriptor.pluginId} is not unload-safe because of use of non-dynamic EP $epName in optional dependencies on it")
            return false
          }
          continue
        }

        val ep = Extensions.getRootArea().getExtensionPointIfRegistered<Any>(epName) ?:
          anyProject.extensionArea.getExtensionPointIfRegistered<Any>(epName)
        if (ep != null) {
          if (!ep.isDynamic) {
            if (baseDescriptor != null) {
              LOG.info("Plugin ${baseDescriptor.pluginId} is not unload-safe because of use of non-dynamic EP $epName in optional dependencies on it")
            }
            else {
              LOG.info("Plugin ${pluginDescriptor.pluginId} is not unload-safe because of extension to non-dynamic EP $epName")
            }
            return false
          }
          continue
        }

        if (baseDescriptor != null) {
          val baseEP = findPluginExtensionPoint(baseDescriptor, epName)
          if (baseEP != null) {
            if (!baseEP.isDynamic) {
              LOG.info("Plugin ${baseDescriptor.pluginId} is not unload-safe because of use of non-dynamic EP $epName in optional dependencies on it")
              return false
            }
            continue
          }
        }

        LOG.info("Plugin ${pluginDescriptor.pluginId} is not unload-safe because of unresolved extension $epName")
        return false
      }
    }

    if (!hasNoComponents(pluginDescriptor)) return false
    if (!((ActionManager.getInstance() as ActionManagerImpl).canUnloadActions(pluginDescriptor))) return false

    pluginDescriptor.optionalConfigs?.forEach { (pluginId, optionalDescriptors) ->
      if (isPluginLoaded(pluginId) && optionalDescriptors.any { !allowLoadUnloadWithoutRestart(it, pluginDescriptor) }) return false
    }

    var canUnload = true
    processOptionalDependenciesOnPlugin(pluginDescriptor) { _, dependencyDescriptor ->
      if (!allowLoadUnloadWithoutRestart(dependencyDescriptor, pluginDescriptor)) canUnload = false
      canUnload
    }

    return canUnload
  }

  private fun processOptionalDependenciesOnPlugin(
    pluginDescriptor: IdeaPluginDescriptorImpl,
    callback: (descriptorWithDependency: IdeaPluginDescriptorImpl, dependencyDescriptor: IdeaPluginDescriptorImpl) -> Boolean
  ) {
    val pluginXmlFactory = PluginXmlFactory()
    for (pluginWithDependency in PluginManager.getPlugins().filter { pluginDescriptor.pluginId in it.optionalDependentPluginIds }.filterIsInstance<IdeaPluginDescriptorImpl>()) {
      val dependencyConfigFile = pluginWithDependency.findOptionalDependencyConfigFile(pluginDescriptor.pluginId)
      if (dependencyConfigFile == null) continue

      val pathResolver = PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER
      val element = try {
        pathResolver.resolvePath(pluginWithDependency.basePath, dependencyConfigFile, pluginXmlFactory)
      }
      catch (e: Exception) {
        LOG.info("Can't resolve optional dependency on plugin being loaded/unloaded: config file $dependencyConfigFile, error ${e.message}")
        continue
      }

      val dependencyDescriptor = IdeaPluginDescriptorImpl(pluginWithDependency.pluginPath, false)
      val listContext = DescriptorListLoadingContext.createSingleDescriptorContext(PluginManagerCore.disabledPlugins())
      val context = DescriptorLoadingContext(listContext, false, false, pathResolver)
      if (!dependencyDescriptor.readExternal(element, pluginWithDependency.basePath, pathResolver, context, pluginWithDependency)) {
        LOG.info("Can't read descriptor $dependencyConfigFile for optional dependency of plugin being loaded/unloaded")
        continue
      }
      if (!callback(pluginWithDependency, dependencyDescriptor)) break
    }
  }

  private fun findPluginExtensionPoint(pluginDescriptor: IdeaPluginDescriptorImpl, epName: String): ExtensionPointImpl<*>? {
    return findContainerExtensionPoint(pluginDescriptor.app, epName) ?:
           findContainerExtensionPoint(pluginDescriptor.project, epName) ?:
           findContainerExtensionPoint(pluginDescriptor.module, epName)
  }

  private fun findContainerExtensionPoint(containerDescriptor: ContainerDescriptor, epName: String): ExtensionPointImpl<*>? {
    val extensionPoints = containerDescriptor.extensionPoints ?: return null
    return extensionPoints.find { it.name == epName }
  }

  /**
   * Checks if the plugin can be loaded/unloaded immediately when the corresponding action is invoked in the
   * plugins settings, without pressing the Apply button.
   */
  @JvmStatic
  fun allowLoadUnloadSynchronously(pluginDescriptor: IdeaPluginDescriptorImpl): Boolean {
    val extensions = pluginDescriptor.extensions
    if (extensions != null && !extensions.all {
        it.key == UIThemeProvider.EP_NAME.name ||
        it.key == BundledKeymapBean.EP_NAME.name ||
        it.key == BundledKeymapProvider.EP_NAME.name }) {
      return false
    }
    return hasNoComponents(pluginDescriptor) && pluginDescriptor.actionDescriptionElements.isNullOrEmpty()
  }

  private fun hasNoComponents(pluginDescriptor: IdeaPluginDescriptorImpl): Boolean {
    return isUnloadSafe(pluginDescriptor.appContainerDescriptor) &&
           isUnloadSafe(pluginDescriptor.projectContainerDescriptor) &&
           isUnloadSafe(pluginDescriptor.moduleContainerDescriptor)
  }

  private fun isUnloadSafe(containerDescriptor: ContainerDescriptor): Boolean {
    return containerDescriptor.components.isNullOrEmpty()
  }

  @JvmStatic
  @JvmOverloads
  fun unloadPluginWithProgress(parentComponent: JComponent?,
                               pluginDescriptor: IdeaPluginDescriptorImpl,
                               disable: Boolean = false,
                               isUpdate: Boolean = false): Boolean {
    var result = false
    runInAutoSaveDisabledMode {
      val saveAndSyncHandler = SaveAndSyncHandler.getInstance()
      saveAndSyncHandler.saveSettingsUnderModalProgress(ApplicationManager.getApplication())
      for (openProject in ProjectManager.getInstance().openProjects) {
        saveAndSyncHandler.saveSettingsUnderModalProgress(openProject)
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
      application.runWriteAction {
        try {
          application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).beforePluginUnload(pluginDescriptor, isUpdate)

          processOptionalDependenciesOnPlugin(pluginDescriptor) { _, dependencyDescriptor ->
            unloadPluginDescriptor(dependencyDescriptor, dependencyDescriptor)
            true
          }

          if (!pluginDescriptor.useIdeaClassLoader) {
            if (loadedPluginDescriptor.pluginClassLoader is PluginClassLoader || 
                ApplicationManager.getApplication().isUnitTestMode) {
              IconLoader.detachClassLoader(loadedPluginDescriptor.pluginClassLoader)
            }
          }

          pluginDescriptor.optionalConfigs?.forEach { (pluginId, optionalDescriptors) ->
            if (isPluginLoaded(pluginId)) {
              for (optionalDescriptor in optionalDescriptors) {
                unloadPluginDescriptor(optionalDescriptor, loadedPluginDescriptor)
              }
            }
          }
          unloadPluginDescriptor(pluginDescriptor, loadedPluginDescriptor)

          for (project in ProjectManager.getInstance().openProjects) {
            (CachedValuesManager.getManager(project) as CachedValuesManagerImpl).clearCachedValues()
          }
          jdomSerializer.clearSerializationCaches()
          BeanBinding.clearSerializationCaches()
          Disposer.clearDisposalTraces()  // ensure we don't have references to plugin classes in disposal backtraces

          if (disable) {
            // Update list of disabled plugins
            PluginManagerCore.setPlugins(PluginManagerCore.getPlugins())
          }
          else {
            PluginManagerCore.setPlugins(ArrayUtil.remove(PluginManagerCore.getPlugins(), loadedPluginDescriptor))
          }

        }
        finally {
          val disposable = pluginDisposables.remove(pluginDescriptor)
          if (disposable != null) {
            Disposer.dispose(disposable)
          }
          application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).pluginUnloaded(pluginDescriptor, isUpdate)
        }
      }
    } catch (e: Exception) {
      Logger.getInstance(DynamicPlugins.javaClass).error(e)
    } finally {
      UIUtil.dispatchAllInvocationEvents()

      val classLoaderUnloaded = loadedPluginDescriptor.unloadClassLoader()
      if (!classLoaderUnloaded) {
        if (Registry.`is`("ide.plugins.snapshot.on.unload.fail") && MemoryDumpHelper.memoryDumpAvailable() && !ApplicationManager.getApplication().isUnitTestMode) {
          val snapshotFolder = System.getProperty("snapshots.path", SystemProperties.getUserHome())
          val snapshotDate = SimpleDateFormat("dd.MM.yyyy_HH.mm.ss").format(Date())
          val snapshotPath = "$snapshotFolder/unload-${pluginDescriptor.pluginId}-$snapshotDate.hprof"
          MemoryDumpHelper.captureMemoryDump(snapshotPath)
          GROUP.createNotification("Captured memory snapshot on plugin unload fail: $snapshotPath",
                                   NotificationType.WARNING).notify(null)
        }
        LOG.info("Plugin ${pluginDescriptor.pluginId} is not unload-safe because class loader cannot be unloaded")
      }

      return classLoaderUnloaded
    }
  }

  private fun unloadPluginDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl,
                                     loadedPluginDescriptor: IdeaPluginDescriptorImpl) {
    val application = ApplicationManager.getApplication() as ApplicationImpl
    (ActionManager.getInstance() as ActionManagerImpl).unloadActions(pluginDescriptor)

    val openProjects = ProjectManager.getInstance().openProjects

    val unloadListeners = mutableListOf<Runnable>()
    pluginDescriptor.extensions?.let { extensions ->
      for ((epName, epExtensions) in extensions) {
        val appEp = Extensions.getRootArea().getExtensionPointIfRegistered<Any>(epName) as ExtensionPointImpl<*>?
        if (appEp != null) {
          appEp.unregisterExtensions(epExtensions, unloadListeners)
        }
        else {
          for (openProject in openProjects) {
            val projectEp = openProject.extensionArea.getExtensionPointIfRegistered<Any>(epName) as ExtensionPointImpl<*>?
            projectEp?.unregisterExtensions(epExtensions, unloadListeners)
          }
        }
      }
    }
    for (unloadListener in unloadListeners) {
      unloadListener.run()
    }

    pluginDescriptor.app.extensionPoints?.let {
      for (point in it) {
        val rootArea = ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl
        rootArea.unregisterExtensionPoint(point.name)
      }
    }
    pluginDescriptor.project.extensionPoints?.let {
      for (point in it) {
        val extensionPointName = point.name
        for (openProject in openProjects) {
          openProject.extensionArea.unregisterExtensionPoint(extensionPointName)
        }
      }
    }

    val appServiceInstances = application.unloadServices(pluginDescriptor.app)
    for (appServiceInstance in appServiceInstances) {
      application.stateStore.unloadComponent(appServiceInstance)
    }
    (application.messageBus as MessageBusImpl).unsubscribePluginListeners(loadedPluginDescriptor)

    for (project in openProjects) {
      val projectServiceInstances = (project as ProjectImpl).unloadServices(pluginDescriptor.project)
      for (projectServiceInstance in projectServiceInstances) {
        project.stateStore.unloadComponent(projectServiceInstance)
      }

      for (module in ModuleManager.getInstance(project).modules) {
        val moduleServiceInstances = (module as PlatformComponentManagerImpl).unloadServices(pluginDescriptor.module)
        for (moduleServiceInstance in moduleServiceInstances) {
          module.stateStore.unloadComponent(moduleServiceInstance)
        }
      }
      (project.messageBus as MessageBusImpl).unsubscribePluginListeners(loadedPluginDescriptor)
    }
  }

  @JvmStatic
  fun loadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl, wasDisabled: Boolean) {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      PluginManagerCore.initClassLoader(pluginDescriptor)
    }
    val application = ApplicationManager.getApplication() as ApplicationImpl
    application.runWriteAction {
      application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).beforePluginLoaded(pluginDescriptor)

      try {
        loadPluginDescriptor(pluginDescriptor, pluginDescriptor)

        processOptionalDependenciesOnPlugin(pluginDescriptor) { descriptorWithDependency, dependencyDescriptor ->
          loadPluginDescriptor(descriptorWithDependency, dependencyDescriptor)
          true
        }

        for (openProject in ProjectManager.getInstance().openProjects) {
          (CachedValuesManager.getManager(openProject) as CachedValuesManagerImpl).clearCachedValues()
        }

        if (wasDisabled) {
          // Update list of disabled plugins
          (PluginManagerCore.getPlugin(pluginDescriptor.pluginId) as? IdeaPluginDescriptorImpl)?.setLoader(
            pluginDescriptor.pluginClassLoader)
          PluginManagerCore.setPlugins(PluginManagerCore.getPlugins())
        }
        else {
          PluginManagerCore.setPlugins(ArrayUtil.mergeArrays(PluginManagerCore.getPlugins(), arrayOf(pluginDescriptor)))
        }
      }
      finally {
        application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).pluginLoaded(pluginDescriptor)
      }
    }
  }

  private fun loadPluginDescriptor(baseDescriptor: IdeaPluginDescriptorImpl, pluginDescriptor: IdeaPluginDescriptorImpl) {
    val application = ApplicationManager.getApplication() as ApplicationImpl
    val listenerCallbacks = arrayListOf<Runnable>()
    val pluginsToLoad = mutableListOf(PlatformComponentManagerImpl.DescriptorToLoad(pluginDescriptor, baseDescriptor))
    pluginDescriptor.optionalConfigs?.forEach { (pluginId, optionalDescriptors) ->
      if (isPluginLoaded(pluginId)) {
        pluginsToLoad.addAll(optionalDescriptors.map { optionalDescriptor -> PlatformComponentManagerImpl.DescriptorToLoad(optionalDescriptor, baseDescriptor) })
      }
    }
    application.registerComponents(pluginsToLoad, listenerCallbacks)
    for (openProject in ProjectManager.getInstance().openProjects) {
      (openProject as ProjectImpl).registerComponents(pluginsToLoad, listenerCallbacks)
      for (module in ModuleManager.getInstance(openProject).modules) {
        (module as PlatformComponentManagerImpl).registerComponents(pluginsToLoad, listenerCallbacks)
      }
    }
    listenerCallbacks.forEach(Runnable::run)
    for (descriptorToLoad in pluginsToLoad) {
      (ActionManager.getInstance() as ActionManagerImpl).registerPluginActions(baseDescriptor, descriptorToLoad.descriptor.actionDescriptionElements, false)
    }
  }

  private fun isPluginLoaded(pluginId: PluginId?) =
    PluginManagerCore.getLoadedPlugins().any { it.pluginId == pluginId }

  @JvmStatic
  fun pluginDisposable(clazz: Class<*>): Disposable? {
    val classLoader = clazz.classLoader
    if (classLoader is PluginClassLoader) {
      val pluginDescriptor = classLoader.pluginDescriptor
      if (pluginDescriptor != null) {
        return pluginDisposables[pluginDescriptor]
      }
    }
    return null
  }

  @JvmStatic
  fun pluginDisposableWrapper(clazz: Class<*>, defaultValue: Disposable): Disposable {
    val pluginDisposable = pluginDisposable(clazz)
    if (pluginDisposable != null) {
      val result = Disposer.newDisposable()
      Disposer.register(pluginDisposable, result)
      Disposer.register(defaultValue, result)
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
