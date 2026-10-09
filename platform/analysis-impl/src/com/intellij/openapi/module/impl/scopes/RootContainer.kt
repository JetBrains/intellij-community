// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.roots.impl.RootDescriptor
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly

internal sealed interface RootContainer {
  /**
   * An ordering key for [root], or 0 if the root is not in the container. Smaller means earlier in the enumeration,
   * which is what `compare` treats as more preferable. Only the classic container answers this; the multiverse one
   * needs the owner of the root, see the [RootDescriptor] overload.
   */
  fun rankOf(root: VirtualFile): Long

  @Deprecated("use findEntity instead")
  fun containsRoot(root: VirtualFile): Boolean

  /**
   * The module, library or SDK through which this scope reaches the root the file index described, or `null` when
   * the scope does not reach it. This is the multiverse `contains`, and the entity is what the caller turns into a
   * [com.intellij.codeInsight.multiverse.CodeInsightContext].
   */
  fun findEntity(rootDescriptor: RootDescriptor): ScopeEntity?

  /** An ordering key for a root the file index has already resolved to its owner, or 0 if it is not in the scope. */
  fun rankOf(rootDescriptor: RootDescriptor): Long

  /**
   * The high half of [rankOf]: the priority of the entity that brings the root in, or 0 if it is not in the scope.
   * Two roots with different priorities are already ordered by it, and computing it costs no lookup in the shared
   * table of entity roots for a library or an SDK, which is what lets `compare` skip the full rank on most pairs.
   */
  fun priorityOf(rootDescriptor: RootDescriptor): Int

  fun getRoots(): Collection<VirtualFile>

  /**
   * The exact number of distinct roots, which for the multiverse container means materialising them. Use
   * [approximateSize] on any path that runs per scope rather than per test.
   */
  val size: Int

  /**
   * An upper bound on [size] that costs no root enumeration: for the entry-set container, the sum of the entity
   * root counts, before duplicates between two entities are removed.
   */
  val approximateSize: Int

  /**
   * Number of entities (or, for the classic container, nothing) the container keeps alive - the per-scope part of
   * its cost. Zero for containers that store no entities at all.
   *
   * Exposed for memory tests, see `ModuleScopeMemoryTest`.
   */
  @get:TestOnly val entityCount: Int

  /**
   * Width of the value stored next to every key in the container's map, in bytes.
   *
   * Exposed for memory tests, see `ModuleScopeMemoryTest`.
   */
  @get:TestOnly val entryValueBytes: Int

  @TestOnly fun getSortedRoots(): Collection<VirtualFile>

  companion object {
    fun Collection<RootContainer>.merge(): RootContainer {
      require(size > 1) { "Expected more than one container" }

      val first = this.first()

      @Suppress("UNCHECKED_CAST")
      return if (first is ClassicRootContainer) {
        ClassicRootContainer.merge(this as List<ClassicRootContainer>)
      }
      else {
        EntrySetRootContainer.merge(this as List<EntrySetRootContainer>)
      }
    }
  }
}
