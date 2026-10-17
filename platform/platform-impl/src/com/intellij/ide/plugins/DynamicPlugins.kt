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
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.platform.diagnostic.telemetry.PlatformMetrics
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.ide.progress.BackgroundTaskOwner
import com.intellij.platform.ide.progress.ComponentModalTaskOwner
import com.intellij.platform.ide.progress.GuessModalTaskOwner
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.ProjectModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.productMode.ProductMode
import com.intellij.ui.IdeUICustomization
import com.intellij.util.ObjectUtils
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import fleet.util.Either
import fleet.util.error
import fleet.util.value
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

private val LOG = Logger.getInstance(DynamicPlugins::class.java)
private val tracer: IJTracer by lazy { TelemetryManager.getInstance().getTracer(Scope("dynamicPlugins", PlatformMetrics)) }

@ApiStatus.Internal
object DynamicPlugins {
  private val reconfigurationLock = Mutex()

  /**
   * The spans around the modal progress of a dynamic load, an unload and a reconfiguration.
   * Each one covers the whole time the IDE stays modal. A performance test collects them by name.
   */
  const val LOAD_MODAL_PROGRESS_SPAN: @NonNls String = "loadPluginsModalProgress"
  const val UNLOAD_MODAL_PROGRESS_SPAN: @NonNls String = "unloadPluginsModalProgress"
  const val RECONFIGURE_MODAL_PROGRESS_SPAN: @NonNls String = "reconfigurePluginsModalProgress"

  /** The span around the complete reconfiguration, including work outside modal progress. */
  const val RECONFIGURE_SPAN: @NonNls String = "reconfigurePlugins"
  const val CRITICAL_SECTION_MODAL_PROGRESS_SPAN: @NonNls String = "criticalSectionModalProgress"
  const val WARMUP_CLASSLOADERS_SPAN: @NonNls String = "warmupClassLoaders"

