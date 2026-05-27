// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.IdeBundle
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

private val LOG = Logger.getInstance(DynamicPlugins::class.java)

@ApiStatus.Internal
object DynamicPlugins {
  fun interface PluginStateValidator {
    /** @return a log message explaining why the state isn't expected, or null otherwise */
    fun validate(resolvedPluginState: ResolvedPluginSet): @NonNls String?
  }

  /**
   * Checks that it is possible to dynamically reconfigure the plugin subsystem, i.e., bring it to the new state that is calculated using
   * the currently known set of plugins and an actual [PluginInitializationContext].
   *
   * @param addNewCustomPlugins newly installed/updated plugins that weren't in the context before
   * @param forceRemovePlugins plugins that should be excluded from the context completely (so it appears as they don't exist at all anymore)
   * @param extraStateValidator additional checks of the target state can be done there (e.g. that a certain plugin loads). See [expectPluginsState]
   */
  @RequiresReadLockAbsence
  @IntellijInternalApi
  suspend fun checkCanReconfigureWithoutRestart(
    addNewCustomPlugins: List<PluginMainDescriptor>,
    forceRemovePlugins: List<PluginMainDescriptor>,
    extraStateValidator: PluginStateValidator,
  ): Boolean {
    DynamicPluginsSupport.getInstance()?.let { instance ->
      val newState = computeNewPluginsState(addNewCustomPlugins, forceRemovePlugins, forceExclude = true)
      // old plugin set resolver is already dropped, so with new dynamic plugins support this thing is expected to be always present
      val resolvedPluginSet = newState.resolvedPluginSet ?: error("resolved plugin set is not set")
      extraStateValidator.validate(resolvedPluginSet)?.let {
        LOG.info("new plugins state did not meet expectations: $it")
        return false
      }
      return instance.validateDynamicTransitionPossible(newState) == null
    }

    if (forceRemovePlugins.isNotEmpty()) {
      return forceRemovePlugins.all {
        DynamicPluginsLegacyImpl.allowLoadUnloadWithoutRestart(it, context = addNewCustomPlugins)
      }
    } else {
      return addNewCustomPlugins.all {
        DynamicPluginsLegacyImpl.allowLoadUnloadWithoutRestart(it) // yep, old implementation assumes unloading even if we want to load the plugin...
      }
    }
  }

  /**
   * Tries to perform a dynamic reconfiguration of plugins: the currently known set of plugins is recalculated against
   * an actual [PluginInitializationContext] instance. If the produced [module graph][ResolvedPluginSet] is different from the currently used one,
   * an attempt is made to unload no longer needed module groups and load new ones.
   *
   * This method may employ its own modality state. To check reconfiguration feasibility completely in the background, use [checkCanReconfigureWithoutRestart].
   *
   * @param project a project that originated the reconfiguration attempt
   * @param addNewCustomPlugins newly installed/updated plugins that weren't in the context before
   * @param forceRemovePlugins plugins that should be excluded from the context completely (so it appears as they don't exist at all anymore)
   * @param extraStateValidator additional checks of the target state can be done there (e.g. that a certain plugin loads). See [expectPluginsState]
   */
  @RequiresReadLockAbsence
  @IntellijInternalApi
  suspend fun reconfigure(
    project: Project?,
    addNewCustomPlugins: List<PluginMainDescriptor>,
    forceRemovePlugins: List<PluginMainDescriptor>,
    extraStateValidator: PluginStateValidator,
  ): Boolean {
    LOG.trace("dynamic plugins reconfiguration attempt")

    DynamicPluginsSupport.getInstance()?.let { instance ->
      val title = when {
        forceRemovePlugins.isEmpty() -> when {
          addNewCustomPlugins.isEmpty() -> IdeBundle.message("modal.progress.title.reconfiguring.plugins")
          addNewCustomPlugins.size == 1 -> IdeBundle.message("modal.progress.title.loading.plugin", addNewCustomPlugins[0].name)
          else -> IdeBundle.message("modal.progress.title.loading.plugins")
        }
        addNewCustomPlugins.isEmpty() -> when {
          // forceRemovePlugins.isEmpty() -> already covered above
          forceRemovePlugins.size == 1 -> IdeBundle.message("modal.progress.title.unloading.plugin", forceRemovePlugins[0].name)
          else -> IdeBundle.message("modal.progress.title.unloading.plugins")
        }
        else -> IdeBundle.message("modal.progress.title.reconfiguring.plugins")
      }
      return withModalProgress(
        project?.let { ModalTaskOwner.project(it) } ?: ModalTaskOwner.guess(),
        title,
        cancellation = TaskCancellation.nonCancellable()
      ) {
        val newState = computeNewPluginsState(addNewCustomPlugins, forceRemovePlugins, forceExclude = true)
        // old plugin set resolver is already dropped, so with new dynamic plugins support this thing is expected to be always present
        val resolvedPluginSet = newState.resolvedPluginSet ?: error("resolved plugin set is not set")
        extraStateValidator.validate(resolvedPluginSet)?.let {
          LOG.info("new plugins state did not meet expectations: $it")
          return@withModalProgress false
        }
        val result = instance.performDynamicTransition(newState)
        result is DynamicPluginsTransitionResult.Success
      }
    }

    return withContext(Dispatchers.EDT) {
      if (forceRemovePlugins.isNotEmpty()) {
        val unloaded = DynamicPluginsLegacyImpl.unloadPlugins(forceRemovePlugins, project = project)
        if (!unloaded) {
          return@withContext false
        }
      }
      if (addNewCustomPlugins.isNotEmpty()) {
        val loaded = DynamicPluginsLegacyImpl.loadPlugins(addNewCustomPlugins, project)
        if (!loaded) {
          return@withContext false
        }
      }
      true
    }
  }

