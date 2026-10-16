// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.ide.plugins.PluginDependencyAnalysis.DependencyRef
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.productMode.ProductMode
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.Nls

// @spec platform/platform-impl/spec/plugin-manager/plugin-loading-problems.spec.md
internal object PluginLoadingProblemAnalyzer {
  internal fun calculateState(
    pluginSet: PluginSet,
    reportingPolicy: PluginLoadingErrorReportingPolicy,
  ): PluginManagerStateSnapshot {
    val initContext = pluginSet.input.initContext
    val discoveredPlugins = pluginSet.input.discoveryResult
    val resolvedPluginSet = pluginSet.resolvedPluginSet
    val excludedFromCandidateSubset = pluginSet.excludedFromCandidateSubset
    val candidateSubset = resolvedPluginSet.candidateSet
    val excludedPluginSet = AmbiguousPluginSet.build(excludedFromCandidateSubset.keys.toList())

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

    val problemReporting = ProblemReporting(pluginSet)

    fun recordResolvedProblem(problem: PluginLoadingProblem) {
      pluginNonLoadReasons[problem.plugin.pluginId] = problem
      problemReporting.collectRecoveryTargets(problem)
    }

    val cycleErrors = ArrayList<@Nls String>()
    for (plugin in resolvedPluginSet.candidateSet.plugins) {
      for (descriptor in plugin.sequenceAllDescriptors()) {
        if (!resolvedPluginSet.isResolved(descriptor)) {
          adaptExclusionReasonAsCycleError(resolvedPluginSet = resolvedPluginSet, descriptor = descriptor, cycleErrors = cycleErrors)
        }
      }
      val exclusionReason = resolvedPluginSet.getExclusionReason(plugin)
      if (exclusionReason != null) {
        analyzePluginExclusion(
          exclusionReason,
          plugin,
          resolvedPluginSet = resolvedPluginSet,
          excludedPluginSet = excludedPluginSet,
          productMode = initContext.productMode,
        )?.let(::recordResolvedProblem)
      }
      else {
        // TODO do we want to somehow report conflicts for optional content modules? or message in the log is enough?
        for (contentModule in plugin.contentModules) {
          val contentModuleExclusionReason = resolvedPluginSet.getExclusionReason(contentModule)
          if (contentModuleExclusionReason is PackagePrefixConflictWithAnotherModule) {
            recordResolvedProblem(pluginPackagePrefixConflict(
              plugin,
              contentModuleExclusionReason.descriptor,
              contentModuleExclusionReason.preferredConflictingModule,
            ))
          }
        }
      }
    }

    val loadingErrors = preparePluginErrors(
      pluginNonLoadReasons,
      discoveredPlugins.descriptorLoadingErrors,
      cycleErrors,
      problemReporting::shouldReportProblem,
      reportingPolicy,
    )

    return PluginManagerStateSnapshot(
      pluginNonLoadReasons = pluginNonLoadReasons.toMap(),
      pluginsToDisable = problemReporting.pluginsToDisable.toMap(),
      pluginsToEnable = problemReporting.pluginsToEnable.toMap(),
      pluginLoadingProblemsForNotification = loadingErrors,
    )
  }

  private class ProblemReporting(private val pluginSet: PluginSet) {
    private val initContext = pluginSet.input.initContext
    private val candidateSubset = pluginSet.resolvedPluginSet.candidateSet
    private val pluginIdMap by lazy(LazyThreadSafetyMode.NONE) { pluginSet.buildPluginIdMap() }
    private val contentModuleIdMap by lazy(LazyThreadSafetyMode.NONE) { pluginSet.buildContentModuleIdMap() }

    val pluginsToDisable = HashMap<PluginId, String>()
    val pluginsToEnable = HashMap<PluginId, String>()

    fun shouldReportProblem(problem: PluginLoadingProblem): Boolean =
      problem.shouldNotifyUser && !initContext.isPluginDisabled(problem.plugin.pluginId) &&
      (!initContext.isPluginDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID) ||
       !pluginRequiresUltimatePlugin(problem.plugin.pluginId, pluginIdMap, contentModuleIdMap))

