// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus

/**
 * This method resolves id conflicts between plugins and returns an [UnambiguousPluginSet].
 * Conflict resolution tries to account for such hints as essential plugins, disabled plugins, compatibility, `incompatible-with` statements.
 *
 * @param onPluginExcluded Callback invoked for each excluded plugin
 * @return Unambiguous plugin set with all id conflicts resolved
 */
@ApiStatus.Internal
fun PluginInitializationContext.resolveIdConflicts(
  plugins: List<PluginMainDescriptor>,
  onPluginExcluded: (DescriptorExclusionReason) -> Unit,
): UnambiguousPluginSet {
  UnambiguousPluginSet.tryBuild(plugins)
    ?.let { return it }
  // slow path: there are conflicts
  val toExclude = HashSet<PluginMainDescriptor>()
  resolveConflicts(plugins) { reason ->
    onPluginExcluded(reason)
    toExclude.add(reason.descriptor.getMainDescriptor())
  }
  val filteredPlugins = plugins.filter { it !in toExclude }
  return UnambiguousPluginSet.tryBuild(filteredPlugins)
         ?: error("failed to build unambiguous plugin set after conflict resolution")
}

// we use both plugin ids and content module ids here
private fun PluginMainDescriptor.sequenceAllKeysWithOrigin(): Sequence<Pair<Any, PluginModuleDescriptor>> {
  return sequence {
    yield(pluginId to this@sequenceAllKeysWithOrigin)
    pluginAliases.forEach { yield(it to this@sequenceAllKeysWithOrigin) }
    for (module in contentModules) {
      yield(module.moduleId to module)
      module.pluginAliases.forEach { yield(it to module) }
    }
  }
}

private fun PluginMainDescriptor.sequenceAllKeys(): Sequence<Any> = sequenceAllKeysWithOrigin().map { it.first }
private fun PluginMainDescriptor.getKeyDeclarationOrigin(key: Any): PluginModuleDescriptor = sequenceAllKeysWithOrigin().first { it.first == key }.second
private fun PluginMainDescriptor.getLastKeyDeclarationOrigin(key: Any): PluginModuleDescriptor = sequenceAllKeysWithOrigin().last { it.first == key }.second

private fun PluginInitializationContext.resolveConflicts(
  plugins: List<PluginMainDescriptor>,
  exclude: (DescriptorExclusionReason) -> Unit,
) {
  val resolutionMapBuilder = ResolutionMapBuilder<Any, PluginMainDescriptor>(getKeys = PluginMainDescriptor::sequenceAllKeys) { existing, candidate, key ->
    if (existing === candidate) {
      val firstDecl = existing.getKeyDeclarationOrigin(key)
      val lastDecl = existing.getLastKeyDeclarationOrigin(key)
      exclude(createIdConflictReason(existing, firstDecl, lastDecl, key))
      return@ResolutionMapBuilder null
    }
    val existingDecl = existing.getKeyDeclarationOrigin(key)
    val candidateDecl = candidate.getKeyDeclarationOrigin(key)

    val existingEssential = existing.pluginId in essentialPlugins
    val candidateEssential = candidate.pluginId in essentialPlugins
    if (existingEssential && !candidateEssential) {
      exclude(createIdConflictReason(candidate, candidateDecl, existingDecl, key))
      return@ResolutionMapBuilder existing
    }
    if (!existingEssential && candidateEssential) {
      exclude(createIdConflictReason(existing, existingDecl, candidateDecl, key))
      return@ResolutionMapBuilder candidate
    }

    val disabledExisting = isPluginDisabled(existing.pluginId)
    val disabledCandidate = isPluginDisabled(candidate.pluginId)
    if (disabledExisting && !disabledCandidate) {
      exclude(PluginIsMarkedDisabled(existing))
      return@ResolutionMapBuilder candidate
    }
    if (!disabledExisting && disabledCandidate) {
      exclude(PluginIsMarkedDisabled(candidate))
      return@ResolutionMapBuilder existing
    }

    val incompatibilityExisting = validatePluginIsCompatible(existing)
    val incompatibilityCandidate = validatePluginIsCompatible(candidate)
    if (incompatibilityExisting != null && incompatibilityCandidate == null) {
      exclude(incompatibilityExisting)
      return@ResolutionMapBuilder candidate
    }
    if (incompatibilityExisting == null && incompatibilityCandidate != null) {
      exclude(incompatibilityCandidate)
      return@ResolutionMapBuilder existing
    }

    val existingIncompatibleWithCandidate = existing.incompatiblePlugins.contains(candidate.pluginId)
    val candidateIncompatibleWithExisting = candidate.incompatiblePlugins.contains(existing.pluginId)
    if (existingIncompatibleWithCandidate && !candidateIncompatibleWithExisting) {
      exclude(IncompatibleWithAnotherModule(existing, candidate))
      return@ResolutionMapBuilder candidate
    }
    if (!existingIncompatibleWithCandidate && candidateIncompatibleWithExisting) {
      exclude(IncompatibleWithAnotherModule(candidate, existing))
      return@ResolutionMapBuilder existing
    }

    exclude(createIdConflictReason(existing, existingDecl, candidateDecl, key))
    exclude(createIdConflictReason(candidate, candidateDecl, existingDecl, key))
    null
  }
  for (plugin in plugins) {
    resolutionMapBuilder.add(plugin)
  }
}

