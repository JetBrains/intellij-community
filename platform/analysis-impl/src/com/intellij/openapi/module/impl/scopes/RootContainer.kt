// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.RootEntry
import com.intellij.openapi.roots.impl.RootDescriptor
import com.intellij.openapi.vfs.VirtualFile
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap
import org.jetbrains.annotations.TestOnly

internal sealed interface RootContainer {
  /**
   * @return known priority or 0 if the root is not in the container
   */
  fun getPriority(root: VirtualFile): Int

  @Deprecated("use getRootDescriptor instead")
  fun containsRoot(root: VirtualFile): Boolean

  // todo IJPL-339 support several roots for a single root in a scope
  fun getRootDescriptor(root: RootDescriptor): ScopeRootDescriptor?

  fun getRootDescriptor(root: VirtualFile): ScopeRootDescriptor?

  fun getRoots(): Collection<VirtualFile>

  val size: Int

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
        MultiverseRootContainer.merge(this as List<MultiverseRootContainer>)
      }
    }
  }
}

internal class ClassicRootContainer(private val roots: Object2IntMap<VirtualFile>) : RootContainer {
  override fun getPriority(root: VirtualFile): Int = roots.getInt(root)

  @Deprecated("use getRootDescriptor instead")
  override fun containsRoot(root: VirtualFile): Boolean = roots.containsKey(root)

  override fun getRoots(): Collection<VirtualFile> = getRoots(sorted = false)

  override fun getSortedRoots(): Collection<VirtualFile> = getRoots(sorted = true)

  override fun getRootDescriptor(root: RootDescriptor): ScopeRootDescriptor =
    throw UnsupportedOperationException()

  override fun getRootDescriptor(root: VirtualFile): ScopeRootDescriptor =
    throw UnsupportedOperationException()

  override val size: Int
    get() = roots.size

  private fun getRoots(sorted: Boolean): Collection<VirtualFile> {
    val files = roots.keys.toMutableList()
    if (sorted) {
      files.sortBy { root -> roots.getInt(root) }
    }
    return files
  }

  companion object {
    internal fun merge(containers: List<ClassicRootContainer>): ClassicRootContainer {
      val result = Object2IntOpenHashMap<VirtualFile>()

      var maxPriority = 0
      for (container in containers) {
        val map = container.roots
        val entrySet = map.object2IntEntrySet()
        var curMax = 0

        for (entry in entrySet) {
          val root = entry.key
          val priority = entry.intValue + maxPriority
          curMax = maxOf(curMax, priority)

          result.putIfAbsent(root, priority)
        }

        maxPriority = maxOf(maxPriority, curMax)
      }

      return ClassicRootContainer(result)
    }
  }
}

// todo IJPL-339 this does not support multiple modules per root file
internal class MultiverseRootContainer(
  /**
   * root -> packed long: the high 32 bits hold an index in [descriptors], the low 32 bits hold the root priority.
   * Priorities start at 1, so the packed value 0 means "the root is absent".
   */
  private val roots: Object2LongOpenHashMap<VirtualFile>,
  /**
   * One descriptor per distinct [OrderEntry]. Merged containers share the descriptors of their sources.
   */
  private val descriptors: Array<ScopeRootDescriptor>,
) : RootContainer {
  override fun getPriority(root: VirtualFile): Int =
    priorityOf(roots.getLong(root))

  @Deprecated("use getRootDescriptor instead")
  override fun containsRoot(root: VirtualFile): Boolean =
    roots.containsKey(root)

  override fun getRootDescriptor(root: RootDescriptor): ScopeRootDescriptor? =
    getRootDescriptor(root.root)?.takeIf { descriptor ->
      descriptor.correspondTo(root)
    }

  override fun getRootDescriptor(root: VirtualFile): ScopeRootDescriptor? {
    val packed = roots.getLong(root)
    if (packed == 0L) return null
    return descriptors[entryIdOf(packed)]
  }

  override fun getRoots(): Collection<VirtualFile> =
    roots.keys

  override val size: Int
    get() = roots.size

  override fun getSortedRoots(): Collection<VirtualFile> =
    roots.keys.sortedBy { root -> priorityOf(roots.getLong(root)) }

  companion object {
    private fun pack(entryId: Int, priority: Int): Long =
      (entryId.toLong() shl 32) or (priority.toLong() and 0xFFFF_FFFFL)

    private fun priorityOf(packed: Long): Int = packed.toInt()

    private fun entryIdOf(packed: Long): Int = (packed ushr 32).toInt()

    internal fun build(entries: Collection<RootEntry>): MultiverseRootContainer {
      val roots = Object2LongOpenHashMap<VirtualFile>(entries.size)
      val descriptors = ArrayList<ScopeRootDescriptor>()
      val entryIds = Reference2IntOpenHashMap<OrderEntry>()
      entryIds.defaultReturnValue(-1)

      var priority = 1
      for (entry in entries) {
        if (!roots.containsKey(entry.root)) {
          var entryId = entryIds.getInt(entry.orderEntry)
          if (entryId == -1) {
            entryId = descriptors.size
            descriptors.add(ScopeRootDescriptor(entry.orderEntry))
            entryIds.put(entry.orderEntry, entryId)
          }
          roots.put(entry.root, pack(entryId, priority))
        }
        priority++
      }
      roots.trim()
      return MultiverseRootContainer(roots, descriptors.toTypedArray())
    }

    // todo multiple modules per root file are not supported yet
    internal fun merge(containers: List<MultiverseRootContainer>): MultiverseRootContainer {
      val resultRoots = Object2LongOpenHashMap<VirtualFile>()
      val resultDescriptors = ArrayList<ScopeRootDescriptor>()
      val descriptorIds = Reference2IntOpenHashMap<ScopeRootDescriptor>()
      descriptorIds.defaultReturnValue(-1)

      var maxPriority = 0
      for (container in containers) {
        var curMax = 0
        for (entry in container.roots.object2LongEntrySet().fastIterator()) {
          val packed = entry.longValue
          val priority = priorityOf(packed) + maxPriority
          curMax = maxOf(curMax, priority)

          if (!resultRoots.containsKey(entry.key)) {
            val descriptor = container.descriptors[entryIdOf(packed)]
            var descriptorId = descriptorIds.getInt(descriptor)
            if (descriptorId == -1) {
              descriptorId = resultDescriptors.size
              resultDescriptors.add(descriptor)
              descriptorIds.put(descriptor, descriptorId)
            }
            resultRoots.put(entry.key, pack(descriptorId, priority))
          }
        }

        maxPriority = maxOf(maxPriority, curMax)
      }

      return MultiverseRootContainer(resultRoots, resultDescriptors.toTypedArray())
    }
  }
}
