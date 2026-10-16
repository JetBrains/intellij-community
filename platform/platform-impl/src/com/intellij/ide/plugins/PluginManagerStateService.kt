// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.lang.ref.WeakReference

internal data class PluginManagerStateSnapshot(
  val pluginNonLoadReasons: Map<PluginId, PluginLoadingProblem>,
  val pluginsToDisable: Map<PluginId, String>,
  val pluginsToEnable: Map<PluginId, String>,
  val pluginLoadingProblemsForNotification: List<@Nls String>,
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

  fun getPluginNonLoadReason(pluginId: PluginId): PluginLoadingProblem? =
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
    val analysis = PluginLoadingProblemAnalyzer.analyze(pluginSet)
    val pluginNonLoadReasons = analysis.selectionProblems.toMutableMap()
    val problemReporting = ProblemReporting(pluginSet)
    for (problem in analysis.resolvedSetProblems) {
      pluginNonLoadReasons[problem.plugin.pluginId] = problem
      problemReporting.collectRecoveryTargets(problem)
    }

    val loadingErrors = preparePluginErrors(
      pluginNonLoadReasons,
      pluginSet.input.discoveryResult.descriptorLoadingErrors,
      analysis.cycleErrors,
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

  companion object {
    @JvmStatic
    fun getInstance(): PluginManagerStateService = service()
  }
}
