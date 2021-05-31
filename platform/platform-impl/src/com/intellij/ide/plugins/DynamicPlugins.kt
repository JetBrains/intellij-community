 // Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.fasterxml.jackson.databind.type.TypeFactory
import com.intellij.application.options.RegistryManager
import com.intellij.configurationStore.StoreUtil.Companion.saveDocumentsAndProjectsAndApp
import com.intellij.configurationStore.jdomSerializer
import com.intellij.configurationStore.runInAutoSaveDisabledMode
import com.intellij.diagnostic.MessagePool
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.diagnostic.hprof.action.SystemTempFilenameSupplier
import com.intellij.diagnostic.hprof.analysis.AnalyzeClassloaderReferencesGraph
import com.intellij.diagnostic.hprof.analysis.HProfAnalysis
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
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
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import com.intellij.openapi.extensions.PluginId
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
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.psi.util.CachedValuesManager
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.ui.IconDeferrer
import com.intellij.ui.mac.touchbar.TouchbarSupport
import com.intellij.util.CachedValuesManagerImpl
import com.intellij.util.MemoryDumpHelper
import com.intellij.util.ReflectionUtil
import com.intellij.util.SystemProperties
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.WeakList
import com.intellij.util.messages.impl.MessageBusEx
import com.intellij.util.ref.GCWatcher
import net.sf.cglib.core.ClassNameReader
import org.jetbrains.annotations.NonNls
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.nio.channels.FileChannel
import java.nio.file.FileVisitResult
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.*
import java.util.function.Predicate
import javax.swing.JComponent
import javax.swing.ToolTipManager
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

  /**
   * @return true if the requested enabled state was applied without restart, false if restart is required
   */
  @JvmStatic
  fun loadPlugins(descriptors: Collection<IdeaPluginDescriptor>): Boolean {
    return updateDescriptorsWithoutRestart(descriptors, load = true) {
      loadPlugin(it, checkImplementationDetailDependencies = true)
    }
  }

  /**
   * @return true if the requested enabled state was applied without restart, false if restart is required
   */
  @JvmStatic
  @JvmOverloads
  fun unloadPlugins(
    descriptors: Collection<IdeaPluginDescriptor>,
    project: Project? = null,
    parentComponent: JComponent? = null,
    options: UnloadPluginOptions = UnloadPluginOptions().withDisable(true),
  ): Boolean = updateDescriptorsWithoutRestart(descriptors, load = false) {
    unloadPluginWithProgress(project, parentComponent, it, options)
  }

  private fun updateDescriptorsWithoutRestart(
    plugins: Collection<IdeaPluginDescriptor>,
    load: Boolean,
    predicate: (IdeaPluginDescriptorImpl) -> Boolean,
  ): Boolean {
    if (plugins.isEmpty()) {
      return true
    }

    val loadedPlugins = PluginManagerCore.getLoadedPlugins().map { it.pluginId }
    val descriptors = plugins
      .asSequence()
      .filterIsInstance<IdeaPluginDescriptorImpl>()
      .filterNot { loadedPlugins.contains(it.pluginId) == load }
      .toList()

    val operationText = if (load) "load" else "unload"
    val message = descriptors.joinToString(prefix = "Plugins to $operationText: [", postfix = "]") {
      it.pluginId.idString
    }
    LOG.info(message)

    if (!descriptors.all { allowLoadUnloadWithoutRestart(it, context = descriptors) }) {
      return false
    }

    pluginsSortedByDependency(descriptors, load).forEach { descriptor ->
      descriptor.isEnabled = load

      if (!predicate.invoke(descriptor)) {
        LOG.info("Failed to $operationText: $descriptor, restart required")
        InstalledPluginsState.getInstance().isRestartRequired = true
        return false
      }
    }

    return true
  }

  private fun pluginsSortedByDependency(descriptors: List<IdeaPluginDescriptorImpl>, load: Boolean): List<IdeaPluginDescriptorImpl> {
    val plugins = getTopologicallySorted(descriptors = descriptors, pluginSet = PluginManagerCore.getPluginSet(), withOptional = true)
    return if (load) plugins else plugins.reversed()
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
    if (descriptor.productCode != null && !descriptor.isBundled && !PluginManagerCore.isDevelopedByJetBrains(descriptor)) {
      return "Plugin ${descriptor.pluginId} is a paid plugin"
    }

    if (InstalledPluginsState.getInstance().isRestartRequired) {
      return InstalledPluginsState.RESTART_REQUIRED_MESSAGE
    }
    if (classloadersFromUnloadedPlugins.get(descriptor.pluginId) != null) {
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

    val pluginSet = PluginManagerCore.getPluginSet()

    if (!Registry.`is`("ide.plugins.allow.unload.from.sources")) {
      val loadedPluginDescriptor = if (descriptor === baseDescriptor) {
        pluginSet.findEnabledPlugin(descriptor.pluginId)
      }
      else {
        null
      }

      if (loadedPluginDescriptor != null && !descriptor.isUseIdeaClassLoader &&
          pluginSet.isPluginEnabled(loadedPluginDescriptor.pluginId)) {
        val pluginClassLoader = loadedPluginDescriptor.classLoader
        if (pluginClassLoader != null && pluginClassLoader !is PluginClassLoader && !app.isUnitTestMode) {
          return "Plugin ${descriptor.pluginId} is not unload-safe because of use of ${pluginClassLoader.javaClass.name} as the default class loader. " +
                 "For example, the IDE is started from the sources with the plugin."
        }
      }
    }

    val isSubDescriptor = baseDescriptor != null && descriptor !== baseDescriptor

    val epNameToExtensions = descriptor.epNameToExtensions
    if (epNameToExtensions != null) {
      doCheckExtensionsCanUnloadWithoutRestart(
        extensions = epNameToExtensions,
        descriptor = descriptor,
        baseDescriptor = baseDescriptor,
        isSubDescriptor = isSubDescriptor,
        app = app,
        optionalDependencyPluginId = optionalDependencyPluginId,
        context = context,
        pluginSet = pluginSet,
      )?.let { return it }
    }

    val pluginId = descriptor.pluginId
    checkNoComponentsOrServiceOverrides(pluginId, descriptor)?.let { return it }
    ActionManagerImpl.checkUnloadActions(pluginId, descriptor)?.let { return it }

    for (dependency in descriptor.pluginDependencies) {
      if (pluginSet.isPluginEnabled(dependency.pluginId)) {
        checkCanUnloadWithoutRestart(dependency.subDescriptor ?: continue, baseDescriptor ?: descriptor, null, context)?.let {
          return "$it in optional dependency on ${dependency.pluginId}"
        }
      }
    }

    // if not a sub plugin descriptor, then check that any dependent plugin also reloadable
    if (isSubDescriptor) {
      return null
    }

    var dependencyMessage: String? = null
    processOptionalDependenciesOnPlugin(descriptor, pluginSet, isLoaded = true) { mainDescriptor, subDescriptor ->
      if (!ClassLoaderConfigurationData.isClassloaderPerDescriptorEnabled(mainDescriptor.pluginId, subDescriptor.packagePrefix)) {
        dependencyMessage = "Plugin ${subDescriptor.pluginId} that optionally depends on ${descriptor.pluginId} does not have a separate classloader for the dependency"
        return@processOptionalDependenciesOnPlugin false
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
      processImplementationDetailDependenciesOnPlugin(descriptor, contextWithImplementationDetails::add)

      processImplementationDetailDependenciesOnPlugin(descriptor) { dependentDescriptor ->
        // don't check a plugin that is an implementation-detail dependency on the current plugin if it has other disabled dependencies
        // and won't be loaded anyway
        if (findMissingRequiredDependency(dependentDescriptor, contextWithImplementationDetails) == null) {
          dependencyMessage = checkCanUnloadWithoutRestart(descriptor = dependentDescriptor,
                                                           context = contextWithImplementationDetails,
                                                           checkImplementationDetailDependencies = false)
          if (dependencyMessage != null) {
            dependencyMessage = "implementation-detail plugin ${dependentDescriptor.pluginId} which depends on ${descriptor.pluginId} requires restart: $dependencyMessage"
          }
        }
        dependencyMessage == null
      }
    }
    return dependencyMessage
  }

  private fun findMissingRequiredDependency(descriptor: IdeaPluginDescriptorImpl,
                                            context: List<IdeaPluginDescriptorImpl>): PluginId? {
    for (dependency in descriptor.pluginDependencies) {
      if (!dependency.isOptional &&
          !PluginManagerCore.isModuleDependency(dependency.pluginId) &&
          PluginManagerCore.getLoadedPlugins(null).none { it.pluginId == dependency.pluginId } &&
          context.none { it.pluginId == dependency.pluginId }
      ) {
        return dependency.pluginId
      }
    }
    return null
  }

  /**
   * Checks if the plugin can be loaded/unloaded immediately when the corresponding action is invoked in the
   * plugins settings, without pressing the Apply button.
   */
  @JvmStatic
  fun allowLoadUnloadSynchronously(pluginDescriptor: IdeaPluginDescriptorImpl): Boolean {
    val extensions = (pluginDescriptor.unsortedEpNameToExtensionElements.takeIf { it.isNotEmpty() } ?: pluginDescriptor.appContainerDescriptor.extensions)
    if (extensions != null && !extensions.all {
        it.key == UIThemeProvider.EP_NAME.name ||
        it.key == BundledKeymapBean.EP_NAME.name
      }) {
      return false
    }
    return checkNoComponentsOrServiceOverrides(pluginDescriptor.pluginId,
                                               pluginDescriptor) == null && pluginDescriptor.actions.isNullOrEmpty()
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
    if (containerDescriptor.services.any { it.overrides }) {
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
    val indicator = PotemkinProgress(IdeBundle.message("plugins.progress.unloading.plugin.title", pluginDescriptor.name),
                                     project,
                                     parentComponent,
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
    fun withUpdate(value: Boolean): UnloadPluginOptions {
      isUpdate = value; return this
    }

    fun withWaitForClassloaderUnload(value: Boolean): UnloadPluginOptions {
      waitForClassloaderUnload = value; return this
    }

    fun withDisable(value: Boolean): UnloadPluginOptions {
      disable = value; return this
    }

    fun withRequireMemorySnapshot(value: Boolean): UnloadPluginOptions {
      requireMemorySnapshot = value; return this
    }

    fun withUnloadWaitTimeout(value: Int): UnloadPluginOptions {
      unloadWaitTimeout = value; return this
    }

    fun withSave(value: Boolean): UnloadPluginOptions {
      save = value; return this
    }
  }

  @JvmStatic
  fun unloadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl, options: UnloadPluginOptions = UnloadPluginOptions()): Boolean {
    val app = ApplicationManager.getApplication() as ApplicationImpl
    val pluginId = pluginDescriptor.pluginId
    val pluginSet = PluginManagerCore.getPluginSet()

    if (options.checkImplementationDetailDependencies) {
      processImplementationDetailDependenciesOnPlugin(pluginDescriptor) { dependentDescriptor ->
        dependentDescriptor.isEnabled = false
        unloadPlugin(dependentDescriptor, UnloadPluginOptions(disable = true,
                                                              save = false,
                                                              waitForClassloaderUnload = false,
                                                              checkImplementationDetailDependencies = false))
        true
      }
    }

    var classLoaderUnloaded: Boolean
    val classLoaders = WeakList<PluginClassLoader>()
    try {
      if (options.save) {
        saveDocumentsAndProjectsAndApp(true)
      }
      TipDialog.hideForProject(null)

      app.messageBus.syncPublisher(DynamicPluginListener.TOPIC).beforePluginUnload(pluginDescriptor, options.isUpdate)
      IdeEventQueue.getInstance().flushQueue()
      app.runWriteAction {
        // must be after flushQueue (e.g. https://youtrack.jetbrains.com/issue/IDEA-252010)
        val forbidGettingServicesToken = app.forbidGettingServices("Plugin $pluginId being unloaded.")
        try {
          (pluginDescriptor.pluginClassLoader as? PluginClassLoader)?.let {
            classLoaders.add(it)
          }
          // https://youtrack.jetbrains.com/issue/IDEA-245031
          // mark plugin classloaders as being unloaded to ensure that new extension instances will be not created during unload
          setClassLoaderState(pluginDescriptor, PluginClassLoader.UNLOAD_IN_PROGRESS)

          unloadLoadedOptionalDependenciesOnPlugin(pluginDescriptor, pluginSet = pluginSet, classLoaders = classLoaders)

          unloadDependencyDescriptors(pluginDescriptor, pluginSet, classLoaders)
          unloadPluginDescriptorNotRecursively(pluginDescriptor, true)

          clearPluginClassLoaderParentListCache()

          app.extensionArea.clearUserCache()
          for (project in ProjectUtil.getOpenProjects()) {
            (project.extensionArea as ExtensionsAreaImpl).clearUserCache()
            (project.serviceIfCreated<CachedValuesManager>() as CachedValuesManagerImpl?)?.clearCachedValues()
          }
          jdomSerializer.clearSerializationCaches()
          TypeFactory.defaultInstance().clearCache()
          app.getServiceIfCreated(TopHitCache::class.java)?.clear()
          PresentationFactory.clearPresentationCaches()
          ActionToolbarImpl.resetAllToolbars()
          TouchbarSupport.reloadAllActions()
          (serviceIfCreated<NotificationsManager>() as? NotificationsManagerImpl)?.expireAll()
          MessagePool.getInstance().clearErrors()
          LaterInvocator.purgeExpiredItems()
          FileAttribute.resetRegisteredIds()
          resetFocusCycleRoot()
          clearNewFocusOwner()
          hideTooltip()
          PerformanceWatcher.getInstance().clearFreezeStacktraces()

          for (classLoader in classLoaders) {
            IconLoader.detachClassLoader(classLoader)
            Language.unregisterLanguages(classLoader)
          }
          serviceIfCreated<IconDeferrer>()?.clearCache()

          (ApplicationManager.getApplication().messageBus as MessageBusEx).clearPublisherCache()
          @Suppress("TestOnlyProblems")
          (ProjectManager.getInstanceIfCreated() as? ProjectManagerImpl)?.disposeDefaultProjectAndCleanupComponentsForDynamicPluginTests()

          if (options.disable) {
            // update list of disabled plugins
            PluginManager.getInstance().setPlugins(PluginManagerCore.getPluginSet().allPlugins)
          }
          else {
            PluginManager.getInstance().setPlugins(PluginManagerCore.getPluginSet().allPlugins.minus(pluginDescriptor))
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
      clearCglibStopBacktrace()

      if (app.isUnitTestMode && pluginDescriptor.pluginClassLoader !is PluginClassLoader) {
        classLoaderUnloaded = true
      }
      else {
        for (classLoader in classLoaders) {
          classloadersFromUnloadedPlugins.put(pluginId, classLoader)
        }
        ClassLoaderTreeChecker(pluginDescriptor, classLoaders).checkThatClassLoaderNotReferencedByPluginClassLoader()
        classLoaders.clear()

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
          if ((options.requireMemorySnapshot || (Registry.`is`("ide.plugins.snapshot.on.unload.fail") && !app.isUnitTestMode)) &&
              MemoryDumpHelper.memoryDumpAvailable()) {
            classLoaderUnloaded = saveMemorySnapshot(pluginId)
          }
          else {
            LOG.info("Plugin $pluginId is not unload-safe because class loader cannot be unloaded")
          }
        }
        if (!classLoaderUnloaded) {
          InstalledPluginsState.getInstance().isRestartRequired = true
        }

        val eventId = if (classLoaderUnloaded) "unload.success" else "unload.fail"
        val fuData = FeatureUsageData().addPluginInfo(getPluginInfoByDescriptor(pluginDescriptor))
        @Suppress("DEPRECATION")
        FUCounterUsageLogger.getInstance().logEvent("plugins.dynamic", eventId, fuData)
      }
    }

    if (!classLoaderUnloaded) {
      setClassLoaderState(pluginDescriptor, PluginAwareClassLoader.ACTIVE)
    }

    return classLoaderUnloaded
  }

  private fun resetFocusCycleRoot() {
    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    var focusCycleRoot = focusManager.currentFocusCycleRoot
    if (focusCycleRoot != null) {
      while (focusCycleRoot != null && focusCycleRoot !is IdeFrameImpl) {
        focusCycleRoot = focusCycleRoot.parent
      }
      if (focusCycleRoot is IdeFrameImpl) {
        focusManager.setGlobalCurrentFocusCycleRoot(focusCycleRoot)
      }
      else {
        focusCycleRoot = focusManager.currentFocusCycleRoot
        val dataContext = DataManager.getInstance().getDataContext(focusCycleRoot)
        val project = CommonDataKeys.PROJECT.getData(dataContext)
        if (project != null) {
          val projectFrame = WindowManager.getInstance().getFrame(project)
          if (projectFrame != null) {
            focusManager.setGlobalCurrentFocusCycleRoot(projectFrame)
          }
        }
      }
    }
  }

  private fun unloadLoadedOptionalDependenciesOnPlugin(dependencyPlugin: IdeaPluginDescriptorImpl,
                                                       pluginSet: PluginSet,
                                                       classLoaders: WeakList<PluginClassLoader>) {
    val dependencyClassloader = dependencyPlugin.classLoader
    processOptionalDependenciesOnPlugin(dependencyPlugin, pluginSet, isLoaded = true) { mainDescriptor, subDescriptor ->
      val classLoader = subDescriptor.classLoader
      unloadPluginDescriptorNotRecursively(subDescriptor, false)

      // this additional code is required because in unit tests PluginClassLoader is not used
      if (mainDescriptor !== subDescriptor) {
        subDescriptor.classLoader = null
      }

      if (dependencyClassloader is PluginClassLoader && classLoader is PluginClassLoader) {
        LOG.info("Detach classloader $dependencyClassloader from $classLoader")
        if (mainDescriptor !== subDescriptor && classLoader.pluginDescriptor === subDescriptor) {
          classLoaders.add(classLoader)
          classLoader.state = PluginClassLoader.UNLOAD_IN_PROGRESS
        }
        else if (!classLoader.detachParent(dependencyClassloader)) {
          LOG.warn("Classloader $dependencyClassloader doesn't have $classLoader as parent")
        }
      }
      true
    }
  }

  private fun unloadDependencyDescriptors(plugin: IdeaPluginDescriptorImpl,
                                          pluginSet: PluginSet,
                                          classLoaders: WeakList<PluginClassLoader>) {
    for (dependency in plugin.pluginDependencies) {
      val subDescriptor = dependency.subDescriptor ?: continue
      val classLoader = subDescriptor.classLoader
      if (!pluginSet.isPluginEnabled(dependency.pluginId)) {
        LOG.assertTrue(classLoader == null,
                       "Expected not to have any sub descriptor classloader when dependency ${dependency.pluginId} is not loaded")
        continue
      }

      if (classLoader is PluginClassLoader && classLoader.pluginDescriptor === subDescriptor) {
        classLoaders.add(classLoader)
      }

      unloadDependencyDescriptors(subDescriptor, pluginSet, classLoaders)
      unloadPluginDescriptorNotRecursively(subDescriptor, true)
      subDescriptor.classLoader = null
    }

    for (module in plugin.content.modules) {
      val subDescriptor = module.requireDescriptor()

      val classLoader = subDescriptor.classLoader ?: continue
      if (classLoader is PluginClassLoader && classLoader.pluginDescriptor === subDescriptor) {
        classLoaders.add(classLoader)
      }

      unloadPluginDescriptorNotRecursively(subDescriptor, true)
      subDescriptor.classLoader = null
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
  private fun unloadPluginDescriptorNotRecursively(pluginDescriptor: IdeaPluginDescriptorImpl, clearExtensionPoints: Boolean) {
    val app = ApplicationManager.getApplication() as ApplicationImpl
    (ActionManager.getInstance() as ActionManagerImpl).unloadActions(pluginDescriptor)

    val openedProjects = ProjectUtil.getOpenProjects().asList()
    val appExtensionArea = app.extensionArea
    val priorityUnloadListeners = mutableListOf<Runnable>()
    val unloadListeners = mutableListOf<Runnable>()
    unregisterUnknownLevelExtensions(pluginDescriptor.unsortedEpNameToExtensionElements, pluginDescriptor, appExtensionArea, openedProjects,
                                     priorityUnloadListeners, unloadListeners)
    for ((epName, epExtensions) in (pluginDescriptor.appContainerDescriptor.extensions ?: emptyMap())) {
      appExtensionArea.unregisterExtensions(epName, pluginDescriptor, epExtensions, priorityUnloadListeners, unloadListeners)
    }
    for ((epName, epExtensions) in (pluginDescriptor.projectContainerDescriptor.extensions ?: emptyMap())) {
      for (project in openedProjects) {
        (project.extensionArea as ExtensionsAreaImpl).unregisterExtensions(epName, pluginDescriptor, epExtensions, priorityUnloadListeners,
                                                                           unloadListeners)
      }
    }

    // not an error - unsorted goes to module level, see registerExtensions
    unregisterUnknownLevelExtensions(pluginDescriptor.moduleContainerDescriptor.extensions, pluginDescriptor, appExtensionArea, openedProjects,
                                     priorityUnloadListeners, unloadListeners)

    for (priorityUnloadListener in priorityUnloadListeners) {
      priorityUnloadListener.run()
    }
    for (unloadListener in unloadListeners) {
      unloadListener.run()
    }

    // first, reset all plugin extension points before unregistering, so that listeners don't see plugin in semi-torn-down state
    processExtensionPoints(pluginDescriptor, openedProjects) { points, area -> area.resetExtensionPoints(points, pluginDescriptor) }
    // unregister plugin extension points
    processExtensionPoints(pluginDescriptor, openedProjects) { points, area -> area.unregisterExtensionPoints(points, pluginDescriptor) }

    // Sub-descriptors remain in memory when the dependent plugin is unloaded, and the EP declarations will be needed again when
    // we load the dependent plugin back, so we can't clear the EPs in this situation
    if (clearExtensionPoints) {
      pluginDescriptor.appContainerDescriptor.extensionPoints = null
      pluginDescriptor.projectContainerDescriptor.extensionPoints = null
      pluginDescriptor.moduleContainerDescriptor.extensionPoints = null
    }

    val pluginId = pluginDescriptor.pluginId
    app.unloadServices(pluginDescriptor.appContainerDescriptor.services, pluginId)
    val appMessageBus = app.messageBus as MessageBusEx
    pluginDescriptor.appContainerDescriptor.listeners?.let { appMessageBus.unsubscribeLazyListeners(pluginDescriptor, it) }

    for (project in openedProjects) {
      (project as ComponentManagerImpl).unloadServices(pluginDescriptor.projectContainerDescriptor.services, pluginId)
      pluginDescriptor.projectContainerDescriptor.listeners?.let {
        ((project as ComponentManagerImpl).messageBus as MessageBusEx).unsubscribeLazyListeners(pluginDescriptor, it)
      }

      val moduleServices = pluginDescriptor.moduleContainerDescriptor.services
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

  private fun unregisterUnknownLevelExtensions(extensionMap: Map<String, List<ExtensionDescriptor>>?,
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
                                            processor: (points: List<ExtensionPointDescriptor>, area: ExtensionsAreaImpl) -> Unit) {
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

  fun loadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl): Boolean {
    return loadPlugin(pluginDescriptor, checkImplementationDetailDependencies = true)
  }

  fun loadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl,
                 checkImplementationDetailDependencies: Boolean = true,
                 classLoaderForTest: ClassLoader? = null): Boolean {
    if (classloadersFromUnloadedPlugins[pluginDescriptor.pluginId] != null) {
      LOG.info("Requiring restart for loading plugin ${pluginDescriptor.pluginId}" +
               " because previous version of the plugin wasn't fully unloaded")
      return false
    }

    val loadStartTime = System.currentTimeMillis()
    val app = ApplicationManager.getApplication() as ApplicationImpl
    val pluginSet = PluginManagerCore.getPluginSet().concat(pluginDescriptor)
    val classLoaderConfigurator = ClassLoaderConfigurator(pluginSet)
    classLoaderConfigurator.configure(pluginDescriptor, classLoaderForTest)

    app.messageBus.syncPublisher(DynamicPluginListener.TOPIC).beforePluginLoaded(pluginDescriptor)
    app.runWriteAction {
      try {
        addToLoadedPlugins(pluginDescriptor)
        val listenerCallbacks = mutableListOf<Runnable>()
        loadPluginDescriptor(pluginDescriptor, app, listenerCallbacks)
        loadOptionalDependenciesOnPlugin(pluginDescriptor, classLoaderConfigurator, pluginSet, listenerCallbacks)
        clearPluginClassLoaderParentListCache()

        for (openProject in ProjectUtil.getOpenProjects()) {
          (CachedValuesManager.getManager(openProject) as CachedValuesManagerImpl).clearCachedValues()
        }

        listenerCallbacks.forEach(Runnable::run)

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
      processImplementationDetailDependenciesOnPlugin(pluginDescriptor) { dependentDescriptor ->
        val dependencies = dependentDescriptor.pluginDependencies
        if (dependencies.all { it.isOptional || PluginManagerCore.getPlugin(it.pluginId) != null }) {
          if (!loadPlugin(dependentDescriptor, checkImplementationDetailDependencies = false)) {
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
    val newPlugins = PluginManagerCore.getPluginSet().allPlugins.map {
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
      PluginManager.getInstance().setPlugins(PluginManagerCore.getPluginSet().allPlugins.plus(pluginDescriptor))
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
        val window = when (frame) {
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

  private fun hideTooltip() {
    try {
      val showMethod = ToolTipManager::class.java.declaredMethods.find { it.name == "show" }
      if (showMethod == null) {
        LOG.info("ToolTipManager.show method not found")
        return
      }
      showMethod.isAccessible = true
      showMethod.invoke(ToolTipManager.sharedInstance(), null)
    }
    catch (e: Throwable) {
      LOG.info("Failed to hide tooltip", e)
    }
  }


  private fun clearCglibStopBacktrace() {
    val field = ReflectionUtil.getDeclaredField(ClassNameReader::class.java, "EARLY_EXIT")
    if (field != null) {
      try {
        ThrowableInterner.clearBacktrace((field[null] as Throwable))
      }
      catch (e: Throwable) {
        LOG.info(e)
      }
    }
  }

  private fun clearNewFocusOwner() {
    val field = ReflectionUtil.getDeclaredField(KeyboardFocusManager::class.java, "newFocusOwner")
    if (field != null) {
      try {
        field.set(null, null)
      }
      catch (e: Throwable) {
        LOG.info(e)
      }
    }
  }

  private fun saveMemorySnapshot(pluginId: PluginId): Boolean {
    val snapshotDate = SimpleDateFormat("dd.MM.yyyy_HH.mm.ss").format(Date())
    val snapshotFileName = "unload-$pluginId-$snapshotDate.hprof"
    val snapshotPath = System.getProperty("memory.snapshots.path", SystemProperties.getUserHome()) + "/" + snapshotFileName

    MemoryDumpHelper.captureMemoryDump(snapshotPath)

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

    LOG.info("Plugin $pluginId is not unload-safe because class loader cannot be unloaded. Memory snapshot created at $snapshotPath")
    return false
  }
}

private fun processImplementationDetailDependenciesOnPlugin(pluginDescriptor: IdeaPluginDescriptorImpl,
                                                           processor: (descriptor: IdeaPluginDescriptorImpl) -> Boolean) {
 PluginManager.getInstance().processAllBackwardDependencies(pluginDescriptor, false) { loadedDescriptor ->
   if (loadedDescriptor.isImplementationDetail) {
     if (processor(loadedDescriptor)) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
   }
   else {
     FileVisitResult.CONTINUE
   }
 }
}

/**
 * Load all sub plugins that depend on specified [dependencyPlugin].
 */
private fun loadOptionalDependenciesOnPlugin(dependencyPlugin: IdeaPluginDescriptorImpl,
                                             classLoaderConfigurator: ClassLoaderConfigurator,
                                             pluginSet: PluginSet,
                                             listenerCallbacks: MutableList<Runnable>) {
  // 1. collect optional descriptors
  val mainToModule = LinkedHashMap<IdeaPluginDescriptorImpl, MutableList<IdeaPluginDescriptorImpl>>()

  processOptionalDependenciesOnPlugin(dependencyPlugin, pluginSet, isLoaded = false) { mainDescriptor, subDescriptor ->
    mainToModule.computeIfAbsent(mainDescriptor) { mutableListOf() }.add(subDescriptor)
  }

  if (mainToModule.isEmpty()) {
    return
  }

  // 2. setup classloaders
  classLoaderConfigurator.configureDependenciesIfNeeded(mainToModule, dependencyPlugin)

  val app = ApplicationManager.getApplication() as ComponentManagerImpl
  // 3. load into service container
  for (entry in mainToModule.entries) {
    for (subDescriptor in entry.value) {
      loadPluginDescriptor(subDescriptor, app, listenerCallbacks)
    }
  }
}

private fun clearPluginClassLoaderParentListCache() {
  for (descriptor in PluginManagerCore.getLoadedPlugins(null)) {
    clearPluginClassLoaderParentListCache(descriptor)
  }
}

private fun clearPluginClassLoaderParentListCache(descriptor: IdeaPluginDescriptorImpl) {
  (descriptor.classLoader as? PluginClassLoader ?: return).clearParentListCache()
  for (dependency in descriptor.pluginDependencies) {
    dependency.subDescriptor?.let {
      clearPluginClassLoaderParentListCache(it)
    }
  }
}

private fun loadPluginDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl,
                                app: ComponentManagerImpl,
                                listenerCallbacks: MutableList<Runnable>) {
 val list = listOf(pluginDescriptor)
 app.registerComponents(plugins = list,
                        app = ApplicationManager.getApplication(),
                        precomputedExtensionModel = null,
                        listenerCallbacks = listenerCallbacks)
 for (openProject in ProjectUtil.getOpenProjects()) {
   (openProject as ComponentManagerImpl).registerComponents(list, ApplicationManager.getApplication(), null, listenerCallbacks)
   for (module in ModuleManager.getInstance(openProject).modules) {
     (module as ComponentManagerImpl).registerComponents(list, ApplicationManager.getApplication(), null, listenerCallbacks)
   }
 }

 (ActionManager.getInstance() as ActionManagerImpl).registerActions(list)
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

private fun processOptionalDependenciesOnPlugin(dependencyPlugin: IdeaPluginDescriptorImpl,
                                                pluginSet: PluginSet,
                                                isLoaded: Boolean,
                                                processor: (pluginDescriptor: IdeaPluginDescriptorImpl,
                                                            moduleDescriptor: IdeaPluginDescriptorImpl) -> Boolean) {
  val wantedIds = HashSet<String>(1 + dependencyPlugin.content.modules.size)
  wantedIds.add(dependencyPlugin.id.idString)
  for (module in dependencyPlugin.content.modules) {
    wantedIds.add(module.name)
  }

  for (plugin in pluginSet.loadedPlugins) {
    if (!processOptionalDependenciesInOldFormatOnPlugin(dependencyPlugin.id, plugin, isLoaded, processor)) {
      return
    }

    for (moduleItem in plugin.content.modules) {
      val module = moduleItem.requireDescriptor()

      val isModuleLoaded = module.classLoader != null
      if (isModuleLoaded != isLoaded) {
        continue
      }

      for (item in module.dependencies.modules) {
        if (wantedIds.contains(item.name) && !processor(plugin, module)) {
          return
        }
      }
    }
  }
}

private fun processOptionalDependenciesInOldFormatOnPlugin(dependencyPluginId: PluginId,
                                                           mainDescriptor: IdeaPluginDescriptorImpl,
                                                           isLoaded: Boolean,
                                                           processor: (mainDescriptor: IdeaPluginDescriptorImpl, subDescriptor: IdeaPluginDescriptorImpl) -> Boolean): Boolean {
  for (dependency in mainDescriptor.pluginDependencies) {
    if (!dependency.isOptional) {
      continue
    }

    val subDescriptor = dependency.subDescriptor ?: continue
    val isModuleLoaded = subDescriptor.classLoader != null
    if (isModuleLoaded != isLoaded) {
      continue
    }

    if (dependency.pluginId == dependencyPluginId && !processor(mainDescriptor, subDescriptor)) {
      return false
    }

    if (!processOptionalDependenciesInOldFormatOnPlugin(dependencyPluginId, subDescriptor, isLoaded, processor)) {
      return false
    }
  }
  return true
}

private fun doCheckExtensionsCanUnloadWithoutRestart(extensions: Map<String, List<ExtensionDescriptor>>,
                                                     descriptor: IdeaPluginDescriptorImpl,
                                                     baseDescriptor: IdeaPluginDescriptorImpl?,
                                                     isSubDescriptor: Boolean,
                                                     app: Application,
                                                     optionalDependencyPluginId: PluginId?,
                                                     context: List<IdeaPluginDescriptorImpl>,
                                                     pluginSet: PluginSet): String? {
  val firstProject = ProjectUtil.getOpenProjects().firstOrNull()
  val anyProject = firstProject ?: ProjectManager.getInstance().defaultProject
  val anyModule = firstProject?.let { ModuleManager.getInstance(it).modules.firstOrNull() }

  val seenPlugins: MutableSet<IdeaPluginDescriptorImpl> = Collections.newSetFromMap(IdentityHashMap())
  epLoop@ for (epName in extensions.keys) {
    seenPlugins.clear()
    val result = findLoadedPluginExtensionPointRecursive(pluginDescriptor = baseDescriptor ?: descriptor,
                                                         epName = epName,
                                                         pluginSet = pluginSet,
                                                         context = context,
                                                         seenPlugins = seenPlugins)
    if (result != null) {
      val (pluginExtensionPoint, foundInDependencies) = result
      // descriptor.pluginId is null when we check the optional dependencies of the plugin which is being loaded
      // if an optional dependency of a plugin extends a non-dynamic EP of that plugin, it shouldn't prevent plugin loading
      if (baseDescriptor != null && (!isSubDescriptor || foundInDependencies) && !pluginExtensionPoint.isDynamic) {
        if (foundInDependencies) {
          return "Plugin ${descriptor.id} is not unload-safe because of extension to non-dynamic EP $epName"
        }
        return "Plugin ${baseDescriptor.pluginId} is not unload-safe because of use of non-dynamic EP $epName" +
               " in optional dependency on it: ${descriptor.pluginId}"
      }
      continue
    }

    @Suppress("RemoveExplicitTypeArguments")
    val ep = app.extensionArea.getExtensionPointIfRegistered<Any>(epName)
             ?: anyProject.extensionArea.getExtensionPointIfRegistered<Any>(epName)
             ?: anyModule?.extensionArea?.getExtensionPointIfRegistered<Any>(epName)
    if (ep != null) {
      if (!ep.isDynamic) {
        return getNonDynamicUnloadError(epName, baseDescriptor, descriptor, optionalDependencyPluginId)
      }
      continue
    }

    if (anyModule == null) {
      val corePlugin = pluginSet.findEnabledPlugin(PluginManagerCore.CORE_ID)
      if (corePlugin != null) {
        val coreEP = findPluginExtensionPoint(corePlugin, epName)
        if (coreEP != null) {
          if (!coreEP.isDynamic) {
            return getNonDynamicUnloadError(epName, baseDescriptor, descriptor, optionalDependencyPluginId)
          }
          continue
        }
      }
    }

    for (contextPlugin in context) {
      val contextEp = findPluginExtensionPoint(contextPlugin, epName) ?: continue
      if (!contextEp.isDynamic) {
        return "Plugin ${descriptor.id} is not unload-safe because of extension to non-dynamic EP $epName"
      }
      continue@epLoop
    }

    // special case Kotlin EPs registered via code in Kotlin compiler
    if (epName.startsWith("org.jetbrains.kotlin") && descriptor.id.idString == "org.jetbrains.kotlin") {
      continue
    }

    return "Plugin ${descriptor.id} is not unload-safe because of unresolved extension $epName"
  }
  return null
}

private fun getNonDynamicUnloadError(epName: String,
                                     baseDescriptor: IdeaPluginDescriptorImpl?,
                                     descriptor: IdeaPluginDescriptorImpl,
                                     optionalDependencyPluginId: PluginId?): String {
  if (optionalDependencyPluginId != null) {
    return "Plugin ${baseDescriptor?.pluginId} is not unload-safe because of use of non-dynamic EP $epName in plugin $optionalDependencyPluginId that optionally depends on it"
  }
  else {
    return "Plugin ${descriptor.id} is not unload-safe because of extension to non-dynamic EP $epName"
  }
}

private fun findPluginExtensionPoint(pluginDescriptor: IdeaPluginDescriptorImpl, epName: String): ExtensionPointDescriptor? {
  fun findContainerExtensionPoint(containerDescriptor: ContainerDescriptor): ExtensionPointDescriptor? {
    return containerDescriptor.extensionPoints?.find { it.nameEquals(epName, pluginDescriptor) }
  }

  return findContainerExtensionPoint(pluginDescriptor.appContainerDescriptor)
         ?: findContainerExtensionPoint(pluginDescriptor.projectContainerDescriptor)
         ?: findContainerExtensionPoint(pluginDescriptor.moduleContainerDescriptor)
}

private fun findLoadedPluginExtensionPointRecursive(pluginDescriptor: IdeaPluginDescriptorImpl,
                                                    epName: String,
                                                    pluginSet: PluginSet,
                                                    context: List<IdeaPluginDescriptorImpl>,
                                                    seenPlugins: MutableSet<IdeaPluginDescriptorImpl>): Pair<ExtensionPointDescriptor, Boolean>? {
  if (!seenPlugins.add(pluginDescriptor)) {
    return null
  }

  findPluginExtensionPoint(pluginDescriptor, epName)?.let { return it to false }
  for (dependency in pluginDescriptor.pluginDependencies) {
    if (pluginSet.isPluginEnabled(dependency.pluginId) || context.any { it.id == dependency.pluginId }) {
      dependency.subDescriptor?.let { subDescriptor ->
        findLoadedPluginExtensionPointRecursive(subDescriptor, epName, pluginSet, context, seenPlugins)?.let { return it }
      }
      pluginSet.findEnabledPlugin(dependency.pluginId)?.let { dependencyDescriptor ->
        findLoadedPluginExtensionPointRecursive(dependencyDescriptor, epName, pluginSet, context, seenPlugins)?.let { return it.first to true }
      }
    }
  }

  processDirectDependencies(pluginDescriptor, pluginSet) {
    findLoadedPluginExtensionPointRecursive(it, epName, pluginSet, context, seenPlugins)?.let { return it.first to true }
  }
  return null
}

private fun unloadClassLoader(pluginDescriptor: IdeaPluginDescriptorImpl, timeoutMs: Int): Boolean {
  if (timeoutMs == 0) {
    pluginDescriptor.classLoader = null
    return true
  }

  val watcher = GCWatcher.tracking(pluginDescriptor.classLoader)
  pluginDescriptor.classLoader = null
  return watcher.tryCollect(timeoutMs)
}

private fun setClassLoaderState(pluginDescriptor: IdeaPluginDescriptorImpl, state: Int) {
  (pluginDescriptor.classLoader as? PluginClassLoader)?.state = state
  for (dependency in pluginDescriptor.pluginDependencies) {
    dependency.subDescriptor?.let { setClassLoaderState(it, state) }
  }
}