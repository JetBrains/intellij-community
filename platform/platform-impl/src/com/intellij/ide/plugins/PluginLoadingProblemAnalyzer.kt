// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.ide.plugins.PluginDependencyAnalysis.DependencyRef
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.productMode.ProductMode
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.Nls

internal class PluginLoadingProblemAnalysis(
  val selectionProblems: Map<PluginId, PluginLoadingProblem>,
  val resolvedSetProblems: List<PluginLoadingProblem>,
  val cycleErrors: List<@Nls String>,
)

// @spec platform/platform-impl/spec/plugin-manager/plugin-loading-problems.spec.md
internal object PluginLoadingProblemAnalyzer {
  internal fun analyze(pluginSet: PluginSet): PluginLoadingProblemAnalysis {
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
    val selectionProblems = incompletePlugins.values.mapNotNull { plugin ->
      excludedFromCandidateSubset[plugin]!!.toSelectionPluginNonLoadReason()?.let { plugin.pluginId to it }
    }.toMap()

    val resolvedSetProblems = ArrayList<PluginLoadingProblem>()
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
        )?.let(resolvedSetProblems::add)
      }
      else {
        // TODO do we want to somehow report conflicts for optional content modules? or message in the log is enough?
        for (contentModule in plugin.contentModules) {
          val contentModuleExclusionReason = resolvedPluginSet.getExclusionReason(contentModule)
          if (contentModuleExclusionReason is PackagePrefixConflictWithAnotherModule) {
            resolvedSetProblems.add(pluginPackagePrefixConflict(
              plugin,
              contentModuleExclusionReason.descriptor,
              contentModuleExclusionReason.preferredConflictingModule,
            ))
          }
        }
      }
    }

    return PluginLoadingProblemAnalysis(selectionProblems, resolvedSetProblems, cycleErrors)
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

    val trace = traceExclusion(exclusionReason, resolvedPluginSet)
    val firstRequiredDescriptor = trace.firstRequiredDescriptor ?: return analyzeRootCause(trace.rootCause)
    return when (val rootCause = trace.rootCause) {
      is PluginIsMarkedDisabled -> {
        val disabledDependencyId = rootCause.descriptor.pluginId
        if (disabledDependencyId == PluginManagerCore.ULTIMATE_PLUGIN_ID) pluginRequiresDisabledUltimate(plugin)
        else pluginDependencyIsDisabled(plugin, disabledDependencyId, shouldNotifyUser, rootCause.descriptor)
      }
      is ExcludedByEnvironmentConfiguration -> analyzeRootCause(rootCause)
      is DependencyIsNotResolved -> when (rootCause.dependency) {
        is DependencyRef.ContentModule -> analyzeRootCause(rootCause)
        is DependencyRef.Plugin -> createFailedDependencyProblem(firstRequiredDescriptor)
      }
      is DependencyIsNotVisible -> createFailedDependencyProblem(rootCause.descriptor.getMainDescriptor())
      is ProductRulesImposedExclusion -> when (rootCause.productReason) {
        is NonBundledPluginsLoadingIsDisabled,
        is PluginIsNotContainedInTheExplicitlyConfiguredSubsetOfPluginsForLoading,
        is PluginLoadingIsDisabledCompletelyExceptCore -> analyzeRootCause(rootCause)
        else -> createFailedDependencyProblem(firstRequiredDescriptor)
      }
      is IncompatibleWithAnotherModule,
      is OnDemandContentModuleHasNoDependentsLeft,
      is PackagePrefixConflictWithAnotherModule,
      is PluginDeclaresConflictingId,
      is PluginIsIncompatibleWithProduct,
      is PluginVersionIsSuperseded,
      is PartOfDependencyCycle,
      is PartOfRuntimeModuleGroupDependencyCycle -> createFailedDependencyProblem(firstRequiredDescriptor)
      is ChainedExclusion -> error("expected a root cause: $rootCause")
    }
  }

  private class ExclusionTrace(
    val rootCause: DescriptorExclusionReason,
    val firstRequiredDescriptor: IdeaPluginDescriptorImpl?,
  )

  private fun traceExclusion(exclusionReason: DescriptorExclusionReason, resolvedPluginSet: ResolvedPluginSet): ExclusionTrace {
    if (exclusionReason !is ChainedExclusion) {
      return ExclusionTrace(exclusionReason, firstRequiredDescriptor = null)
    }
    val exclusionChain = exclusionReason.descriptor.sequenceDescriptorExclusionChain(resolvedPluginSet::getExclusionReason)
    val firstRequiredDescriptor = exclusionChain.zipWithNext().firstOrNull { (descriptor, dependency) ->
      descriptor.pluginId != dependency.pluginId
    }?.second
    val rootCause = resolvedPluginSet.getExclusionReason(exclusionChain.last())!!
    return ExclusionTrace(rootCause, firstRequiredDescriptor)
  }

}
