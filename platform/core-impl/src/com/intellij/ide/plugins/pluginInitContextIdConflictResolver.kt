// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus

/**
 * Resolves ID conflicts between plugins (including aliases and content modules),
 * preferring essential plugins to non-essential ones.
 * 
 * Note: Input should already be filtered for compatibility and version selection using
 * [PluginInitializationContext.selectCompatibleAndMostRecentPlugins].
 * 
 * @param compatiblePlugins List of discovered plugins (already filtered for compatibility and version selection)
 * @param onPluginExcluded Callback invoked for each excluded plugin
 * @return Unambiguous plugin set with all ID conflicts resolved
 */
@ApiStatus.Internal
fun PluginInitializationContext.resolveIdConflicts(
  compatiblePlugins: List<DiscoveredPluginsList>,
  onPluginExcluded: (PluginMainDescriptor, PluginNonLoadReason) -> Unit,
): UnambiguousPluginSet {
  val allPlugins = compatiblePlugins.flatMapTo(ArrayList()) { it.plugins }
  UnambiguousPluginSet.tryBuild(allPlugins)
    ?.let { return it }
  // slow path: there are conflicts
  val toExclude = HashSet<PluginMainDescriptor>()
  allPlugins.resolveConflicts(essentialPlugins) { plugin, reason ->
    onPluginExcluded(plugin, reason)
    toExclude.add(plugin)
  }
  val filteredPlugins = allPlugins.filter { it !in toExclude }
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

private fun List<PluginMainDescriptor>.resolveConflicts(
  essentialPlugins: Set<PluginId>,
  exclude: (PluginMainDescriptor, PluginNonLoadReason) -> Unit,
) {
  val resolutionMapBuilder = ResolutionMapBuilder<Any, PluginMainDescriptor>(getKeys = PluginMainDescriptor::sequenceAllKeys) { existing, candidate, key ->
    if (existing === candidate) {
      val firstDecl = existing.getKeyDeclarationOrigin(key)
      val lastDecl = existing.getLastKeyDeclarationOrigin(key)
      exclude(existing, PluginDeclaresConflictingId(firstDecl, lastDecl, key))
      return@ResolutionMapBuilder null
    }
    val existingDecl = existing.getKeyDeclarationOrigin(key)
    val candidateDecl = candidate.getKeyDeclarationOrigin(key)
    val existingEssential = existing.pluginId in essentialPlugins
    val candidateEssential = candidate.pluginId in essentialPlugins
    if (existingEssential && !candidateEssential) {
      exclude(candidate, PluginDeclaresConflictingId(candidateDecl, existingDecl, key))
      return@ResolutionMapBuilder existing
    }
    if (!existingEssential && candidateEssential) {
      exclude(existing, PluginDeclaresConflictingId(existingDecl, candidateDecl, key))
      return@ResolutionMapBuilder candidate
    }
    exclude(existing, PluginDeclaresConflictingId(existingDecl, candidateDecl, key))
    exclude(candidate, PluginDeclaresConflictingId(candidateDecl, existingDecl, key))
    null
  }
  for (plugin in this) {
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
  override fun getFullPluginIdMapping(): Map<PluginId, PluginModuleDescriptor> = pluginIdMap
  override fun getFullContentModuleIdMapping(): Map<PluginModuleId, ContentModuleDescriptor> = contentModuleIdMap
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
  override fun getFullPluginIdMapping(): Map<PluginId, List<PluginModuleDescriptor>> = pluginIdMap
  override fun getFullContentModuleIdMapping(): Map<PluginModuleId, List<ContentModuleDescriptor>> = contentModuleIdMap
}