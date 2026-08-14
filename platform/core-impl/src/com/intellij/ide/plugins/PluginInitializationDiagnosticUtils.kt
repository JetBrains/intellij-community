// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginDependencyAnalysis.DependencyRef
import com.intellij.idea.AppMode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.asSafely
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PluginInitializationDiagnosticUtils {
  fun getLogMessageForRootExclusionReason(reason: DescriptorExclusionReason): String = reason.logMessage()

  fun logExclusionTree(logger: Logger, pluginSet: PluginSet) {
    val resolvedPluginSet = pluginSet.resolvedPluginSet
    val exclusionChildren = LinkedHashMap<IdeaPluginDescriptorImpl, ArrayList<IdeaPluginDescriptorImpl>>()
    val roots = LinkedHashSet<IdeaPluginDescriptorImpl>()
    for (plugin in resolvedPluginSet.candidateSet.plugins) {
      for (descriptor in plugin.sequenceAllDescriptors()) {
        if (resolvedPluginSet.isResolved(descriptor)) continue
        val chain = descriptor.sequenceDescriptorExclusionChain(resolvedPluginSet::getExclusionReason).take(2).toList()
        if (chain.size == 2) {
          val precedingExclusion = chain[1]
          val precedingReason = resolvedPluginSet.getExclusionReason(precedingExclusion)
          if (precedingReason is PartOfDependencyCycle || precedingReason is PartOfRuntimeModuleGroupDependencyCycle) {
            val key = precedingReason.getDependencyCycleRepresentative()
            exclusionChildren.getOrPut(key) { ArrayList() }.add(descriptor) // attach chained exclusions to a cycle representative only, so they all are grouped up
          }
          else {
            exclusionChildren.getOrPut(precedingExclusion) { ArrayList() }.add(descriptor)
          }
        } else if (chain.size == 1) {
          val exclusionReason = resolvedPluginSet.getExclusionReason(descriptor)
          val shouldAddRoot = if (exclusionReason is PartOfDependencyCycle || exclusionReason is PartOfRuntimeModuleGroupDependencyCycle) {
            exclusionReason.getDependencyCycleRepresentative() == descriptor
          } else {
            true
          }
          if (shouldAddRoot) roots.add(descriptor)
        }
      }
    }

    fun StringBuilder.writeExclusionTree(descriptor: IdeaPluginDescriptorImpl, indent: Int) {
      val exclusionReason = resolvedPluginSet.getExclusionReason(descriptor)!!
      appendIndentString(indent)
      appendLine(exclusionReason.exclusionTreeLogMessage())
      val children = exclusionChildren[descriptor] ?: emptyList()
      val (childFreeChainedExclusions, otherChildren) = children.partition { (exclusionChildren[it]?.size ?: 0) == 0 && resolvedPluginSet.getExclusionReason(it) is ChainedExclusion }
      if (childFreeChainedExclusions.isNotEmpty()) {
        appendIndentString(indent + 1)
        appendLine("dependent ${childFreeChainedExclusions.joinToString(", ") { it.shortLogDescription }} excluded")
      }
      for (child in otherChildren) {
        writeExclusionTree(child, indent + 1)
      }
    }

    val dependencyIsNotResolvedRoots = roots.asSequence().filter { resolvedPluginSet.getExclusionReason(it) is DependencyIsNotResolved }.toSet()
    roots.removeAll(dependencyIsNotResolvedRoots)

    val childFreeOnDemandRoots = roots.filter {
      resolvedPluginSet.getExclusionReason(it) is OnDemandContentModuleHasNoDependentsLeft && exclusionChildren[it].isNullOrEmpty()
    }.toSet()
    roots.removeAll(childFreeOnDemandRoots)

    val logHeader = "Plugin set resolution:\n"
    val logBuilder = StringBuilder().apply {
      append(logHeader)
      for (reason in pluginSet.excludedFromCandidateSubset.values) {
        if (reason !is PluginVersionIsSuperseded || logger.isDebugEnabled) {
          append("excluded from candidate set: ")
          appendLine(reason.logMessage())
        }
      }
      dependencyIsNotResolvedRoots.map { resolvedPluginSet.getExclusionReason(it) as DependencyIsNotResolved }.groupBy { it.dependency }
        .forEach { (ref, roots) ->
          appendDependencyIsNotResolvedLogMessage(ref)
          // a bit of duplication, but I guess it's alright for this code
          val (childFreeExclusions, otherRoots) = roots.partition { (exclusionChildren[it.descriptor]?.size ?: 0) == 0 }
          if (childFreeExclusions.isNotEmpty()) {
            appendIndentString(1)
            appendLine("dependent ${childFreeExclusions.joinToString(", ") { it.descriptor.shortLogDescription }} excluded")
          }
          for (root in otherRoots) {
            writeExclusionTree(root.descriptor, 1)
          }
        }
      for (root in roots) {
        writeExclusionTree(root, 0)
      }
      if (childFreeOnDemandRoots.isNotEmpty()) {
        appendLine("excluded on-demand modules (no dependents left): ${childFreeOnDemandRoots.joinToString(", ") { it.shortLogDescription }}")
      }
    }
    if (logBuilder.last() == '\n') {
      logBuilder.setLength(logBuilder.length - 1)
    }
    if (logBuilder.length == logHeader.length - 1) {
      logBuilder.append(" no exclusions")
    }
    logger.info(logBuilder.toString())

    if (logger.isDebugEnabled) {
      logger.debug(buildString {
        appendLine("Resolved descriptors:")
        for ((index, descriptor) in resolvedPluginSet.sortedResolvedDescriptors.withIndex()) {
          if (index > 0) append(", ")
          append(descriptor.shortLogDescription)
        }
      })
    }
  }

  fun buildSingleExclusionChainMessage(
    resolvedPluginSet: ResolvedPluginSet,
    descriptor: IdeaPluginDescriptorImpl,
  ): String? {
    if (resolvedPluginSet.isResolved(descriptor)) {
      return null
    }
    // TODO decrease code duplication
    val chain = descriptor.sequenceDescriptorExclusionChain(resolvedPluginSet::getExclusionReason).toList().reversed()
    val msgBuilder = StringBuilder().apply {
      if (chain.firstOrNull()?.let { resolvedPluginSet.getExclusionReason(it) } is DependencyIsNotResolved) {
        val rootCause = resolvedPluginSet.getExclusionReason(chain[0])!! as DependencyIsNotResolved
        appendDependencyIsNotResolvedLogMessage(rootCause.dependency)
        for ((index, excludedDescriptor) in chain.withIndex()) {
          if (index > 0) appendLine()
          val exclusionReason = resolvedPluginSet.getExclusionReason(excludedDescriptor)!!
          appendIndentString(index + 1)
          append(exclusionReason.logMessage())
        }
      } else {
        for ((index, excludedDescriptor) in chain.withIndex()) {
          if (index > 0) appendLine()
          val exclusionReason = resolvedPluginSet.getExclusionReason(excludedDescriptor)!!
          appendIndentString(index)
          append(exclusionReason.logMessage())
        }
      }
    }
    return msgBuilder.toString()
  }

  private fun DescriptorExclusionReason.getDependencyCycleRepresentative(): IdeaPluginDescriptorImpl =
    asSafely<PartOfDependencyCycle>()?.dependencyCycle?.nodesWithDependenciesOnCycle?.keys?.first()
    ?: asSafely<PartOfRuntimeModuleGroupDependencyCycle>()?.dependencyCycle?.nodesWithDependenciesOnCycle?.keys?.first()?.representativeModule
    ?: error("$this is not a cycle exclusion reason")

  private fun DescriptorExclusionReason.exclusionTreeLogMessage(): String {
    val logDescr by descriptor::shortLogDescription
    return when (this) {
      is ContentModuleParentIsExcluded,
      is RequiredContentModuleIsExcluded,
      is DependencyIsExcluded,
      is DependsParentIsExcluded,
      is DependencyIsNotResolved -> "dependent $logDescr excluded" // special handling in logExclusionTree
      else -> logMessage()
    }
  }

  private fun DescriptorExclusionReason.logMessage(): String {
    val logDescr by descriptor::shortLogDescription
    return when (this) {
      // chained:
      is ContentModuleParentIsExcluded -> "$logDescr excluded due to its parent ${this.precedingExcludedDescriptor.shortLogDescription} exclusion"
      is DependsParentIsExcluded -> "$logDescr excluded due to its parent ${this.precedingExcludedDescriptor.shortLogDescription} exclusion"
      is RequiredContentModuleIsExcluded -> "$logDescr excluded due to its required ${this.precedingExcludedDescriptor.shortLogDescription} exclusion"
      is DependencyIsExcluded -> "$logDescr excluded due to its dependency ${this.precedingExcludedDescriptor.shortLogDescription} exclusion"
      // root:
      is DependencyIsNotResolved -> "$logDescr depends on ${dependency.logDescription} which is absent"
      is DependencyIsNotVisible -> "$logDescr depends on ${dependencyModule.shortLogDescription} which is not visible"
      is ExcludedByEnvironmentConfiguration -> "$logDescr is excluded: ${reason.logMessage}"
      is IncompatibleWithAnotherModule -> "$logDescr is incompatible with ${preferredIncompatibleModule.shortLogDescription}"
      is OnDemandContentModuleHasNoDependentsLeft -> "$logDescr is on-demand and has no dependents left"
      is PackagePrefixConflictWithAnotherModule -> "$logDescr declares the same package prefix as in " +
                                                   "${preferredConflictingModule.shortLogDescription}: " +
                                                   "${formatPackagePrefixConflictDetails(descriptor)} conflicts with " +
                                                   formatPackagePrefixConflictDetails(preferredConflictingModule)
      is PartOfDependencyCycle -> buildString {
        appendLine("The following modules form a dependency cycle:")
        explainCycle(dependencyCycle, fmtNode = { it.shortLogDescription })
      }
      is PartOfRuntimeModuleGroupDependencyCycle -> buildString {
        appendLine("Classloaders made from the following groups form a dependency cycle:")
        explainCycle(
          dependencyCycle,
          fmtNode = { "${it.representativeModule.shortLogDescription} (${it.sortedDescriptors.joinToString { it.shortLogDescription }})" },
          fmtDeps = { it.joinToString(", ") { it.representativeModule.shortLogDescription } }
        )
      }
      is ProductRulesImposedExclusion -> "$logDescr is excluded: ${productReason.getLogMessage()}"
      is PluginDeclaresConflictingId -> "$logDescr declares conflicting id with ${this.conflictingModule.shortLogDescription}: ${conflictingPluginId ?: conflictingModuleId}"
      is PluginIsIncompatibleWithProduct -> "$logDescr is incompatible with the product: ${incompatibilityReason.getLogMessageForRootExclusionReason(descriptor)}"
      is PluginIsMarkedDisabled -> "$logDescr is marked disabled"
      is PluginVersionIsSuperseded -> "$logDescr is superseded by ${supersededBy.shortLogDescription}"
    }
  }

  private fun StringBuilder.appendDependencyIsNotResolvedLogMessage(ref: DependencyRef) {
    when (ref) {
      is DependencyRef.ContentModule -> append("module ${ref.moduleId.name} (namespace=${ref.moduleId.namespace})")
      is DependencyRef.Plugin -> append("plugin ${ref.pluginId.idString}")
    }
    appendLine(" is not resolved")
  }

  private fun StringBuilder.appendIndentString(indent: Int) {
    repeat(indent) { append("  ") }
    if (indent > 0) append("└ ")
  }

  private fun <N> StringBuilder.explainCycle(cycle: DependencyCycleInfo<N>, fmtNode: (N) -> String, fmtDeps: (List<N>) -> String = { it.joinToString(", ") { fmtNode(it) }}) {
    var endLine = false
    cycle.nodesWithDependenciesOnCycle.forEach { (node, dependencies) ->
      if (endLine) appendLine()
      else endLine = true
      append("    | ${fmtNode(node)} depends on: ${fmtDeps(dependencies)}")
    }
  }

  internal fun DependencyRef.getIdString(): String = when (this) {
    is DependencyRef.ContentModule -> moduleId.name
    is DependencyRef.Plugin -> pluginId.idString
  }

  internal fun logPluginLists(
    logger: Logger,
    initContext: PluginInitializationContext,
    plugins: Collection<PluginMainDescriptor>,
  ) {
    fun StringBuilder.appendPlugin(plugin: PluginMainDescriptor) {
      if (isNotEmpty()) {
        append(", ")
      }
      append(plugin.name).append(" (").append(plugin.pluginId.idString)
      if (plugin.version != null) {
        append(", ").append(plugin.version)
      }
      append(')')
    }

    if (AppMode.isDisableNonBundledPlugins()) { // TODO this should be part of initContext
      logger.info("Running with disableThirdPartyPlugins argument, third-party plugins will be disabled")
    }

    val bundled = StringBuilder()
    val disabled = StringBuilder()
    val custom = StringBuilder()
    for (descriptor in plugins) {
      val pluginId = descriptor.pluginId
      val target = if (!PluginManagerCore.isLoaded(descriptor)) {
        if (!initContext.isPluginDisabled(pluginId)) {
          // the plugin will be logged as part of "Problems found loading plugins"
          continue
        }
        disabled
      }
      else if (descriptor.isBundled || PluginManagerCore.SPECIAL_IDEA_PLUGIN_ID == pluginId) {
        bundled
      }
      else {
        custom
      }
      target.appendPlugin(descriptor)
    }

    logger.info("Loaded bundled plugins: $bundled")
    if (custom.isNotEmpty()) {
      logger.info("Loaded custom plugins: $custom")
    }
    if (disabled.isNotEmpty()) {
      logger.info("Disabled plugins: $disabled")
    }
  }

  fun logMajorPluginLoadingProblems(
    logger: Logger,
    pluginSet: PluginSet,
    reportingPolicy: PluginLoadingErrorReportingPolicy,
  ) {
    val problems = collectMajorPluginLoadingProblemMessages(pluginSet)
    if (problems.isEmpty()) {
      return
    }

    val message = "Problems found while loading plugins:\n  " + problems.joinToString(separator = "\n  ")
    when (reportingPolicy.logLevel) {
      PluginLoadingErrorLogLevel.INFO -> logger.info(message)
      PluginLoadingErrorLogLevel.WARN -> logger.warn(message)
      PluginLoadingErrorLogLevel.ERROR -> logger.error(message)
    }
  }

  fun collectMajorPluginLoadingProblemMessages(pluginSet: PluginSet): List<String> {
    val resolvedPluginSet = pluginSet.resolvedPluginSet
    val result = ArrayList<String>()
    val reportedCycles = HashSet<IdeaPluginDescriptorImpl>()

    pluginSet.input.discoveryResult.descriptorLoadingErrors.mapTo(result) {
      "Failed to read a plugin descriptor from path ${PluginUtils.pluginPathToUserString(it.path)}"
    }

    for (plugin in pluginSet.allPlugins) {
      val earlyExclusionReason = pluginSet.excludedFromCandidateSubset[plugin]
      if (earlyExclusionReason != null) {
        if (!pluginSet.initContext.isPluginDisabled(plugin.pluginId)) {
          earlyExclusionReason.majorProblemLogMessage()?.let(result::add)
        }
        continue
      }

      for (descriptor in plugin.sequenceAllDescriptors()) {
        val exclusionReason = resolvedPluginSet.getExclusionReason(descriptor)
        if ((exclusionReason is PartOfDependencyCycle || exclusionReason is PartOfRuntimeModuleGroupDependencyCycle) &&
            reportedCycles.add(exclusionReason.getDependencyCycleRepresentative())) {
          exclusionReason.majorProblemLogMessage()?.let(result::add)
        }
        if (exclusionReason is PackagePrefixConflictWithAnotherModule) {
          exclusionReason.majorProblemLogMessage()?.let(result::add)
        }
      }
      resolvedPluginSet.getExclusionReason(plugin)
        ?.buildMajorProblemRootCauseLogMessageForPlugin(plugin, resolvedPluginSet)
        ?.let(result::add)
    }
    return result
  }

  private fun DescriptorExclusionReason.buildMajorProblemRootCauseLogMessageForPlugin(
    plugin: PluginMainDescriptor,
    resolvedPluginSet: ResolvedPluginSet,
  ): String? {
    if (this is PartOfDependencyCycle || this is PartOfRuntimeModuleGroupDependencyCycle) {
      return null // special handling in collectMajorPluginProblemLogMessages
    }
    if (this !is ChainedExclusion) {
      return majorProblemLogMessage()
    }
    val exclusionChain = descriptor.sequenceDescriptorExclusionChain(resolvedPluginSet::getExclusionReason)
    val rootCauseDescriptor = exclusionChain.last()
    val rootCause = resolvedPluginSet.getExclusionReason(rootCauseDescriptor)!!
    if (rootCause is ExcludedByEnvironmentConfiguration) {
      return null
    }
    return when (rootCause) {
      is PluginIsMarkedDisabled ->
        "${plugin.shortLogDescription} requires ${rootCause.descriptor.getMainDescriptor().shortLogDescription} to be enabled"
      is PartOfDependencyCycle, is PartOfRuntimeModuleGroupDependencyCycle ->
        "${plugin.shortLogDescription} requires ${rootCause.descriptor.shortLogDescription} which is a part of a dependency cycle"
      else ->
        "${plugin.shortLogDescription} requires ${rootCause.descriptor.getMainDescriptor().shortLogDescription}: ${rootCause.logMessage()}"
    }
  }

  private fun DescriptorExclusionReason.isMajorProblemRootCause(): Boolean {
    return when (this) {
      is ExcludedByEnvironmentConfiguration,
      is OnDemandContentModuleHasNoDependentsLeft,
      is PluginIsMarkedDisabled,
      is PluginVersionIsSuperseded -> false
      is ProductRulesImposedExclusion -> when (productReason) {
        NonBundledPluginsLoadingIsDisabled,
        PluginIsNotContainedInTheExplicitlyConfiguredSubsetOfPluginsForLoading,
        PluginLoadingIsDisabledCompletelyExceptCore -> false
        else -> true
      }
      else -> true
    }
  }

  private fun DescriptorExclusionReason.majorProblemLogMessage(): String? {
    if (!isMajorProblemRootCause()) return null
    return when (this) {
      // more concise messages for cycles
      is PartOfDependencyCycle -> "Dependency cycle detected between ${dependencyCycle.nodesWithDependenciesOnCycle.keys.joinToString { it.shortLogDescription }}"
      is PartOfRuntimeModuleGroupDependencyCycle -> "Runtime module group dependency cycle detected between " +
                                                    dependencyCycle.nodesWithDependenciesOnCycle.keys.joinToString { it.representativeModule.shortLogDescription }
      else -> logMessage()
    }
  }

  private val DependencyRef.logDescription: String
    get() = when (this) {
      is DependencyRef.ContentModule -> "module $moduleId"
      is DependencyRef.Plugin -> "plugin ${pluginId.idString}"
    }
}
