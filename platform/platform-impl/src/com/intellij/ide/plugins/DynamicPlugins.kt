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
import com.intellij.openapi.actionSystem.AnAction
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
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.objectTree.ThrowableInterner
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.psi.util.CachedValuesManager
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.serviceContainer.ComponentManagerImpl.DescriptorToLoad
import com.intellij.util.CachedValuesManagerImpl
import com.intellij.util.MemoryDumpHelper
import com.intellij.util.SystemProperties
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.URLUtil
import com.intellij.util.messages.Topic
import com.intellij.util.messages.impl.MessageBusEx
import com.intellij.util.xmlb.BeanBinding
import java.awt.Window
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.function.Predicate
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
    @JvmField val TOPIC = Topic(DynamicPluginListener::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN)
  }
}

object DynamicPlugins {
  private val LOG = logger<DynamicPlugins>()
  private val GROUP = NotificationGroup("Dynamic plugin installation", NotificationDisplayType.BALLOON, false)

  private val classloadersFromUnloadedPlugins = ContainerUtil.createWeakValueMap<PluginId, PluginClassLoader>()

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
  fun allowLoadUnloadAllWithoutRestart(descriptors: List<IdeaPluginDescriptorImpl>): Boolean {
    return descriptors.all { descriptor ->
      checkCanUnloadWithoutRestart(descriptor, context = descriptors).also { message -> message?.let { LOG.info(it) } } == null
    }
  }

