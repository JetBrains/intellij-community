// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.fasterxml.jackson.databind.type.TypeFactory
import com.intellij.application.options.RegistryManager
import com.intellij.configurationStore.StoreUtil.Companion.saveDocumentsAndProjectsAndApp
import com.intellij.configurationStore.jdomSerializer
import com.intellij.configurationStore.runInAutoSaveDisabledMode
import com.intellij.diagnostic.MessagePool
import com.intellij.diagnostic.hprof.action.SystemTempFilenameSupplier
import com.intellij.diagnostic.hprof.analysis.AnalyzeClassloaderReferencesGraph
import com.intellij.diagnostic.hprof.analysis.HProfAnalysis
import com.intellij.ide.IdeBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.ui.TopHitCache
import com.intellij.ide.ui.UIThemeProvider
import com.intellij.ide.util.TipDialog
import com.intellij.idea.IdeaLogger
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.lang.Language
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.notification.impl.NotificationsManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.DecodeDefaultsUtil
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.keymap.impl.BundledKeymapBean
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.PotemkinProgress
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.objectTree.ThrowableInterner
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.psi.util.CachedValuesManager
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.ui.IconDeferrer
import com.intellij.util.CachedValuesManagerImpl
import com.intellij.util.MemoryDumpHelper
import com.intellij.util.SystemProperties
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.URLUtil
import com.intellij.util.messages.impl.MessageBusEx
import com.intellij.util.ref.GCWatcher
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import java.awt.Window
import java.nio.channels.FileChannel
import java.nio.file.FileVisitResult
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.*
import java.util.function.Function
import java.util.function.Predicate
import javax.swing.JComponent
import kotlin.collections.component1
import kotlin.collections.component2

private val LOG = logger<DynamicPlugins>()
private val classloadersFromUnloadedPlugins = ContainerUtil.createWeakValueMap<PluginId, PluginClassLoader>()

object DynamicPlugins {
  private const val GROUP_ID = "Dynamic plugin installation"

  @JvmStatic
  @JvmOverloads
  fun allowLoadUnloadWithoutRestart(descriptor: IdeaPluginDescriptorImpl,
                                    baseDescriptor: IdeaPluginDescriptorImpl? = null,
                                    context: List<IdeaPluginDescriptorImpl> = emptyList()): Boolean {
    val reason = checkCanUnloadWithoutRestart(descriptor, baseDescriptor, context = context)
    if (reason != null) {
      LOG.info(reason)
    }
    return reason == null
  }

  @JvmStatic
  @JvmOverloads
  fun loadUnloadPlugins(pluginsToEnable: List<IdeaPluginDescriptor>,
                        pluginsToDisable: List<IdeaPluginDescriptor>,
                        project: Project? = null,
                        parentComponent: JComponent? = null,
                        options: UnloadPluginOptions = UnloadPluginOptions().withDisable(true)): Boolean {
    val descriptorsToEnable = loadFullDescriptorsWithoutRestart(pluginsToEnable, enable = true) ?: return false
    val descriptorsToDisable = loadFullDescriptorsWithoutRestart(pluginsToDisable, enable = false) ?: return false
    val loader = lazy(LazyThreadSafetyMode.NONE) { OptionalDependencyDescriptorLoader() }
    return descriptorsToDisable.reversed().all {
      unloadPluginWithProgress(project, parentComponent, it, options)
    } && descriptorsToEnable.all {
      loadPlugin(it, checkImplementationDetailDependencies = true, loader = loader)
    }
  }

  private fun loadFullDescriptorsWithoutRestart(plugins: List<IdeaPluginDescriptor>, enable: Boolean): List<IdeaPluginDescriptorImpl>? {
    val loadedPlugins = PluginManagerCore.getLoadedPlugins(null)
    val descriptors = plugins
      .asSequence()
      .filterIsInstance<IdeaPluginDescriptorImpl>()
      .filter { loadedPlugins.contains(it) != enable }
      .map { PluginDescriptorLoader.loadFullDescriptor(it) }
      .toList()

    if (descriptors.all { allowLoadUnloadWithoutRestart(it, context = descriptors) }) {
      return PluginManagerCore.getPluginsSortedByDependency(descriptors, true)
    }
    else {
      return null
    }
  }

