// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.fasterxml.jackson.databind.type.TypeFactory
import com.intellij.DynamicBundle.LanguageBundleEP
import com.intellij.configurationStore.jdomSerializer
import com.intellij.configurationStore.runInAutoSaveDisabledMode
import com.intellij.configurationStore.saveProjectsAndApp
import com.intellij.diagnostic.MessagePool
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.diagnostic.hprof.action.SystemTempFilenameSupplier
import com.intellij.diagnostic.hprof.analysis.AnalyzeClassloaderReferencesGraph
import com.intellij.diagnostic.hprof.analysis.HProfAnalysis
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.cancelAndJoinBlocking
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.ui.TopHitCache
import com.intellij.ide.ui.UIThemeProvider
import com.intellij.ide.util.TipAndTrickManager
import com.intellij.idea.IdeaLogger
import com.intellij.lang.Language
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
import com.intellij.openapi.actionSystem.impl.canUnloadActionGroup
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
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.keymap.impl.BundledKeymapBean
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.PotemkinProgress
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.objectTree.ThrowableInterner
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.util.CachedValuesManager
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.ui.IconDeferrer
import com.intellij.ui.mac.touchbar.TouchbarSupport
import com.intellij.util.CachedValuesManagerImpl
import com.intellij.util.MemoryDumpHelper
import com.intellij.util.ReflectionUtil
import com.intellij.util.SystemProperties
import com.intellij.util.containers.WeakList
import com.intellij.util.messages.impl.MessageBusEx
import com.intellij.util.ref.GCWatcher
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.*
import java.util.function.Predicate
import javax.swing.JComponent
import javax.swing.ToolTipManager

private val LOG = logger<DynamicPlugins>()
private val classloadersFromUnloadedPlugins = mutableMapOf<PluginId, WeakList<PluginClassLoader>>()

object DynamicPlugins {
  private var myProcessRun = 0
  private val myProcessCallbacks = mutableListOf<Runnable>()
  private val myLock = Any()

  @JvmStatic
  @JvmOverloads
  fun allowLoadUnloadWithoutRestart(descriptor: IdeaPluginDescriptorImpl,
                                    baseDescriptor: IdeaPluginDescriptorImpl? = null,
                                    context: List<IdeaPluginDescriptorImpl> = emptyList()): Boolean {
    val reason = checkCanUnloadWithoutRestart(module = descriptor, parentModule = baseDescriptor, context = context)
    if (reason != null) {
      LOG.info(reason)
    }
    return reason == null
  }

  /**
   * @return true if the requested enabled state was applied without restart, false if restart is required
   */
  fun loadPlugins(descriptors: Collection<IdeaPluginDescriptorImpl>, project: Project?): Boolean {
    return runProcess {
      updateDescriptorsWithoutRestart(descriptors, load = true) {
        doLoadPlugin(it, project)
      }
    }
  }

  /**
   * @return true if the requested enabled state was applied without restart, false if restart is required
   */
  fun unloadPlugins(
    descriptors: Collection<IdeaPluginDescriptorImpl>,
    project: Project? = null,
    parentComponent: JComponent? = null,
    options: UnloadPluginOptions = UnloadPluginOptions(disable = true),
  ): Boolean {
    return runProcess {
      updateDescriptorsWithoutRestart(descriptors, load = false) {
        doUnloadPluginWithProgress(project, parentComponent, it, options)
      }
    }
  }

  private fun runProcess(process: () -> Boolean): Boolean {
    try {
      synchronized(myLock) {
        myProcessRun++
      }
      return process.invoke()
    }
    finally {
      val callbacks = mutableListOf<Runnable>()
      synchronized(myLock) {
        myProcessRun--
        callbacks.addAll(myProcessCallbacks)
        myProcessCallbacks.clear()
      }
      callbacks.forEach { it.run() }
    }
  }

  fun runAfter(runAlways: Boolean, callback: Runnable) {
    synchronized(myLock) {
      if (myProcessRun > 0) {
        myProcessCallbacks.add(callback)
        return
      }
    }
    if (runAlways) {
      callback.run()
    }
  }

  private fun updateDescriptorsWithoutRestart(
    plugins: Collection<IdeaPluginDescriptorImpl>,
    load: Boolean,
    executor: (IdeaPluginDescriptorImpl) -> Boolean,
  ): Boolean {
    if (plugins.isEmpty()) {
      return true
    }

    val pluginSet = PluginManagerCore.getPluginSet()
    val descriptors = plugins
      .asSequence()
      .distinctBy { it.pluginId }
      .filter { pluginSet.isPluginEnabled(it.pluginId) != load }
      .toList()

    val operationText = if (load) "load" else "unload"
    val message = descriptors.joinToString(prefix = "Plugins to $operationText: [", postfix = "]")
    LOG.info(message)

    if (!descriptors.all { allowLoadUnloadWithoutRestart(it, context = descriptors) }) {
      return false
    }

    // todo plugin installation should be done not in this method
    var allPlugins = pluginSet.allPlugins
    for (descriptor in descriptors) {
      if (!allPlugins.contains(descriptor)) {
        allPlugins = allPlugins + descriptor
      }
    }

    // todo make internal:
    //  1) ModuleGraphBase;
    //  2) SortedModuleGraph;
    //  3) SortedModuleGraph.topologicalComparator;
    //  4) PluginSetBuilder.sortedModuleGraph.
    var comparator = PluginSetBuilder(allPlugins)
      .moduleGraph
      .topologicalComparator

    if (!load) {
      comparator = comparator.reversed()
    }
    for (descriptor in descriptors.sortedWith(comparator)) {
      descriptor.isEnabled = load
      if (!executor.invoke(descriptor)) {
        LOG.info("Failed to $operationText: $descriptor, restart required")
        InstalledPluginsState.getInstance().isRestartRequired = true
        return false
      }
    }

    return true
  }

  fun checkCanUnloadWithoutRestart(module: IdeaPluginDescriptorImpl): String? {
    return checkCanUnloadWithoutRestart(module, parentModule = null)
  }

