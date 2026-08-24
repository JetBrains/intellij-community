// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.diagnostic.Activity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus


/**
 * @throws EssentialPluginMissingException if [checkEssentialPlugins] is true and any essential plugin is missing
 */
@ApiStatus.Internal
@Throws(EssentialPluginMissingException::class)
fun PluginInitializationContext.computeTargetState(
  discoveryResult: PluginsDiscoveryResult,
  isStartupInit: Boolean,
  parentActivity: Activity?,
): PluginSet {
  var initStagesActivity = parentActivity?.startChild("select candidate subset")
  val excludedFromCandidateSubset = LinkedHashMap<PluginMainDescriptor, DescriptorExclusionReason>()
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
        "  ${it.key.shortLogDescription}: ${PluginInitializationDiagnosticUtils.getLogMessage(it.value)}"
      }}")
      throw e
    }
  }

  initStagesActivity = initStagesActivity?.endAndStart("resolve constraints")
  val resolvedPluginSet = resolveConstraints(candidateSubset)

  if (checkEssentialPlugins) {
    initStagesActivity = initStagesActivity?.endAndStart("check essential plugins")
    checkEssentialPluginsAreAvailable(resolvedPluginSet, essentialPlugins)
  }

  initStagesActivity = initStagesActivity?.endAndStart("adapt plugin set")
  val pluginSet = PluginSet(
    input = PluginSubsystemInput(this, discoveryResult),
    excludedFromCandidateSubset = excludedFromCandidateSubset,
    resolvedPluginSet = resolvedPluginSet,
  )
  initStagesActivity?.end()

  return pluginSet
}

/**
 * @throws EssentialPluginMissingException if any essential plugin is missing
 */
private fun checkEssentialPluginsAreAvailable(
  resolvedPluginSet: ResolvedPluginSet,
  essentialPlugins: Set<PluginId>,
) {
  var missingIds: ArrayList<String>? = null
  var diagnosticMessage: StringBuilder? = null
  for (id in essentialPlugins) {
    val module = resolvedPluginSet.candidateSet.resolvePluginId(id)
    if (module != null && resolvedPluginSet.isResolved(module)) {
      continue
    }
    missingIds = missingIds ?: ArrayList()
    missingIds.add(id.idString)
    val reason = module?.let { resolvedPluginSet.getExclusionReason(it) }
    if (reason != null) {
      diagnosticMessage = diagnosticMessage ?: StringBuilder("Exclusion traces:")
      diagnosticMessage.appendLine()
      diagnosticMessage.append(PluginInitializationDiagnosticUtils.buildSingleExclusionChainMessage(resolvedPluginSet, module))
    }
  }
  if (missingIds != null) {
    throw EssentialPluginMissingException(missingIds, diagnosticMessage?.toString())
  }
}