  /**
   * @param context Plugins which are being loaded at the same time as [descriptor]
   */
  @JvmStatic
  @JvmOverloads
  @NonNls
  fun checkCanUnloadWithoutRestart(descriptor: IdeaPluginDescriptorImpl,
                                   baseDescriptor: IdeaPluginDescriptorImpl? = null,
                                   optionalDependencyPluginId: PluginId? = null,
                                   context: List<IdeaPluginDescriptorImpl> = emptyList(),
                                   checkImplementationDetailDependencies: Boolean = true): String? {
    if (descriptor.isRequireRestart) {
      return "Plugin ${descriptor.pluginId} is explicitly marked as requiring restart"
    }
    if (descriptor.productCode != null && !descriptor.isBundled && !PluginManager.getInstance().isDevelopedByJetBrains(descriptor)) {
      return "Plugin ${descriptor.pluginId} is a paid plugin"
    }

    if (InstalledPluginsState.getInstance().isRestartRequired) {
      return "Not allowing load/unload without restart because of pending restart operation"
    }
    if (classloadersFromUnloadedPlugins[descriptor.pluginId] != null) {
      return "Not allowing load/unload of ${descriptor.pluginId} because of incomplete previous unload operation for that plugin"
    }
    findMissingRequiredDependency(descriptor, context)?.let { pluginDependency ->
      return "Required dependency ${pluginDependency} of plugin ${descriptor.pluginId} is not currently loaded"
    }

    if (!RegistryManager.getInstance().`is`("ide.plugins.allow.unload")) {
      val canLoadSynchronously = allowLoadUnloadSynchronously(descriptor)
      if (!canLoadSynchronously) {
        return "ide.plugins.allow.unload is disabled and synchronous load/unload is not possible for ${descriptor.pluginId}"
      }
      return null
    }

    val app = ApplicationManager.getApplication()
    try {
      app.messageBus.syncPublisher(DynamicPluginListener.TOPIC).checkUnloadPlugin(descriptor)
    }
    catch (e: CannotUnloadPluginException) {
      return e.cause?.localizedMessage ?: "checkUnloadPlugin listener blocked plugin unload"
    }

    val pluginStateChecker = PluginStateChecker()
    if (!Registry.`is`("ide.plugins.allow.unload.from.sources")) {
      val loadedPluginDescriptor = if (descriptor === baseDescriptor) PluginManagerCore.getPlugin(descriptor.pluginId) as? IdeaPluginDescriptorImpl else null
      if (loadedPluginDescriptor != null && pluginStateChecker.isPluginOrModuleLoaded(loadedPluginDescriptor.pluginId) && !descriptor.isUseIdeaClassLoader) {
        val pluginClassLoader = loadedPluginDescriptor.pluginClassLoader
        if (pluginClassLoader !is PluginClassLoader && !app.isUnitTestMode) {
          return "Plugin ${descriptor.pluginId} is not unload-safe because of use of ${pluginClassLoader.javaClass.name} as the default class loader. " +
                 "For example, the IDE is started from the sources with the plugin."
        }
      }
    }

    val isSubDescriptor = baseDescriptor != null && descriptor !== baseDescriptor
    checkExtensionsCanUnloadWithoutRestart(descriptor, baseDescriptor, isSubDescriptor, app, optionalDependencyPluginId, context)?.let {
      return it
    }

    val pluginId = descriptor.pluginId
    checkNoComponentsOrServiceOverrides(pluginId, descriptor)?.let { return it }
    ActionManagerImpl.checkUnloadActions(pluginId, descriptor)?.let { return it }

    descriptor.pluginDependencies?.let { pluginDependencies ->
      for (dependency in pluginDependencies) {
        if (pluginStateChecker.isPluginOrModuleLoaded(dependency.id)) {
          checkCanUnloadWithoutRestart(dependency.subDescriptor ?: continue, baseDescriptor ?: descriptor, null, context)?.let {
            return "$it in optional dependency on ${dependency.id}"
          }
        }
      }
    }

    // if not a sub plugin descriptor, then check that any dependent plugin also reloadable
    if (isSubDescriptor) {
      return null
    }

    var dependencyMessage: String? = null
    processLoadedOptionalDependenciesOnPlugin(descriptor.pluginId) { mainDescriptor, subDescriptor ->
      if (subDescriptor == null) {
        // <depends optional="true">XPathView</depends> Here subDescriptor will be null.
        return@processLoadedOptionalDependenciesOnPlugin true
      }

      dependencyMessage = checkCanUnloadWithoutRestart(subDescriptor, mainDescriptor, subDescriptor.pluginId, context)
      if (dependencyMessage == null) {
        true
      }
      else {
        dependencyMessage = "Plugin ${subDescriptor.pluginId} that optionally depends on ${descriptor.pluginId} requires restart: $dependencyMessage"
        false
      }
    }

    if (dependencyMessage == null && checkImplementationDetailDependencies) {
      val contextWithImplementationDetails = context.toMutableList()
      contextWithImplementationDetails.add(descriptor)
      processImplementationDetailDependenciesOnPlugin(descriptor) { _, fullDescriptor ->
        contextWithImplementationDetails.add(fullDescriptor)
      }

      processImplementationDetailDependenciesOnPlugin(descriptor) { _, fullDescriptor ->
        // Don't check a plugin that is an implementation-detail dependency on the current plugin if it has other disabled dependencies
        // and won't be loaded anyway
        if (findMissingRequiredDependency(fullDescriptor, contextWithImplementationDetails) == null) {
          dependencyMessage = checkCanUnloadWithoutRestart(fullDescriptor, context = contextWithImplementationDetails,
                                                           checkImplementationDetailDependencies = false)
          if (dependencyMessage != null) {
            dependencyMessage = "implementation-detail plugin ${fullDescriptor.pluginId} which depends on ${descriptor.pluginId} requires restart: $dependencyMessage"
          }
        }
        dependencyMessage == null
      }
    }
    return dependencyMessage
  }

  private fun findMissingRequiredDependency(descriptor: IdeaPluginDescriptorImpl,
                                            context: List<IdeaPluginDescriptorImpl>): PluginId? {
    for (dependency in (descriptor.pluginDependencies ?: return null)) {
      if (!dependency.isOptional &&
          !PluginManagerCore.isModuleDependency(dependency.id) &&
          PluginManagerCore.getLoadedPlugins(null).none { it.pluginId == dependency.id } &&
          context.none { it.pluginId == dependency.id }
      ) {
        return dependency.id
      }
    }
    return null
  }

  /**
   * Load all sub plugins that depend on specified [dependencyPluginId].
   */
  private fun processOptionalDependenciesOnPlugin(dependencyPluginId: PluginId,
                                                  loader: Lazy<OptionalDependencyDescriptorLoader>,
                                                  processor: (pluginDescriptor: IdeaPluginDescriptorImpl) -> Boolean) {
    for (descriptor in PluginManagerCore.getLoadedPlugins(null)) {
      for (dependency in (descriptor.pluginDependencies ?: continue)) {
        if (!processOptionalDependencyDescriptor(dependencyPluginId, descriptor, dependency, loader, processor)) {
          break
        }
      }
    }
  }