  /**
   * @param context Plugins which are being loaded at the same time as [module]
   */
  private fun checkCanUnloadWithoutRestart(module: IdeaPluginDescriptorImpl,
                                           parentModule: IdeaPluginDescriptorImpl?,
                                           optionalDependencyPluginId: PluginId? = null,
                                           context: List<IdeaPluginDescriptorImpl> = emptyList(),
                                           checkImplementationDetailDependencies: Boolean = true): String? {
    if (parentModule == null) {
      if (module.isRequireRestart) {
        return "Plugin ${module.pluginId} is explicitly marked as requiring restart"
      }
      if (module.productCode != null && !module.isBundled && !PluginManagerCore.isDevelopedByJetBrains(module)) {
        return "Plugin ${module.pluginId} is a paid plugin"
      }
      if (InstalledPluginsState.getInstance().isRestartRequired) {
        return InstalledPluginsState.RESTART_REQUIRED_MESSAGE
      }
    }

    val pluginSet = PluginManagerCore.getPluginSet()

    if (classloadersFromUnloadedPlugins[module.pluginId]?.isEmpty() == false) {
      return "Not allowing load/unload of ${module.pluginId} because of incomplete previous unload operation for that plugin"
    }
    findMissingRequiredDependency(module, context, pluginSet)?.let { pluginDependency ->
      return "Required dependency ${pluginDependency} of plugin ${module.pluginId} is not currently loaded"
    }

    val app = ApplicationManager.getApplication()

    if (parentModule == null) {
      if (!RegistryManager.getInstance().`is`("ide.plugins.allow.unload")) {
        if (!allowLoadUnloadSynchronously(module)) {
          return "ide.plugins.allow.unload is disabled and synchronous load/unload is not possible for ${module.pluginId}"
        }
        return null
      }

      try {
        app.messageBus.syncPublisher(DynamicPluginListener.TOPIC).checkUnloadPlugin(module)
      }
      catch (e: CannotUnloadPluginException) {
        return e.cause?.localizedMessage ?: "checkUnloadPlugin listener blocked plugin unload"
      }
    }

    if (!Registry.`is`("ide.plugins.allow.unload.from.sources")) {
      if (pluginSet.findEnabledPlugin(module.pluginId) != null && module === parentModule && !module.isUseIdeaClassLoader) {
        val pluginClassLoader = module.pluginClassLoader
        if (pluginClassLoader != null && pluginClassLoader !is PluginClassLoader && !app.isUnitTestMode) {
          return "Plugin ${module.pluginId} is not unload-safe because of use of ${pluginClassLoader.javaClass.name} as the default class loader. " +
                 "For example, the IDE is started from the sources with the plugin."
        }
      }
    }

    val epNameToExtensions = module.epNameToExtensions
    if (!epNameToExtensions.isEmpty()) {
      doCheckExtensionsCanUnloadWithoutRestart(
        extensions = epNameToExtensions,
        descriptor = module,
        baseDescriptor = parentModule,
        app = app,
        optionalDependencyPluginId = optionalDependencyPluginId,
        context = context,
        pluginSet = pluginSet,
      )?.let { return it }
    }

    checkNoComponentsOrServiceOverrides(module)?.let { return it }
    checkUnloadActions(module)?.let { return it }

    for (moduleRef in module.content.modules) {
      if (pluginSet.isModuleEnabled(moduleRef.name)) {
        val subModule = moduleRef.requireDescriptor()
        checkCanUnloadWithoutRestart(module = subModule,
                                     parentModule = module,
                                     optionalDependencyPluginId = null,
                                     context = context)?.let {
          return "$it in optional dependency on ${subModule.pluginId}"
        }
      }
    }

    for (dependency in module.pluginDependencies) {
      if (pluginSet.isPluginEnabled(dependency.pluginId)) {
        checkCanUnloadWithoutRestart(dependency.subDescriptor ?: continue, parentModule ?: module, null, context)?.let {
          return "$it in optional dependency on ${dependency.pluginId}"
        }
      }
    }

    // if not a sub plugin descriptor, then check that any dependent plugin also reloadable
    if (parentModule != null && module !== parentModule) {
      return null
    }

    if (isPluginWhichDependsOnKotlinPluginAndItsIncompatibleWithIt(module)) {
      // force restarting the IDE in the case the dynamic plugin is incompatible with Kotlin Plugin K1/K2 modes KTIJ-24797
      val mode = if (isKotlinPluginK1Mode()) "K1" else "K2"
      return "Plugin ${module.pluginId} depends on the Kotlin plugin in $mode Mode, but the plugin does not support $mode Mode"
    }

    var dependencyMessage: String? = null
    processOptionalDependenciesOnPlugin(module, pluginSet, isLoaded = true) { mainDescriptor, subDescriptor ->
      if (subDescriptor.packagePrefix == null
          || mainDescriptor.pluginId.idString == "org.jetbrains.kotlin" || mainDescriptor.pluginId == PluginManagerCore.JAVA_PLUGIN_ID) {
        dependencyMessage = "Plugin ${subDescriptor.pluginId} that optionally depends on ${module.pluginId}" +
                            " does not have a separate classloader for the dependency"
        return@processOptionalDependenciesOnPlugin false
      }

      dependencyMessage = checkCanUnloadWithoutRestart(subDescriptor, mainDescriptor, subDescriptor.pluginId, context)
      if (dependencyMessage == null) {
        true
      }
      else {
        dependencyMessage = "Plugin ${subDescriptor.pluginId} that optionally depends on ${module.pluginId} requires restart: $dependencyMessage"
        false
      }
    }

    if (dependencyMessage == null && checkImplementationDetailDependencies) {
      val contextWithImplementationDetails = context.toMutableList()
      contextWithImplementationDetails.add(module)
      processImplementationDetailDependenciesOnPlugin(module, pluginSet, contextWithImplementationDetails::add)

      processImplementationDetailDependenciesOnPlugin(module, pluginSet) { dependentDescriptor ->
        // don't check a plugin that is an implementation-detail dependency on the current plugin if it has other disabled dependencies
        // and won't be loaded anyway
        if (findMissingRequiredDependency(dependentDescriptor, contextWithImplementationDetails, pluginSet) == null) {
          dependencyMessage = checkCanUnloadWithoutRestart(module = dependentDescriptor,
                                                           parentModule = null,
                                                           context = contextWithImplementationDetails,
                                                           checkImplementationDetailDependencies = false)
          if (dependencyMessage != null) {
            dependencyMessage = "implementation-detail plugin ${dependentDescriptor.pluginId} which depends on ${module.pluginId}" +
                                " requires restart: $dependencyMessage"
          }
        }
        dependencyMessage == null
      }
    }
    return dependencyMessage
  }