  /**
   * Helper method for [checkCanReconfigureWithoutRestart]
   */
  fun expectPluginsState(
    expectToLoad: List<PluginId> = emptyList(),
    expectNotToLoad: List<PluginId> = emptyList(),
  ): PluginStateValidator = validator@{ state ->
    for (id in expectToLoad) {
      val plugin = state.originalPluginSet.resolvePluginId(id)
      if (plugin == null) {
        return@validator "plugin $id was expected to be loaded but is not found in the target plugin state"
      }
      if (!state.isResolved(plugin)) {
        return@validator "plugin ${plugin.shortLogDescription} was expected to be loaded but was excluded"
      }
    }
    for (id in expectNotToLoad) {
      val plugin = state.originalPluginSet.resolvePluginId(id)
      if (plugin != null && state.isResolved(plugin)) {
        return@validator "plugin ${plugin.shortLogDescription} was expected not to be loaded but was included in the module graph"
      }
    }
    null
  }

  /**
   * @return true if the requested enabled state was applied without restart, false if restart is required
   */
  @RequiresEdt(generateAssertion = false)
  fun loadPlugins(plugins: List<PluginMainDescriptor>, project: Project?): Boolean {
    DynamicPluginsSupport.getInstance()?.let { instance ->
      return runWithModalProgressBlocking(
        project?.let { ModalTaskOwner.project(it) } ?: ModalTaskOwner.guess(),
        IdeBundle.message("modal.progress.title.loading.plugins"),
        cancellation = TaskCancellation.nonCancellable()
      ) {
        val newState = computeNewPluginsState(plugins, emptyList())
        val result = instance.performDynamicTransition(newState)
        result is DynamicPluginsTransitionResult.Success
      }
    }
    return DynamicPluginsLegacyImpl.loadPlugins(plugins, project)
  }

  @RequiresEdt(generateAssertion = false)
  @JvmOverloads
  fun loadPlugin(pluginDescriptor: PluginMainDescriptor, project: Project? = null): Boolean {
    DynamicPluginsSupport.getInstance()?.let { instance ->
      return runWithModalProgressBlocking(
        project?.let { ModalTaskOwner.project(it) } ?: ModalTaskOwner.guess(),
        IdeBundle.message("modal.progress.title.loading.plugin", pluginDescriptor.name),
        cancellation = TaskCancellation.nonCancellable()
      ) {
        val newState = computeNewPluginsState(listOf(pluginDescriptor), emptyList())
        val result = instance.performDynamicTransition(newState)
        result is DynamicPluginsTransitionResult.Success
      }
    }
    return DynamicPluginsLegacyImpl.loadPlugin(pluginDescriptor, project)
  }

  /**
   * @return true if the requested enabled state was applied without restart, false if restart is required
   */
  @RequiresEdt(generateAssertion = false)
  fun unloadPlugins(
    plugins: List<PluginMainDescriptor>,
    project: Project? = null,
    parentComponent: JComponent? = null,
    options: UnloadPluginOptions = UnloadPluginOptions(disable = true),
  ): Boolean {
    DynamicPluginsSupport.getInstance()?.let { instance ->
      return runWithModalProgressBlocking(
        project?.let { ModalTaskOwner.project(it) } ?: ModalTaskOwner.guess(),
        IdeBundle.message("modal.progress.title.unloading.plugins"),
        cancellation = TaskCancellation.nonCancellable()
      ) {
        val newState = computeNewPluginsState(emptyList(), plugins)
        val result = instance.performDynamicTransition(newState)
        result is DynamicPluginsTransitionResult.Success
      }
    }
    return DynamicPluginsLegacyImpl.unloadPlugins(plugins, project, parentComponent, options)
  }