  private fun processOptionalDependencyDescriptor(dependencyPluginId: PluginId,
                                                  contextDescriptor: IdeaPluginDescriptorImpl,
                                                  dependency: PluginDependency,
                                                  loader: Lazy<OptionalDependencyDescriptorLoader>,
                                                  processor: (pluginDescriptor: IdeaPluginDescriptorImpl) -> Boolean): Boolean {
    if (!dependency.isOptional) {
      return true
    }

    val newPluginDescriptor = dependency.configFile?.let { loader.value.load(contextDescriptor, it) } ?: return true
    // todo classloader per partial descriptor
    newPluginDescriptor.classLoader = contextDescriptor.pluginClassLoader

    if (dependency.id == dependencyPluginId) {
      dependency.subDescriptor = newPluginDescriptor
      dependency.isDisabledOrBroken = false
      if (!processor(newPluginDescriptor)) {
        return false
      }
    }

    for (subDependency in (newPluginDescriptor.pluginDependencies ?: return true)) {
      if (!processOptionalDependencyDescriptor(dependencyPluginId, newPluginDescriptor, subDependency, loader, processor)) {
        return false
      }
    }
    return true
  }

  private fun processImplementationDetailDependenciesOnPlugin(pluginDescriptor: IdeaPluginDescriptorImpl,
                                                              processor: (loadedDescriptor: IdeaPluginDescriptorImpl, fullDescriptor: IdeaPluginDescriptorImpl) -> Boolean) {
    PluginManager.getInstance().processAllBackwardDependencies(pluginDescriptor, false) { loadedDescriptor ->
      if (loadedDescriptor.isImplementationDetail) {
        val fullDescriptor = PluginDescriptorLoader.loadFullDescriptor(loadedDescriptor as IdeaPluginDescriptorImpl)
        if (processor(loadedDescriptor, fullDescriptor)) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
      }
      else {
        FileVisitResult.CONTINUE
      }
    }
  }

  private class OptionalDependencyDescriptorLoader {
    private val pluginXmlFactory = PluginXmlFactory()
    private val listContext = DescriptorListLoadingContext.createSingleDescriptorContext(DisabledPluginsState.disabledPlugins())

    fun load(contextDescriptor: IdeaPluginDescriptorImpl, dependencyConfigFile: String): IdeaPluginDescriptorImpl? {
      val context = DescriptorLoadingContext(listContext, contextDescriptor.isBundled, /* isEssential = */ false,
                                             PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER)
      val pathResolver = PluginDescriptorLoader.createPathResolverForPlugin(contextDescriptor, context)
      try {
        val jarPair = URLUtil.splitJarUrl(contextDescriptor.basePath.toUri().toString())
        val newBasePath = if (jarPair == null) {
          contextDescriptor.basePath
        }
        else {
          context.open(Paths.get(jarPair.first)).getPath(jarPair.second)
        }

        val element = pathResolver.resolvePath(newBasePath, dependencyConfigFile, pluginXmlFactory)
        val subDescriptor = IdeaPluginDescriptorImpl(contextDescriptor.pluginPath, newBasePath, contextDescriptor.isBundled)
        // readExternal requires not-null id
        subDescriptor.id = contextDescriptor.id
        subDescriptor.name = contextDescriptor.name
        if (subDescriptor.readExternal(element, pathResolver, listContext, contextDescriptor)) {
          return subDescriptor
        }

        LOG.info("Can't read descriptor $dependencyConfigFile for optional dependency of plugin being loaded/unloaded")
        return null
      }
      catch (e: Exception) {
        LOG.info("Can't resolve optional dependency on plugin being loaded/unloaded: config file $dependencyConfigFile", e)
        return null
      }
      finally {
        context.close()
      }
    }
  }

  /**
   * Checks if the plugin can be loaded/unloaded immediately when the corresponding action is invoked in the
   * plugins settings, without pressing the Apply button.
   */
  @JvmStatic
  fun allowLoadUnloadSynchronously(pluginDescriptor: IdeaPluginDescriptorImpl): Boolean {
    val extensions = (pluginDescriptor.unsortedEpNameToExtensionElements.takeIf { it.isNotEmpty() } ?: pluginDescriptor.app.extensions)
    if (extensions != null && !extensions.all {
        it.key == UIThemeProvider.EP_NAME.name ||
        it.key == BundledKeymapBean.EP_NAME.name}) {
      return false
    }
    return checkNoComponentsOrServiceOverrides(pluginDescriptor.pluginId, pluginDescriptor) == null && pluginDescriptor.actionDescriptionElements.isNullOrEmpty()
  }

