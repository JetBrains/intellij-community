// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginDependencyAnalysis.DependencyRef
import com.intellij.ide.plugins.PluginSetConstraintsResolver.CandidateState.Candidate
import com.intellij.ide.plugins.PluginSetConstraintsResolver.CandidateState.Excluded
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.graph.DFSTBuilder
import com.intellij.util.graph.OutboundSemiGraph
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun PluginInitializationContext.resolveConstraints(
  pluginSet: UnambiguousPluginSet,
): ResolvedPluginSet {
  val resolver = PluginSetConstraintsResolver(this, pluginSet)
  return resolver.resolveConstraints()
}

private class PluginSetConstraintsResolver(
  private val initContext: PluginInitializationContext,
  private val pluginSet: UnambiguousPluginSet,
) {
  private sealed class CandidateState {
    class Excluded(val reason: DescriptorExclusionReason) : CandidateState()
    class Candidate(var listeners: ArrayList<ExclusionListenerData>? = null) : CandidateState() {
      fun addListener(listener: ExclusionListenerData) {
        if (listeners == null) {
          listeners = ArrayList()
        }
        listeners!!.add(listener)
      }
    }
  }

  // LinkedHashMap ensures stable order
  private val candidates: LinkedHashMap<IdeaPluginDescriptorImpl, CandidateState>

  private fun IdeaPluginDescriptorImpl.getState(): CandidateState = candidates[this] ?: error("Unknown descriptor: $this")

  init {
    val allDescriptors = pluginSet.sequenceAllDescriptors().toList()
    candidates = LinkedHashMap(allDescriptors.size)
    for (descriptor in allDescriptors) {
      candidates[descriptor] = Candidate()
    }
  }

  fun resolveConstraints(): ResolvedPluginSet {
    applyEnvironmentConfiguredExclusions()
    applyProductRulesImposedExclusions()

    val constraintBuilders = listOf(
      ::establishPluginIntegrityConstraints,
      ::establishDependencyFulfillmentConstraintsAndRememberResolvedDependencies,
      ::rememberIncompatibleWithViolations,
      ::rememberPackagePrefixRegistration
    )

    for (candidate in candidates.keys) {
      for (builder in constraintBuilders) {
        if (candidate.getState() is Excluded) break
        builder(candidate)
      }
    }

    resolveRemainingIncompatibleWithViolations()
    resolveRemainingPackagePrefixConflicts()

    while (true) {
      // if there are cycles, at least one candidate gets excluded, so the process is finite;
      // in fact, candidates are excluded in groups, so it should converge pretty fast;
      // we don't expect cycles, so if they happen, it is a slow path anyway
      tryBuildRuntimeModuleGroupDAGOrExcludeCycles()?.let {
        return it
      }
    }
  }

  /** DFS exclusion semantics */
  private fun exclude(reason: DescriptorExclusionReason) {
    val candidate = reason.descriptor
    when (val state = candidate.getState()) {
      is Excluded -> return
      is Candidate -> {
        candidates[candidate] = Excluded(reason)
        state.listeners?.forEach { listener ->
          processExclusionListener(candidate, listener)
        }
      }
    }
  }

  private fun processExclusionListener(excludedDescriptor: IdeaPluginDescriptorImpl, data: ExclusionListenerData) {
    when (data) {
      is ExcludeDependsDescriptorOnParentExclusion ->
        exclude(DependsParentIsExcluded(data.dependsDescriptor))
      is ExcludeContentModuleOnPluginExclusion ->
        exclude(ContentModuleParentIsExcluded(data.contentModule))
      is ExcludePluginOnRequiredContentModuleExclusion ->
        exclude(RequiredContentModuleIsExcluded(data.plugin, excludedDescriptor as ContentModuleDescriptor))
      is ExcludeDependentDescriptorOnModuleExclusion ->
        exclude(DependencyIsExcluded(data.dependentDescriptor, excludedDescriptor as PluginModuleDescriptor))
    }
  }

  /** Special case for dependency cycle exclusions */
  private fun batchExclude(candidatesToExclude: Collection<IdeaPluginDescriptorImpl>, reasonBuilder: (IdeaPluginDescriptorImpl) -> DescriptorExclusionReason) {
    val deferredListeners = ArrayList<Pair<IdeaPluginDescriptorImpl, ArrayList<ExclusionListenerData>>>()
    for (candidate in candidatesToExclude) {
      when (val state = candidate.getState()) {
        is Excluded -> continue
        is Candidate -> {
          candidates[candidate] = Excluded(reasonBuilder(candidate))
          state.listeners?.let { deferredListeners.add(candidate to it) }
        }
      }
    }
    for ((candidate, listeners) in deferredListeners) {
      listeners.forEach { listener ->
        processExclusionListener(candidate, listener)
      }
    }
  }

  private fun applyProductRulesImposedExclusions() {
    for ((module, reason) in initContext.provideModuleExclusionsImposedByProductRules(pluginSet)) {
      exclude(ProductRulesImposedExclusion(module, reason))
    }
  }

  private fun applyEnvironmentConfiguredExclusions() {
    for ((moduleId, envConfig) in initContext.environmentConfiguredModules) {
      val module = pluginSet.resolveContentModuleId(moduleId) ?: run {
        if (envConfig.unavailabilityReason == null) {
          PluginManagerCore.logger.warn("Environment-configured module is not found: ${moduleId.displayName}") // TODO ideally this should be an exception
        }
        continue
      }
      if (envConfig.unavailabilityReason != null) {
        exclude(ExcludedByEnvironmentConfiguration(module, envConfig.unavailabilityReason))
      }
    }
  }

  /**
   * 1. if a depends-descriptor's parent is excluded, it is excluded too;
   * 2. if a content module's plugin is excluded, it is excluded too;
   * 3. if a required content module is excluded, its main plugin descriptor is excluded too.
   */
  private fun establishPluginIntegrityConstraints(candidate: IdeaPluginDescriptorImpl) {
    when (candidate) {
      is DependsSubDescriptor ->
        when (val parentState = candidate.parent.getState()) {
          is Excluded -> exclude(DependsParentIsExcluded(candidate))
          is Candidate -> parentState.addListener(ExcludeDependsDescriptorOnParentExclusion(candidate))
        }
      is ContentModuleDescriptor ->
        when (val parentState = candidate.parent.getState()) {
          is Excluded -> exclude(ContentModuleParentIsExcluded(candidate))
          is Candidate -> parentState.addListener(ExcludeContentModuleOnPluginExclusion(candidate))
        }
      is PluginMainDescriptor -> {
        for (contentModule in candidate.contentModules) {
          if (!contentModule.isRequiredContentModule) {
            continue
          }
          when (val contentModuleState = contentModule.getState()) {
            is Excluded -> {
              exclude(RequiredContentModuleIsExcluded(candidate, contentModule))
              break
            }
            is Candidate -> contentModuleState.addListener(ExcludePluginOnRequiredContentModuleExclusion(candidate))
          }
        }
      }
    }
    // TODO: do we want to support non-optional `depends` with a sub-descriptor?
  }

  private fun sequenceAllDependenciesOfCandidateIncludingCompatibility(candidate: IdeaPluginDescriptorImpl): Sequence<DependencyRef> {
    return PluginDependencyAnalysis.sequenceStrictDependencies(candidate) + initContext.provideCompatibilityDependencies(candidate, pluginSet)
  }

  /**
   * Stores a list of descriptor's dependencies, i.e., such modules (and descriptors) that must be registered
   * by the application before ours and whose resources and classes must be available in our classloader.
   *
   * For `<depends>` dependencies **does not** include edges to the content modules of the target plugin
   * (the accurate set of such dependencies can only be determined after all exclusions are settled).
   *
   * LinkedHashMap is used to preserve iteration order.
   */
  private val resolvedDependenciesLists: LinkedHashMap<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>> = LinkedHashMap()

  /**
   * For all strict dependencies and implicit dependencies provided by [PluginInitializationContext.provideCompatibilityDependencies]:
   * 1. if dependency is not resolved, the candidate is excluded;
   * 2. if dependency is excluded, the candidate is excluded;
   * 3. if dependency violates visibility rules, the candidate is excluded.
   */
  private fun establishDependencyFulfillmentConstraintsAndRememberResolvedDependencies(candidate: IdeaPluginDescriptorImpl) {
    val resolvedDependencies = ArrayList<IdeaPluginDescriptorImpl>()
    val seenDependencies = HashSet<IdeaPluginDescriptorImpl>()
    fun tryAddDependency(dependency: IdeaPluginDescriptorImpl): Boolean {
      if (dependency !== candidate && seenDependencies.add(dependency)) {
        resolvedDependencies.add(dependency)
        return true
      }
      return false
    }
    for (dependencyRef in sequenceAllDependenciesOfCandidateIncludingCompatibility(candidate)) {
      val target = pluginSet.resolveReference(dependencyRef)
      if (target == null) {
        exclude(DependencyIsNotResolved(candidate, dependencyRef))
        return
      }
      else if (tryAddDependency(target)) {
        if (target is ContentModuleDescriptor && dependencyRef is DependencyRef.ContentModule) {
          val visibilityViolation = PluginSetBuilder.checkVisibilityAndReturnErrorMessage(
            candidate as? ContentModuleDescriptor ?: candidate.getMainDescriptor(),
            target
          )
          if (visibilityViolation != null) {
            exclude(DependencyIsNotVisible(candidate, target, visibilityViolation))
            return
          }
        }
        when (val targetState = target.getState()) {
          is Excluded -> {
            exclude(DependencyIsExcluded(candidate, target))
            return
          }
          is Candidate -> targetState.addListener(ExcludeDependentDescriptorOnModuleExclusion(candidate))
        }
      }
    }
    // handle implicit dependencies
    when (candidate) {
      is PluginMainDescriptor -> {}
      is ContentModuleDescriptor -> {
        if (candidate.moduleLoadingRule == ModuleLoadingRule.OPTIONAL) {
          // there is an implicit dependency on main
          tryAddDependency(candidate.parent)
        }
      }
      is DependsSubDescriptor -> {
        // `<depends>`' parent must be registered before the sub-descriptor
        tryAddDependency(candidate.parent)
      }
    }
    resolvedDependenciesLists[candidate] = resolvedDependencies
  }

  private val essentialModulesClosure: Set<PluginModuleDescriptor> by lazy {
    // TODO: does not handle implicitly added dependencies. Is it a big problem?
    PluginDependencyAnalysis.getRequiredTransitiveModules(
      initContext = initContext,
      plugins = initContext.essentialPlugins.mapNotNull { pluginSet.resolvePluginId(it) },
      ambiguousPluginSet = pluginSet.asAmbiguousPluginSet(),
      null
    )
  }

  private tailrec fun IdeaPluginDescriptorImpl.isEssential(): Boolean {
    return when (this) {
      is PluginModuleDescriptor -> this in essentialModulesClosure
      is DependsSubDescriptor -> { // TODO do we even want to support such cases?
        val dependency = parent.pluginDependencies.find { it.subDescriptor === this } ?: error("unattached depends sub-descriptor: $this")
        if (dependency.isOptional) false
        else parent.isEssential()
      }
    }
  }

  val incompatibleWithEdges = ArrayList<Pair<IdeaPluginDescriptorImpl, PluginModuleDescriptor>>()

  private fun rememberIncompatibleWithViolations(candidate: IdeaPluginDescriptorImpl) {
    for (incompatiblePluginId in candidate.incompatiblePlugins) {
      val target = pluginSet.resolvePluginId(incompatiblePluginId)
      if (target != null && target.getState() is Candidate) {
        incompatibleWithEdges.add(candidate to target)
      }
    }
  }

  private fun resolveRemainingIncompatibleWithViolations() {
    for ((candidate, incompatiblePlugin) in incompatibleWithEdges) {
      if (candidate.getState() is Excluded || incompatiblePlugin.getState() is Excluded) {
        continue
      }
      val (survivor, excluded) = if (candidate.isEssential()) {
        candidate to incompatiblePlugin
      }
      else {
        incompatiblePlugin to candidate
      }
      exclude(IncompatibleWithAnotherModule(
        descriptor = excluded,
        preferredIncompatibleModule = survivor as? PluginModuleDescriptor ?: survivor.getMainDescriptor()
      ))
    }
  }

  private val packagePrefixRegistrations = HashMap<String, ArrayList<PluginModuleDescriptor>>()

  private fun rememberPackagePrefixRegistration(candidate: IdeaPluginDescriptorImpl) {
    if (candidate !is PluginModuleDescriptor) {
      return
    }
    if (candidate.packagePrefix != null) {
      packagePrefixRegistrations.getOrPut(candidate.packagePrefix) { ArrayList() }.add(candidate)
    }
  }

  private fun resolveRemainingPackagePrefixConflicts() {
    for (conflictingModules in packagePrefixRegistrations.values) {
      var currentSurvivor: PluginModuleDescriptor? = null
      for (candidate in conflictingModules) {
        if (candidate.getState() is Excluded) {
          continue
        }
        if (currentSurvivor == null) {
          currentSurvivor = candidate
        }
        else {
          val (survivor, excluded) = if (currentSurvivor.isEssential()) {
            currentSurvivor to candidate
          }
          else {
            candidate to currentSurvivor
          }
          exclude(PackagePrefixConflictWithAnotherModule(excluded, survivor))
          currentSurvivor = survivor
        }
      }
    }
  }


  /**
   * DFSTBuilder expects edge to represent `<` relation, but in our case dependents of a descriptor should come first, so we need dependents, not dependencies
   */
  private fun tryBuildRuntimeModuleGroupDAGOrExcludeCycles(): ResolvedPluginSet? {
    val remainingCandidates = candidates.keys.filterTo(ArrayList()) { it.getState() is Candidate }
    val resolvedDependencies = populateDependsEdges(resolvedDependenciesLists.filterKeys { it.getState() is Candidate })
    val resolvedDependents = resolvedDependencies.invertEdges()
    val sortedCandidates = sortRemainingCandidatesTopologicallyOrExcludeCycles(remainingCandidates, resolvedDependencies, resolvedDependents)
                           ?: return null
    val runtimeModuleGroupGraph = buildAcyclicRuntimeModuleGroupGraphOrExcludeCycles(sortedCandidates, resolvedDependencies)
                                  ?: return null

    // preserves all keys for 'unknown descriptor' check
    val exclusions = candidates.mapValuesTo(HashMap(candidates.size)) { (it.value as? Excluded)?.reason }
    val resolvedPluginSet = ResolvedPluginSetImpl(
      originalPluginSet = pluginSet,
      initContext = initContext,
      sortedResolvedDescriptors = LinkedHashSet(sortedCandidates),
      runtimeModuleGroupGraph = runtimeModuleGroupGraph,
      exclusions = exclusions,
      resolvedDependencies = resolvedDependencies,
      resolvedDependents = resolvedDependents,
    )
    return resolvedPluginSet
  }


  /**
   * To preserve compatibility, all "active" "depends"-edges, in fact, should be treated as a dependency on all loaded modules of the target plugin, so we
   * add try to process them at the end of the resolution attempt when all other exclusions are settled.
   */
  private fun populateDependsEdges(
    remainingCandidatesDependencies: Map<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>
  ): Map<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>> {
    return remainingCandidatesDependencies.mapValues { (descriptor, dependencies) ->
      var populatedList: ArrayList<IdeaPluginDescriptorImpl>? = null
      fun contributeDependencies(extra: List<IdeaPluginDescriptorImpl>) {
        if (populatedList == null) {
          populatedList = ArrayList(dependencies)
        }
        populatedList.addAll(extra)
      }
      fun contributeContentModulesFromTarget(targetId: PluginId) {
        val target = pluginSet.resolvePluginId(targetId)
                     ?: return
        assert(target in remainingCandidatesDependencies) {
          "dependency target is excluded, but the descriptor is still a candidate:\ncandidate=$descriptor\ntarget=$target"
        }
        if (target is PluginMainDescriptor && initContext.shouldIncludeContentModulesForDependsEdgeTarget(target)) {
          val remainingContentModules = target.contentModules.filter { it in remainingCandidatesDependencies.keys }
          if (remainingContentModules.isNotEmpty()) {
            contributeDependencies(remainingContentModules)
          }
        }
        // if target is a content module, it is already accounted for, and we don't need to include other content modules from the same plugin
      }
      for (depends in descriptor.pluginDependencies) {
        if (depends.subDescriptor != null) {
          // this case is covered by the statement under this `for` loop;
          // technically it might be that `isOptional` could be `false` here, that's okay;
          // also, `config-file` might be unspecified when `isOptional` is `true`, but for such cases we generate an empty [DependsSubDescriptor],
          // see [PluginDescriptorLoader.loadPluginDependencyDescriptors]
          continue
        }
        if (depends.isOptional) {
          // optional config file that wasn't found, we may skip it
          continue
        }
        contributeContentModulesFromTarget(targetId = depends.pluginId)
      }
      if (descriptor is DependsSubDescriptor) {
        contributeContentModulesFromTarget(targetId = descriptor.dependsTargetId)
      }
      populatedList?.distinct() ?: dependencies
    }
  }

  private fun sortRemainingCandidatesTopologicallyOrExcludeCycles(
    remainingCandidates: ArrayList<IdeaPluginDescriptorImpl>,
    resolvedDependencies: Map<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>,
    resolvedDependents: Map<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>
  ): List<IdeaPluginDescriptorImpl>? {
    val descriptorGraph = DFSTBuilder(DescriptorGraphAdapter(remainingCandidates, resolvedDependents))
    if (!descriptorGraph.isAcyclic) {
      for (component in descriptorGraph.components) {
        if (component.size <= 1) {
          continue
        }
        val component = component.sortedWith(compareBy { it.pluginId }) // makes result stable
        val cycleNodesWithDependencies = component.associateWith { ArrayList<IdeaPluginDescriptorImpl>() }
        for (descriptor in component) {
          cycleNodesWithDependencies[descriptor]!!.addAll(resolvedDependencies[descriptor]!!.filter { it in cycleNodesWithDependencies.keys })
        }
        val cycleInfo = DependencyCycleInfo(cycleNodesWithDependencies)
        batchExclude(cycleNodesWithDependencies.keys) { PartOfDependencyCycle(it, cycleInfo) }
      }
      return null
    }
    remainingCandidates.sortWith(descriptorGraph.comparator())
    return remainingCandidates
  }

  private typealias RepresentativeModule = PluginModuleDescriptor

  private fun buildAcyclicRuntimeModuleGroupGraphOrExcludeCycles(
    sortedCandidates: List<IdeaPluginDescriptorImpl>,
    resolvedDependencies: Map<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>
  ): RuntimeModuleGroupGraphImpl? {
    // should preserve iteration order, so DFSTBuilder is stable
    val representativeToGroups = LinkedHashMap<RepresentativeModule, RuntimeModuleGroupImpl>(sortedCandidates.size)
    val candidateToGroup = HashMap<IdeaPluginDescriptorImpl, RuntimeModuleGroup>(sortedCandidates.size)
    for (candidate in sortedCandidates) {
      val representative = getRuntimeModuleGroupRepresentative(candidate)
      val group = representativeToGroups.getOrPut(representative) { RuntimeModuleGroupImpl(representative) }
      group.sortedDescriptors.add(candidate)
      candidateToGroup[candidate] = group
    }
    val groupToGroupDependencies = HashMap<RuntimeModuleGroup, List<RuntimeModuleGroup>>()
    for (group in representativeToGroups.values) {
      val groupDependencies = ArrayList<RuntimeModuleGroup>()
      val descriptors = group.sortedDescriptors
      val seenDependencies = HashSet<RuntimeModuleGroupImpl>()
      for (descriptor in descriptors) {
        for (target in resolvedDependencies[descriptor]!!) {
          val targetGroup = candidateToGroup[target] as? RuntimeModuleGroupImpl ?: error("runtime module group not found for $target")
          if (targetGroup === group) {
            continue
          }
          if (seenDependencies.add(targetGroup)) {
            groupDependencies.add(targetGroup)
          }
        }
      }
      groupToGroupDependencies[group] = groupDependencies
    }

    val groupToGroupDependents = groupToGroupDependencies.invertEdges()
    val dfstBuilder = DFSTBuilder(RuntimeModuleGroupGraphAdapter(representativeToGroups.values, groupToGroupDependents))
    if (!dfstBuilder.isAcyclic) {
      for (component in dfstBuilder.components) {
        if (component.size <= 1) {
          continue
        }
        val component = component.sortedWith(compareBy { it.representativeModule.pluginId }) // make result stable
        val cycleNodesWithDependencies = component.associateWith { ArrayList<RuntimeModuleGroup>() }
        for (group in component) {
          cycleNodesWithDependencies[group]!!.addAll(groupToGroupDependencies[group]!!.filter { it in cycleNodesWithDependencies.keys })
        }
        val cycleInfo = DependencyCycleInfo(cycleNodesWithDependencies)
        val descriptors = cycleNodesWithDependencies.keys.asSequence().flatMap { it.sortedDescriptors }
          // Exclusion of a "depends" sub-descriptor due to a dependency cycle historically led to exclusion of the corresponding plugin. That behavior is preserved, though it isn't necessary
          .map { if (it is DependsSubDescriptor) it.getMainDescriptor() else it }
          .toList()
        batchExclude(descriptors) { PartOfRuntimeModuleGroupDependencyCycle(it, cycleInfo) }
      }
      return null
    }
    val runtimeModuleGroupGraph = RuntimeModuleGroupGraphImpl(
      sortedGroups = representativeToGroups.values.sortedWith(dfstBuilder.comparator()),
      dependencies = groupToGroupDependencies,
      dependents = groupToGroupDependents,
      descriptorToGroup = candidateToGroup
    )
    return runtimeModuleGroupGraph
  }

  /** finds a representative module for the runtime module group current [candidate] belongs to */
  private tailrec fun getRuntimeModuleGroupRepresentative(candidate: IdeaPluginDescriptorImpl): PluginModuleDescriptor {
    if (candidate is PluginModuleDescriptor) {
      val customized = initContext.provideCustomRuntimeModuleGroupAffiliation(candidate, pluginSet)
      if (customized != null) {
        return getRuntimeModuleGroupRepresentative(customized)
      }
    }
    return when (candidate) {
      is PluginMainDescriptor -> candidate
      is ContentModuleDescriptor -> {
        if (candidate.moduleLoadingRule == ModuleLoadingRule.EMBEDDED) {
          getRuntimeModuleGroupRepresentative(candidate.parent)
        }
        else {
          candidate
        }
      }
      is DependsSubDescriptor -> {
        getRuntimeModuleGroupRepresentative(candidate.getMainDescriptor())
      }
    }
  }

  private class DescriptorGraphAdapter(
    val remainingCandidates: List<IdeaPluginDescriptorImpl>,
    val resolvedDependents: Map<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>
  ) : OutboundSemiGraph<IdeaPluginDescriptorImpl> {
    override fun getNodes(): Collection<IdeaPluginDescriptorImpl> = remainingCandidates
    override fun getOut(node: IdeaPluginDescriptorImpl): Iterator<IdeaPluginDescriptorImpl> {
      return resolvedDependents[node]!!.iterator()
    }
  }

  private class RuntimeModuleGroupGraphAdapter(val groups: Collection<RuntimeModuleGroup>, val dependents: Map<RuntimeModuleGroup, List<RuntimeModuleGroup>>) :
    OutboundSemiGraph<RuntimeModuleGroup> {
    override fun getNodes(): Collection<RuntimeModuleGroup> = groups
    override fun getOut(node: RuntimeModuleGroup): Iterator<RuntimeModuleGroup> {
      return dependents[node]!!.iterator()
    }
  }

  private class RuntimeModuleGroupImpl(
    override val representativeModule: PluginModuleDescriptor,
    override val sortedDescriptors: ArrayList<IdeaPluginDescriptorImpl> = ArrayList(),
  ) : RuntimeModuleGroup {
    override fun toString(): String = buildString {
      append(representativeModule.shortLogDescription)
      if (sortedDescriptors.size > 1) {
        append(" (${sortedDescriptors.joinToString(limit = 5, truncated = "... size=${sortedDescriptors.size}") { it.shortLogDescription }})")
      }
    }
  }

  private class RuntimeModuleGroupGraphImpl(
    override val sortedGroups: List<RuntimeModuleGroup>,
    private val dependencies: Map<RuntimeModuleGroup, List<RuntimeModuleGroup>>,
    private val descriptorToGroup: Map<IdeaPluginDescriptorImpl, RuntimeModuleGroup>,
    private val dependents: Map<RuntimeModuleGroup, List<RuntimeModuleGroup>>,
  ) : RuntimeModuleGroupGraph {
    override fun getRuntimeModuleGroup(resolvedDescriptor: IdeaPluginDescriptorImpl): RuntimeModuleGroup {
      return descriptorToGroup[resolvedDescriptor] ?: throw IllegalArgumentException("unknown descriptor: $resolvedDescriptor")
    }

    override fun getDirectDependencies(group: RuntimeModuleGroup): List<RuntimeModuleGroup> {
      return dependencies[group] ?: throw IllegalArgumentException("unknown group: $group")
    }

    override fun getDirectDependents(group: RuntimeModuleGroup): List<RuntimeModuleGroup> {
      return dependents[group] ?: throw IllegalArgumentException("unknown group: $group")
    }
  }

  private class ResolvedPluginSetImpl(
    /** May contain unresolved plugins */
    override val originalPluginSet: UnambiguousPluginSet,
    override val initContext: PluginInitializationContext,
    override val sortedResolvedDescriptors: Set<IdeaPluginDescriptorImpl>,
    override val runtimeModuleGroupGraph: RuntimeModuleGroupGraph,
    private val exclusions: Map<IdeaPluginDescriptorImpl, DescriptorExclusionReason?>,
    private val resolvedDependencies: Map<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>,
    private val resolvedDependents: Map<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>,
  ) : ResolvedPluginSet {
    override fun getExclusionReason(descriptor: IdeaPluginDescriptorImpl): DescriptorExclusionReason? {
      require(descriptor in exclusions) { "unknown descriptor: $descriptor" }
      return exclusions[descriptor]
    }

    override fun getDirectResolvedDependencies(resolvedDescriptor: IdeaPluginDescriptorImpl): List<IdeaPluginDescriptorImpl> {
      return resolvedDependencies[resolvedDescriptor] ?: throw IllegalArgumentException("unknown/unresolved descriptor: $resolvedDescriptor")
    }

    override fun getDirectResolvedDependents(resolvedDescriptor: IdeaPluginDescriptorImpl): List<IdeaPluginDescriptorImpl> {
      return resolvedDependents[resolvedDescriptor] ?: throw IllegalArgumentException("unknown/unresolved descriptor: $resolvedDescriptor")
    }
  }
}

/**
 * Lambdas are heavy, so only a minimal set of data is stored
 */
private sealed interface ExclusionListenerData

private class ExcludeDependsDescriptorOnParentExclusion(val dependsDescriptor: DependsSubDescriptor) : ExclusionListenerData
private class ExcludeContentModuleOnPluginExclusion(val contentModule: ContentModuleDescriptor) : ExclusionListenerData
private class ExcludePluginOnRequiredContentModuleExclusion(val plugin: PluginMainDescriptor) : ExclusionListenerData
private class ExcludeDependentDescriptorOnModuleExclusion(val dependentDescriptor: IdeaPluginDescriptorImpl) : ExclusionListenerData

private fun UnambiguousPluginSet.sequenceAllDescriptors(): Sequence<IdeaPluginDescriptorImpl> {
  return sequence {
    for (plugin in plugins) {
      yieldAllDescriptors(plugin)
    }
  }
}

/** preserves the key set */
private fun <T> Map<T, List<T>>.invertEdges(): Map<T, List<T>> {
  val result = mapValues { ArrayList<T>() }
  for ((node, edgeTarget) in this) {
    for (target in edgeTarget) {
      result[target]!!.add(node)
    }
  }
  return result
}