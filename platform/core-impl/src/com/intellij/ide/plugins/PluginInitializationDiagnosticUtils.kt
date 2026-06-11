// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginDependencyAnalysis.DependencyRef
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.asSafely
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PluginInitializationDiagnosticUtils {
  fun logExclusionTree(logger: Logger, resolvedPluginSet: ResolvedPluginSet, incompletePlugins: Map<PluginId, PluginMainDescriptor>) {
    val broadResolveContext by lazy { AmbiguousPluginSet.build(resolvedPluginSet.originalPluginSet.plugins + incompletePlugins.values) }
    val exclusionChildren = LinkedHashMap<IdeaPluginDescriptorImpl, ArrayList<IdeaPluginDescriptorImpl>>()
    val roots = LinkedHashSet<IdeaPluginDescriptorImpl>()
    for (plugin in resolvedPluginSet.originalPluginSet.plugins) {
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
      appendLine(exclusionReason.logMessage())
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
    val logHeader = "Plugin set resolution:\n"
    val logBuilder = StringBuilder().apply {
      append(logHeader)
      dependencyIsNotResolvedRoots.map { resolvedPluginSet.getExclusionReason(it) as DependencyIsNotResolved }.groupBy { it.dependency }
        .forEach { (ref, roots) ->
          appendDependencyIsNotResolvedLogMessage(ref, broadResolveContext, resolvedPluginSet)

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
    incompletePlugins: Map<PluginId, PluginMainDescriptor>,
    descriptor: IdeaPluginDescriptorImpl,
  ): String? {
    if (resolvedPluginSet.isResolved(descriptor)) {
      return null
    }
    // TODO decrease code duplication
    val broadResolveContext by lazy { AmbiguousPluginSet.build(resolvedPluginSet.originalPluginSet.plugins + incompletePlugins.values) }
    val chain = descriptor.sequenceDescriptorExclusionChain(resolvedPluginSet::getExclusionReason).toList().reversed()
    val msgBuilder = StringBuilder().apply {
      if (chain.firstOrNull()?.let { resolvedPluginSet.getExclusionReason(it) } is DependencyIsNotResolved) {
        val rootCause = resolvedPluginSet.getExclusionReason(chain[0])!! as DependencyIsNotResolved
        appendDependencyIsNotResolvedLogMessage(rootCause.dependency, broadResolveContext, resolvedPluginSet)
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

  private fun DescriptorExclusionReason.logMessage(): String {
    val logDescr = descriptor.shortLogDescription
    return when (this) {
      // chained:
      is ContentModuleParentIsExcluded -> "dependent $logDescr excluded"
      is RequiredContentModuleIsExcluded -> "dependent $logDescr excluded"
      is DependencyIsExcluded -> "dependent $logDescr excluded"
      is DependsParentIsExcluded -> "dependent $logDescr excluded"
      // root:
      is DependencyIsNotResolved -> "dependent $logDescr excluded" // special handling in logExclusionTree
      is DependencyIsNotVisible -> "$logDescr depends on ${dependencyModule.shortLogDescription} which is not visible"
      is ExcludedByEnvironmentConfiguration -> "$logDescr is excluded: ${reason.logMessage}"
      is IncompatibleWithAnotherModule -> "$logDescr is incompatible with ${preferredIncompatibleModule.shortLogDescription}"
      is PackagePrefixConflictWithAnotherModule -> "$logDescr declares the same package prefix as in ${preferredConflictingModule.shortLogDescription}"
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
      is ProductRulesImposedExclusion -> "$logDescr is excluded by product rules: ${this.productReason}"
    }
  }

  private fun StringBuilder.appendDependencyIsNotResolvedLogMessage(
    ref: DependencyRef,
    broadResolveContext: AmbiguousPluginSet,
    resolvedPluginSet: ResolvedPluginSet,
  ) {
    val disabledPlugin = broadResolveContext.resolveReference(ref).firstOrNull { resolvedPluginSet.initContext.isPluginDisabled(it.pluginId) }
    if (disabledPlugin != null) {
      appendLine("${disabledPlugin.shortLogDescription} is marked disabled")
    }
    else {
      when (ref) {
        is DependencyRef.ContentModule -> append("module ${ref.moduleId.name} (namespace=${ref.moduleId.namespace})")
        is DependencyRef.Plugin -> append("plugin ${ref.pluginId.idString}")
      }
      appendLine(" is not resolved")
    }
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
}