  private fun checkNoComponentsOrServiceOverrides(pluginId: PluginId?, pluginDescriptor: IdeaPluginDescriptorImpl): String? {
    return checkNoComponentsOrServiceOverrides(pluginId, pluginDescriptor.appContainerDescriptor)
           ?: checkNoComponentsOrServiceOverrides(pluginId, pluginDescriptor.projectContainerDescriptor)
           ?: checkNoComponentsOrServiceOverrides(pluginId, pluginDescriptor.moduleContainerDescriptor)
  }

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
                               options: UnloadPluginOptions): Boolean {
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
    val indicator = PotemkinProgress(IdeBundle.message("unloading.plugin.progress.title", pluginDescriptor.name), project, parentComponent,
                                     null)
    indicator.runInSwingThread {
      result = unloadPlugin(pluginDescriptor, options.withSave(false))
    }
    return result
  }

  @JvmStatic
  fun getPluginUnloadingTask(pluginDescriptor: IdeaPluginDescriptorImpl, options: UnloadPluginOptions): Runnable {
    return Runnable { unloadPlugin(pluginDescriptor, options) }
  }

  data class UnloadPluginOptions(
    var disable: Boolean = false,
    var isUpdate: Boolean = false,
    var save: Boolean = true,
    var requireMemorySnapshot: Boolean = false,
    var waitForClassloaderUnload: Boolean = false,
    var checkImplementationDetailDependencies: Boolean = true,
    var unloadWaitTimeout: Int? = null
  ) {
    fun withUpdate(value: Boolean): UnloadPluginOptions { isUpdate = value; return this }
    fun withWaitForClassloaderUnload(value: Boolean): UnloadPluginOptions { waitForClassloaderUnload = value; return this }
    fun withDisable(value: Boolean): UnloadPluginOptions { disable = value; return this }
    fun withRequireMemorySnapshot(value: Boolean): UnloadPluginOptions { requireMemorySnapshot = value; return this }
    fun withUnloadWaitTimeout(value: Int): UnloadPluginOptions { unloadWaitTimeout = value; return this }
    fun withSave(value: Boolean): UnloadPluginOptions { save = value; return this }
  }

  @JvmStatic
  fun unloadPlugin(fullyLoadedPluginDescriptor: IdeaPluginDescriptorImpl, options: UnloadPluginOptions = UnloadPluginOptions()): Boolean {
    val app = ApplicationManager.getApplication() as ApplicationImpl
    val pluginId = fullyLoadedPluginDescriptor.pluginId

    // The descriptor passed to `unloadPlugin` is the full descriptor loaded from disk, it does not have a classloader.
    // We need to find the real plugin loaded into the current instance and unload its classloader.
    val pluginDescriptor = PluginManagerCore.getPlugin(pluginId) as? IdeaPluginDescriptorImpl
                           ?: return false

    if (options.checkImplementationDetailDependencies) {
      processImplementationDetailDependenciesOnPlugin(pluginDescriptor) { loadedDescriptor, fullDescriptor ->
        loadedDescriptor.isEnabled = false
        unloadPlugin(fullDescriptor, UnloadPluginOptions(disable = true, save = false, waitForClassloaderUnload = false,
                                                         checkImplementationDetailDependencies = false))
        true
      }
    }

    var classLoaderUnloaded: Boolean
    try {
      if (options.save) {
        saveDocumentsAndProjectsAndApp(true)
      }
      TipDialog.hideForProject(null)

      app.messageBus.syncPublisher(DynamicPluginListener.TOPIC).beforePluginUnload(pluginDescriptor, options.isUpdate)
      IdeEventQueue.getInstance().flushQueue()
      // must be after flushQueue (e.g. https://youtrack.jetbrains.com/issue/IDEA-252010)
      app.runWriteAction {
        val forbidGettingServicesToken = app.forbidGettingServices("Plugin $pluginId being unloaded.")
        try {
          processLoadedOptionalDependenciesOnPlugin(pluginId) { mainDescriptor, subDescriptor ->
            // must be before unloadPluginDescriptorNotRecursively as this method nullize classloader
            val classLoader = (subDescriptor ?: mainDescriptor).pluginClassLoader as? PluginClassLoader
            subDescriptor?.let { unloadPluginDescriptorNotRecursively(it) }

            var detached: Boolean? = false
            if (pluginDescriptor.pluginClassLoader is PluginClassLoader) {
              detached = classLoader?.detachParent(pluginDescriptor.pluginClassLoader)
            }
            if (detached != true) {
              LOG.info("Failed to detach classloader of $pluginId from classloader of ${(subDescriptor ?: mainDescriptor).pluginId}")
            }
            true
          }

          if (pluginDescriptor.pluginClassLoader is PluginClassLoader) {
            IconLoader.detachClassLoader(pluginDescriptor.pluginClassLoader)
            Language.unregisterLanguages(pluginDescriptor.pluginClassLoader)
          }

          pluginDescriptor.pluginDependencies?.let { unloadDependencyDescriptors(it, PluginStateChecker()) }
          unloadPluginDescriptorNotRecursively(pluginDescriptor)

          app.extensionArea.clearUserCache()
          for (project in ProjectUtil.getOpenProjects()) {
            (project.extensionArea as ExtensionsAreaImpl).clearUserCache()
            (project.serviceIfCreated<CachedValuesManager>() as CachedValuesManagerImpl?)?.clearCachedValues()
          }
          jdomSerializer.clearSerializationCaches()
          TypeFactory.defaultInstance().clearCache()
          app.getServiceIfCreated(TopHitCache::class.java)?.clear()
          PresentationFactory.clearPresentationCaches()
          ActionToolbarImpl.updateAllToolbarsImmediately(true)
          (serviceIfCreated<NotificationsManager>() as? NotificationsManagerImpl)?.expireAll()
          MessagePool.getInstance().clearErrors()
          DecodeDefaultsUtil.clearResourceCache()
          LaterInvocator.purgeExpiredItems()

          serviceIfCreated<IconDeferrer>()?.clearCache()

          (ApplicationManager.getApplication().messageBus as MessageBusEx).clearPublisherCache()
          (ProjectManager.getInstanceIfCreated() as? ProjectManagerImpl)?.disposeDefaultProjectAndCleanupComponentsForDynamicPluginTests()

          if (options.disable) {
            // update list of disabled plugins
            PluginManager.getInstance().setPlugins(PluginManagerCore.getPlugins().asList())
          }
          else {
            PluginManager.getInstance().setPlugins(PluginManagerCore.getPlugins().asSequence().minus(pluginDescriptor).toList())
          }
        }
        finally {
          try {
            forbidGettingServicesToken.finish()
          }
          finally {
            app.messageBus.syncPublisher(DynamicPluginListener.TOPIC).pluginUnloaded(pluginDescriptor, options.isUpdate)
          }
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

      if (app.isUnitTestMode && pluginDescriptor.pluginClassLoader !is PluginClassLoader) {
        classLoaderUnloaded = true
      }
      else {
        classloadersFromUnloadedPlugins.put(pluginId, pluginDescriptor.pluginClassLoader as? PluginClassLoader)
        val checkClassLoaderUnload = options.waitForClassloaderUnload ||
                                     options.requireMemorySnapshot ||
                                     Registry.`is`("ide.plugins.snapshot.on.unload.fail")
        val timeout = if (checkClassLoaderUnload) {
          options.unloadWaitTimeout ?: Registry.intValue("ide.plugins.unload.timeout", 5000)
        }
        else {
          0
        }

        classLoaderUnloaded = unloadClassLoader(pluginDescriptor, timeout)
        if (classLoaderUnloaded) {
          LOG.info("Successfully unloaded plugin $pluginId (classloader unload checked=$checkClassLoaderUnload)")
          classloadersFromUnloadedPlugins.remove(pluginId)
        }
        else {
          InstalledPluginsState.getInstance().isRestartRequired = true
          if ((options.requireMemorySnapshot || (Registry.`is`("ide.plugins.snapshot.on.unload.fail") && !app.isUnitTestMode)) &&
              MemoryDumpHelper.memoryDumpAvailable()) {
            classLoaderUnloaded = saveMemorySnapshot(pluginId)
          }
          else {
            LOG.info("Plugin $pluginId is not unload-safe because class loader cannot be unloaded")
          }
        }

        val eventId = if (classLoaderUnloaded) "unload.success" else "unload.fail"
        val fuData = FeatureUsageData().addPluginInfo(getPluginInfoByDescriptor(pluginDescriptor))
        @Suppress("DEPRECATION")
        FUCounterUsageLogger.getInstance().logEvent("plugins.dynamic", eventId, fuData)
      }
    }
    return classLoaderUnloaded
  }

  private fun unloadDependencyDescriptors(pluginDependencies: List<PluginDependency>, pluginStateChecker: PluginStateChecker) {
    for (dependency in pluginDependencies) {
      val subDescriptor = dependency.subDescriptor ?: continue
      if (pluginStateChecker.isPluginOrModuleLoaded(dependency.id)) {
        unloadPluginDescriptorNotRecursively(subDescriptor)
        unloadDependencyDescriptors(subDescriptor.pluginDependencies ?: continue, pluginStateChecker)
      }
    }
  }

  internal fun notify(@NlsContexts.NotificationContent text: String, notificationType: NotificationType, vararg actions: AnAction) {
    val notification = NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID).createNotification(text, notificationType)
    for (action in actions) {
      notification.addAction(action)
    }
    notification.notify(null)
  }

  // PluginId cannot be used to unload related resources because one plugin descriptor may consist of several sub descriptors, each of them depends on presense of another plugin,
  // here not the whole plugin is unloaded, but only one part.
  private fun unloadPluginDescriptorNotRecursively(pluginDescriptor: IdeaPluginDescriptorImpl) {
    val app = ApplicationManager.getApplication() as ApplicationImpl
    (ActionManager.getInstance() as ActionManagerImpl).unloadActions(pluginDescriptor)

    val openedProjects = ProjectUtil.getOpenProjects().asList()
    val appExtensionArea = app.extensionArea
    val priorityUnloadListeners = mutableListOf<Runnable>()
    val unloadListeners = mutableListOf<Runnable>()
    unregisterUnknownLevelExtensions(pluginDescriptor.unsortedEpNameToExtensionElements, pluginDescriptor, appExtensionArea, openedProjects,
                                     priorityUnloadListeners, unloadListeners)
    for ((epName, epExtensions) in (pluginDescriptor.app.extensions ?: emptyMap())) {
      appExtensionArea.unregisterExtensions(epName, pluginDescriptor, epExtensions, priorityUnloadListeners, unloadListeners)
    }
    for ((epName, epExtensions) in (pluginDescriptor.project.extensions ?: emptyMap())) {
      for (project in openedProjects) {
        (project.extensionArea as ExtensionsAreaImpl).unregisterExtensions(epName, pluginDescriptor, epExtensions, priorityUnloadListeners,
                                                                           unloadListeners)
      }
    }

    // not an error - unsorted goes to module level, see registerExtensions
    unregisterUnknownLevelExtensions(pluginDescriptor.module.extensions, pluginDescriptor, appExtensionArea, openedProjects,
                                     priorityUnloadListeners, unloadListeners)

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

    pluginDescriptor.app.extensionPoints = null
    pluginDescriptor.project.extensionPoints = null
    pluginDescriptor.module.extensionPoints = null

    val pluginId = pluginDescriptor.pluginId
    app.unloadServices(pluginDescriptor.appContainerDescriptor.getServices(), pluginId)
    val appMessageBus = app.messageBus as MessageBusEx
    pluginDescriptor.appContainerDescriptor.listeners?.let { appMessageBus.unsubscribeLazyListeners(pluginId, it) }

    for (project in openedProjects) {
      (project as ComponentManagerImpl).unloadServices(pluginDescriptor.projectContainerDescriptor.getServices(), pluginId)
      pluginDescriptor.projectContainerDescriptor.getListeners()?.let {
        ((project as ComponentManagerImpl).messageBus as MessageBusEx).unsubscribeLazyListeners(pluginId, it)
      }

      val moduleServices = pluginDescriptor.moduleContainerDescriptor.getServices()
      for (module in ModuleManager.getInstance(project).modules) {
        (module as ComponentManagerImpl).unloadServices(moduleServices, pluginId)
        createDisposeTreePredicate(pluginDescriptor)?.let { Disposer.disposeChildren(module, it) }
      }

      createDisposeTreePredicate(pluginDescriptor)?.let { Disposer.disposeChildren(project, it) }
    }

    appMessageBus.disconnectPluginConnections(Predicate { aClass ->
      (aClass.classLoader as? PluginClassLoader)?.pluginDescriptor == pluginDescriptor
    })

    createDisposeTreePredicate(pluginDescriptor)?.let { Disposer.disposeChildren(ApplicationManager.getApplication(), it) }
  }

  private fun unregisterUnknownLevelExtensions(extensionMap: Map<String, List<Element>>?,
                                               pluginDescriptor: IdeaPluginDescriptorImpl,
                                               appExtensionArea: ExtensionsAreaImpl,
                                               openedProjects: List<Project>,
                                               priorityUnloadListeners: MutableList<Runnable>,
                                               unloadListeners: MutableList<Runnable>) {
    for ((epName, epExtensions) in (extensionMap ?: return)) {
      val isAppLevelEp = appExtensionArea.unregisterExtensions(epName, pluginDescriptor, epExtensions, priorityUnloadListeners,
                                                               unloadListeners)
      if (isAppLevelEp) {
        continue
      }

      for (project in openedProjects) {
        val isProjectLevelEp = (project.extensionArea as ExtensionsAreaImpl)
          .unregisterExtensions(epName, pluginDescriptor, epExtensions, priorityUnloadListeners, unloadListeners)
        if (!isProjectLevelEp) {
          for (module in ModuleManager.getInstance(project).modules) {
            (module.extensionArea as ExtensionsAreaImpl)
              .unregisterExtensions(epName, pluginDescriptor, epExtensions, priorityUnloadListeners, unloadListeners)
          }
        }
      }
    }
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
    return loadPlugin(pluginDescriptor, checkImplementationDetailDependencies,
                      lazy(LazyThreadSafetyMode.NONE) { OptionalDependencyDescriptorLoader() })
  }

  private fun loadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl,
                         checkImplementationDetailDependencies: Boolean,
                         loader: Lazy<OptionalDependencyDescriptorLoader>): Boolean {
    if (classloadersFromUnloadedPlugins[pluginDescriptor.pluginId] != null) {
      LOG.info("Requiring restart for loading plugin ${pluginDescriptor.pluginId}" +
               " because previous version of the plugin wasn't fully unloaded")
      return false
    }

    val loadStartTime = System.currentTimeMillis()
    val app = ApplicationManager.getApplication() as ApplicationImpl
    if (!app.isUnitTestMode) {
      PluginManagerCore.initClassLoaderForDynamicPlugin(pluginDescriptor)
    }

    app.messageBus.syncPublisher(DynamicPluginListener.TOPIC).beforePluginLoaded(pluginDescriptor)
    app.runWriteAction {
      try {
        addToLoadedPlugins(pluginDescriptor)
        val pluginStateChecker = PluginStateChecker()
        loadPluginDescriptor(pluginDescriptor, app, pluginStateChecker)
        processOptionalDependenciesOnPlugin(pluginDescriptor.pluginId, loader) { subDescriptor ->
          if (pluginDescriptor.pluginClassLoader is PluginClassLoader) {
            (subDescriptor.pluginClassLoader as? PluginClassLoader)?.attachParent(pluginDescriptor.pluginClassLoader)
          }
          loadPluginDescriptor(subDescriptor, app, pluginStateChecker)
          true
        }

        for (openProject in ProjectUtil.getOpenProjects()) {
          (CachedValuesManager.getManager(openProject) as CachedValuesManagerImpl).clearCachedValues()
        }

        val fuData = FeatureUsageData().addPluginInfo(getPluginInfoByDescriptor(pluginDescriptor))
        @Suppress("DEPRECATION")
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
          if (!loadPlugin(fullDescriptor, checkImplementationDetailDependencies = false, loader = loader)) {
            implementationDetailsLoadedWithoutRestart = false
          }
        }
        implementationDetailsLoadedWithoutRestart
      }
      return implementationDetailsLoadedWithoutRestart
    }
    return true
  }

  private fun addToLoadedPlugins(pluginDescriptor: IdeaPluginDescriptorImpl) {
    var foundExistingPlugin = false
    val newPlugins = PluginManagerCore.getPlugins().map {
      if (it.pluginId == pluginDescriptor.pluginId) {
        foundExistingPlugin = true
        pluginDescriptor
      }
      else {
        it
      }
    }

    if (foundExistingPlugin) {
      PluginManager.getInstance().setPlugins(newPlugins)
    }
    else {
      PluginManager.getInstance().setPlugins(PluginManagerCore.getPlugins().asSequence().plus(pluginDescriptor).toList())
    }
  }

  private fun loadPluginDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl,
                                   app: ComponentManagerImpl,
                                   pluginStateChecker: PluginStateChecker) {
    updateDependenciesStatus(pluginDescriptor, pluginStateChecker)

    val listenerCallbacks = mutableListOf<Runnable>()
    val list = listOf(pluginDescriptor)
    app.registerComponents(list, listenerCallbacks)
    for (openProject in ProjectUtil.getOpenProjects()) {
      (openProject as ComponentManagerImpl).registerComponents(list, listenerCallbacks)
      for (module in ModuleManager.getInstance(openProject).modules) {
        (module as ComponentManagerImpl).registerComponents(list, listenerCallbacks)
      }
    }

    val actionManager = ActionManager.getInstance() as ActionManagerImpl
    actionManager.registerActions(list, false)
    listenerCallbacks.forEach(Runnable::run)
  }

  private fun updateDependenciesStatus(pluginDescriptor: IdeaPluginDescriptorImpl, pluginStateChecker: PluginStateChecker) {
    for (dependency in (pluginDescriptor.pluginDependencies ?: return))  {
      val subDescriptor = dependency.subDescriptor ?: continue
      if (pluginStateChecker.isPluginOrModuleLoaded(dependency.id)) {
        dependency.isDisabledOrBroken = false
        updateDependenciesStatus(subDescriptor, pluginStateChecker)
      }
      else {
        dependency.isDisabledOrBroken = true
      }
    }
  }

  @JvmStatic
  fun onPluginUnload(parentDisposable: Disposable, callback: Runnable) {
    ApplicationManager.getApplication().messageBus.connect(parentDisposable)
      .subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
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

  private fun saveMemorySnapshot(pluginId: PluginId): Boolean {
    val snapshotDate = SimpleDateFormat("dd.MM.yyyy_HH.mm.ss").format(Date())
    val snapshotFileName = "unload-$pluginId-$snapshotDate.hprof"
    val snapshotPath = System.getProperty("memory.snapshots.path", SystemProperties.getUserHome()) + "/" + snapshotFileName

    MemoryDumpHelper.captureMemoryDump(snapshotPath)
    notify(
      IdeBundle.message("memory.snapshot.captured.text", snapshotPath, snapshotFileName),
      NotificationType.WARNING,
      object : AnAction(IdeBundle.message("ide.restart.action")), DumbAware {
        override fun actionPerformed(e: AnActionEvent) = ApplicationManager.getApplication().restart()
      },
      object : AnAction(
        IdeBundle.message("memory.snapshot.captured.action.text", snapshotFileName, RevealFileAction.getFileManagerName())), DumbAware {
        override fun actionPerformed(e: AnActionEvent) = RevealFileAction.openFile(Paths.get(snapshotPath))
      }
    )

    if (classloadersFromUnloadedPlugins[pluginId] == null) {
      LOG.info("Successfully unloaded plugin $pluginId (classloader collected during memory snapshot generation)")
      return true
    }

    if (Registry.`is`("ide.plugins.analyze.snapshot")) {
      val analysisResult = analyzeSnapshot(snapshotPath, pluginId)
      @Suppress("ReplaceSizeZeroCheckWithIsEmpty")
      if (analysisResult.length == 0) {
        LOG.info("Successfully unloaded plugin $pluginId (no strong references to classloader in .hprof file)")
        classloadersFromUnloadedPlugins.remove(pluginId)
        return true
      }
      else {
        LOG.info("Snapshot analysis result: $analysisResult")
      }
    }

    LOG.info("Plugin $pluginId is not unload-safe because class loader cannot be unloaded. Memory snapshot created at $snapshotPath")
    return false
  }
}

