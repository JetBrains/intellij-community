// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.ide.plugins.PluginDependencyAnalysis.DependencyRef
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.lang.ref.WeakReference

internal data class PluginManagerStateSnapshot(
  val pluginNonLoadReasons: Map<PluginId, PluginNonLoadReason>,
  val pluginsToDisable: Map<PluginId, String>,
  val pluginsToEnable: Map<PluginId, String>,
  val loadingErrors: List<@Nls String>,
)

// for now this is a temporary place to store state that previously lived in PluginManagerCore, how it properly should be stored is not clear atm
@ApiStatus.Internal
@Service
class PluginManagerStateService {
  private class CachedState(
    val pluginSetReference: WeakReference<PluginSet>,
    val reportingPolicy: PluginLoadingErrorReportingPolicy,
    val state: PluginManagerStateSnapshot,
  )

  @Volatile
  private var cachedState: CachedState? = null

  fun getPluginNonLoadReason(pluginId: PluginId): PluginNonLoadReason? =
    getCurrentState()?.pluginNonLoadReasons?.get(pluginId)

  @Synchronized
  internal fun getCurrentState(
    reportingPolicy: PluginLoadingErrorReportingPolicy = PluginLoadingErrorReportingPolicy.forCurrentProduct(),
  ): PluginManagerStateSnapshot? {
    val pluginSet = PluginManagerCore.getPluginSetOrNull() ?: return null
    val cachedState = cachedState
    if (cachedState?.pluginSetReference?.get() === pluginSet && cachedState.reportingPolicy == reportingPolicy) {
      return cachedState.state
    }

    val state = calculateState(pluginSet, reportingPolicy)
    this.cachedState = CachedState(WeakReference(pluginSet), reportingPolicy, state)
    return state
  }

  internal fun calculateState(
    pluginSet: PluginSet,
    reportingPolicy: PluginLoadingErrorReportingPolicy,
  ): PluginManagerStateSnapshot {
    val initContext = pluginSet.input.initContext
    val discoveredPlugins = pluginSet.input.discoveryResult
    val resolvedPluginSet = pluginSet.resolvedPluginSet
    val excludedFromCandidateSubset = pluginSet.excludedFromCandidateSubset
    val candidateSubset = resolvedPluginSet.candidateSet

    val incompletePlugins = HashMap<PluginId, PluginMainDescriptor>()
    for (pluginList in discoveredPlugins.pluginLists) {
      for (plugin in pluginList.plugins) {
        val exclusionReason = excludedFromCandidateSubset[plugin]
        if (candidateSubset.resolvePluginId(plugin.pluginId) == null && exclusionReason != null && exclusionReason !is PluginVersionIsSuperseded) {
          val existing = incompletePlugins[plugin.pluginId]
          if (existing == null || VersionComparatorUtil.compare(plugin.version, existing.version) > 0) {
            incompletePlugins[plugin.pluginId] = plugin
          }
        }
      }
    }
    val pluginNonLoadReasons = incompletePlugins.values.mapNotNull { plugin ->
      excludedFromCandidateSubset[plugin]!!.toSelectionPluginNonLoadReason()?.let { plugin.pluginId to it }
    }.toMap(mutableMapOf())

    val pluginsToDisable = HashMap<PluginId, String>()
    val pluginsToEnable = HashMap<PluginId, String>()

    fun registerLoadingError(loadingError: PluginNonLoadReason) {
      pluginNonLoadReasons[loadingError.plugin.pluginId] = loadingError
      pluginsToDisable[loadingError.plugin.pluginId] = loadingError.plugin.name
      if (loadingError is PluginDependencyIsDisabled) {
        val disabledDependencyId = loadingError.dependencyId
        if (initContext.isPluginDisabled(disabledDependencyId)) {
          val disabledPlugin = candidateSubset.resolvePluginId(disabledDependencyId)
          if (disabledPlugin != null) {
            pluginsToEnable[disabledDependencyId] = disabledPlugin.name
          }
        }
      }
    }

    val cycleErrors = adaptDescriptorExclusionReasonAsPluginNonLoadReason(
      resolvedPluginSet = resolvedPluginSet,
      registerLoadingError = ::registerLoadingError,
    )
    val loadingErrors = preparePluginErrors(
      pluginNonLoadReasons,
      discoveredPlugins.descriptorLoadingErrors,
      cycleErrors,
      initContext,
      reportingPolicy,
    )

    return PluginManagerStateSnapshot(
      pluginNonLoadReasons = pluginNonLoadReasons.toMap(),
      pluginsToDisable = pluginsToDisable.toMap(),
      pluginsToEnable = pluginsToEnable.toMap(),
      loadingErrors = loadingErrors,
    )
  }