  /**
   * @param context Plugins which are being loaded at the same time as [descriptor]
   */
  @JvmStatic
  @JvmOverloads
  fun checkCanUnloadWithoutRestart(
    descriptor: IdeaPluginDescriptorImpl,
    baseDescriptor: IdeaPluginDescriptorImpl? = null,
    context: List<IdeaPluginDescriptorImpl> = emptyList(),
    checkImplementationDetailDependencies: Boolean = true
  ): String? {
    if (InstalledPluginsState.getInstance().isRestartRequired) {
      return "Not allowing load/unload without restart because of pending restart operation"
    }
    if (classloadersFromUnloadedPlugins[descriptor.pluginId] != null) {
      return "Not allowing load/unload of ${descriptor.pluginId} because of incomplete previous unload operation for that plugin"
    }

    if (!RegistryManager.getInstance().`is`("ide.plugins.allow.unload")) {
      val canLoadSynchronously = allowLoadUnloadSynchronously(descriptor)
      if (!canLoadSynchronously) {
        return "ide.plugins.allow.unload is disabled and synchronous load/unload is not possible for ${descriptor.pluginId}"
      }
      return null
    }

    if (descriptor.isRequireRestart) {
      return "Plugin ${descriptor.pluginId} is explicitly marked as requiring restart"
    }

    val loadedPluginDescriptor = if (descriptor.pluginId == null) null else PluginManagerCore.getPlugin(descriptor.pluginId) as? IdeaPluginDescriptorImpl

    val app = ApplicationManager.getApplication()
    try {
      app.messageBus.syncPublisher(DynamicPluginListener.TOPIC).checkUnloadPlugin(descriptor)
    }
    catch (e: CannotUnloadPluginException) {
      return e.cause?.localizedMessage ?: "checkUnloadPlugin listener blocked plugin unload"
    }

    if (!Registry.`is`("ide.plugins.allow.unload.from.sources")) {
      if (loadedPluginDescriptor != null && isPluginOrModuleLoaded(loadedPluginDescriptor.pluginId) && !descriptor.isUseIdeaClassLoader) {
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
        val contextEP = context.asSequence().mapNotNull { contextPlugin -> findPluginExtensionPoint(contextPlugin, epName) }.firstOrNull()
        if (contextEP != null) {
          if (!contextEP.isDynamic) {
            return "Plugin ${descriptor.pluginId} is not unload-safe because of extension to non-dynamic EP $epName"
          }
          continue
        }

        return "Plugin ${descriptor.pluginId} is not unload-safe because of unresolved extension $epName"
      }
    }

    val pluginId = loadedPluginDescriptor?.pluginId ?: baseDescriptor?.pluginId
    checkNoComponentsOrServiceOverrides(pluginId, descriptor)?.let { return it }
    ActionManagerImpl.checkUnloadActions(pluginId, descriptor)?.let { return it }

    descriptor.pluginDependencies?.forEach { dependency ->
      if (isPluginOrModuleLoaded(dependency.id)) {
        val message = checkCanUnloadWithoutRestart(dependency.subDescriptor ?: return@forEach, descriptor, context)
        if (message != null) {
          return message
        }
      }
    }

    var dependencyMessage: String? = null
    // if not a sub plugin descriptor, then check that any dependent plugin also reloadable
    if (descriptor.pluginId != null) {
      processOptionalDependenciesOnPlugin(descriptor.pluginId) { _, subDescriptor ->
        if (subDescriptor != null) {
          dependencyMessage = checkCanUnloadWithoutRestart(subDescriptor, descriptor, context)
        }
        dependencyMessage == null
      }

      if (dependencyMessage == null && checkImplementationDetailDependencies) {
        processImplementationDetailDependenciesOnPlugin(descriptor) { _, fullDescriptor ->
          dependencyMessage = checkCanUnloadWithoutRestart(fullDescriptor, context = context, checkImplementationDetailDependencies = false)
          dependencyMessage == null
        }
      }
    }

    return dependencyMessage
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

  private fun processImplementationDetailDependenciesOnPlugin(pluginDescriptor: IdeaPluginDescriptorImpl,
                                                              processor: (loadedDescriptor: IdeaPluginDescriptorImpl, fullDescriptor: IdeaPluginDescriptorImpl) -> Boolean) {
    PluginManagerCore.processAllBackwardDependencies(pluginDescriptor, false) { loadedDescriptor ->
      if (loadedDescriptor.isImplementationDetail) {
        val fullDescriptor = PluginDescriptorLoader.loadFullDescriptor(loadedDescriptor as IdeaPluginDescriptorImpl)
        if (processor(loadedDescriptor, fullDescriptor)) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
      }
      else {
        FileVisitResult.CONTINUE
      }
    }
  }

  private fun loadOptionalDependencyDescriptor(descriptor: IdeaPluginDescriptorImpl, dependencyConfigFile: String): IdeaPluginDescriptorImpl? {
    val pluginXmlFactory = PluginXmlFactory()
    val listContext = DescriptorListLoadingContext.createSingleDescriptorContext(
      DisabledPluginsState.disabledPlugins())
    val context = DescriptorLoadingContext(listContext, false, false, PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER)
    val pathResolver = PluginDescriptorLoader.createPathResolverForPlugin(descriptor, context)
    try {
      val jarPair = URLUtil.splitJarUrl(descriptor.basePath.toUri().toString())
      val newBasePath = if (jarPair == null) {
        descriptor.basePath
      }
      else {
        context.open(Paths.get(jarPair.first)).getPath(jarPair.second)
      }
      val element = pathResolver.resolvePath(newBasePath, dependencyConfigFile, pluginXmlFactory)
      val subDescriptor = IdeaPluginDescriptorImpl(descriptor.pluginPath, newBasePath, false)
      if (!subDescriptor.readExternal(element, pathResolver, context.parentContext, descriptor)) {
        LOG.error("Can't read descriptor $dependencyConfigFile for optional dependency of plugin being loaded/unloaded")
        return null
      }
      return subDescriptor
    }
    catch (e: Exception) {
      LOG.error("Can't resolve optional dependency on plugin being loaded/unloaded: config file $dependencyConfigFile", e)
      return null
    }
    finally {
      context.close()
    }
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
  fun unloadPluginWithProgress(project: Project? = null,
                               parentComponent: JComponent?,
                               pluginDescriptor: IdeaPluginDescriptorImpl,
                               disable: Boolean = false,
                               isUpdate: Boolean = false): Boolean {
    var result = false
    if (!allowLoadUnloadSynchronously(pluginDescriptor)) {
      runInAutoSaveDisabledMode {
        val saveAndSyncHandler = SaveAndSyncHandler.getInstance()
        saveAndSyncHandler.saveSettingsUnderModalProgress(ApplicationManager.getApplication())
        for (openProject in ProjectUtil.getOpenProjects()) {
          saveAndSyncHandler.saveSettingsUnderModalProgress(openProject)
        }
      }
    }
    val indicator = PotemkinProgress("Unloading plugin ${pluginDescriptor.name}", project, parentComponent, null)
    indicator.runInSwingThread {
      result = unloadPlugin(pluginDescriptor, UnloadPluginOptions(disable, isUpdate, save = false))
    }
    return result
  }

  @JvmStatic
  fun getPluginUnloadingTask(pluginDescriptor: IdeaPluginDescriptorImpl, options: UnloadPluginOptions): Runnable =
    Runnable { unloadPlugin(pluginDescriptor, options) }

  data class UnloadPluginOptions(
    var disable: Boolean = false,
    var isUpdate: Boolean = false,
    var save: Boolean = true,
    var requireMemorySnapshot: Boolean = false,
    var waitForClassloaderUnload: Boolean = false,
    var checkImplementationDetailDependencies: Boolean = true
  ) {
    fun withUpdate(value: Boolean): UnloadPluginOptions { isUpdate = value; return this }
    fun withWaitForClassloaderUnload(value: Boolean): UnloadPluginOptions { waitForClassloaderUnload = value; return this }
    fun withDisable(value: Boolean): UnloadPluginOptions { disable = value; return this }
    fun withRequireMemorySnapshot(value: Boolean): UnloadPluginOptions { requireMemorySnapshot = value; return this }
  }

  @JvmStatic
  fun unloadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl, options: UnloadPluginOptions = UnloadPluginOptions()): Boolean {
    val application = ApplicationManager.getApplication() as ApplicationImpl

    if (options.checkImplementationDetailDependencies) {
      processImplementationDetailDependenciesOnPlugin(pluginDescriptor) { loadedDescriptor, fullDescriptor ->
        loadedDescriptor.isEnabled = false
        unloadPlugin(fullDescriptor, UnloadPluginOptions(disable = true, save = false, waitForClassloaderUnload = false, checkImplementationDetailDependencies = false))
        true
      }
    }

    // The descriptor passed to `unloadPlugin` is the full descriptor loaded from disk, it does not have a classloader.
    // We need to find the real plugin loaded into the current instance and unload its classloader.
    val loadedPluginDescriptor = PluginManagerCore.getPlugin(pluginDescriptor.pluginId) as? IdeaPluginDescriptorImpl
                                 ?: return false

    try {
      if (options.save) {
        saveDocumentsAndProjectsAndApp(true)
      }
      application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).beforePluginUnload(pluginDescriptor, options.isUpdate)
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

          if (!pluginDescriptor.isUseIdeaClassLoader) {
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
          PresentationFactory.clearPresentationCaches()
          ActionToolbarImpl.updateAllToolbarsImmediately()
          (NotificationsManager.getNotificationsManager() as NotificationsManagerImpl).expireAll()

          (ApplicationManager.getApplication().messageBus as MessageBusEx).clearPublisherCache()
          val projectManager = ProjectManagerEx.getInstanceExIfCreated()
          if (projectManager != null && projectManager.isDefaultProjectInitialized) {
            Disposer.dispose(projectManager.defaultProject)
          }

          if (options.disable) {
            // update list of disabled plugins
            PluginManager.getInstance().setPlugins(PluginManagerCore.getPlugins().asList())
          }
          else {
            PluginManager.getInstance().setPlugins(PluginManagerCore.getPlugins().asSequence().minus(loadedPluginDescriptor).toList())
          }
        }
        finally {
          application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).pluginUnloaded(pluginDescriptor, options.isUpdate)
        }
      }
    }
    catch (e: Exception) {
      logger<DynamicPlugins>().error(e)
    }
    finally {
      IdeEventQueue.getInstance().flushQueue()

      // do it after IdeEventQueue.flushQueue() to ensure that Disposer.isDisposed(...) works as expected in flushed tasks.
      Disposer.clearDisposalTraces()   // ensure we don't have references to plugin classes in disposal backtraces
      ThrowableInterner.clearInternedBacktraces()
      IdeaLogger.ourErrorsOccurred = null   // ensure we don't have references to plugin classes in exception stacktraces
      clearTemporaryLostComponent()

      if (ApplicationManager.getApplication().isUnitTestMode && loadedPluginDescriptor.pluginClassLoader !is PluginClassLoader) {
        return true
      }

      classloadersFromUnloadedPlugins[pluginDescriptor.pluginId] = loadedPluginDescriptor.pluginClassLoader as? PluginClassLoader
      val checkClassLoaderUnload = options.waitForClassloaderUnload || Registry.`is`("ide.plugins.snapshot.on.unload.fail") || options.requireMemorySnapshot
      val timeout = if (checkClassLoaderUnload)
        Registry.intValue("ide.plugins.unload.timeout", 5000)
      else
        0
      var classLoaderUnloaded = loadedPluginDescriptor.unloadClassLoader(timeout)

      if (!classLoaderUnloaded) {
        InstalledPluginsState.getInstance().isRestartRequired = true

        if (((Registry.`is`("ide.plugins.snapshot.on.unload.fail") && !ApplicationManager.getApplication().isUnitTestMode) || options.requireMemorySnapshot) && MemoryDumpHelper.memoryDumpAvailable()) {
          val snapshotFolder = System.getProperty("memory.snapshots.path", SystemProperties.getUserHome())
          val snapshotDate = SimpleDateFormat("dd.MM.yyyy_HH.mm.ss").format(Date())
          val snapshotPath = "$snapshotFolder/unload-${pluginDescriptor.pluginId}-$snapshotDate.hprof"
          MemoryDumpHelper.captureMemoryDump(snapshotPath)
          if (classloadersFromUnloadedPlugins[pluginDescriptor.pluginId] == null) {
            LOG.info("Successfully unloaded plugin ${pluginDescriptor.pluginId} (classloader collected during memory snapshot generation)")
            FileUtil.asyncDelete(File(snapshotPath))
            classLoaderUnloaded = true
          }
          notify("Captured memory snapshot on plugin unload fail: $snapshotPath", NotificationType.WARNING)
          LOG.info("Plugin ${pluginDescriptor.pluginId} is not unload-safe because class loader cannot be unloaded. Memory snapshot created at $snapshotPath")
        }
        else {
          LOG.info("Plugin ${pluginDescriptor.pluginId} is not unload-safe because class loader cannot be unloaded")
        }
      }
      else {
        LOG.info("Successfully unloaded plugin ${pluginDescriptor.pluginId} (classloader unload checked=$checkClassLoaderUnload)")
      }

      val eventId = if (classLoaderUnloaded) "unload.success" else "unload.fail"
      val fuData = FeatureUsageData().addPluginInfo(getPluginInfoByDescriptor(loadedPluginDescriptor))
      FUCounterUsageLogger.getInstance().logEvent("plugins.dynamic", eventId, fuData)

      return classLoaderUnloaded
    }
  }

  internal fun notify(text: String, notificationType: NotificationType, vararg actions: AnAction) {
    val notification = GROUP.createNotification(text, notificationType)
    for (action in actions) {
      notification.addAction(action)
    }
    notification.notify(null)
  }

  // PluginId cannot be used to unload related resources because one plugin descriptor may consist of several sub descriptors, each of them depends on presense of another plugin,
  // here not the whole plugin is unloaded, but only one part.
  private fun unloadPluginDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl, loadedPluginDescriptor: IdeaPluginDescriptorImpl) {
    val application = ApplicationManager.getApplication() as ApplicationImpl
    (ActionManager.getInstance() as ActionManagerImpl).unloadActions(pluginDescriptor)

    val openedProjects = ProjectUtil.getOpenProjects().asList()
    val appExtensionArea = application.extensionArea
    val priorityUnloadListeners = mutableListOf<Runnable>()
    val unloadListeners = mutableListOf<Runnable>()
    pluginDescriptor.extensions?.let { extensions ->
      for ((epName, epExtensions) in extensions) {
        val isAppLevelEp = appExtensionArea.unregisterExtensions(epName, loadedPluginDescriptor, epExtensions, priorityUnloadListeners, unloadListeners)
        if (!isAppLevelEp) {
          for (project in openedProjects) {
            val isProjectLevelEp = (project.extensionArea as ExtensionsAreaImpl).unregisterExtensions(epName, loadedPluginDescriptor, epExtensions, priorityUnloadListeners, unloadListeners)
            if (!isProjectLevelEp) {
              for (module in ModuleManager.getInstance(project).modules) {
                (module.extensionArea as ExtensionsAreaImpl).unregisterExtensions(epName, loadedPluginDescriptor, epExtensions, priorityUnloadListeners, unloadListeners)
              }
            }
          }
        }
      }

      // todo clear extension cache granularly
      appExtensionArea.clearUserCache()
      for (project in openedProjects) {
        (project.extensionArea as ExtensionsAreaImpl).clearUserCache()
      }
    }

    for (priorityUnloadListener in priorityUnloadListeners) {
      priorityUnloadListener.run()
    }
    for (unloadListener in unloadListeners) {
      unloadListener.run()
    }

    // first, reset all plugin extension points before unregistering, so that listeners don't see plugin in semi-torn-down state
    processExtensionPoints(pluginDescriptor, openedProjects) { points, area -> area.resetExtensionPoints(points) }
    // unregister plugin extension points
    processExtensionPoints(pluginDescriptor, openedProjects) { points, area -> area.unregisterExtensionPoints(points) }

    val pluginId = pluginDescriptor.pluginId ?: loadedPluginDescriptor.pluginId
    application.unloadServices(pluginDescriptor.appContainerDescriptor.getServices(), pluginId)
    val appMessageBus = application.messageBus as MessageBusEx
    appMessageBus.unsubscribeLazyListeners(pluginId, pluginDescriptor.appContainerDescriptor.getListeners())

    for (project in openedProjects) {
      (project as ComponentManagerImpl).unloadServices(pluginDescriptor.projectContainerDescriptor.getServices(), pluginId)
      ((project as ComponentManagerImpl).messageBus as MessageBusEx).unsubscribeLazyListeners(pluginId, pluginDescriptor.projectContainerDescriptor.getListeners())

      val moduleServices = pluginDescriptor.moduleContainerDescriptor.getServices()
      for (module in ModuleManager.getInstance(project).modules) {
        (module as ComponentManagerImpl).unloadServices(moduleServices, pluginId)
      }
    }

    appMessageBus.disconnectPluginConnections(Predicate { aClass ->
      (aClass.classLoader as? PluginClassLoader)?.pluginId == pluginId
    })
  }

  private inline fun processExtensionPoints(pluginDescriptor: IdeaPluginDescriptorImpl,
                                            projects: List<Project>,
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
  @JvmOverloads
  fun loadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl, checkImplementationDetailDependencies: Boolean = true): Boolean {
    if (classloadersFromUnloadedPlugins[pluginDescriptor.pluginId] != null) {
      LOG.info("Requiring restart for loading plugin ${pluginDescriptor.pluginId} because previous version of the plugin wasn't fully unloaded")
      return false
    }

    val loadStartTime = System.currentTimeMillis()
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
        LOG.info("Plugin ${pluginDescriptor.pluginId} loaded without restart in ${System.currentTimeMillis() - loadStartTime} ms")
      }
      finally {
        app.messageBus.syncPublisher(DynamicPluginListener.TOPIC).pluginLoaded(pluginDescriptor)
      }
    }

    if (checkImplementationDetailDependencies) {
      var implementationDetailsLoadedWithoutRestart = true
      processImplementationDetailDependenciesOnPlugin(pluginDescriptor) { _, fullDescriptor ->
        val dependencies = fullDescriptor.pluginDependencies
        if (dependencies == null || dependencies.all { it.isOptional || PluginManagerCore.getPlugin(it.id) != null }) {
          if (!loadPlugin(fullDescriptor, false)) {
            implementationDetailsLoadedWithoutRestart = false
          }
        }
        implementationDetailsLoadedWithoutRestart
      }
      return implementationDetailsLoadedWithoutRestart
    }
    return true
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
      (openProject as ComponentManagerImpl).registerComponents(pluginsToLoad, listenerCallbacks)
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

  private fun clearTemporaryLostComponent() {
    try {
      val clearMethod = Window::class.java.declaredMethods.find { it.name == "setTemporaryLostComponent" }
      if (clearMethod == null) {
        LOG.info("setTemporaryLostComponent method not found")
        return
      }
      clearMethod.isAccessible = true
      loop@ for (frame in WindowManager.getInstance().allProjectFrames) {
        val window = when(frame) {
          is ProjectFrameHelper -> frame.frame
          is Window -> frame
          else -> continue@loop
        }
        clearMethod.invoke(window, null)
      }
    }
    catch (e: Throwable) {
      LOG.info("Failed to clear Window.temporaryLostComponent", e)
    }
  }
}