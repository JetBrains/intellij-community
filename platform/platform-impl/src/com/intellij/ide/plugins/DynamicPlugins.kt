// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.IdeBundle
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.UpdateCheckerFacade
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.util.ObjectUtils
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

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
   * @param pretendEnabled plugins that should be treated as not disabled
   * @param pretendDisabled plugins that should be treated as disabled
   */
  @RequiresReadLockAbsence
  @ApiStatus.Internal
  suspend fun checkCanReconfigureWithoutRestart(
    addNewCustomPlugins: List<PluginMainDescriptor>,
    forceRemovePlugins: List<PluginMainDescriptor>,
    extraStateValidator: PluginStateValidator,
    pretendEnabled: List<PluginId>,
    pretendDisabled: List<PluginId>,
  ): Boolean {
    val dynamicPlugins = DynamicPluginsSupport.getInstance()
    val newState = computeNewPluginsState(
      include = addNewCustomPlugins,
      exclude = forceRemovePlugins,
      forceExclude = true,
      pretendEnabled = pretendEnabled,
      pretendDisabled = pretendDisabled,
    )
    // old plugin set resolver is already dropped, so with new dynamic plugins support this thing is expected to be always present
    val resolvedPluginSet = newState.resolvedPluginSet
    extraStateValidator.validate(resolvedPluginSet)?.let {
      LOG.info("new plugins state did not meet expectations: $it")
      return false
    }
    return dynamicPlugins.validateDynamicReconfigurationPossible(newState) == null
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
  @ApiStatus.Internal
  suspend fun reconfigure(
    project: Project?,
    addNewCustomPlugins: List<PluginMainDescriptor>,
    forceRemovePlugins: List<PluginMainDescriptor>,
    extraStateValidator: PluginStateValidator,
  ): Boolean {
    LOG.trace("dynamic plugins reconfiguration attempt")
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
      val resolvedPluginSet = newState.resolvedPluginSet
      extraStateValidator.validate(resolvedPluginSet)?.let {
        LOG.info("new plugins state did not meet expectations: $it")
        return@withModalProgress false
      }
      val result = DynamicPluginsSupport.getInstance().performDynamicReconfiguration(newState)
      result is DynamicPluginsReconfigurationResult.Success
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
      val plugin = state.candidateSet.resolvePluginId(id)
      if (plugin == null) {
        return@validator "plugin $id was expected to be loaded but is not found in the target plugin state"
      }
      if (state.isExcluded(plugin)) {
        return@validator "${plugin.shortLogDescription} was expected to be loaded but was excluded (disabled plugins may appear as unresolved):\n" + // FIXME IJPL-246161
                         "${PluginInitializationDiagnosticUtils.buildSingleExclusionChainMessage(state, emptyMap(), plugin)}"
      }
    }
    for (id in expectNotToLoad) {
      val plugin = state.candidateSet.resolvePluginId(id)
      if (plugin != null && state.isResolved(plugin)) {
        return@validator "${plugin.shortLogDescription} was expected not to be loaded but was included in the module graph"
      }
    }
    null
  }

  /**
   * @return true if the requested enabled state was applied without restart, false if restart is required
   */
  @RequiresEdt(generateAssertion = false)
  fun loadPlugins(plugins: List<PluginMainDescriptor>, project: Project?): Boolean {
    return runWithModalProgressBlocking(
      project?.let { ModalTaskOwner.project(it) } ?: ModalTaskOwner.guess(),
      IdeBundle.message("modal.progress.title.loading.plugins"),
      cancellation = TaskCancellation.nonCancellable()
    ) {
      val newState = computeNewPluginsState(plugins, emptyList())
      // old plugin set resolver is already dropped, so with new dynamic plugins support this thing is expected to be always present
      val resolvedPluginSet = newState.resolvedPluginSet
      expectPluginsState(expectToLoad = plugins.map { it.pluginId }).validate(resolvedPluginSet)?.let {
        LOG.info("new plugins state did not meet expectations: $it")
        return@runWithModalProgressBlocking false
      }
      val result = DynamicPluginsSupport.getInstance().performDynamicReconfiguration(newState)
      result is DynamicPluginsReconfigurationResult.Success
    }
  }

  @RequiresEdt(generateAssertion = false)
  @JvmOverloads
  fun loadPlugin(pluginDescriptor: PluginMainDescriptor, project: Project? = null): Boolean {
    return runWithModalProgressBlocking(
      project?.let { ModalTaskOwner.project(it) } ?: ModalTaskOwner.guess(),
      IdeBundle.message("modal.progress.title.loading.plugin", pluginDescriptor.name),
      cancellation = TaskCancellation.nonCancellable()
    ) {
      val newState = computeNewPluginsState(listOf(pluginDescriptor), emptyList())
      // old plugin set resolver is already dropped, so with new dynamic plugins support this thing is expected to be always present
      val resolvedPluginSet = newState.resolvedPluginSet
      expectPluginsState(expectToLoad = listOf(pluginDescriptor.pluginId)).validate(resolvedPluginSet)?.let {
        LOG.info("new plugins state did not meet expectations: $it")
        return@runWithModalProgressBlocking false
      }
      val result = DynamicPluginsSupport.getInstance().performDynamicReconfiguration(newState)
      result is DynamicPluginsReconfigurationResult.Success
    }
  }

  /**
   * @return true if the requested enabled state was applied without restart, false if restart is required
   */
  @RequiresEdt(generateAssertion = false)
  fun unloadPlugins(
    plugins: List<PluginMainDescriptor>,
    project: Project? = null,
  ): Boolean {
    return runWithModalProgressBlocking(
      project?.let { ModalTaskOwner.project(it) } ?: ModalTaskOwner.guess(),
      IdeBundle.message("modal.progress.title.unloading.plugins"),
      cancellation = TaskCancellation.nonCancellable()
    ) {
      val newState = computeNewPluginsState(emptyList(), plugins)
      // old plugin set resolver is already dropped, so with new dynamic plugins support this thing is expected to be always present
      val resolvedPluginSet = newState.resolvedPluginSet
      expectPluginsState(expectNotToLoad = plugins.map { it.pluginId }).validate(resolvedPluginSet)?.let {
        LOG.info("new plugins state did not meet expectations: $it")
        return@runWithModalProgressBlocking false
      }
      val result = DynamicPluginsSupport.getInstance().performDynamicReconfiguration(newState)
      result is DynamicPluginsReconfigurationResult.Success
    }
  }

  @RequiresEdt(generateAssertion = false)
  fun unloadPlugin(pluginDescriptor: PluginMainDescriptor): Boolean {
    return runWithModalProgressBlocking(
      ModalTaskOwner.guess(),
      IdeBundle.message("modal.progress.title.unloading.plugin", pluginDescriptor.name),
      cancellation = TaskCancellation.nonCancellable()
    ) {
      val newState = computeNewPluginsState(emptyList(), listOf(pluginDescriptor))
      // old plugin set resolver is already dropped, so with new dynamic plugins support this thing is expected to be always present
      val resolvedPluginSet = newState.resolvedPluginSet
      expectPluginsState(expectNotToLoad = listOf(pluginDescriptor.pluginId)).validate(resolvedPluginSet)?.let {
        LOG.info("new plugins state did not meet expectations: $it")
        return@runWithModalProgressBlocking false
      }
      val result = DynamicPluginsSupport.getInstance().performDynamicReconfiguration(newState)
      result is DynamicPluginsReconfigurationResult.Success
    }
  }

  /**
   * @return non-null message explaining why unloading is not possible, null otherwise
   */
  @RequiresBackgroundThread(generateAssertion = false)
  fun validateCanUnloadWithoutRestart(plugin: PluginMainDescriptor): String? {
    val newState = computeNewPluginsState(emptyList(), listOf(plugin), pretendDisabled = listOf(plugin.pluginId))
    // old plugin set resolver is already dropped, so with new dynamic plugins support this thing is expected to be always present
    val resolvedPluginSet = newState.resolvedPluginSet
    expectPluginsState(expectNotToLoad = listOf(plugin.pluginId)).validate(resolvedPluginSet)?.let {
      return it
    }
    return runBlockingMaybeCancellable {
      DynamicPluginsSupport.getInstance().validateDynamicReconfigurationPossible(newState)?.reason?.logMessage
    }
  }

  /**
   * @return non-null message explaining why loading is not possible, null otherwise
   */
  @RequiresBackgroundThread(generateAssertion = false)
  fun validateCanLoadWithoutRestart(plugin: PluginMainDescriptor): String? {
    val newState = computeNewPluginsState(listOf(plugin), listOf(), pretendEnabled = listOf(plugin.pluginId))
    // old plugin set resolver is already dropped, so with new dynamic plugins support this thing is expected to be always present
    val resolvedPluginSet = newState.resolvedPluginSet
    expectPluginsState(expectToLoad = listOf(plugin.pluginId)).validate(resolvedPluginSet)?.let {
      return it
    }
    return runBlockingMaybeCancellable {
      DynamicPluginsSupport.getInstance().validateDynamicReconfigurationPossible(newState)?.reason?.logMessage
    }
  }

  @RequiresBackgroundThread(generateAssertion = false)
  fun checkCanUnloadWithoutRestart(plugin: PluginMainDescriptor): Boolean {
    val reason = validateCanUnloadWithoutRestart(plugin)
    reason?.let { LOG.info("${plugin.shortLogDescription} cannot be unloaded dynamically: $it") }
    return reason == null
  }

  @RequiresBackgroundThread(generateAssertion = false)
  fun checkCanLoadWithoutRestart(plugin: PluginMainDescriptor): Boolean {
    val reason = validateCanLoadWithoutRestart(plugin)
    reason?.let { LOG.info("${plugin.shortLogDescription} cannot be loaded dynamically: $it") }
    return reason == null
  }

  /**
   * Checks if the plugin can be loaded/unloaded immediately when the corresponding action is invoked in the
   * plugins settings, without pressing the Apply button.
   */
  // TODO migrate to isUIOnlyDynamicPlugin
  @JvmStatic
  fun allowLoadUnloadSynchronously(module: IdeaPluginDescriptorImpl): Boolean {
    return module is PluginMainDescriptor && isUIOnlyDynamicPlugin(module)
  }

  internal fun notify(@NlsContexts.NotificationContent text: String, notificationType: NotificationType, vararg actions: AnAction) {
    val notification = UpdateCheckerFacade.getInstance().getNotificationGroupForPluginUpdateResults().createNotification(text, notificationType)
    for (action in actions) {
      notification.addAction(action)
    }
    notification.notify(null)
  }

  /**
   * Checks if a given plugin affects only the UI representation of the IDE.
   *
   * Acts as an allowlist condition to enable some features for "UI-only" plugins.
   */
  // TODO should we demand their "statelessness"?
  fun isUIOnlyDynamicPlugin(plugin: PluginMainDescriptor): Boolean {
    var cause: String? = null
    val reporter = DynamicPluginsValidators.IssueReporter { reason ->
      if (cause == null) {
        cause = reason.logMessage
      }
      throw DynamicPluginsValidators.AbortDynamicPluginIssuesComputation()
    }
    try {
      with(DynamicPluginsValidators) {
        reporter.validatePluginIsUIOnlyAndDynamic(plugin)
      }
    }
    catch (_: DynamicPluginsValidators.AbortDynamicPluginIssuesComputation) {
    }
    LOG.debug(cause)
    return cause == null
  }

  /**
   * Note: this method should not be expected to produce the exact max loadable subset, because the actual maximum may actually require a lot of computation.
   * Instead, expect this method to employ heuristics to get some approximation in a reasonable time.
   *
   * @param plugins ids of plugins from the current context that are disabled, but there is a demand to load as many of them as possible dynamically
   * @return a list of ids of plugins that can be enabled and loaded dynamically together
   */
  @ApiStatus.Internal
  suspend fun findMaxLoadableSubsetApproximation(plugins: List<PluginId>): List<PluginId> {
    val dynamicPluginsSupport = DynamicPluginsSupport.getInstance()
    val candidates = plugins.toMutableSet()

    val externalConflict = ObjectUtils.sentinel("external conflict")

    suspend fun testConfiguration(candidates: Set<PluginId>): Any? {
      val newState = computeNewPluginsState(
        include = emptyList(),
        exclude = emptyList(),
        pretendEnabled = candidates.toList(),
        pretendDisabled = emptyList(),
      )
      val excludedCandidates: List<PluginId> = candidates.filter { candidateId ->
        val candidate = newState.resolvedPluginSet.candidateSet.resolvePluginId(candidateId)
        candidate == null || !newState.resolvedPluginSet.isResolved(candidate)
      }
      if (excludedCandidates.isNotEmpty()) {
        // TODO log
        return excludedCandidates
      }
      val dynamicReconfigurationImpossible = dynamicPluginsSupport.validateDynamicReconfigurationPossible(newState)
      if (dynamicReconfigurationImpossible == null) {
        return null
      }
      val problematicPlugin = dynamicReconfigurationImpossible.reason.problematicPlugin
      if (problematicPlugin == null || problematicPlugin.pluginId !in candidates) {
        return externalConflict
      }
      return listOf(problematicPlugin.pluginId)
    }

    while (true) {
      when (val testResult = testConfiguration(candidates)) {
        null -> return candidates.toList()
        externalConflict -> break
        is List<*> -> candidates.removeAll(testResult.toSet())
      }
    }
    // either dynamic reconfiguration sequence is not allowed by settings, or this subset of candidates cannot be loaded due to conflicts with other plugins
    // let's make a single pass trying to greedily include as many plugins as possible
    val acceptedCandidates = mutableListOf<PluginId>()
    for (candidate in candidates) {
      if (testConfiguration(acceptedCandidates.toSet() + candidate) == null) {
        acceptedCandidates.add(candidate)
      }
    }
    return acceptedCandidates.toList()
  }

  private fun computeNewPluginsState(
    include: List<PluginMainDescriptor>,
    exclude: List<PluginMainDescriptor>,
    forceExclude: Boolean = false,
    pretendEnabled: List<PluginId> = emptyList(),
    pretendDisabled: List<PluginId> = emptyList(),
  ): PluginSet {
    LOG.info(buildString {
      append("Computing new plugins state with")
      append(" include=${include.joinToString(prefix = "[", postfix = "]") { it.shortLogDescription }}")
      append(", exclude=${exclude.joinToString(prefix = "[", postfix = "]") { it.shortLogDescription }}")
      if (forceExclude) append(", forceExclude=true")
      if (pretendEnabled.isNotEmpty()) append(", pretendEnabled=${pretendEnabled.joinToString(prefix = "[", postfix = "]")}")
      if (pretendDisabled.isNotEmpty()) append(", pretendDisabled=${pretendDisabled.joinToString(prefix = "[", postfix = "]")}")
    })
    val newInitContext = if (pretendDisabled.isEmpty() && pretendEnabled.isEmpty()) {
      PluginInitContextFactory.getInstance().createActualContext()
    } else {
      PluginInitContextFactory.getInstance().createMockContextWithOverrides(
        disabledPluginsOverride = DisabledPluginsState.getDisabledIds() - pretendEnabled.toSet() + pretendDisabled
      )
    }
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
    val newState = PluginManagerCore.adaptResolvedPluginSetAsOldPluginSet(
      PluginSubsystemInput(newInitContext, newDiscoveryResult),
      resolvedSet,
    ) {
      // TODO handle later, not important right now
    }

    return newState.first
  }
}