private class PluginStateChecker {
  companion object {
    private val NULL_PLUGIN_DESCRIPTOR = IdeaPluginDescriptorImpl(Paths.get(""), Paths.get(""), false)
  }

  private val plugins = PluginManagerCore.getLoadedPlugins(null)

  private val moduleToPluginCache = IdentityHashMap<PluginId, IdeaPluginDescriptor>()

  private fun findPluginByModuleDependency(pluginId: PluginId): IdeaPluginDescriptor? {
    return moduleToPluginCache.computeIfAbsent(pluginId, Function {
      for (descriptor in PluginManagerCore.getPlugins()) {
        if ((descriptor as IdeaPluginDescriptorImpl).modules.contains(it)) {
          return@Function descriptor
        }
      }
      NULL_PLUGIN_DESCRIPTOR
    }).takeIf { it !== NULL_PLUGIN_DESCRIPTOR }
  }

  fun isPluginOrModuleLoaded(pluginId: PluginId): Boolean {
    if (PluginManagerCore.isModuleDependency(pluginId)) {
      return findPluginByModuleDependency(pluginId) != null
    }
    else {
      return plugins.any { it.pluginId == pluginId }
    }
  }
}

private fun analyzeSnapshot(hprofPath: String, pluginId: PluginId): String {
  FileChannel.open(Paths.get(hprofPath), StandardOpenOption.READ).use { channel ->
    val analysis = HProfAnalysis(channel, SystemTempFilenameSupplier()) { analysisContext, progressIndicator ->
      AnalyzeClassloaderReferencesGraph(analysisContext, pluginId.idString).analyze(progressIndicator)
    }
    analysis.onlyStrongReferences = true
    analysis.includeClassesAsRoots = false
    analysis.setIncludeMetaInfo(false)
    return analysis.analyze(ProgressManager.getGlobalProgressIndicator() ?: EmptyProgressIndicator())
  }
}