  fun interface PluginStateValidator {
    /** @return a log message explaining why the state isn't expected, or null otherwise */
    fun validate(resolvedPluginState: ResolvedPluginSet): @NonNls String?
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
        return@validator "${plugin.shortLogDescription} was expected to be loaded but was excluded:\n" +
                         "${PluginInitializationDiagnosticUtils.buildSingleExclusionChainMessage(state, plugin)}"
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
   * Checks that it is possible to dynamically reconfigure the plugin subsystem, i.e., bring it to the new state that is calculated using
   * the currently known set of plugins and an actual [PluginInitializationContext].
   *
   * @param addNewCustomPlugins newly installed/updated plugins that weren't in the context before
   * @param forceRemovePlugins plugins that should be excluded from the context completely (so it appears as they don't exist at all anymore)
   * @param extraStateValidator additional checks of the target state can be done there (e.g. that a certain plugin loads). See [expectPluginsState]
   * @param pretendEnabled plugins that should be treated as not disabled
   * @param pretendDisabled plugins that should be treated as disabled
   *
   * @return a non-null string with an explanation if the reconfiguration is not possible, null otherwise
   */
  @RequiresReadLockAbsence
  @ApiStatus.Internal
  suspend fun validateReconfigurationIsPossible(
    addNewCustomPlugins: List<PluginMainDescriptor>,
    forceRemovePlugins: List<PluginMainDescriptor>,
    extraStateValidator: PluginStateValidator,
    pretendEnabled: List<PluginId>,
    pretendDisabled: List<PluginId>,
  ): String? {
    return validateReconfigurationIsPossible(
      buildNewState = {
        computeNewPluginsState(
          include = addNewCustomPlugins,
          exclude = forceRemovePlugins,
          forceExclude = true,
          pretendEnabled = pretendEnabled,
          pretendDisabled = pretendDisabled,
        )
      },
      extraStateValidator = extraStateValidator
    )
  }

  /**
   * @return non-null message explaining why unloading is not possible, null otherwise
   */
  @RequiresBackgroundThread(generateAssertion = false)
  fun validateCanUnloadWithoutRestart(plugin: PluginMainDescriptor): String? {
    return runBlockingMaybeCancellable {
      validateReconfigurationIsPossible(
        buildNewState = { computeNewPluginsState(emptyList(), listOf(plugin), pretendDisabled = listOf(plugin.pluginId)) },
        extraStateValidator = expectPluginsState(expectNotToLoad = listOf(plugin.pluginId))
      )
    }
  }

  /**
   * @return non-null message explaining why loading is not possible, null otherwise
   */
  @RequiresBackgroundThread(generateAssertion = false)
  fun validateCanLoadWithoutRestart(plugin: PluginMainDescriptor): String? {
    return runBlockingMaybeCancellable {
      validateReconfigurationIsPossible(
        buildNewState = { computeNewPluginsState(listOf(plugin), listOf(), pretendEnabled = listOf(plugin.pluginId)) },
        extraStateValidator = expectPluginsState(expectToLoad = listOf(plugin.pluginId))
      )
    }
  }

  private suspend fun validateReconfigurationIsPossible(
    buildNewState: () -> Either<PluginSet, DynamicReconfigurationIsNotPossibleReason>,
    extraStateValidator: PluginStateValidator,
  ): String? {
    val newStateErr = buildNewState()
    if (newStateErr.isError) {
      return "failed to build a new plugins state: ${newStateErr.error.logMessage}"
    }
    val newState = newStateErr.value
    extraStateValidator.validate(newState.resolvedPluginSet)?.let {
      return "new plugins state did not meet expectations: $it"
    }
    return DynamicPluginsSupport.getInstance().validateDynamicReconfigurationPossible(newState)?.reason?.logMessage
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
    val reason = validateReconfigurationIsPossible(
      addNewCustomPlugins,
      forceRemovePlugins,
      extraStateValidator,
      pretendEnabled,
      pretendDisabled,
    )
    reason?.let { LOG.info("reconfiguration is not possible: $it") }
    return reason == null
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
   * @param targetProductMode when set, the process moves into this product mode as a part of this reconfiguration.
   *   A mode change takes effect only through the module set that the mode implies, so the two never happen apart.
   *   The mode moves first, because the recalculation and the descriptor parsing below both read the current mode.
   *   Nothing happens and the result is `false` when the mode cannot move to [targetProductMode], which is also what
   *   a second call with the same target does.
   */
  @RequiresReadLockAbsence
  @ApiStatus.Internal
  suspend fun reconfigure(
    project: Project?,
    addNewCustomPlugins: List<PluginMainDescriptor>,
    forceRemovePlugins: List<PluginMainDescriptor>,
    extraStateValidator: PluginStateValidator,
    targetProductMode: ProductMode? = null,
    progressTitle: @Nls String? = null,
  ): Boolean {
    val title = when {
      progressTitle != null -> progressTitle
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
    val criticalSectionModalProgress = Registry.`is`("ide.plugins.modal.progress.on.reconfiguration.critical.section", true)
    val modalProgressConfig = ModalProgressConfig(modalTaskOwner(project), title)
    return withSpan(RECONFIGURE_SPAN) {
      reconfigurationLock.withLock {
        reconfigureImpl(
          buildNewState = {
            val currentMode = PluginManagerCore.getPluginSet().initContext.productMode
            if (targetProductMode != null && targetProductMode != currentMode) {
              // FIXME this is bad: it mutates the state before we actually know the transition is possible.
              //  Instead, product mode change should only apply after a successful transition, and the preliminary init context instance with adjusted product change should come from [PluginInitContextFactory].
              if (!CurrentProductMode.transitionTo(targetProductMode)) {
                return@reconfigureImpl Either.error(DynamicReconfigurationIsNotPossibleReason.of(
                  logMessage = "The product mode does not move to '${targetProductMode.id}' from '${currentMode.id}', so no plugin is reconfigured",
                  problematicPlugin = null
                ))
              }
            }
            computeNewPluginsState(addNewCustomPlugins, forceRemovePlugins, forceExclude = true)
          },
          extraStateValidator = extraStateValidator,
          outerModalProgressConfig = modalProgressConfig.takeIf { !criticalSectionModalProgress },
          criticalSectionModalProgressConfig = modalProgressConfig.takeIf { criticalSectionModalProgress },
        ) is DynamicPluginsReconfigurationResult.Success
      }
    }
  }

  /**
   * @return true if the requested enabled state was applied without restart, false if restart is required
   */
  @RequiresEdt(generateAssertion = false)
  fun loadPlugins(plugins: List<PluginMainDescriptor>, project: Project?, progressTitle: @Nls String? = null): Boolean {
    val title = progressTitle ?: defaultLoadingTitle(plugins)
    return withReconfigurationLockBlocking {
      withSpan(LOAD_MODAL_PROGRESS_SPAN) {
        runWithModalProgressBlocking(
          owner = modalTaskOwner(project),
          title = title,
          cancellation = TaskCancellation.nonCancellable(),
        ) {
          reconfigureImpl(
            buildNewState = {
              computeNewPluginsState(plugins, emptyList())
            },
            extraStateValidator = expectPluginsState(expectToLoad = plugins.map { it.pluginId }),
            outerModalProgressConfig = null,
            criticalSectionModalProgressConfig = null,
          ) is DynamicPluginsReconfigurationResult.Success
        }
      }
    }
  }

  internal fun defaultLoadingTitle(plugins: Collection<PluginMainDescriptor>): @NlsContexts.ModalProgressTitle String {
    return if (plugins.any { it.pluginId == PluginManagerCore.ULTIMATE_PLUGIN_ID }) {
      IdeBundle.message("modal.progress.title.activating.ultimate.subscription", IdeUICustomization.getInstance().paidVersionName)
    }
    else {
      IdeBundle.message("modal.progress.title.loading.plugins")
    }
  }

  @RequiresEdt(generateAssertion = false)
  @JvmOverloads
  fun loadPlugin(pluginDescriptor: PluginMainDescriptor, project: Project? = null): Boolean {
    return withReconfigurationLockBlocking {
      withSpan(LOAD_MODAL_PROGRESS_SPAN) {
        runWithModalProgressBlocking(
          owner = modalTaskOwner(project),
          title = IdeBundle.message("modal.progress.title.loading.plugin", pluginDescriptor.name),
          cancellation = TaskCancellation.nonCancellable(),
        ) {
          reconfigureImpl(
            buildNewState = {
              computeNewPluginsState(listOf(pluginDescriptor), emptyList())
            },
            extraStateValidator = expectPluginsState(expectToLoad = listOf(pluginDescriptor.pluginId)),
            outerModalProgressConfig = null,
            criticalSectionModalProgressConfig = null,
          ) is DynamicPluginsReconfigurationResult.Success
        }
      }
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
    return withReconfigurationLockBlocking {
      withSpan(UNLOAD_MODAL_PROGRESS_SPAN) {
        runWithModalProgressBlocking(
          owner = modalTaskOwner(project),
          title = IdeBundle.message("modal.progress.title.unloading.plugins"),
          cancellation = TaskCancellation.nonCancellable(),
        ) {
          reconfigureImpl(
            buildNewState = {
              computeNewPluginsState(emptyList(), plugins)
            },
            extraStateValidator = expectPluginsState(expectNotToLoad = plugins.map { it.pluginId }),
            outerModalProgressConfig = null,
            criticalSectionModalProgressConfig = null,
          ) is DynamicPluginsReconfigurationResult.Success
        }
      }
    }
  }

  @RequiresEdt(generateAssertion = false)
  fun unloadPlugin(pluginDescriptor: PluginMainDescriptor): Boolean {
    return withReconfigurationLockBlocking {
      withSpan(UNLOAD_MODAL_PROGRESS_SPAN) {
        runWithModalProgressBlocking(
          owner = modalTaskOwner(null),
          title = IdeBundle.message("modal.progress.title.unloading.plugin", pluginDescriptor.name),
          cancellation = TaskCancellation.nonCancellable(),
        ) {
          reconfigureImpl(
            buildNewState = {
              computeNewPluginsState(emptyList(), listOf(pluginDescriptor))
            },
            extraStateValidator = expectPluginsState(expectNotToLoad = listOf(pluginDescriptor.pluginId)),
            outerModalProgressConfig = null,
            criticalSectionModalProgressConfig = null,
          ) is DynamicPluginsReconfigurationResult.Success
        }
      }
    }
  }

  private inline fun withReconfigurationLockBlocking(action: () -> Boolean): Boolean {
    if (!reconfigurationLock.tryLock()) {
      LOG.info("Cannot reconfigure plugins dynamically because a concurrent operation is in progress")
      return false
    }
    try {
      return action()
    }
    finally {
      reconfigurationLock.unlock()
    }
  }

  private suspend fun reconfigureImpl(
    buildNewState: () -> Either<PluginSet, DynamicReconfigurationIsNotPossibleReason>,
    extraStateValidator: PluginStateValidator,
    outerModalProgressConfig: ModalProgressConfig?,
    criticalSectionModalProgressConfig: ModalProgressConfig?,
  ): DynamicPluginsReconfigurationResult {
    LOG.trace("dynamic plugins reconfiguration attempt")
    val runReconfiguration = suspend {
      val newStateErr = buildNewState()
      if (newStateErr.isError) {
        DynamicPluginsReconfigurationResult.Invalid(newStateErr.error)
      }
      else {
        val newState = newStateErr.value
        val validationReason =
          extraStateValidator.validate(newState.resolvedPluginSet)?.let {
            DynamicPluginsReconfigurationResult.Invalid(
              DynamicReconfigurationIsNotPossibleReason.of(it, null)
            )
          }
        if (validationReason != null) validationReason
        else DynamicPluginsSupport.getInstance().performDynamicReconfiguration(newState, criticalSectionModalProgressConfig)
      }
    }
    val result = if (outerModalProgressConfig != null) {
      withSpan(RECONFIGURE_MODAL_PROGRESS_SPAN) {
        withModalProgress(outerModalProgressConfig.owner, outerModalProgressConfig.title, cancellation = TaskCancellation.nonCancellable()) {
          runReconfiguration()
        }
      }
    }
    else if (criticalSectionModalProgressConfig != null) {
      withBackgroundProgress(
        owner = when (val owner = criticalSectionModalProgressConfig.owner) {
          is ProjectModalTaskOwner -> BackgroundTaskOwner.project(owner.project)
          is ComponentModalTaskOwner, is GuessModalTaskOwner -> BackgroundTaskOwner.guess()
        },
        title = criticalSectionModalProgressConfig.title,
        cancellation = TaskCancellation.nonCancellable(),
      ) {
        runReconfiguration()
      }
    }
    else {
      runReconfiguration()
    }
    if (result is DynamicPluginsReconfigurationResult.Invalid) LOG.warn("Reconfiguration is not possible: ${result.reason.logMessage}")
    return result
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
   * Each test of a subset treats the plugins from [plugins] outside of the subset as disabled.
   * So the test state resolves the subset and the other plugins that are not disabled, but not the other candidates.
   * A plugin that cannot load dynamically then fails only the tests of the subsets that contain it.
   *
   * The caller must load the result in the same state.
   * So the plugins from [plugins] outside of the result must stay disabled during the load.
   *
   * @param plugins ids of plugins from the current context that are not loaded, but there is a demand to load as many of them as
   *   possible dynamically. A plugin can be disabled, or it can be enabled but not resolved because a dependency was disabled.
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
        pretendDisabled = plugins.filter { it !in candidates },
      ).valueOrNull ?: return externalConflict
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
  ): Either<PluginSet, DynamicReconfigurationIsNotPossibleReason> {
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
      discoveredPluginLists = newPluginLists,
      descriptorLoadingErrors = currentSet.input.discoveryResult.descriptorLoadingErrors,
    )

    try {
      return Either.value(newInitContext.computeTargetState(newDiscoveryResult, isStartupInit = false, parentActivity = null))
    }
    catch (e: EssentialPluginMissingException) {
      LOG.warn("target plugin set is missing essential plugins", e)
      return Either.error(DynamicReconfigurationIsNotPossibleReason.of(
        logMessage = e.message ?: "missing essential plugins", problematicPlugin = null
      ))
    }
  }
}

/**
 * Runs [action] inside a span named [spanName].
 * The span stays out of the current context on purpose: a modal progress pumps unrelated events on the EDT,
 * and their spans must not nest under it.
 */
private inline fun <T> withSpan(spanName: @NonNls String, action: () -> T): T {
  val span = tracer.spanBuilder(spanName).startSpan()
  try {
    return action()
  }
  finally {
    span.end()
  }
}

private fun modalTaskOwner(project: Project?): ModalTaskOwner = project?.let { ModalTaskOwner.project(it) } ?: ModalTaskOwner.guess()