  private fun preparePluginErrors(
    pluginNonLoadReasons: Map<PluginId, PluginNonLoadReason>,
    descriptorLoadingErrors: List<PluginDescriptorLoadingError>,
    cycleErrors: List<@Nls String>,
    initContext: PluginInitializationContext,
    reportingPolicy: PluginLoadingErrorReportingPolicy,
  ): List<@Nls String> {
    // name shadowing is intended
    val pluginNonLoadReasons = pluginNonLoadReasons.filterValues {
      !initContext.isPluginDisabled(it.plugin.pluginId)
    }
    val globalErrors = ArrayList<@Nls String>().apply {
      for (descriptorLoadingError in descriptorLoadingErrors) {
        add(CoreBundle.message("plugin.loading.error.text.file.contains.invalid.plugin.descriptor",
                               PluginUtils.pluginPathToUserString(descriptorLoadingError.path)))
      }
      addAll(cycleErrors)
    }
    if (pluginNonLoadReasons.isEmpty() && globalErrors.isEmpty()) {
      return emptyList()
    }

    val loadingErrors = pluginNonLoadReasons.values
    return if (reportingPolicy.reportToUser) globalErrors + mapForUserNotification(loadingErrors) else emptyList()
  }

  private fun mapForUserNotification(loadingErrors: Collection<PluginNonLoadReason>): List<@Nls String> =
    loadingErrors.asSequence()
      .filter { it.shouldNotifyUser }
      .map { it.detailedMessage }
      .toList()

  private fun adaptDescriptorExclusionReasonAsPluginNonLoadReason(
    resolvedPluginSet: ResolvedPluginSet,
    registerLoadingError: (PluginNonLoadReason) -> Unit,
  ): List<@Nls String> {
    val cycleErrors = ArrayList<@Nls String>()
    for (plugin in resolvedPluginSet.candidateSet.plugins) {
      for (descriptor in plugin.sequenceAllDescriptors()) {
        if (!resolvedPluginSet.isResolved(descriptor)) {
          adaptExclusionReasonAsCycleError(resolvedPluginSet, descriptor, cycleErrors)
        }
      }
      val exclusionReason = resolvedPluginSet.getExclusionReason(plugin)
      if (exclusionReason != null) {
        adaptExclusionReasonAsNonLoadReason(exclusionReason, plugin, resolvedPluginSet, registerLoadingError, resolvedPluginSet.candidateSet)
      }
      else {
        // TODO do we want to somehow report conflicts for optional content modules? or message in the log is enough?
        for (contentModule in plugin.contentModules) {
          val contentModuleExclusionReason = resolvedPluginSet.getExclusionReason(contentModule)
          if (contentModuleExclusionReason is PackagePrefixConflictWithAnotherModule) {
            registerLoadingError(PluginPackagePrefixConflict(plugin, contentModuleExclusionReason.descriptor, contentModuleExclusionReason.preferredConflictingModule))
          }
        }
      }
    }
    return cycleErrors
  }

  private fun adaptExclusionReasonAsCycleError(
    resolvedPluginSet: ResolvedPluginSet,
    descriptor: IdeaPluginDescriptorImpl,
    cycleErrors: ArrayList<@Nls String>,
  ) {
    // Detailed cycle diagnostics are logged during initialization; this calculation only creates user-visible messages.
    fun createCyclePluginLoadingError(component: Collection<PluginModuleDescriptor>): @Nls String {
      val pluginString =
        component.joinToString(separator = ", ") { "'${it.name} (${it.pluginId.idString}${if (it.contentModuleName != null) ":" + it.contentModuleName else ""})'" }
      return CoreBundle.message("plugin.loading.error.plugins.cannot.be.loaded.because.they.form.a.dependency.cycle", pluginString)
    }

    val exclusionReason = resolvedPluginSet.getExclusionReason(descriptor)
    when (exclusionReason) {
      is PartOfDependencyCycle -> {
        val error = createCyclePluginLoadingError(exclusionReason.dependencyCycle.nodesWithDependenciesOnCycle.keys.filterIsInstance<PluginModuleDescriptor>())
        if (error !in cycleErrors) { // slow path anyway
          cycleErrors.add(error)
        }
      }
      is PartOfRuntimeModuleGroupDependencyCycle -> {
        val cycle = exclusionReason.dependencyCycle.nodesWithDependenciesOnCycle.keys.asSequence()
          .flatMap { it.sortedDescriptors }.distinct().filterIsInstance<PluginModuleDescriptor>().toList()
        val error = createCyclePluginLoadingError(cycle)
        if (error !in cycleErrors) { // slow path anyway
          cycleErrors.add(error)
        }
      }
      else -> { /* no-op */ }
    }
  }