  private fun findMissingRequiredDependency(descriptor: IdeaPluginDescriptorImpl,
                                            context: List<IdeaPluginDescriptorImpl>,
                                            pluginSet: PluginSet): PluginId? {
    for (dependency in descriptor.pluginDependencies) {
      if (!dependency.isOptional &&
          !PluginManagerCore.isModuleDependency(dependency.pluginId) &&
          !pluginSet.isPluginEnabled(dependency.pluginId) &&
          context.none { it.pluginId == dependency.pluginId }) {
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
  fun allowLoadUnloadSynchronously(module: IdeaPluginDescriptorImpl): Boolean {
    val extensions = (module.epNameToExtensions.takeIf { it.isNotEmpty() } ?: module.appContainerDescriptor.extensions)
    if (!extensions.all { it.key == UIThemeProvider.EP_NAME.name || it.key == BundledKeymapBean.EP_NAME.name || it.key == LanguageBundleEP.EP_NAME.name}) {
      return false
    }
    return checkNoComponentsOrServiceOverrides(module) == null && module.actions.isEmpty()
  }

  private fun checkNoComponentsOrServiceOverrides(module: IdeaPluginDescriptorImpl): String? {
    val id = module.pluginId
    return checkNoComponentsOrServiceOverrides(id, module.appContainerDescriptor)
           ?: checkNoComponentsOrServiceOverrides(id, module.projectContainerDescriptor)
           ?: checkNoComponentsOrServiceOverrides(id, module.moduleContainerDescriptor)
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

  fun unloadPluginWithProgress(project: Project? = null,
                               parentComponent: JComponent?,
                               pluginDescriptor: IdeaPluginDescriptorImpl,
                               options: UnloadPluginOptions): Boolean {
    return runProcess {
      doUnloadPluginWithProgress(project, parentComponent, pluginDescriptor, options)
    }
  }

  private fun doUnloadPluginWithProgress(project: Project? = null,
                                         parentComponent: JComponent?,
                                         pluginDescriptor: IdeaPluginDescriptorImpl,
                                         options: UnloadPluginOptions): Boolean {
    var result = false
    if (options.save) {
      runInAutoSaveDisabledMode {
        FileDocumentManager.getInstance().saveAllDocuments()
        runWithModalProgressBlocking(project?.let { ModalTaskOwner.project(it) } ?: ModalTaskOwner.guess(), "") {
          saveProjectsAndApp(true)
        }
      }
    }
    val indicator = PotemkinProgress(IdeBundle.message("plugins.progress.unloading.plugin.title", pluginDescriptor.name),
                                     project,
                                     parentComponent,
                                     null)
    indicator.runInSwingThread {
      result = unloadPluginWithoutProgress(pluginDescriptor, options.withSave(false))
    }
    return result
  }

  data class UnloadPluginOptions(
    var disable: Boolean = true,
    var isUpdate: Boolean = false,
    var save: Boolean = true,
    var requireMemorySnapshot: Boolean = false,
    var waitForClassloaderUnload: Boolean = false,
    var checkImplementationDetailDependencies: Boolean = true,
    var unloadWaitTimeout: Int? = null,
  ) {
    fun withUpdate(isUpdate: Boolean): UnloadPluginOptions = also {
      this.isUpdate = isUpdate
    }

    fun withWaitForClassloaderUnload(waitForClassloaderUnload: Boolean): UnloadPluginOptions = also {
      this.waitForClassloaderUnload = waitForClassloaderUnload
    }

    fun withDisable(disable: Boolean): UnloadPluginOptions = also {
      this.disable = disable
    }

    fun withRequireMemorySnapshot(requireMemorySnapshot: Boolean): UnloadPluginOptions = also {
      this.requireMemorySnapshot = requireMemorySnapshot
    }

    fun withUnloadWaitTimeout(unloadWaitTimeout: Int): UnloadPluginOptions = also {
      this.unloadWaitTimeout = unloadWaitTimeout
    }

    fun withSave(save: Boolean): UnloadPluginOptions = also {
      this.save = save
    }

    fun withCheckImplementationDetailDependencies(checkImplementationDetailDependencies: Boolean): UnloadPluginOptions = also {
      this.checkImplementationDetailDependencies = checkImplementationDetailDependencies
    }
  }

  @JvmOverloads
  fun unloadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl,
                   options: UnloadPluginOptions = UnloadPluginOptions(disable = true)): Boolean {
    return runProcess {
      doUnloadPluginWithProgress(project = null, parentComponent = null, pluginDescriptor, options)
    }
  }

  private fun unloadPluginWithoutProgress(pluginDescriptor: IdeaPluginDescriptorImpl,
                                          options: UnloadPluginOptions = UnloadPluginOptions(disable = true)): Boolean {
    val app = ApplicationManager.getApplication() as ApplicationImpl
    val pluginId = pluginDescriptor.pluginId
    val pluginSet = PluginManagerCore.getPluginSet()

    if (options.checkImplementationDetailDependencies) {
      processImplementationDetailDependenciesOnPlugin(pluginDescriptor, pluginSet) { dependentDescriptor ->
        dependentDescriptor.isEnabled = false
        unloadPluginWithoutProgress(dependentDescriptor, UnloadPluginOptions(waitForClassloaderUnload = false,
                                                                             checkImplementationDetailDependencies = false))
        true
      }
    }

    try {
      TipAndTrickManager.getInstance().closeTipDialog()

      app.messageBus.syncPublisher(DynamicPluginListener.TOPIC).beforePluginUnload(pluginDescriptor, options.isUpdate)
      IdeEventQueue.getInstance().flushQueue()
    }
    catch (e: Exception) {
      logger<DynamicPlugins>().error(e)
      DynamicPluginsUsagesCollector.logDescriptorUnload(pluginDescriptor, success = false)
      return false
    }

    var classLoaderUnloaded: Boolean
    val classLoaders = WeakList<PluginClassLoader>()
    try {
      app.runWriteAction {
        // must be after flushQueue (e.g. https://youtrack.jetbrains.com/issue/IDEA-252010)
        val forbidGettingServicesToken = app.forbidGettingServices("Plugin $pluginId being unloaded.")
        try {
          (pluginDescriptor.pluginClassLoader as? PluginClassLoader)?.let {
            classLoaders.add(it)
          }
          // https://youtrack.jetbrains.com/issue/IDEA-245031
          // mark plugin classloaders as being unloaded to ensure that new extension instances will be not created during unload
          setClassLoaderState(pluginDescriptor, PluginAwareClassLoader.UNLOAD_IN_PROGRESS)

          unloadLoadedOptionalDependenciesOnPlugin(pluginDescriptor, pluginSet = pluginSet, classLoaders = classLoaders)

          unloadDependencyDescriptors(pluginDescriptor, pluginSet, classLoaders)
          unloadModuleDescriptorNotRecursively(pluginDescriptor)

          clearPluginClassLoaderParentListCache(pluginSet)

          app.extensionArea.clearUserCache()
          for (project in ProjectUtil.getOpenProjects()) {
            (project.extensionArea as ExtensionsAreaImpl).clearUserCache()
          }
          clearCachedValues()

          jdomSerializer.clearSerializationCaches()
          TypeFactory.defaultInstance().clearCache()
          TopHitCache.getInstance().clear()
          ActionToolbarImpl.resetAllToolbars()
          PresentationFactory.clearPresentationCaches()
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
            Language.unregisterAllLanguagesIn(classLoader, pluginDescriptor)
          }
          serviceIfCreated<IconDeferrer>()?.clearCache()

          (ApplicationManager.getApplication().messageBus as MessageBusEx).clearPublisherCache()
          @Suppress("TestOnlyProblems")
          (ProjectManager.getInstanceIfCreated() as? ProjectManagerImpl)?.disposeDefaultProjectAndCleanupComponentsForDynamicPluginTests()

          val newPluginSet = pluginSet.withoutModule(
            module = pluginDescriptor,
            disable = options.disable,
          ).createPluginSetWithEnabledModulesMap()

          PluginManagerCore.setPluginSet(newPluginSet)
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
      cancelAndJoinPluginScopes(classLoaders)

      // do it after IdeEventQueue.flushQueue() to ensure that Disposer.isDisposed(...) works as expected in flushed tasks.
      Disposer.clearDisposalTraces()   // ensure we don't have references to plugin classes in disposal backtraces
      ThrowableInterner.clearInternedBacktraces()
      IdeaLogger.ourErrorsOccurred = null   // ensure we don't have references to plugin classes in exception stacktraces
      clearTemporaryLostComponent()

      if (app.isUnitTestMode && pluginDescriptor.pluginClassLoader !is PluginClassLoader) {
        classLoaderUnloaded = true
      }
      else {
        classloadersFromUnloadedPlugins[pluginId] = classLoaders
        ClassLoaderTreeChecker(pluginDescriptor, classLoaders).checkThatClassLoaderNotReferencedByPluginClassLoader()

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

        DynamicPluginsUsagesCollector.logDescriptorUnload(pluginDescriptor, success = classLoaderUnloaded)
      }
    }

    if (!classLoaderUnloaded) {
      setClassLoaderState(pluginDescriptor, PluginAwareClassLoader.ACTIVE)
    }

    ActionToolbarImpl.updateAllToolbarsImmediately(true)

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
      unloadModuleDescriptorNotRecursively(subDescriptor)

      // this additional code is required because in unit tests PluginClassLoader is not used
      if (mainDescriptor !== subDescriptor) {
        subDescriptor.pluginClassLoader = null
      }

      if (dependencyClassloader is PluginClassLoader && classLoader is PluginClassLoader) {
        LOG.info("Detach classloader $dependencyClassloader from $classLoader")
        if (mainDescriptor !== subDescriptor && classLoader.pluginDescriptor === subDescriptor) {
          classLoaders.add(classLoader)
          classLoader.state = PluginAwareClassLoader.UNLOAD_IN_PROGRESS
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
      val classLoader = subDescriptor.pluginClassLoader
      if (!pluginSet.isPluginEnabled(dependency.pluginId)) {
        LOG.assertTrue(classLoader == null,
                       "Expected not to have any sub descriptor classloader when dependency ${dependency.pluginId} is not loaded")
        continue
      }

      if (classLoader is PluginClassLoader && classLoader.pluginDescriptor === subDescriptor) {
        classLoaders.add(classLoader)
        classLoader.state = PluginAwareClassLoader.UNLOAD_IN_PROGRESS
      }

      unloadDependencyDescriptors(subDescriptor, pluginSet, classLoaders)
      unloadModuleDescriptorNotRecursively(subDescriptor)
      subDescriptor.pluginClassLoader = null
    }

    for (module in plugin.content.modules) {
      val subDescriptor = module.requireDescriptor()

      val classLoader = subDescriptor.pluginClassLoader ?: continue
      if (classLoader is PluginClassLoader && classLoader.pluginDescriptor === subDescriptor) {
        classLoaders.add(classLoader)
        classLoader.state = PluginAwareClassLoader.UNLOAD_IN_PROGRESS
      }

      unloadModuleDescriptorNotRecursively(subDescriptor)
      subDescriptor.pluginClassLoader = null
    }
  }

  internal fun notify(@NlsContexts.NotificationContent text: String, notificationType: NotificationType, vararg actions: AnAction) {
    val notification = UpdateChecker.getNotificationGroupForPluginUpdateResults().createNotification(text, notificationType)
    for (action in actions) {
      notification.addAction(action)
    }
    notification.notify(null)
  }

  // PluginId cannot be used to unload related resources because one plugin descriptor may consist of several sub descriptors,
  // each of them depends on presense of another plugin, here not the whole plugin is unloaded, but only one part.
  private fun unloadModuleDescriptorNotRecursively(module: IdeaPluginDescriptorImpl) {
    val app = ApplicationManager.getApplication() as ApplicationImpl
    (ActionManager.getInstance() as ActionManagerImpl).unloadActions(module)

    if (module.pluginId.idString == "com.intellij.jsp") {
      println("Unloading")
    }
    val openedProjects = ProjectUtil.getOpenProjects().asList()
    val appExtensionArea = app.extensionArea
    val priorityUnloadListeners = mutableListOf<Runnable>()
    val unloadListeners = mutableListOf<Runnable>()
    unregisterUnknownLevelExtensions(module.epNameToExtensions, module, appExtensionArea, openedProjects,
                                     priorityUnloadListeners, unloadListeners)
    for (epName in module.appContainerDescriptor.extensions.keys) {
      appExtensionArea.unregisterExtensions(extensionPointName = epName,
                                            pluginDescriptor = module,
                                            priorityListenerCallbacks = priorityUnloadListeners,
                                            listenerCallbacks = unloadListeners)
    }
    for (epName in module.projectContainerDescriptor.extensions.keys) {
      for (project in openedProjects) {
        (project.extensionArea as ExtensionsAreaImpl).unregisterExtensions(extensionPointName = epName,
                                                                           pluginDescriptor = module,
                                                                           priorityListenerCallbacks = priorityUnloadListeners,
                                                                           listenerCallbacks = unloadListeners)
      }
    }

    // not an error - unsorted goes to module level, see registerExtensions
    unregisterUnknownLevelExtensions(module.moduleContainerDescriptor.extensions, module, appExtensionArea, openedProjects,
                                     priorityUnloadListeners, unloadListeners)

    for (priorityUnloadListener in priorityUnloadListeners) {
      priorityUnloadListener.run()
    }
    for (unloadListener in unloadListeners) {
      unloadListener.run()
    }

    // first, reset all plugin extension points before unregistering, so that listeners don't see plugin in semi-torn-down state
    processExtensionPoints(module, openedProjects) { points, area ->
      area.resetExtensionPoints(points, module)
    }
    // unregister plugin extension points
    processExtensionPoints(module, openedProjects) { points, area ->
      area.unregisterExtensionPoints(points, module)
    }

    app.unloadServices(module, module.appContainerDescriptor.services)
    val appMessageBus = app.messageBus as MessageBusEx
    module.appContainerDescriptor.listeners?.let { appMessageBus.unsubscribeLazyListeners(module, it) }

    for (project in openedProjects) {
      (project as ComponentManagerImpl).unloadServices(module, module.projectContainerDescriptor.services)
      module.projectContainerDescriptor.listeners?.let {
        ((project as ComponentManagerImpl).messageBus as MessageBusEx).unsubscribeLazyListeners(module, it)
      }

      val moduleServices = module.moduleContainerDescriptor.services
      for (ideaModule in ModuleManager.getInstance(project).modules) {
        (ideaModule as ComponentManagerImpl).unloadServices(module, moduleServices)
        createDisposeTreePredicate(module)?.let { Disposer.disposeChildren(ideaModule, it) }
      }

      createDisposeTreePredicate(module)?.let { Disposer.disposeChildren(project, it) }
    }

    appMessageBus.disconnectPluginConnections(Predicate { aClass ->
      (aClass.classLoader as? PluginClassLoader)?.pluginDescriptor === module
    })

    createDisposeTreePredicate(module)?.let { Disposer.disposeChildren(ApplicationManager.getApplication(), it) }
  }

  private fun unregisterUnknownLevelExtensions(extensionMap: Map<String, List<ExtensionDescriptor>>?,
                                               pluginDescriptor: IdeaPluginDescriptorImpl,
                                               appExtensionArea: ExtensionsAreaImpl,
                                               openedProjects: List<Project>,
                                               priorityUnloadListeners: MutableList<Runnable>,
                                               unloadListeners: MutableList<Runnable>) {
    for (epName in (extensionMap?.keys ?: return)) {
      val isAppLevelEp = appExtensionArea.unregisterExtensions(epName, pluginDescriptor, priorityUnloadListeners,
                                                               unloadListeners)
      if (isAppLevelEp) {
        continue
      }

      for (project in openedProjects) {
        val isProjectLevelEp = (project.extensionArea as ExtensionsAreaImpl)
          .unregisterExtensions(epName, pluginDescriptor, priorityUnloadListeners, unloadListeners)
        if (!isProjectLevelEp) {
          for (module in ModuleManager.getInstance(project).modules) {
            (module.extensionArea as ExtensionsAreaImpl)
              .unregisterExtensions(epName, pluginDescriptor, priorityUnloadListeners, unloadListeners)
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

  @JvmOverloads
  fun loadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl, project: Project? = null): Boolean {
    return runProcess {
      doLoadPlugin(pluginDescriptor, project)
    }
  }

  private fun doLoadPlugin(pluginDescriptor: IdeaPluginDescriptorImpl, project: Project? = null): Boolean {
    var result = false
    val indicator = PotemkinProgress(IdeBundle.message("plugins.progress.loading.plugin.title", pluginDescriptor.name),
                                     project,
                                     null,
                                     null)
    indicator.runInSwingThread {
      result = loadPluginWithoutProgress(pluginDescriptor, checkImplementationDetailDependencies = true)
    }
    return result
  }

  private fun loadPluginWithoutProgress(pluginDescriptor: IdeaPluginDescriptorImpl, checkImplementationDetailDependencies: Boolean = true): Boolean {
    if (classloadersFromUnloadedPlugins[pluginDescriptor.pluginId]?.isEmpty() == false) {
      LOG.info("Requiring restart for loading plugin ${pluginDescriptor.pluginId}" +
               " because previous version of the plugin wasn't fully unloaded")
      return false
    }

    val loadStartTime = System.currentTimeMillis()

    val pluginSet = PluginManagerCore.getPluginSet()
      .withModule(pluginDescriptor)
      .createPluginSetWithEnabledModulesMap()

    val classLoaderConfigurator = ClassLoaderConfigurator(pluginSet)

    // todo loadPluginWithoutProgress should be called per each module, temporary solution
    val pluginWithContentModules = pluginSet.getEnabledModules()
      .filter { it.pluginId == pluginDescriptor.pluginId }
      .filter(classLoaderConfigurator::configureModule)
      .toList()

    val app = ApplicationManager.getApplication() as ApplicationImpl
    app.messageBus.syncPublisher(DynamicPluginListener.TOPIC).beforePluginLoaded(pluginDescriptor)
    app.runWriteAction {
      try {
        PluginManagerCore.setPluginSet(pluginSet)

        val listenerCallbacks = mutableListOf<Runnable>()

        // 4. load into service container
        loadModules(modules = pluginWithContentModules, app = app, listenerCallbacks = listenerCallbacks)
        loadModules(
          modules = optionalDependenciesOnPlugin(dependencyPlugin = pluginDescriptor,
                                                 classLoaderConfigurator = classLoaderConfigurator,
                                                 pluginSet = pluginSet).toList(),
          app = app,
          listenerCallbacks = listenerCallbacks,
        )

        clearPluginClassLoaderParentListCache(pluginSet)
        clearCachedValues()

        listenerCallbacks.forEach(Runnable::run)

        DynamicPluginsUsagesCollector.logDescriptorLoad(pluginDescriptor)
        LOG.info("Plugin ${pluginDescriptor.pluginId} loaded without restart in ${System.currentTimeMillis() - loadStartTime} ms")
      }
      finally {
        app.messageBus.syncPublisher(DynamicPluginListener.TOPIC).pluginLoaded(pluginDescriptor)
      }
    }

    if (checkImplementationDetailDependencies) {
      var implementationDetailsLoadedWithoutRestart = true
      processImplementationDetailDependenciesOnPlugin(pluginDescriptor, pluginSet) { dependentDescriptor ->
        val dependencies = dependentDescriptor.pluginDependencies
        if (dependencies.all { it.isOptional || PluginManagerCore.getPlugin(it.pluginId) != null }) {
          if (!loadPluginWithoutProgress(dependentDescriptor, checkImplementationDetailDependencies = false)) {
            implementationDetailsLoadedWithoutRestart = false
          }
        }
        implementationDetailsLoadedWithoutRestart
      }
      return implementationDetailsLoadedWithoutRestart
    }
    return true
  }

  fun onPluginUnload(parentDisposable: Disposable, callback: Runnable) {
    ApplicationManager.getApplication().messageBus.connect(parentDisposable)
      .subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
        override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
          callback.run()
        }
      })
  }
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

private fun cancelAndJoinPluginScopes(classLoaders: WeakList<PluginClassLoader>) {
  for (classLoader in classLoaders) {
    cancelAndJoinBlocking(classLoader.pluginCoroutineScope, "Plugin ${classLoader.pluginId}") { job, _ ->
      while (job.isActive) {
        ProgressManager.checkCanceled()
        IdeEventQueue.getInstance().flushQueue()
      }
    }
  }
}

private fun saveMemorySnapshot(pluginId: PluginId): Boolean {
  val snapshotDate = SimpleDateFormat("dd.MM.yyyy_HH.mm.ss").format(Date())
  val snapshotFileName = "unload-$pluginId-$snapshotDate.hprof"
  val snapshotPath = System.getProperty("memory.snapshots.path", SystemProperties.getUserHome()) + "/" + snapshotFileName

  MemoryDumpHelper.captureMemoryDump(snapshotPath)

  if (classloadersFromUnloadedPlugins[pluginId]?.isEmpty() != false) {
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

  DynamicPlugins.notify(
    IdeBundle.message("memory.snapshot.captured.text", snapshotPath),
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

private fun processImplementationDetailDependenciesOnPlugin(pluginDescriptor: IdeaPluginDescriptorImpl,
                                                            pluginSet: PluginSet,
                                                            processor: (descriptor: IdeaPluginDescriptorImpl) -> Boolean) {
  processDependenciesOnPlugin(dependencyPlugin = pluginDescriptor,
                              pluginSet = pluginSet,
                              loadStateFilter = LoadStateFilter.ANY,
                              onlyOptional = false) { _, module ->
    if (module.isImplementationDetail) {
      processor(module)
    }
    else {
      true
    }
  }
}

/**
 * @return a Set of modules that depend on [dependencyPlugin]
 */
private fun optionalDependenciesOnPlugin(
  dependencyPlugin: IdeaPluginDescriptorImpl,
  classLoaderConfigurator: ClassLoaderConfigurator,
  pluginSet: PluginSet,
): Set<IdeaPluginDescriptorImpl> {
  // 1. collect optional descriptors
  val dependentPluginsAndItsModule = ArrayList<Pair<IdeaPluginDescriptorImpl, IdeaPluginDescriptorImpl>>()

  processOptionalDependenciesOnPlugin(dependencyPlugin, pluginSet, isLoaded = false) { main, module ->
    dependentPluginsAndItsModule.add(main to module)
    true
  }

  if (dependentPluginsAndItsModule.isEmpty()) {
    return emptySet()
  }

  // 2. sort topologically
  val topologicalComparator = PluginSetBuilder(dependentPluginsAndItsModule.map { it.first })
    .moduleGraph
    .topologicalComparator
  dependentPluginsAndItsModule.sortWith(Comparator { o1, o2 -> topologicalComparator.compare(o1.first, o2.first) })

  return dependentPluginsAndItsModule
    .distinct()
    .filter { (mainDescriptor, moduleDescriptor) ->
      // 3. setup classloaders
      classLoaderConfigurator.configureDependency(mainDescriptor, moduleDescriptor)
    }
    .map { it.second }
    .toSet()
}

private fun loadModules(modules: List<IdeaPluginDescriptorImpl>, app: ApplicationImpl, listenerCallbacks: MutableList<in Runnable>) {
  app.registerComponents(modules = modules, app = app, listenerCallbacks = listenerCallbacks)
  for (openProject in getOpenedProjects()) {
    (openProject as ComponentManagerImpl).registerComponents(modules = modules, app = app, listenerCallbacks = listenerCallbacks)

    for (module in ModuleManager.getInstance(openProject).modules) {
      (module as ComponentManagerImpl).registerComponents(modules = modules, app = app, listenerCallbacks = listenerCallbacks)
    }
  }

  (ActionManager.getInstance() as ActionManagerImpl).registerActions(modules)
}

private fun analyzeSnapshot(hprofPath: String, pluginId: PluginId): String {
  FileChannel.open(Paths.get(hprofPath), StandardOpenOption.READ).use { channel ->
    val analysis = HProfAnalysis(channel, SystemTempFilenameSupplier()) { analysisContext, listProvider, progressIndicator ->
      AnalyzeClassloaderReferencesGraph(analysisContext, listProvider, pluginId.idString).analyze(progressIndicator).mainReport.toString()
    }
    analysis.onlyStrongReferences = true
    analysis.includeClassesAsRoots = false
    analysis.setIncludeMetaInfo(false)
    return analysis.analyze(ProgressManager.getGlobalProgressIndicator() ?: EmptyProgressIndicator())
  }
}

private fun createDisposeTreePredicate(pluginDescriptor: IdeaPluginDescriptorImpl): Predicate<Disposable>? {
  val classLoader = pluginDescriptor.pluginClassLoader as? PluginClassLoader ?: return null
  return Predicate {
    it::class.java.classLoader === classLoader
  }
}

private fun processOptionalDependenciesOnPlugin(
  dependencyPlugin: IdeaPluginDescriptorImpl,
  pluginSet: PluginSet,
  isLoaded: Boolean,
  processor: (pluginDescriptor: IdeaPluginDescriptorImpl, moduleDescriptor: IdeaPluginDescriptorImpl) -> Boolean,
) {
  processDependenciesOnPlugin(
    dependencyPlugin = dependencyPlugin,
    pluginSet = pluginSet,
    onlyOptional = true,
    loadStateFilter = if (isLoaded) LoadStateFilter.LOADED else LoadStateFilter.NOT_LOADED,
    processor = processor,
  )
}

private fun processDependenciesOnPlugin(
  dependencyPlugin: IdeaPluginDescriptorImpl,
  pluginSet: PluginSet,
  loadStateFilter: LoadStateFilter,
  onlyOptional: Boolean,
  processor: (pluginDescriptor: IdeaPluginDescriptorImpl, moduleDescriptor: IdeaPluginDescriptorImpl) -> Boolean,
) {
  val wantedIds = HashSet<String>(1 + dependencyPlugin.content.modules.size)
  wantedIds.add(dependencyPlugin.pluginId.idString)
  for (module in dependencyPlugin.content.modules) {
    wantedIds.add(module.name)
  }

  for (plugin in pluginSet.enabledPlugins) {
    if (plugin === dependencyPlugin) {
      continue
    }

    if (!processOptionalDependenciesInOldFormatOnPlugin(dependencyPluginId = dependencyPlugin.pluginId,
                                                        mainDescriptor = plugin,
                                                        loadStateFilter = loadStateFilter,
                                                        onlyOptional = onlyOptional,
                                                        processor = processor)) {
      return
    }

    for (moduleItem in plugin.content.modules) {
      val module = moduleItem.requireDescriptor()

      if (loadStateFilter != LoadStateFilter.ANY) {
        val isModuleLoaded = module.pluginClassLoader != null
        if (isModuleLoaded != (loadStateFilter == LoadStateFilter.LOADED)) {
          continue
        }
      }

      for (item in module.dependencies.modules) {
        if (wantedIds.contains(item.name) && !processor(plugin, module)) {
          return
        }
      }
      for (item in module.dependencies.plugins) {
        if (dependencyPlugin.pluginId == item.id && !processor(plugin, module)) {
          return
        }
      }
    }
  }
}

private enum class LoadStateFilter {
  LOADED, NOT_LOADED, ANY
}

private fun processOptionalDependenciesInOldFormatOnPlugin(
  dependencyPluginId: PluginId,
  mainDescriptor: IdeaPluginDescriptorImpl,
  loadStateFilter: LoadStateFilter,
  onlyOptional: Boolean,
  processor: (main: IdeaPluginDescriptorImpl, sub: IdeaPluginDescriptorImpl) -> Boolean
): Boolean {
  for (dependency in mainDescriptor.pluginDependencies) {
    if (!dependency.isOptional) {
      if (!onlyOptional && dependency.pluginId == dependencyPluginId && !processor(mainDescriptor, mainDescriptor)) {
        return false
      }
      continue
    }

    val subDescriptor = dependency.subDescriptor ?: continue
    if (loadStateFilter != LoadStateFilter.ANY) {
      val isModuleLoaded = subDescriptor.pluginClassLoader != null
      if (isModuleLoaded != (loadStateFilter == LoadStateFilter.LOADED)) {
        continue
      }
    }

    if (dependency.pluginId == dependencyPluginId && !processor(mainDescriptor, subDescriptor)) {
      return false
    }

    if (!processOptionalDependenciesInOldFormatOnPlugin(
        dependencyPluginId = dependencyPluginId,
        mainDescriptor = subDescriptor,
        loadStateFilter = loadStateFilter,
        onlyOptional = onlyOptional,
        processor = processor)) {
      return false
    }
  }
  return true
}

private fun doCheckExtensionsCanUnloadWithoutRestart(
  extensions: Map<String, List<ExtensionDescriptor>>,
  descriptor: IdeaPluginDescriptorImpl,
  baseDescriptor: IdeaPluginDescriptorImpl?,
  app: Application,
  optionalDependencyPluginId: PluginId?,
  context: List<IdeaPluginDescriptorImpl>,
  pluginSet: PluginSet,
): String? {
  val firstProject = ProjectUtil.getOpenProjects().firstOrNull()
  val anyProject = firstProject ?: ProjectManager.getInstance().defaultProject
  val anyModule = firstProject?.let { ModuleManager.getInstance(it).modules.firstOrNull() }

  val seenPlugins: MutableSet<IdeaPluginDescriptorImpl> = Collections.newSetFromMap(IdentityHashMap())
  epLoop@ for (epName in extensions.keys) {
    seenPlugins.clear()

    fun getNonDynamicUnloadError(optionalDependencyPluginId: PluginId?): String = optionalDependencyPluginId?.let {
      "Plugin ${baseDescriptor?.pluginId} is not unload-safe because of use of non-dynamic EP $epName in plugin $it that optionally depends on it"
    } ?: "Plugin ${descriptor.pluginId} is not unload-safe because of extension to non-dynamic EP $epName"

    val result = findLoadedPluginExtensionPointRecursive(
      pluginDescriptor = baseDescriptor ?: descriptor,
      epName = epName,
      pluginSet = pluginSet,
      context = context,
      seenPlugins = seenPlugins,
    )
    if (result != null) {
      val (pluginExtensionPoint, foundInDependencies) = result // descriptor.pluginId is null when we check the optional dependencies of the plugin which is being loaded
      // if an optional dependency of a plugin extends a non-dynamic EP of that plugin, it shouldn't prevent plugin loading
      if (!pluginExtensionPoint.isDynamic) {
        if (baseDescriptor == null || foundInDependencies) {
          return getNonDynamicUnloadError(null)
        }
        else if (descriptor === baseDescriptor) {
          return getNonDynamicUnloadError(descriptor.pluginId)
        }
      }
      continue
    }

    val ep = app.extensionArea.getExtensionPointIfRegistered<Any>(epName)
             ?: anyProject.extensionArea.getExtensionPointIfRegistered<Any>(epName)
             ?: anyModule?.extensionArea?.getExtensionPointIfRegistered<Any>(epName)
    if (ep != null) {
      if (!ep.isDynamic) {
        return getNonDynamicUnloadError(optionalDependencyPluginId)
      }
      continue
    }

    if (anyModule == null) {
      val corePlugin = pluginSet.findEnabledPlugin(PluginManagerCore.CORE_ID)
      if (corePlugin != null) {
        val coreEP = findPluginExtensionPoint(corePlugin, epName)
        if (coreEP != null) {
          if (!coreEP.isDynamic) {
            return getNonDynamicUnloadError(optionalDependencyPluginId)
          }
          continue
        }
      }
    }

    for (contextPlugin in context) {
      val contextEp = findPluginExtensionPoint(contextPlugin, epName) ?: continue
      if (!contextEp.isDynamic) {
        return getNonDynamicUnloadError(null)
      }
      continue@epLoop
    }

    // special case Kotlin EPs registered via code in Kotlin compiler
    if (epName.startsWith("org.jetbrains.kotlin") && descriptor.pluginId.idString == "org.jetbrains.kotlin") {
      continue
    }

    return "Plugin ${descriptor.pluginId} is not unload-safe because of unresolved extension $epName"
  }
  return null
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
    if (pluginSet.isPluginEnabled(dependency.pluginId) || context.any { it.pluginId == dependency.pluginId }) {
      dependency.subDescriptor?.let { subDescriptor ->
        findLoadedPluginExtensionPointRecursive(subDescriptor, epName, pluginSet, context, seenPlugins)?.let { return it }
      }
      pluginSet.findEnabledPlugin(dependency.pluginId)?.let { dependencyDescriptor ->
        findLoadedPluginExtensionPointRecursive(dependencyDescriptor, epName, pluginSet, context, seenPlugins)?.let { return it.first to true }
      }
    }
  }

  processDirectDependencies(pluginDescriptor, pluginSet) { dependency ->
    findLoadedPluginExtensionPointRecursive(dependency, epName, pluginSet, context, seenPlugins)?.let { return it.first to true }
  }
  return null
}

private inline fun processDirectDependencies(module: IdeaPluginDescriptorImpl,
                                             pluginSet: PluginSet,
                                             processor: (IdeaPluginDescriptorImpl) -> Unit) {
   for (item in module.dependencies.modules) {
     val descriptor = pluginSet.findEnabledModule(item.name)
     if (descriptor != null) {
       processor(descriptor)
    }
  }
  for (item in module.dependencies.plugins) {
    val descriptor = pluginSet.findEnabledPlugin(item.id)
    if (descriptor != null) {
      processor(descriptor)
    }
  }
}

private fun unloadClassLoader(pluginDescriptor: IdeaPluginDescriptorImpl, timeoutMs: Int): Boolean {
  if (timeoutMs == 0) {
    pluginDescriptor.pluginClassLoader = null
    return true
  }

  val watcher = GCWatcher.tracking(pluginDescriptor.pluginClassLoader)
  pluginDescriptor.pluginClassLoader = null
  return watcher.tryCollect(timeoutMs)
}

private fun setClassLoaderState(pluginDescriptor: IdeaPluginDescriptorImpl, state: Int) {
  (pluginDescriptor.pluginClassLoader as? PluginClassLoader)?.state = state
  for (dependency in pluginDescriptor.pluginDependencies) {
    dependency.subDescriptor?.let { setClassLoaderState(it, state) }
  }
}

private fun clearPluginClassLoaderParentListCache(pluginSet: PluginSet) {
  // yes, clear not only enabled plugins, but all, just to be sure; it's a cheap operation
  for (descriptor in pluginSet.allPlugins) {
    (descriptor.pluginClassLoader as? PluginClassLoader)?.clearParentListCache()
  }
}

private fun clearCachedValues() {
  for (project in ProjectUtil.getOpenProjects()) {
    (CachedValuesManager.getManager(project) as? CachedValuesManagerImpl)?.clearCachedValues()
  }
}

private fun checkUnloadActions(module: IdeaPluginDescriptorImpl): String? {
  for (descriptor in module.actions) {
    val element = descriptor.element
    val elementName = descriptor.name
    if (elementName != ActionDescriptorName.action &&
        !(elementName == ActionDescriptorName.group && canUnloadActionGroup(element)) && elementName != ActionDescriptorName.reference) {
      return "Plugin $module is not unload-safe because of action element $elementName"
    }
  }
  return null
}