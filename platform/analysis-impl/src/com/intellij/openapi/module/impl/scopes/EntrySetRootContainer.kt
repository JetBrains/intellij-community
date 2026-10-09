// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.EnumeratedEntry
import com.intellij.openapi.roots.impl.RootDescriptor
import com.intellij.openapi.vfs.VirtualFile
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap

/**
 * The multiverse container, which has to answer *which* order entry brought a root in, not only whether the root is
 * there - and does so without storing a single root.
 *
 * What a scope knows that nothing else does is small: which modules, libraries and SDKs its order-entry walk
 * reached, in which order, and - for a module - whether the walk took its production roots, its test roots or both.
 * Everything else is global. That a root belongs to a given library is what the file index already answers on every
 * `contains` call ([com.intellij.openapi.roots.ProjectFileIndex.getModuleSourceOrLibraryClassesRoots]), and the
 * order of the roots within one entity is a property of the entity, kept once per project in [ScopeEntityRoots].
 *
 * So the container is a map of ~95 entities rather than one of ~5100 roots, which is what the per-root slot used to
 * cost: 16.5 GiB of cached scopes in the snapshot of IJPL-255937, about 0.84 GiB as an entity map. See
 * `docs/IntelliJ-Platform/4_man/Lang/Shared-Sources/Module-Scope-Root-Container.md`.
 *
 * Priorities are a dense 1-based index over the distinct entities, so 0 keeps meaning "the entity is absent"; only
 * their order matters, and that order is the order the entries were enumerated in.
 *
 * Storing owners rather than roots also lifts the IJPL-339 limitation the root map had: one root could hold one
 * descriptor, so a source root shared by two modules reported only whichever of them the walk reached first. Here
 * a shared root reports every owner the scope reaches, which is the point of shared sources. See
 * `RootContainerScopeUnionTest.united scope reports both contexts of a root that both scopes contain`.
 */