    fun collectRecoveryTargets(problem: PluginLoadingProblem) {
      if (!shouldReportProblem(problem)) {
        return
      }
      pluginsToDisable[problem.plugin.pluginId] = problem.plugin.name
      val disabledDependencyId = problem.disabledDependencyId
      if (disabledDependencyId != null && disabledDependencyId != PluginManagerCore.ULTIMATE_PLUGIN_ID) {
        if (initContext.isPluginDisabled(disabledDependencyId)) {
          val disabledPlugin = candidateSubset.resolvePluginId(disabledDependencyId)
          if (disabledPlugin != null) {
            pluginsToEnable[disabledDependencyId] = disabledPlugin.name
          }
        }
      }
    }
  }

  private fun preparePluginErrors(
    pluginNonLoadReasons: Map<PluginId, PluginLoadingProblem>,
    descriptorLoadingErrors: List<PluginDescriptorLoadingError>,
    cycleErrors: List<@Nls String>,
    shouldReportProblem: (PluginLoadingProblem) -> Boolean,
    reportingPolicy: PluginLoadingErrorReportingPolicy,
  ): List<@Nls String> {
    // name shadowing is intended
    val pluginNonLoadReasons = pluginNonLoadReasons.filterValues(shouldReportProblem)
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
    return if (reportingPolicy.reportToUser) globalErrors + loadingErrors.map { it.detailedMessage } else emptyList()
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
        val error = createCyclePluginLoadingError(exclusionReason.dependencyCycle.nodes.filterIsInstance<PluginModuleDescriptor>())
        if (error !in cycleErrors) { // slow path anyway
          cycleErrors.add(error)
        }
      }
      is PartOfRuntimeModuleGroupDependencyCycle -> {
        val cycle = exclusionReason.dependencyCycle.nodes.asSequence()
          .flatMap { it.sortedDescriptors }.distinct().filterIsInstance<PluginModuleDescriptor>().toList()
        val error = createCyclePluginLoadingError(cycle)
        if (error !in cycleErrors) { // slow path anyway
          cycleErrors.add(error)
        }
      }
      else -> { /* no-op */ }
    }
  }

  private fun analyzePluginExclusion(
    exclusionReason: DescriptorExclusionReason,
    plugin: PluginMainDescriptor,
    resolvedPluginSet: ResolvedPluginSet,
    excludedPluginSet: AmbiguousPluginSet,
    productMode: ProductMode,
  ): PluginLoadingProblem? {
    val shouldNotifyUser = !plugin.isImplementationDetail

    fun createFailedDependencyProblem(descriptor: IdeaPluginDescriptorImpl): PluginLoadingProblem {
      val dependency = if (descriptor is ContentModuleDescriptor) {
        DependencyRef.ContentModule(descriptor.moduleId)
      }
      else {
        DependencyRef.Plugin(descriptor.pluginId)
      }
      return pluginDependencyCannotBeLoaded(plugin, dependency, shouldNotifyUser, descriptor)
    }

    fun analyzeRootCause(exclusionReason: DescriptorExclusionReason): PluginLoadingProblem? =
      when (exclusionReason) {
        is DependencyIsNotResolved -> when (val dependency = exclusionReason.dependency) {
          is DependencyRef.Plugin -> {
            val descriptor = resolvedPluginSet.candidateSet.resolvePluginId(dependency.pluginId)
                             ?: excludedPluginSet.resolvePluginId(dependency.pluginId).firstOrNull()
            if (descriptor == null) {
              pluginDependencyIsNotInstalled(plugin, dependency, shouldNotifyUser)
            }
            else {
              pluginDependencyCannotBeLoaded(plugin, dependency, shouldNotifyUser, descriptor.getMainDescriptor())
            }
          }
          is DependencyRef.ContentModule ->
            pluginRequiresUnavailableModule(plugin, dependency.moduleId, shouldNotifyUser)
        }
        is DependencyIsNotVisible ->
          pluginDependencyIsNotVisible(plugin, exclusionReason.dependencyModule, shouldNotifyUser)
        is ExcludedByEnvironmentConfiguration -> when (val reason = exclusionReason.reason) {
          is UnsuitableProductModeModuleUnavailabilityReason ->
            pluginRequiresUnavailableProductModeModule(plugin, reason.moduleId, productMode)
        }
        is IncompatibleWithAnotherModule -> {
          pluginIsIncompatibleWithAnotherPlugin(plugin, exclusionReason.preferredIncompatibleModule, shouldNotifyUser)
        }
        is OnDemandContentModuleHasNoDependentsLeft -> null // expected exclusion, not a loading error
        is PackagePrefixConflictWithAnotherModule -> {
          pluginPackagePrefixConflict(plugin, exclusionReason.descriptor, exclusionReason.preferredConflictingModule)
        }
        is ProductRulesImposedExclusion -> {
          val productReason = exclusionReason.productReason as? IntelliJImposedModuleExclusionReason
          when (productReason) {
            is AiIsDisabled -> null // an expected exclusion, not a loading error
            is PluginHasExpiredLicense -> null // not handled in old init, FIXME later
            is ThirdPartyPrivacyNoticeIsNotAccepted -> null
            is NonBundledPluginsLoadingIsDisabled -> nonBundledPluginsAreExplicitlyDisabled(plugin)
            is PluginIsNotContainedInTheExplicitlyConfiguredSubsetOfPluginsForLoading ->
              pluginIsNotRequiredForLoadingTheExplicitlyConfiguredSubsetOfPlugins(plugin)
            is PluginLoadingIsDisabledCompletelyExceptCore -> pluginLoadingIsDisabledCompletely(plugin)
            is LegacyPluginIsCompatibleOnlyWithIntelliJIDEA -> exclusionReason.toSelectionPluginNonLoadReason()
            null -> null // logged during initialization
          }
        }
        is PluginDeclaresConflictingId,
        is PluginIsIncompatibleWithProduct -> exclusionReason.toSelectionPluginNonLoadReason()
        is PluginIsMarkedDisabled,
        is PluginVersionIsSuperseded -> null
        is PartOfDependencyCycle -> null // logged elsewhere
        is PartOfRuntimeModuleGroupDependencyCycle -> null // logged elsewhere
        is ChainedExclusion -> error("expected a root cause: $exclusionReason")
      }

    return if (exclusionReason is ChainedExclusion) {
      val exclusionChain = exclusionReason.descriptor.sequenceDescriptorExclusionChain(resolvedPluginSet::getExclusionReason)
      val boundaryExclusion = exclusionChain.windowed(2).firstOrNull { (pluginModule, other) -> other.pluginId != pluginModule.pluginId }
      val rootCauseDescriptor = exclusionChain.last()
      val rootCause = resolvedPluginSet.getExclusionReason(rootCauseDescriptor)!!
      if (boundaryExclusion != null) {
        val excludedRequiredDescriptor = boundaryExclusion[1]
        when (rootCause) {
          is PluginIsMarkedDisabled -> {
            val disabledDependencyId = rootCause.descriptor.pluginId
            if (disabledDependencyId == PluginManagerCore.ULTIMATE_PLUGIN_ID) pluginRequiresDisabledUltimate(plugin)
            else pluginDependencyIsDisabled(plugin, disabledDependencyId, shouldNotifyUser, rootCause.descriptor)
          }
          is ExcludedByEnvironmentConfiguration -> analyzeRootCause(rootCause)
          is DependencyIsNotResolved -> when (rootCause.dependency) {
            is DependencyRef.ContentModule -> analyzeRootCause(rootCause)
            is DependencyRef.Plugin -> createFailedDependencyProblem(excludedRequiredDescriptor)
          }
          is DependencyIsNotVisible -> createFailedDependencyProblem(rootCause.descriptor.getMainDescriptor())
          is ProductRulesImposedExclusion -> when (rootCause.productReason) {
            is NonBundledPluginsLoadingIsDisabled,
            is PluginIsNotContainedInTheExplicitlyConfiguredSubsetOfPluginsForLoading,
            is PluginLoadingIsDisabledCompletelyExceptCore -> analyzeRootCause(rootCause)
            else -> createFailedDependencyProblem(excludedRequiredDescriptor)
          }
          is IncompatibleWithAnotherModule,
          is OnDemandContentModuleHasNoDependentsLeft,
          is PackagePrefixConflictWithAnotherModule,
          is PluginDeclaresConflictingId,
          is PluginIsIncompatibleWithProduct,
          is PluginVersionIsSuperseded,
          is PartOfDependencyCycle,
          is PartOfRuntimeModuleGroupDependencyCycle -> createFailedDependencyProblem(excludedRequiredDescriptor)
          is ChainedExclusion -> error("expected a root cause: $rootCause")
        }
      }
      else {
        analyzeRootCause(rootCause)
      }
    }
    else {
      analyzeRootCause(exclusionReason)
    }
  }

}
