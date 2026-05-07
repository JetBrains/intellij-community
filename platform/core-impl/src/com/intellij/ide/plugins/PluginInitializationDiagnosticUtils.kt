// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginDependencyAnalysis.DependencyRef
import com.intellij.ide.plugins.PluginManagerCore.logger
import com.intellij.ide.plugins.PluginManagerCore.oldPluginSetBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.asSafely
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PluginInitializationDiagnosticUtils {
  fun logExclusionTree(resolvedPluginSet: ResolvedPluginSet, incompletePlugins: HashMap<PluginId, PluginMainDescriptor>) {
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

    fun StringBuilder.appendIndentString(indent: Int) {
      repeat(indent) { append("  ") }
      if (indent > 0) append("└ ")
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
          val disabledPlugin = broadResolveContext.resolveReference(ref).firstOrNull { resolvedPluginSet.initContext.isPluginDisabled(it.pluginId) }
          if (disabledPlugin != null) {
            appendLine("${disabledPlugin.shortLogDescription} is marked disabled")
          } else {
            when (ref) {
              is DependencyRef.ContentModule -> append("module ${ref.moduleId.name} (namespace=${ref.moduleId.namespace})")
              is DependencyRef.Plugin -> append("plugin ${ref.pluginId.idString}")
            }
            appendLine(" is not resolved")
          }

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

  fun runNewPluginSetDiagnosticsIfNeeded(
    initContext: PluginInitializationContext,
    pluginsToLoad: UnambiguousPluginSet,
    incompletePlugins: HashMap<PluginId, PluginMainDescriptor>,
    idMap: Map<PluginId, PluginModuleDescriptor>,
    fullIdMap: Map<PluginId, PluginModuleDescriptor>,
    fullContentModuleIdMap: Map<PluginModuleId, ContentModuleDescriptor>,
    pluginNonLoadReasons: MutableMap<PluginId, PluginNonLoadReason>,
    adaptedPluginSet: PluginSet,
  ) {
    if (System.getProperty("psr.diff") != "true" && System.getProperty("psr.bisect") == null) {
      return
    }
    val oldLoadingErrors = ArrayList<PluginNonLoadReason>()
    val (oldSet, _) = oldPluginSetBuilder(
      initContext,
      PluginsDiscoveryResult.build(emptyList()),
      pluginsToLoad,
      incompletePlugins,
      idMap,
      fullIdMap,
      fullContentModuleIdMap,
      pluginNonLoadReasons,
      oldLoadingErrors::add,
    )

    val psrBisect = System.getProperty("psr.bisect")
    if (psrBisect != null) {
      val bisectSequence = buildBisectSequence(
        newOrder = adaptedPluginSet.sequenceResolvedSortedDescriptorsForRegistration().toList(),
        oldOrder = oldSet.sequenceResolvedSortedDescriptorsForRegistration().toList(),
        psrBisect = psrBisect,
      )
      adaptedPluginSet.descriptorsSequenceForRegistrationInBisectMode = bisectSequence
    }

    if (System.getProperty("psr.diff") == "true") {
      logger.warn("========= Plugin Set Resolution Diff =========")
      val (oldDescriptorRegistrationOrder, newDescriptorRegistrationOrder) = oldSet.sequenceResolvedSortedDescriptorsForRegistration()
        .toList() to adaptedPluginSet.sequenceResolvedSortedDescriptorsForRegistration().toList()
      val oldDescriptorsSet = oldDescriptorRegistrationOrder.toSet()
      val newDescriptorsSet = newDescriptorRegistrationOrder.toSet()
      if (oldDescriptorsSet != newDescriptorsSet) {
        (oldDescriptorsSet - newDescriptorsSet).takeIf { it.isNotEmpty() }?.let {
          logger.warn("!!! Old descriptors that are excluded by new resolver:\n" + it.joinToString("\n") + "\n")
        }
        (newDescriptorsSet - oldDescriptorsSet).takeIf { it.isNotEmpty() }?.let {
          logger.warn("!!! New descriptors that are included by new resolver:\n" + it.joinToString("\n") + "\n")
        }
      }
      if (oldDescriptorRegistrationOrder != newDescriptorRegistrationOrder) {
        logger.warn("!!! Old descriptor registration order:\n" + oldDescriptorRegistrationOrder.joinToString("\n") + "\n")
        logger.warn("!!! New descriptor registration order:\n" + newDescriptorRegistrationOrder.joinToString("\n") + "\n")
      }
      else {
        logger.warn("Enabled modules are identical")
      }

      val newClassloaderConfOrder = adaptedPluginSet.getModulesOrderedForClassLoaderConfiguration().toList()
      val oldClassloaderOrder = oldSet.getModulesOrderedForClassLoaderConfiguration().toList()
      if (newClassloaderConfOrder != oldClassloaderOrder) {
        logger.warn("!!! Old classloader configuration order:\n" + oldClassloaderOrder.joinToString("\n") + "\n")
        logger.warn("!!! New classloader configuration order:\n" + newClassloaderConfOrder.joinToString("\n") + "\n")
      }
      else {
        logger.warn("Classloader configuration order is identical")
      }
      logger.warn("==============================================")
    }
  }

  private fun buildBisectSequence(
    newOrder: List<IdeaPluginDescriptorImpl>,
    oldOrder: List<IdeaPluginDescriptorImpl>,
    psrBisect: String,
  ): Sequence<IdeaPluginDescriptorImpl> {
    val logger = Logger.getInstance(PluginSet::class.java)
    logger.warn("!!!! BISECT MODE !!!! provide -Dpsr.bisect={sequence of 0 and 1 symbols: 0 - test fails, 1 - test succeeds, start with an empty sequence; e.g. 000101}")
    logger.warn("!!!! psr.bisect=$psrBisect")
    check(newOrder.toSet() == oldOrder.toSet()) { "New order and old order must have the same set of plugins" }
    val descriptorToExpectedIndex = HashMap<IdeaPluginDescriptorImpl, Int>()
    val indexToDescriptor = HashMap<Int, IdeaPluginDescriptorImpl>()
    for ((index, descriptor) in oldOrder.withIndex()) {
      descriptorToExpectedIndex[descriptor] = index
      indexToDescriptor[index] = descriptor
    }

    val initialOrder = newOrder.mapTo(ArrayList()) { descriptorToExpectedIndex[it]!! }
    val bisectOrder = ArrayList(initialOrder)

    val totalInvs = bubbleSort(ArrayList(initialOrder)).first
    logger.warn("!!!! total inversions count: $totalInvs")

    var L = 0 // fails
    var R = totalInvs // succeeds
    for (result in psrBisect) {
      val mid = (L + R) / 2
      when (result) {
        '0' -> L = mid
        '1' -> R = mid
        else -> error("Unknown bisect result, use only 0 or 1 symbols: $result")
      }
    }
    logger.warn("!!!! L position (test still fails at): $L")
    logger.warn("!!!! R position (test still succeeds at): $R")

    val mid = (L + R) / 2
    logger.warn("!!!! building sequence at $mid")
    val (invs, lastSwapPos) = bubbleSort(bisectOrder, mid)
    if (invs != mid) {
      logger.warn("!!!! ERROR: applied inversions count is not equal to expected count: $invs")
    }
    val (d1, d2) = indexToDescriptor[bisectOrder[lastSwapPos]] to indexToDescriptor[bisectOrder[lastSwapPos + 1]]
    logger.warn("!!!! last inversion:\n  $d2\n  was put after\n  $d1")
    val nextInversionOrder = ArrayList(initialOrder)
    val (_, nextSwapPos) = bubbleSort(nextInversionOrder, mid + 1)
    val (n1, n2) = indexToDescriptor[nextInversionOrder[nextSwapPos]] to indexToDescriptor[nextInversionOrder[nextSwapPos + 1]]
    logger.warn("!!!! next inversion would be:\n  put $n2\n  after $n1")
    val bisectSequence = sequence {
      for (index in bisectOrder) {
        yield(indexToDescriptor[index]!!)
      }
    }
    if (System.getProperty("psr.bisect.show.sequence") == "true") {
      logger.warn("!!!! running sequence:\n${bisectSequence.joinToString("\n")}\n\n!!!!")
    }
    return bisectSequence
  }

  /**
   * @return (inversion count, last swap index)
   */
  private fun bubbleSort(array: ArrayList<Int>, targetInversions: Int = -1): Pair<Int, Int> {
    if (targetInversions == 0) return 0 to -1
    val n = array.size
    var invs = 0
    repeat(n) {
      for (i in 0..<(n - 1)){
        if (array[i] > array[i + 1]) {
          val temp = array[i]
          array[i] = array[i + 1]
          array[i + 1] = temp
          invs++
          if (invs == targetInversions) {
            return invs to i
          }
        }
      }
    }
    return invs to -1
  }
}