  @RequiresEdt(generateAssertion = false)
  @JvmOverloads
  fun unloadPlugin(pluginDescriptor: PluginMainDescriptor,
                   options: UnloadPluginOptions = UnloadPluginOptions(disable = true)): Boolean {
    DynamicPluginsSupport.getInstance()?.let { instance ->
      return runWithModalProgressBlocking(
        ModalTaskOwner.guess(),
        IdeBundle.message("modal.progress.title.unloading.plugin", pluginDescriptor.name),
        cancellation = TaskCancellation.nonCancellable()
      ) {
        val newState = computeNewPluginsState(emptyList(), listOf(pluginDescriptor))
        val result = instance.performDynamicTransition(newState)
        result is DynamicPluginsTransitionResult.Success
      }
    }
    return DynamicPluginsLegacyImpl.unloadPlugin(pluginDescriptor, options)
  }

  @RequiresEdt(generateAssertion = false)
  fun unloadPluginWithProgress(project: Project? = null,
                               parentComponent: JComponent?,
                               pluginDescriptor: PluginMainDescriptor,
                               options: UnloadPluginOptions): Boolean {
    DynamicPluginsSupport.getInstance()?.let { instance ->
      return runWithModalProgressBlocking(
        project?.let { ModalTaskOwner.project(it) } ?: ModalTaskOwner.guess(),
        IdeBundle.message("modal.progress.title.unloading.plugin", pluginDescriptor.name),
        cancellation = TaskCancellation.nonCancellable()
      ) {
        val newState = computeNewPluginsState(emptyList(), listOf(pluginDescriptor))
        val result = instance.performDynamicTransition(newState)
        result is DynamicPluginsTransitionResult.Success
      }
    }
    return DynamicPluginsLegacyImpl.unloadPluginWithProgress(project, parentComponent, pluginDescriptor, options)
  }

  @Deprecated("use checkCanLoadWithoutRestart or checkCanUnloadWithoutRestart instead")
  @RequiresBackgroundThread(generateAssertion = false)
  @JvmStatic
  @JvmOverloads
  fun allowLoadUnloadWithoutRestart(descriptor: IdeaPluginDescriptorImpl,
                                    baseDescriptor: IdeaPluginDescriptorImpl? = null,
                                    context: List<IdeaPluginDescriptorImpl> = emptyList()): Boolean {
    DynamicPluginsSupport.getInstance()?.let { instance ->
      require(descriptor is PluginMainDescriptor) { "PluginMainDescriptor expected" }
      if (context.any { it !is PluginMainDescriptor }) throw IllegalArgumentException("Context must contain only PluginMainDescriptor instances")
      @Suppress("UNCHECKED_CAST")
      val newState = computeNewPluginsState(context as List<PluginMainDescriptor>, listOf(descriptor)) // treat as unload
      return runBlockingMaybeCancellable {
        instance.validateDynamicTransitionPossible(newState) == null
      }
    }
    return DynamicPluginsLegacyImpl.allowLoadUnloadWithoutRestart(descriptor, baseDescriptor, context)
  }

  @RequiresBackgroundThread(generateAssertion = false)
  fun checkCanUnloadWithoutRestart(module: IdeaPluginDescriptorImpl): String? {
    DynamicPluginsSupport.getInstance()?.let { instance ->
      require(module is PluginMainDescriptor) { "PluginMainDescriptor expected" }
      val newState = computeNewPluginsState(emptyList(), listOf(module))
      // old plugin set resolver is already dropped, so with new dynamic plugins support this thing is expected to be always present
      val resolvedPluginSet = newState.resolvedPluginSet ?: error("resolved plugin set is not set")
      expectPluginsState(expectNotToLoad = listOf(module.pluginId)).validate(resolvedPluginSet)?.let {
        return it
      }
      return runBlockingMaybeCancellable {
        instance.validateDynamicTransitionPossible(newState)?.reason?.logMessage
      }
    }
    return DynamicPluginsLegacyImpl.checkCanUnloadWithoutRestart(module)
  }

  @RequiresBackgroundThread(generateAssertion = false)
  fun checkCanLoadWithoutRestart(plugin: PluginMainDescriptor): String? {
    DynamicPluginsSupport.getInstance()?.let { instance ->
      val newState = computeNewPluginsState(listOf(plugin), listOf())
      // old plugin set resolver is already dropped, so with new dynamic plugins support this thing is expected to be always present
      val resolvedPluginSet = newState.resolvedPluginSet ?: error("resolved plugin set is not set")
      expectPluginsState(expectToLoad = listOf(plugin.pluginId)).validate(resolvedPluginSet)?.let {
        return it
      }
      return runBlockingMaybeCancellable {
        instance.validateDynamicTransitionPossible(newState)?.reason?.logMessage
      }
    }
    return DynamicPluginsLegacyImpl.checkCanUnloadWithoutRestart(plugin) // old impl always assumes unload :igor-dead-inside:
  }

