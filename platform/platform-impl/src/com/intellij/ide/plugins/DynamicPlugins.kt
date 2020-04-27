// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.fasterxml.jackson.databind.type.TypeFactory
import com.intellij.application.options.RegistryManager
import com.intellij.configurationStore.StoreUtil.Companion.saveDocumentsAndProjectsAndApp
import com.intellij.configurationStore.jdomSerializer
import com.intellij.configurationStore.runInAutoSaveDisabledMode
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.ui.TopHitCache
import com.intellij.ide.ui.UIThemeProvider
import com.intellij.idea.IdeaLogger
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.lang.Language
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.notification.impl.NotificationsManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.keymap.impl.BundledKeymapBean
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.PotemkinProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
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
import kotlin.collections.component1
import kotlin.collections.component2

class CannotUnloadPluginException(value: String) : ProcessCanceledException(RuntimeException(value))

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
  private val LOG = logger<DynamicPlugins>()
  private val GROUP = NotificationGroup("Dynamic plugin installation", NotificationDisplayType.BALLOON, false)

  @JvmStatic
  @JvmOverloads
  fun allowLoadUnloadWithoutRestart(descriptor: IdeaPluginDescriptorImpl, baseDescriptor: IdeaPluginDescriptorImpl? = null): Boolean {
    val reason = checkCanUnloadWithoutRestart(descriptor, baseDescriptor)
    if (reason != null) {
      LOG.info(reason)
    }
    return reason == null
  }

  @JvmStatic
  @JvmOverloads
  fun checkCanUnloadWithoutRestart(descriptor: IdeaPluginDescriptorImpl, baseDescriptor: IdeaPluginDescriptorImpl? = null): String? {
    if (InstalledPluginsState.getInstance().isRestartRequired) {
      return "Not allowing load/unload without restart because of pending restart operation"
    }

    if (!RegistryManager.getInstance().`is`("ide.plugins.allow.unload")) {
      val canLoadSynchronously = allowLoadUnloadSynchronously(descriptor)
      if (!canLoadSynchronously) {
        return "ide.plugins.allow.unload is disabled and synchronous load/unload is not possible for ${descriptor.pluginId}"
      }
      return null
    }

    val loadedPluginDescriptor = if (descriptor.pluginId == null) null else PluginManagerCore.getPlugin(descriptor.pluginId) as? IdeaPluginDescriptorImpl

    val app = ApplicationManager.getApplication()
    try {
      app.messageBus.syncPublisher(DynamicPluginListener.TOPIC).checkUnloadPlugin(descriptor)
    }
    catch (e: CannotUnloadPluginException) {
      return e.cause?.localizedMessage
    }

    if (!Registry.`is`("ide.plugins.allow.unload.from.sources")) {
      if (loadedPluginDescriptor != null && isPluginOrModuleLoaded(loadedPluginDescriptor.pluginId) && !descriptor.useIdeaClassLoader) {
        val pluginClassLoader = loadedPluginDescriptor.pluginClassLoader
        if (pluginClassLoader !is PluginClassLoader && !app.isUnitTestMode) {
          val loader = baseDescriptor ?: descriptor
          return "Plugin ${loader.pluginId} is not unload-safe because of use of ${pluginClassLoader.javaClass.name} as the default class loader. " +
                   "For example, the IDE is started from the sources with the plugin."
        }
      }
    }

    val extensions = descriptor.extensions
    if (extensions != null) {
      val openedProjects = ProjectUtil.getOpenProjects()
      val anyProject = openedProjects.firstOrNull() ?: ProjectManager.getInstance().defaultProject
      val anyModule = openedProjects.firstOrNull()?.let { ModuleManager.getInstance(it).modules.firstOrNull() }

      for (epName in extensions.keys) {
        val pluginExtensionPoint = findPluginExtensionPoint(baseDescriptor ?: descriptor, epName)
        if (pluginExtensionPoint != null) {
          // descriptor.pluginId is null when we check the optional dependencies of the plugin which is being loaded
          // if an optional dependency of a plugin extends a non-dynamic EP of that plugin, it shouldn't prevent plugin loading
          if (baseDescriptor != null && descriptor.pluginId != null && !pluginExtensionPoint.isDynamic) {
            return "Plugin ${baseDescriptor.pluginId} is not unload-safe because of use of non-dynamic EP $epName" +
                     " in optional dependency on it: ${descriptor.pluginId}"
          }
          continue
        }

        @Suppress("RemoveExplicitTypeArguments")
        val ep =
          app.extensionArea.getExtensionPointIfRegistered<Any>(epName)
          ?: anyProject.extensionArea.getExtensionPointIfRegistered<Any>(epName)
          ?: anyModule?.extensionArea?.getExtensionPointIfRegistered<Any>(epName)
        if (ep != null) {
          if (!ep.isDynamic) {
            if (baseDescriptor != null) {
              return "Plugin ${baseDescriptor.pluginId} is not unload-safe because of use of non-dynamic EP $epName in optional dependencies on it"
            }
            else {
              return "Plugin ${descriptor.pluginId} is not unload-safe because of extension to non-dynamic EP $epName"
            }
          }
          continue
        }

        if (baseDescriptor != null) {
          val baseEP = findPluginExtensionPoint(baseDescriptor, epName)
          if (baseEP != null) {
            if (!baseEP.isDynamic) {
              return "Plugin ${baseDescriptor.pluginId} is not unload-safe because of use of non-dynamic EP $epName in optional dependencies on it"
            }
            continue
          }
        }

        return "Plugin ${descriptor.pluginId} is not unload-safe because of unresolved extension $epName"
      }
    }

    val pluginId = loadedPluginDescriptor?.pluginId ?: baseDescriptor?.pluginId
    checkNoComponentsOrServiceOverrides(pluginId, descriptor)?.let { return it }
    ActionManagerImpl.checkUnloadActions(descriptor)?.let { return it }

    descriptor.pluginDependencies?.forEach { dependency ->
      if (isPluginOrModuleLoaded(dependency.id)) {
        val message = checkCanUnloadWithoutRestart(dependency.subDescriptor ?: return@forEach, descriptor)
        if (message != null) {
          return message
        }
      }
    }

    var optionalDependencyMessage: String? = null
    // if not a sub plugin descriptor, then check that any dependent plugin also reloadable
    if (descriptor.pluginId != null) {
      processOptionalDependenciesOnPlugin(descriptor.pluginId) { _, subDescriptor ->
        if (subDescriptor != null) {
          optionalDependencyMessage = checkCanUnloadWithoutRestart(subDescriptor, descriptor)
        }
        optionalDependencyMessage == null
      }
    }

    return optionalDependencyMessage
  }

  private fun processOptionalDependenciesOnPlugin(dependencyPluginId: PluginId,
                                                  processor: (mainDescriptor: IdeaPluginDescriptorImpl, subDescriptor: IdeaPluginDescriptorImpl?) -> Boolean) {
    for (descriptor in PluginManagerCore.getLoadedPlugins(null)) {
      for (dependency in (descriptor.pluginDependencies ?: continue)) {
        if (!dependency.isOptional || dependency.id != dependencyPluginId) {
          continue
        }

        val subDescriptor = dependency.configFile?.let { loadOptionalDependencyDescriptor(descriptor, it) }
        if (!processor(descriptor, subDescriptor)) {
          break
        }
      }
    }
  }

  private fun loadOptionalDependencyDescriptor(descriptor: IdeaPluginDescriptorImpl, dependencyConfigFile: String): IdeaPluginDescriptorImpl? {
    val pluginXmlFactory = PluginXmlFactory()
    val listContext = DescriptorListLoadingContext.createSingleDescriptorContext(PluginManagerCore.disabledPlugins())
    val context = DescriptorLoadingContext(listContext, false, false, PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER)
    val pathResolver = PluginEnabler.createPathResolverForPlugin(descriptor, context)
    val element = try {
      val jarPair = URLUtil.splitJarUrl(descriptor.basePath.toUri().toString())
      val newBasePath = if (jarPair == null) {
        descriptor.basePath
      }
      else {
        context.open(Paths.get(jarPair.first)).getPath(jarPair.second)
      }
      pathResolver.resolvePath(newBasePath, dependencyConfigFile, pluginXmlFactory)
    }
    catch (e: Exception) {
      LOG.error("Can't resolve optional dependency on plugin being loaded/unloaded: config file $dependencyConfigFile", e)
      return null
    }
    finally {
      context.close()
    }

    val subDescriptor = IdeaPluginDescriptorImpl(descriptor.pluginPath, descriptor.basePath, false)
    if (!subDescriptor.readExternal(element, pathResolver, context, descriptor)) {
      LOG.error("Can't read descriptor $dependencyConfigFile for optional dependency of plugin being loaded/unloaded")
      return null
    }
    return subDescriptor
  }

  private fun findPluginExtensionPoint(pluginDescriptor: IdeaPluginDescriptorImpl, epName: String): ExtensionPointImpl<*>? {
    return findContainerExtensionPoint(pluginDescriptor.app, epName)
           ?: findContainerExtensionPoint(pluginDescriptor.project, epName)
           ?: findContainerExtensionPoint(pluginDescriptor.module, epName)
  }

  private fun findContainerExtensionPoint(containerDescriptor: ContainerDescriptor, epName: String): ExtensionPointImpl<*>? {
    return containerDescriptor.extensionPoints?.find { it.name == epName }
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
        it.key == BundledKeymapBean.EP_NAME.name}) {
      return false
    }
    return checkNoComponentsOrServiceOverrides(pluginDescriptor.pluginId, pluginDescriptor) == null && pluginDescriptor.actionDescriptionElements.isNullOrEmpty()
  }

  private fun checkNoComponentsOrServiceOverrides(pluginId: PluginId?, pluginDescriptor: IdeaPluginDescriptorImpl): String? =
    checkNoComponentsOrServiceOverrides(pluginId, pluginDescriptor.appContainerDescriptor) ?:
    checkNoComponentsOrServiceOverrides(pluginId, pluginDescriptor.projectContainerDescriptor) ?:
    checkNoComponentsOrServiceOverrides(pluginId, pluginDescriptor.moduleContainerDescriptor)

  private fun checkNoComponentsOrServiceOverrides(pluginId: PluginId?, containerDescriptor: ContainerDescriptor): String? {
    if (!containerDescriptor.components.isNullOrEmpty()) {
      return "Plugin $pluginId is not unload-safe because it declares components"
    }
    if (containerDescriptor.services?.any { it.overrides } == true) {
      return "Plugin $pluginId is not unload-safe because it overrides services"
    }
    return null
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
          processOptionalDependenciesOnPlugin(pluginDescriptor.pluginId) { mainDescriptor, subDescriptor ->
            if (subDescriptor != null) {
              unloadPluginDescriptor(subDescriptor, mainDescriptor)
            }

            var detached: Boolean? = false
            if (loadedPluginDescriptor.pluginClassLoader is PluginClassLoader) {
              detached = (mainDescriptor.pluginClassLoader as? PluginClassLoader)?.detachParent(loadedPluginDescriptor.pluginClassLoader)
            }
            if (detached != true) {
              LOG.info("Failed to detach classloader of ${loadedPluginDescriptor.pluginId} from classloader of ${mainDescriptor.pluginId}")
            }
            true
          }

          if (!pluginDescriptor.useIdeaClassLoader) {
            if (loadedPluginDescriptor.pluginClassLoader is PluginClassLoader) {
              IconLoader.detachClassLoader(loadedPluginDescriptor.pluginClassLoader)
              Language.unregisterLanguages(loadedPluginDescriptor.pluginClassLoader)
            }
          }

          pluginDescriptor.pluginDependencies?.forEach { dependency ->
            if (isPluginOrModuleLoaded(dependency.id)) {
              unloadPluginDescriptor(dependency.subDescriptor ?: return@forEach, loadedPluginDescriptor)
            }
          }
          unloadPluginDescriptor(pluginDescriptor, loadedPluginDescriptor)

          for (project in ProjectUtil.getOpenProjects()) {
            (project.getServiceIfCreated(CachedValuesManager::class.java) as CachedValuesManagerImpl?)?.clearCachedValues()
          }
          jdomSerializer.clearSerializationCaches()
          BeanBinding.clearSerializationCaches()
          TypeFactory.defaultInstance().clearCache()
          @Suppress("DEPRECATION")
          com.intellij.openapi.util.DefaultJDOMExternalizer.clearFieldCache()
          application.getServiceIfCreated(TopHitCache::class.java)?.clear()
          Disposer.clearDisposalTraces()  // ensure we don't have references to plugin classes in disposal backtraces
          PresentationFactory.clearPresentationCaches()
          IdeaLogger.ourErrorsOccurred = null   // ensure we don't have references to plugin classes in exception stacktraces
          ActionToolbarImpl.updateAllToolbarsImmediately()
          (NotificationsManager.getNotificationsManager() as NotificationsManagerImpl).expireAll()

          for (project in ProjectUtil.getOpenProjects()) {
            (project.messageBus as MessageBusImpl).clearPublisherCache()
          }
          (ApplicationManager.getApplication().messageBus as MessageBusImpl).clearPublisherCache()

          if (disable) {
            // update list of disabled plugins
            PluginManager.getInstance().setPlugins(PluginManagerCore.getPlugins().asList())
          }
          else {
            PluginManager.getInstance().setPlugins(PluginManagerCore.getPlugins().asSequence().minus(loadedPluginDescriptor).toList())
          }
        }
        finally {
          application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).pluginUnloaded(pluginDescriptor, isUpdate)
        }
      }
    }
    catch (e: Exception) {
      logger<DynamicPlugins>().error(e)
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
          LOG.info("Plugin ${pluginDescriptor.pluginId} is not unload-safe because class loader cannot be unloaded. Memory snapshot created at $snapshotPath")
        }
        else {
          LOG.info("Plugin ${pluginDescriptor.pluginId} is not unload-safe because class loader cannot be unloaded")
        }
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

  // PluginId cannot be used to unload related resources because one plugin descriptor may consist of several sub descriptors, each of them depends on presense of another plugin,
  // here not the whole plugin is unloaded, but only one part.
  private fun unloadPluginDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl, loadedPluginDescriptor: IdeaPluginDescriptorImpl) {
    val application = ApplicationManager.getApplication() as ApplicationImpl
    (ActionManager.getInstance() as ActionManagerImpl).unloadActions(pluginDescriptor)

    val openProjects = ProjectUtil.getOpenProjects()
    val appExtensionArea = application.extensionArea
    val unloadListeners = mutableListOf<Runnable>()
    pluginDescriptor.extensions?.let { extensions ->
      for ((epName, epExtensions) in extensions) {
        val isAppLevelEp = appExtensionArea.unregisterExtensions(epName, loadedPluginDescriptor, epExtensions, unloadListeners)
        if (!isAppLevelEp) {
          for (project in openProjects) {
            val isProjectLevelEp = (project.extensionArea as ExtensionsAreaImpl).unregisterExtensions(epName, loadedPluginDescriptor, epExtensions, unloadListeners)
            if (!isProjectLevelEp) {
              for (module in ModuleManager.getInstance(project).modules) {
                (module.extensionArea as ExtensionsAreaImpl).unregisterExtensions(epName, loadedPluginDescriptor, epExtensions, unloadListeners)
              }
            }
          }
        }
      }

      // todo clear extension cache granularly
      appExtensionArea.clearUserCache()
      for (project in openProjects) {
        (project.extensionArea as ExtensionsAreaImpl).clearUserCache()
      }
    }

    for (unloadListener in unloadListeners) {
      unloadListener.run()
    }

    // first, reset all plugin extension points before unregistering, so that listeners don't see plugin in semi-torn-down state
    processExtensionPoints(pluginDescriptor, openProjects) { points, area -> area.resetExtensionPoints(points) }
    // unregister plugin extension points
    processExtensionPoints(pluginDescriptor, openProjects) { points, area -> area.unregisterExtensionPoints(points) }

    val pluginId = pluginDescriptor.pluginId ?: loadedPluginDescriptor.pluginId
    application.unloadServices(pluginDescriptor.appContainerDescriptor.getServices(), pluginId)
    (application.messageBus as MessageBusImpl).unsubscribePluginListeners(pluginDescriptor)

    for (project in openProjects) {
      (project as ProjectImpl).unloadServices(pluginDescriptor.projectContainerDescriptor.getServices(), pluginId)
      val moduleServices = pluginDescriptor.moduleContainerDescriptor.getServices()
      for (module in ModuleManager.getInstance(project).modules) {
        (module as ComponentManagerImpl).unloadServices(moduleServices, pluginId)
      }
      (project.messageBus as MessageBusImpl).unsubscribePluginListeners(pluginDescriptor)
    }
  }

  private inline fun processExtensionPoints(pluginDescriptor: IdeaPluginDescriptorImpl,
                                            projects: Array<Project>,
                                            processor: (points: List<ExtensionPointImpl<*>>, area: ExtensionsAreaImpl) -> Unit) {
    pluginDescriptor.appContainerDescriptor.extensionPoints?.let {
      processor(it, ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
    }
    pluginDescriptor.projectContainerDescriptor.extensionPoints?.let { extensionPoints ->
      for (project in projects) {
        processor(extensionPoints, project.extensionArea as ExtensionsAreaImpl)
      }
    }
    pluginDescriptor.moduleContainerDescriptor.extensionPoints?.let { extensionPoints ->
      for (project in projects) {
        for (module in ModuleManager.getInstance(project).modules) {
          processor(extensionPoints, module.extensionArea as ExtensionsAreaImpl)
        }
      }
    }
  }

  @JvmStatic
  fun loadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl) {
    val app = ApplicationManager.getApplication() as ApplicationImpl
    if (!app.isUnitTestMode) {
      PluginManagerCore.initClassLoader(pluginDescriptor)
    }
    app.messageBus.syncPublisher(DynamicPluginListener.TOPIC).beforePluginLoaded(pluginDescriptor)
    app.runWriteAction {
      try {
        loadPluginDescriptor(pluginDescriptor, pluginDescriptor, app)

        processOptionalDependenciesOnPlugin(pluginDescriptor.pluginId) { loadedDescriptorOfDependency, fullDescriptor ->
          if (pluginDescriptor.pluginClassLoader is PluginClassLoader) {
            (loadedDescriptorOfDependency.pluginClassLoader as? PluginClassLoader)?.attachParent(pluginDescriptor.pluginClassLoader)
          }

          if (fullDescriptor != null) {
            loadPluginDescriptor(loadedDescriptorOfDependency, fullDescriptor, app)
          }
          true
        }

        for (openProject in ProjectUtil.getOpenProjects()) {
          (CachedValuesManager.getManager(openProject) as CachedValuesManagerImpl).clearCachedValues()
        }

        var foundExistingPlugin = false
        val newPlugins = PluginManagerCore.getPlugins().map {
          if (it.pluginId == pluginDescriptor.pluginId) {
            foundExistingPlugin = true
            pluginDescriptor
          } else {
            it
          }
        }

        if (foundExistingPlugin) {
          PluginManager.getInstance().setPlugins(newPlugins)
        }
        else {
          PluginManager.getInstance().setPlugins(PluginManagerCore.getPlugins().asSequence().plus(pluginDescriptor).toList())
        }
        val fuData = FeatureUsageData().addPluginInfo(getPluginInfoByDescriptor(pluginDescriptor))
        FUCounterUsageLogger.getInstance().logEvent("plugins.dynamic", "load", fuData)
      }
      finally {
        app.messageBus.syncPublisher(DynamicPluginListener.TOPIC).pluginLoaded(pluginDescriptor)
      }
    }
  }

  private fun loadPluginDescriptor(baseDescriptor: IdeaPluginDescriptorImpl,
                                   fullyLoadedBaseDescriptor: IdeaPluginDescriptorImpl,
                                   app: ComponentManagerImpl) {
    val pluginsToLoad = mutableListOf(DescriptorToLoad(fullyLoadedBaseDescriptor, baseDescriptor))
    fullyLoadedBaseDescriptor.pluginDependencies?.forEach { dependency ->
      if (isPluginOrModuleLoaded(dependency.id)) {
        pluginsToLoad.add(DescriptorToLoad(dependency.subDescriptor ?: return@forEach, baseDescriptor))
      }
    }

    val listenerCallbacks = mutableListOf<Runnable>()
    app.registerComponents(pluginsToLoad, listenerCallbacks)
    for (openProject in ProjectUtil.getOpenProjects()) {
      (openProject as ProjectImpl).registerComponents(pluginsToLoad, listenerCallbacks)
      for (module in ModuleManager.getInstance(openProject).modules) {
        (module as ComponentManagerImpl).registerComponents(pluginsToLoad, listenerCallbacks)
      }
    }

    val actionManager = ActionManager.getInstance() as ActionManagerImpl
    for (descriptorToLoad in pluginsToLoad) {
      actionManager.registerPluginActions(baseDescriptor, descriptorToLoad.descriptor.actionDescriptionElements, false)
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
  fun onPluginUnload(parentDisposable: Disposable, callback: Runnable) {
    ApplicationManager.getApplication().messageBus.connect(parentDisposable).subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        callback.run()
      }
    })
  }
}