internal class EntrySetRootContainer(
  private val entityRoots: ScopeEntityRoots,
  /** entity -> `(priority shl FLAG_BITS) or flags`; 0 (the fastutil default) means the entity is not reachable. */
  private val entries: Object2IntOpenHashMap<ScopeEntity>,
) : RootContainer {

  override fun rankOf(root: VirtualFile): Long =
    throw UnsupportedOperationException("the multiverse container ranks a root through its owner")

  @Deprecated("use findEntity instead")
  override fun containsRoot(root: VirtualFile): Boolean =
    throw UnsupportedOperationException("the multiverse container does not store roots")

  override fun findEntity(rootDescriptor: RootDescriptor): ScopeEntity? =
    withEntry(rootDescriptor, absent = null) { entity, value ->
      if (acceptsRoot(entity, rootDescriptor.root, value)) entity else null
    }

  override fun priorityOf(rootDescriptor: RootDescriptor): Int =
    withEntry(rootDescriptor, absent = 0) { entity, value ->
      if (acceptsRoot(entity, rootDescriptor.root, value)) value ushr FLAG_BITS else 0
    }

  override fun rankOf(rootDescriptor: RootDescriptor): Long =
    withEntry(rootDescriptor, absent = 0L) { entity, value ->
      // One lookup in the shared table serves both the root filter and the position within the entity.
      val order = entityRoots.rootsOf(entity)
      val index = order.indexOf(rootDescriptor.root)
      if (acceptsRoot(entity, order, index, value)) rank(value ushr FLAG_BITS, index) else 0L
    }

  /**
   * Runs [block] with the key of this container that stands for the root's owner and its entry value, or returns
   * [absent] when the scope does not reach the owner. Inline, so that the common case - the owner the file index
   * reports is a key here - probes the map once and allocates nothing.
   */
  private inline fun <R> withEntry(rootDescriptor: RootDescriptor, absent: R, block: (ScopeEntity, Int) -> R): R {
    val owner = scopeEntityOf(rootDescriptor)
    if (owner != null) {
      val value = entries.getInt(owner)
      if (value != 0) return block(owner, value)
    }
    val entity = when (owner) {
      null -> findUnattributedRoot(rootDescriptor.root)
      is Sdk -> findSdkByValue(owner)
      else -> null
    } ?: return absent
    return block(entity, entries.getInt(entity))
  }

  /** @see isSameSdkInTests */
  private fun findSdkByValue(sdk: Sdk): ScopeEntity? {
    if (!ApplicationManager.getApplication().isUnitTestMode) return null
    return entries.keys.firstOrNull { it is Sdk && isSameSdkInTests(it, sdk) }
  }

  /**
   * A root the file index could not attribute to an owner - a file set registered without the data that names one,
   * which [ScopeEntity] maps to `null`. The descriptor-based container answered those from its root map; here the
   * only way is to ask the entities this scope has, which is a probe per entity and so stays bounded by the entry
   * count rather than the root count.
   */
  private fun findUnattributedRoot(root: VirtualFile): ScopeEntity? {
    for (entry in entries.object2IntEntrySet().fastIterator()) {
      val order = entityRoots.rootsOf(entry.key)
      val index = order.indexOf(root)
      if (index >= 0 && accepts(order, index, entry.intValue)) return entry.key
    }
    return null
  }

  /**
   * Whether the walk that built this container took this particular root of an entity it reached. Only a module
   * entry is filtered - the `TESTS` option, and a production-on-test dependency, select roots *within* it - so for
   * a library or an SDK reaching the entity is the whole answer.
   */
  private fun acceptsRoot(entity: ScopeEntity, root: VirtualFile, value: Int): Boolean {
    if (!entityHasTestRoots(entity)) return true
    val order = entityRoots.rootsOf(entity)
    return acceptsRoot(entity, order, if (order.hasTestRoots) order.indexOf(root) else -1, value)
  }

  /** The same, for a caller that already holds the entity's [order] and the root's [index] in it. */
  private fun acceptsRoot(entity: ScopeEntity, order: EntityRootOrder, index: Int, value: Int): Boolean {
    if (!entityHasTestRoots(entity)) return true
    if (!order.hasTestRoots) return value and PRODUCTION_ROOTS != 0
    // A root an OrderEnumerationHandler contributed is not in the entity's own list; the entity is reachable and
    // there is nothing better to go on, so take it.
    if (index < 0) return true
    return accepts(order, index, value)
  }

  private fun accepts(order: EntityRootOrder, index: Int, value: Int): Boolean =
    if (order.isTestRoot(index)) value and TEST_ROOTS != 0 else value and PRODUCTION_ROOTS != 0

  override fun getRoots(): Collection<VirtualFile> = materializeRoots()

  override fun getSortedRoots(): Collection<VirtualFile> = materializeRoots()

  /**
   * The roots the walk would have produced, rebuilt from the entities, in rank order and deduplicated the way the
   * walk deduplicated them: the first entity to reach a root owns it.
   *
   * Nothing on a hot path calls this - `contains`, `compare` and `getFileInfo` all go through the file index - and
   * the one production caller, the file enumeration of a single-root scope, only ever asks tiny containers.
   */
  private fun materializeRoots(): List<VirtualFile> {
    val ordered = ArrayList<ScopeEntity>(entries.size)
    entries.keys.forEach { ordered.add(it) }
    ordered.sortBy { entity -> entries.getInt(entity) ushr FLAG_BITS }

    val result = ArrayList<VirtualFile>()
    val seen = HashSet<VirtualFile>()
    for (entity in ordered) {
      val value = entries.getInt(entity)
      val order = entityRoots.rootsOf(entity)
      for (index in order.roots.indices) {
        if (!accepts(order, index, value)) continue
        val root = order.roots[index]
        if (seen.add(root)) result.add(root)
      }
    }
    return result
  }

  override val size: Int
    get() = materializeRoots().size

  override val approximateSize: Int
    get() {
      var total = 0
      for (entity in entries.keys) {
        total += entityRoots.rootsOf(entity).size
      }
      return total
    }

  override val entityCount: Int
    get() = entries.size

  override val entryValueBytes: Int
    get() = Int.SIZE_BYTES

  companion object {
    /** The entry value packs the root filter under the priority; both bits set means the entity is taken whole. */
    private const val PRODUCTION_ROOTS = 1
    private const val TEST_ROOTS = 2
    private const val FLAG_MASK = PRODUCTION_ROOTS or TEST_ROOTS
    private const val FLAG_BITS = 2

    private fun rank(priority: Int, indexWithinEntity: Int): Long =
      (priority.toLong() shl 32) or (if (indexWithinEntity < 0) Int.MAX_VALUE.toLong() else indexWithinEntity.toLong())

    internal fun build(project: Project,
                       enumeratedEntries: Collection<EnumeratedEntry>): EntrySetRootContainer {
      val entries = Object2IntOpenHashMap<ScopeEntity>(enumeratedEntries.size)
      var nextPriority = 1
      for (enumerated in enumeratedEntries) {
        val entity = scopeEntityOf(enumerated.orderEntry()) ?: continue
        val flags = flagsOf(enumerated.includeProduction(), enumerated.includeTests())
        val existing = entries.getInt(entity)
        if (existing == 0) {
          // priorities are 1-based, so 0 means "absent"
          entries.put(entity, (nextPriority++ shl FLAG_BITS) or flags)
        }
        else {
          // The same entity reached twice - a module both as a dependency and through its own source entry, say.
          // The walk would have taken the union of their roots, at the priority of the first arrival.
          entries.put(entity, existing or flags)
        }
      }
      entries.trim()
      return EntrySetRootContainer(ScopeEntityRoots.getInstance(project), entries)
    }

    private fun flagsOf(includeProduction: Boolean, includeTests: Boolean): Int =
      (if (includeProduction) PRODUCTION_ROOTS else 0) or (if (includeTests) TEST_ROOTS else 0)

    internal fun merge(containers: List<EntrySetRootContainer>): EntrySetRootContainer {
      val result = Object2IntOpenHashMap<ScopeEntity>()

      var maxPriority = 0
      for (container in containers) {
        val offset = maxPriority
        var curMax = 0
        for (entry in container.entries.object2IntEntrySet().fastIterator()) {
          val value = entry.intValue
          val priority = (value ushr FLAG_BITS) + offset
          curMax = maxOf(curMax, priority)

          val merged = (priority shl FLAG_BITS) or (value and FLAG_MASK)
          val existing = result.getInt(entry.key)
          // First arrival wins the priority, as in build, but the union of the two filters is what the union of the
          // two walks would have taken.
          result.put(entry.key, if (existing == 0) merged else existing or (value and FLAG_MASK))
        }

        maxPriority = maxOf(maxPriority, curMax)
      }
      result.trim()
      return EntrySetRootContainer(containers.first().entityRoots, result)
    }
  }
}
