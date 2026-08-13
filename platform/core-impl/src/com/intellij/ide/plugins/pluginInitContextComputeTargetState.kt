// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.diagnostic.Activity
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import java.util.IdentityHashMap

@ApiStatus.Internal
fun PluginInitializationContext.computeTargetState(
  discoveryResult: PluginsDiscoveryResult,
  isStartupInit: Boolean,
  parentActivity: Activity?,
): PluginSet {
  var initStagesActivity = parentActivity?.startChild("select candidate subset")
  val excludedFromCandidateSubset = IdentityHashMap<PluginMainDescriptor, DescriptorExclusionReason>()
  val candidateSubset = selectCandidateSubset(discoveryResult, excludedFromCandidateSubset)

  if (isStartupInit) {
    try {
      initStagesActivity = initStagesActivity?.endAndStart("startup configuration")
      runConfigurationDuringStartup(candidateSubset)
    }
    catch (e: Exception) {
      val logger = logger<PluginManagerCore>()
      logger.error("Fatal plugin initialization error", e)
      logger.error("[plugins] candidate subset:\n${candidateSubset.plugins.joinToString { it.shortLogDescription }}")
      logger.error("[plugins] excluded from candidate subset:\n${excludedFromCandidateSubset.entries.joinToString(separator = "\n") {
        "  ${it.key.shortLogDescription}: ${PluginInitializationDiagnosticUtils.getLogMessageForRootExclusionReason(it.value)}"
      }}")
      throw e
    }
  }

  initStagesActivity = initStagesActivity?.endAndStart("resolve constraints")
  val resolvedPluginSet = resolveConstraints(candidateSubset)

  initStagesActivity = initStagesActivity?.endAndStart("adapt plugin set")
  val pluginSet = PluginSet(
    input = PluginSubsystemInput(this, discoveryResult),
    excludedFromCandidateSubset = excludedFromCandidateSubset,
    resolvedPluginSet = resolvedPluginSet,
  )
  initStagesActivity?.end()

  return pluginSet
}