  /**
   * Checks if the plugin can be loaded/unloaded immediately when the corresponding action is invoked in the
   * plugins settings, without pressing the Apply button.
   */
  // TODO migrate to isUIOnlyDynamicPlugin
  @JvmStatic
  fun allowLoadUnloadSynchronously(module: IdeaPluginDescriptorImpl): Boolean {
    return DynamicPluginsLegacyImpl.allowLoadUnloadSynchronously(module)
  }

  internal fun notify(@NlsContexts.NotificationContent text: String, notificationType: NotificationType, vararg actions: AnAction) {
    return DynamicPluginsLegacyImpl.notify(text, notificationType, *actions)
  }

  /**
   * Checks if a given plugin affects only the UI representation of the IDE.
   *
   * Acts as an allowlist condition to enable some features for "UI-only" plugins.
   */
  // TODO should we demand their "statelessness"?
  fun isUIOnlyDynamicPlugin(plugin: PluginMainDescriptor): Boolean {
    return DynamicPluginsLegacyImpl.isUIOnlyDynamicPlugin(plugin)
  }

  // TODO imprecise naming
  internal fun isUIOnlyExtension(extensionFqn: String): Boolean {
    return DynamicPluginsLegacyImpl.isUIOnlyExtension(extensionFqn)
  }

  fun onPluginUnload(parentDisposable: Disposable, callback: Runnable) {
    return DynamicPluginsLegacyImpl.onPluginUnload(parentDisposable, callback)
  }

  private fun computeNewPluginsState(include: List<PluginMainDescriptor>, exclude: List<PluginMainDescriptor>, forceExclude: Boolean = false): PluginSet {
    LOG.info("Computing new plugins state with" +
             " include=" + include.joinToString(prefix = "[", postfix = "]") { it.shortLogDescription } +
             " and exclude=" + exclude.joinToString(prefix = "[", postfix = "]") { it.shortLogDescription } +
             ", forceExclude=" + forceExclude)
    val newInitContext = ProductPluginInitContext()
    val currentSet = PluginManagerCore.getPluginSet()

    // name shadowing intended
    val exclude = exclude.filter {
      forceExclude || !newInitContext.isPluginDisabled(it.pluginId)
    }.toSet()
    if (exclude.isNotEmpty()) {
      LOG.warn("Following plugins will be removed from context completely: ${exclude.joinToString { it.shortLogDescription }}")
    }

    val totalExistingSet = currentSet.input.discoveryResult.pluginLists.flatMap { it.plugins }.toSet()
    // name shadowing intended
    val (alreadyPresent, include) = include.partition { it in totalExistingSet }
    if (alreadyPresent.isNotEmpty()) {
      LOG.info("Following plugins are already present in the context: ${alreadyPresent.joinToString { it.shortLogDescription }}")
    }

    val newPluginLists = if (include.isEmpty() && exclude.isEmpty()) {
      currentSet.input.discoveryResult.pluginLists
    } else {
      val result = ArrayList<DiscoveredPluginsList>()
      for (discoveredList in currentSet.input.discoveryResult.pluginLists) {
        if (discoveredList.source == PluginsSourceContext.Custom) {
          val newCustom = (discoveredList.plugins - exclude + include).distinct()
          result.add(DiscoveredPluginsList(newCustom, discoveredList.source))
        } else {
          result.add(DiscoveredPluginsList(discoveredList.plugins - exclude, discoveredList.source))
        }
      }
      result
    }

    val newDiscoveryResult = PluginsDiscoveryResult.build(
      newPluginLists
    )

    val incompletePlugins = mutableMapOf<PluginId, PluginMainDescriptor> ()
    val pluginsToLoad = newInitContext.selectPluginsToLoad(newDiscoveryResult) { plugin, reason ->
      incompletePlugins[plugin.pluginId] = plugin
    }
    val resolvedSet = newInitContext.resolveConstraints(pluginsToLoad)
    PluginInitializationDiagnosticUtils.logExclusionTree(resolvedSet, incompletePlugins)

    val newState = PluginManagerCore.adaptResolvedPluginSetAsOldPluginSet(
      PluginSubsystemInput(newInitContext, newDiscoveryResult),
      resolvedSet,
    ) {
      // TODO handle later, not important right now
    }

    return newState.first
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
}