  private fun adaptExclusionReasonAsNonLoadReason(
    exclusionReason: DescriptorExclusionReason,
    plugin: PluginMainDescriptor,
    resolvedPluginSet: ResolvedPluginSet,
    registerLoadingError: (PluginNonLoadReason) -> Unit,
    candidateSet: UnambiguousPluginSet,
  ) {
    val shouldNotifyUser = !plugin.isImplementationDetail && !pluginRequiresUltimatePluginButItsDisabled(
      initContext = resolvedPluginSet.initContext,
      ambiguousPluginSet = candidateSet.asAmbiguousPluginSet(),
      plugin
    )

    fun processRootCause(exclusionReason: DescriptorExclusionReason) {
      when (exclusionReason) {
        is DependencyIsNotResolved -> {
          registerLoadingError(PluginDependencyIsNotInstalled(plugin, exclusionReason.dependency.getIdString(), shouldNotifyUser))
        }
        is DependencyIsNotVisible -> {
          // TODO bad mapping
          registerLoadingError(PluginDependencyIsNotInstalled(plugin, exclusionReason.dependencyModule.pluginId.idString, shouldNotifyUser))
        }
        is ExcludedByEnvironmentConfiguration -> {} // logged during initialization
        is IncompatibleWithAnotherModule -> {
          registerLoadingError(PluginIsIncompatibleWithAnotherPlugin(plugin, exclusionReason.preferredIncompatibleModule, shouldNotifyUser))
        }
        is OnDemandContentModuleHasNoDependentsLeft -> {} // expected exclusion, not a loading error
        is PackagePrefixConflictWithAnotherModule -> {
          registerLoadingError(PluginPackagePrefixConflict(plugin, exclusionReason.descriptor, exclusionReason.preferredConflictingModule))
        }
        is ProductRulesImposedExclusion -> {
          val productReason = exclusionReason.productReason as? IntelliJImposedModuleExclusionReason
          when (productReason) {
            is PluginHasExpiredLicense -> {} // not handled in old init, FIXME later
            is ThirdPartyPrivacyNoticeIsNotAccepted -> {}
            is LegacyPluginIsCompatibleOnlyWithIntelliJIDEA,
            is NonBundledPluginsLoadingIsDisabled,
            is PluginIsNotContainedInTheExplicitlyConfiguredSubsetOfPluginsForLoading,
            is PluginLoadingIsDisabledCompletelyExceptCore ->
              exclusionReason.toSelectionPluginNonLoadReason()?.let(registerLoadingError)
            null -> {} // logged during initialization
          }
        }
        is PluginDeclaresConflictingId,
        is PluginIsIncompatibleWithProduct -> exclusionReason.toSelectionPluginNonLoadReason()?.let(registerLoadingError)
        is PluginIsMarkedDisabled,
        is PluginVersionIsSuperseded -> {}
        is PartOfDependencyCycle -> {} // logged elsewhere
        is PartOfRuntimeModuleGroupDependencyCycle -> {} // logged elsewhere
        is ChainedExclusion -> error("expected a root cause: $exclusionReason")
      }
    }

    if (exclusionReason is ChainedExclusion) {
      val exclusionChain = exclusionReason.descriptor.sequenceDescriptorExclusionChain(resolvedPluginSet::getExclusionReason)
      val boundaryExclusion = exclusionChain.windowed(2).firstOrNull { (pluginModule, other) -> other.pluginId != pluginModule.pluginId }
      if (boundaryExclusion != null) {
        val excludedRequiredDescriptor = boundaryExclusion[1]
        val rootCauseDescriptor = exclusionChain.last()
        val rootCause = resolvedPluginSet.getExclusionReason(rootCauseDescriptor)!!
        if (rootCause is PluginIsMarkedDisabled && rootCause.descriptor.pluginId == excludedRequiredDescriptor.pluginId) {
          registerLoadingError(PluginDependencyIsDisabled(plugin, rootCause.descriptor.pluginId, shouldNotifyUser))
        }
        else {
          registerLoadingError(PluginDependencyCannotBeLoaded(plugin, excludedRequiredDescriptor, shouldNotifyUser))
        }
      }
      else {
        val rootCauseDescriptor = exclusionChain.last()
        val rootCause = resolvedPluginSet.getExclusionReason(rootCauseDescriptor)!!
        processRootCause(rootCause)
      }
    }
    else {
      processRootCause(exclusionReason)
    }
  }

  private fun pluginRequiresUltimatePluginButItsDisabled(
    initContext: PluginInitializationContext,
    ambiguousPluginSet: AmbiguousPluginSet,
    plugin: PluginMainDescriptor,
  ): Boolean {
    if (!initContext.isPluginDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID)) {
      return false
    }
    val ultimate = ambiguousPluginSet.resolvePluginId(PluginManagerCore.ULTIMATE_PLUGIN_ID).firstOrNull()
                   ?: return false
    val requiredModules = PluginDependencyAnalysis.getRequiredTransitiveModules(
      initContext = initContext,
      plugins = listOf(plugin),
      ambiguousPluginSet = ambiguousPluginSet,
      unresolvedStrictDependenciesCollector = null,
    )
    return ultimate in requiredModules
  }

  private fun DependencyRef.getIdString(): String = when (this) {
    is DependencyRef.ContentModule -> moduleId.name
    is DependencyRef.Plugin -> pluginId.idString
  }

  companion object {
    @JvmStatic
    fun getInstance(): PluginManagerStateService = service()
  }
}
