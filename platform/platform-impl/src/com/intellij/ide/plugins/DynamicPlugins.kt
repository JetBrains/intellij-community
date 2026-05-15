// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.IdeBundle
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

private val LOG = Logger.getInstance(DynamicPlugins::class.java)

@ApiStatus.Internal
object DynamicPlugins {
  /**
   * @return true if the requested enabled state was applied without restart, false if restart is required
   */
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
  fun unloadPlugins(
    plugins: List<PluginMainDescriptor>,
    project: Project? = null,
    parentComponent: JComponent? = null,
    options: UnloadPluginOptions = UnloadPluginOptions(disable = true),
  ): Boolean {
    DynamicPluginsSupport.getInstance()?.let { instance ->
      return runWithModalProgressBlocking(
        parentComponent?.let { ModalTaskOwner.component(it) } ?: project?.let { ModalTaskOwner.project(it) } ?: ModalTaskOwner.guess(),
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

  fun unloadPluginWithProgress(project: Project? = null,
                               parentComponent: JComponent?,
                               pluginDescriptor: PluginMainDescriptor,
                               options: UnloadPluginOptions): Boolean {
    DynamicPluginsSupport.getInstance()?.let { instance ->
      return runWithModalProgressBlocking(
        parentComponent?.let { ModalTaskOwner.component(it) } ?: project?.let { ModalTaskOwner.project(it) } ?: ModalTaskOwner.guess(),
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

  fun checkCanUnloadWithoutRestart(module: IdeaPluginDescriptorImpl): String? {
    DynamicPluginsSupport.getInstance()?.let { instance ->
      require(module is PluginMainDescriptor) { "PluginMainDescriptor expected" }
      val newState = computeNewPluginsState(emptyList(), listOf(module))
      return runBlockingMaybeCancellable {
        instance.validateDynamicTransitionPossible(newState)?.reason?.logMessage
      }
    }
    return DynamicPluginsLegacyImpl.checkCanUnloadWithoutRestart(module)
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

  fun runAfter(callback: Runnable) {
    // TODO adjust for new support
    return DynamicPluginsLegacyImpl.runAfter(callback)
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

  private fun computeNewPluginsState(include: List<PluginMainDescriptor>, exclude: List<PluginMainDescriptor>): PluginSet {
    LOG.info("Computing new plugins state with" +
             " include=" + include.joinToString(prefix = "[", postfix = "]") { it.shortLogDescription } +
             " and exclude=" + exclude.joinToString(prefix = "[", postfix = "]") { it.shortLogDescription })
    val newInitContext = ProductPluginInitContext()
    val currentSet = PluginManagerCore.getPluginSet()

    // name shadowing intended
    val exclude = exclude.filter {
      !newInitContext.isPluginDisabled(it.pluginId)
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