/**
 * @return `null` if there are id conflicts
 */
@ApiStatus.Internal
fun UnambiguousPluginSet.Companion.tryBuild(
  plugins: List<PluginMainDescriptor>
): UnambiguousPluginSet? {
  val pluginIdMap = HashMap<PluginId, PluginModuleDescriptor>()
  val contentModuleIdMap = HashMap<PluginModuleId, ContentModuleDescriptor>()
  for (plugin in plugins) {
    for ((key, module) in plugin.sequenceAllKeysWithOrigin()) {
      when (key) {
        is PluginId -> {
          if (key in pluginIdMap) return null // conflict
          pluginIdMap[key] = module
        }
        is PluginModuleId -> {
          if (key in contentModuleIdMap) return null // conflict
          contentModuleIdMap[key] = module as ContentModuleDescriptor
        }
        else -> error("unexpected key type: $key")
      }
    }
  }
  return UnambiguousPluginSetImpl(plugins, pluginIdMap, contentModuleIdMap)
}

private class UnambiguousPluginSetImpl(
  override val plugins: List<PluginMainDescriptor>,
  private val pluginIdMap: Map<PluginId, PluginModuleDescriptor>,
  private val contentModuleIdMap: Map<PluginModuleId, ContentModuleDescriptor>,
): UnambiguousPluginSet {
  override fun resolvePluginId(id: PluginId): PluginModuleDescriptor? = pluginIdMap[id]
  override fun resolveContentModuleId(id: PluginModuleId): ContentModuleDescriptor? = contentModuleIdMap[id]
  override fun sequenceAllPluginIds(): Sequence<PluginId> = pluginIdMap.keys.asSequence()
  override fun sequenceAllContentModuleIds(): Sequence<PluginModuleId> = contentModuleIdMap.keys.asSequence()
}

@ApiStatus.Internal
fun AmbiguousPluginSet.Companion.build(
  plugins: List<PluginMainDescriptor>
): AmbiguousPluginSet {
  val pluginIdMap = HashMap<PluginId, SmartList<PluginModuleDescriptor>>()
  val contentModuleIdMap = HashMap<PluginModuleId, SmartList<ContentModuleDescriptor>>()
  for (plugin in plugins) {
    for ((key, module) in plugin.sequenceAllKeysWithOrigin()) {
      when (key) {
        is PluginId -> pluginIdMap.getOrPut(key) { SmartList() }.add(module)
        is PluginModuleId -> contentModuleIdMap.getOrPut(key) { SmartList() }.add(module as ContentModuleDescriptor)
        else -> error("unexpected key type: $key")
      }
    }
  }
  return AmbiguousPluginSetImpl(plugins, pluginIdMap, contentModuleIdMap)
}

private class AmbiguousPluginSetImpl(
  override val plugins: List<PluginMainDescriptor>,
  private val pluginIdMap: Map<PluginId, List<PluginModuleDescriptor>>,
  private val contentModuleIdMap: Map<PluginModuleId, List<ContentModuleDescriptor>>,
): AmbiguousPluginSet {
  override fun resolvePluginId(id: PluginId): Sequence<PluginModuleDescriptor> = pluginIdMap[id]?.asSequence() ?: emptySequence()
  override fun resolveContentModuleId(id: PluginModuleId): Sequence<ContentModuleDescriptor> = contentModuleIdMap[id]?.asSequence() ?: emptySequence()
  override fun sequenceAllPluginIds(): Sequence<PluginId> = pluginIdMap.keys.asSequence()
  override fun sequenceAllContentModuleIds(): Sequence<PluginModuleId> = contentModuleIdMap.keys.asSequence()
}

private fun createIdConflictReason(plugin: PluginMainDescriptor, declarationOrigin: PluginModuleDescriptor, conflictingModule: PluginModuleDescriptor, key: Any) = when (key) {
  is PluginId -> PluginDeclaresConflictingId(plugin, declarationOrigin, conflictingModule, conflictingPluginId = key)
  is PluginModuleId -> PluginDeclaresConflictingId(plugin, declarationOrigin, conflictingModule, conflictingModuleId = key)
  else -> error("unexpected key: $key")
}