private fun createDisposeTreePredicate(pluginDescriptor: IdeaPluginDescriptorImpl): Predicate<Disposable>? {
  val classLoader = pluginDescriptor.classLoader as? PluginClassLoader ?: return null
  return Predicate {
    if (it is PluginManager.PluginAwareDisposable) {
      it.classLoaderId == classLoader.instanceId
    }
    else {
      it::class.java.classLoader == classLoader
    }
  }
}

private fun processLoadedOptionalDependenciesOnPlugin(dependencyPluginId: PluginId,
                                                      processor: (mainDescriptor: IdeaPluginDescriptorImpl, subDescriptor: IdeaPluginDescriptorImpl?) -> Boolean) {
  for (descriptor in PluginManagerCore.getLoadedPlugins(null)) {
    if (!processLoadedOptionalDependenciesOnPlugin(dependencyPluginId, descriptor, processor)) {
      break
    }
  }
}

private fun processLoadedOptionalDependenciesOnPlugin(dependencyPluginId: PluginId,
                                                      mainDescriptor: IdeaPluginDescriptorImpl,
                                                      processor: (mainDescriptor: IdeaPluginDescriptorImpl, subDescriptor: IdeaPluginDescriptorImpl?) -> Boolean): Boolean {
  for (dependency in (mainDescriptor.pluginDependencies ?: return true)) {
    if (!dependency.isOptional || dependency.isDisabledOrBroken) {
      continue
    }

    if (dependency.id == dependencyPluginId && !processor(mainDescriptor, dependency.subDescriptor)) {
      return false
    }

    if (!processLoadedOptionalDependenciesOnPlugin(dependencyPluginId, dependency.subDescriptor ?: continue, processor)) {
      return false
    }
  }
  return true
}

@Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty")
private fun checkExtensionsCanUnloadWithoutRestart(descriptor: IdeaPluginDescriptorImpl,
                                                   baseDescriptor: IdeaPluginDescriptorImpl?,
                                                   isSubDescriptor: Boolean,
                                                   app: Application,
                                                   optionalDependencyPluginId: PluginId?,
                                                   context: List<IdeaPluginDescriptorImpl>): String? {
  for (extensions in listOf(descriptor.unsortedEpNameToExtensionElements,
                            descriptor.app.extensions,
                            descriptor.project.extensions,
                            descriptor.module.extensions)) {
    if (extensions != null && !extensions.isEmpty()) {
      doCheckExtensionsCanUnloadWithoutRestart(extensions, descriptor, baseDescriptor, isSubDescriptor, app, optionalDependencyPluginId, context)?.let {
        return it
      }
    }
  }
  return null
}

private fun doCheckExtensionsCanUnloadWithoutRestart(extensions: Map<String, List<Element>>,
                                                     descriptor: IdeaPluginDescriptorImpl,
                                                     baseDescriptor: IdeaPluginDescriptorImpl?,
                                                     isSubDescriptor: Boolean,
                                                     app: Application,
                                                     optionalDependencyPluginId: PluginId?,
                                                     context: List<IdeaPluginDescriptorImpl>): String? {
  val openedProjects = ProjectUtil.getOpenProjects()
  val anyProject = openedProjects.firstOrNull() ?: ProjectManager.getInstance().defaultProject
  val anyModule = openedProjects.firstOrNull()?.let { ModuleManager.getInstance(it).modules.firstOrNull() }

  for (epName in extensions.keys) {
    val pluginExtensionPoint = findPluginExtensionPoint(baseDescriptor ?: descriptor, epName)
    if (pluginExtensionPoint != null) {
      // descriptor.pluginId is null when we check the optional dependencies of the plugin which is being loaded
      // if an optional dependency of a plugin extends a non-dynamic EP of that plugin, it shouldn't prevent plugin loading
      if (baseDescriptor != null && !isSubDescriptor && !pluginExtensionPoint.isDynamic) {
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
        if (optionalDependencyPluginId != null) {
          return "Plugin ${baseDescriptor?.pluginId} is not unload-safe because of use of non-dynamic EP $epName in plugin $optionalDependencyPluginId that optionally depends on it"
        }
        else {
          return "Plugin ${descriptor.pluginId ?: baseDescriptor?.pluginId} is not unload-safe because of extension to non-dynamic EP $epName"
        }
      }
      continue
    }

    val pluginEP = findPluginExtensionPoint(descriptor, epName)
    if (pluginEP != null) {
      if (!pluginEP.isDynamic) {
        return "Plugin ${descriptor.pluginId ?: baseDescriptor?.pluginId} is not unload-safe because of use of non-dynamic EP $epName in optional dependencies on it"
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
        return "Plugin ${descriptor.pluginId ?: baseDescriptor?.pluginId} is not unload-safe because of extension to non-dynamic EP $epName"
      }
      continue
    }

    return "Plugin ${descriptor.pluginId ?: baseDescriptor?.pluginId} is not unload-safe because of unresolved extension $epName"
  }
  return null
}

private fun findPluginExtensionPoint(pluginDescriptor: IdeaPluginDescriptorImpl, epName: String): ExtensionPointImpl<*>? {
  return findContainerExtensionPoint(pluginDescriptor.app, epName)
         ?: findContainerExtensionPoint(pluginDescriptor.project, epName)
         ?: findContainerExtensionPoint(pluginDescriptor.module, epName)
}

private fun findContainerExtensionPoint(containerDescriptor: ContainerDescriptor, epName: String): ExtensionPointImpl<*>? {
  return containerDescriptor.extensionPoints?.find { it.name == epName }
}

private fun unloadClassLoader(pluginDescriptor: IdeaPluginDescriptorImpl, timeoutMs: Int): Boolean {
  if (timeoutMs == 0) {
    pluginDescriptor.classLoader = null
    return true
  }

  val watcher = GCWatcher.tracking(pluginDescriptor.classLoader)
  ClassLoaderConfigurator.setPluginClassLoaderForMainAndSubPlugins(pluginDescriptor, null)
  return watcher.tryCollect(timeoutMs)
}