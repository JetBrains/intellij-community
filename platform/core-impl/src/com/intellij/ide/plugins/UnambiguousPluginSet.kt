// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginDependencyAnalysis.DependencyRef
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.containers.sequenceOfNotNull
import org.jetbrains.annotations.ApiStatus

/**
 * An instance of [UnambiguousPluginSet] represents a set of plugins that do not conflict
 * with each other by declared plugin and content module ids (including plugin aliases).
 *
 * @see AmbiguousPluginSet
 *
 * TODO: rename into PluginSet after current PluginSet is dropped, rename AmbiguousPluginSet into PluginMultiSet
 */
@ApiStatus.Internal
interface UnambiguousPluginSet {
  val plugins: List<PluginMainDescriptor>

  /**
   * Plugin id can resolve either as a regular plugin id, or as a plugin alias,
   * in which case it may refer either to the plugin descriptor module or to the plugin content module.
   */
  fun resolvePluginId(id: PluginId): PluginModuleDescriptor?

  fun resolveContentModuleId(id: PluginModuleId): ContentModuleDescriptor?

  /**
   * @return a sequence of all keys that are resolvable by [resolvePluginId]
   */
  fun sequenceAllPluginIds(): Sequence<PluginId>

  /**
   * @return a sequence of all keys that are resolvable by [resolveContentModuleId]
   */
  fun sequenceAllContentModuleIds(): Sequence<PluginModuleId>

  companion object
}

/**
 * Counterpart to [UnambiguousPluginSet], plugin set may contain multiple versions of the same plugin,
 * or there can be id conflicts between plugins/modules.
 * Resolve methods return all applicable entries.
 */
@ApiStatus.Internal
interface AmbiguousPluginSet {
  val plugins: List<PluginMainDescriptor>
  /**
   * Plugin id can resolve either as a regular plugin id, or as a plugin alias,
   * in which case it may refer either to the plugin descriptor module or to the plugin content module.
   */
  fun resolvePluginId(id: PluginId): Sequence<PluginModuleDescriptor>

  fun resolveContentModuleId(id: PluginModuleId): Sequence<ContentModuleDescriptor>

  /**
   * @return a sequence of all keys that are resolvable by [resolvePluginId]
   */
  fun sequenceAllPluginIds(): Sequence<PluginId>

  /**
   * @return a sequence of all keys that are resolvable by [resolveContentModuleId]
   */
  fun sequenceAllContentModuleIds(): Sequence<PluginModuleId>

  companion object
}

/**
 * Plugin id can resolve either as a regular plugin id, or as a plugin alias,
 * in which case it may refer either to the plugin descriptor module or to the plugin content module.
 */
@ApiStatus.Internal
fun UnambiguousPluginSet.buildFullPluginIdMapping(): Map<PluginId, PluginModuleDescriptor> =
  sequenceAllPluginIds().associateWith { resolvePluginId(it)!! }

@ApiStatus.Internal
fun UnambiguousPluginSet.buildFullContentModuleIdMapping(): Map<PluginModuleId, ContentModuleDescriptor> =
  sequenceAllContentModuleIds().associateWith { resolveContentModuleId(it)!! }

/**
 * Plugin id can resolve either as a regular plugin id, or as a plugin alias,
 * in which case it may refer either to the plugin descriptor module or to the plugin content module.
 */
@ApiStatus.Internal
fun AmbiguousPluginSet.buildFullPluginIdMapping(): Map<PluginId, List<PluginModuleDescriptor>> =
  sequenceAllPluginIds().associateWith { resolvePluginId(it).toList() }

@ApiStatus.Internal
fun AmbiguousPluginSet.buildFullContentModuleIdMapping(): Map<PluginModuleId, List<ContentModuleDescriptor>> =
  sequenceAllContentModuleIds().associateWith { resolveContentModuleId(it).toList() }

@ApiStatus.Internal
fun UnambiguousPluginSet.resolveReference(ref: DependencyRef): PluginModuleDescriptor? {
  return when (ref) {
    is DependencyRef.Plugin -> resolvePluginId(ref.pluginId)
    is DependencyRef.ContentModule -> resolveContentModuleId(ref.moduleId)
  }
}

@ApiStatus.Internal
fun AmbiguousPluginSet.resolveReference(ref: DependencyRef): Sequence<PluginModuleDescriptor> {
  return when (ref) {
    is DependencyRef.Plugin -> resolvePluginId(ref.pluginId)
    is DependencyRef.ContentModule -> resolveContentModuleId(ref.moduleId)
  }
}

@ApiStatus.Internal
fun UnambiguousPluginSet.asAmbiguousPluginSet(): AmbiguousPluginSet = object : AmbiguousPluginSet {
  override val plugins: List<PluginMainDescriptor> get() = this@asAmbiguousPluginSet.plugins
  override fun resolvePluginId(id: PluginId): Sequence<PluginModuleDescriptor> = sequenceOfNotNull(this@asAmbiguousPluginSet.resolvePluginId(id))
  override fun resolveContentModuleId(id: PluginModuleId): Sequence<ContentModuleDescriptor> = sequenceOfNotNull(this@asAmbiguousPluginSet.resolveContentModuleId(id))
  override fun sequenceAllPluginIds(): Sequence<PluginId> = this@asAmbiguousPluginSet.sequenceAllPluginIds()
  override fun sequenceAllContentModuleIds(): Sequence<PluginModuleId> = this@asAmbiguousPluginSet.sequenceAllContentModuleIds()
}