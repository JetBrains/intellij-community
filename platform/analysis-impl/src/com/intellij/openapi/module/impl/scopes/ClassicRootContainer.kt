// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.roots.impl.RootDescriptor
import com.intellij.openapi.vfs.VirtualFile
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap

/**
 * The container used when shared-source support is off: a plain root -> priority map, with no notion of which
 * order entry brought a root in, so none of the [RootDescriptor]-based methods apply.
 */
internal class ClassicRootContainer(private val roots: Object2IntMap<VirtualFile>) : RootContainer {
  override fun rankOf(root: VirtualFile): Long = roots.getInt(root).toLong()

  @Deprecated("use findEntity instead")
  override fun containsRoot(root: VirtualFile): Boolean = roots.containsKey(root)

  override fun getRoots(): Collection<VirtualFile> = getRoots(sorted = false)

  override fun getSortedRoots(): Collection<VirtualFile> = getRoots(sorted = true)

  override fun findEntity(rootDescriptor: RootDescriptor): ScopeEntity =
    throw UnsupportedOperationException()

  override fun rankOf(rootDescriptor: RootDescriptor): Long =
    throw UnsupportedOperationException()

  override fun priorityOf(rootDescriptor: RootDescriptor): Int =
    throw UnsupportedOperationException()

  override val size: Int
    get() = roots.size

  override val approximateSize: Int
    get() = roots.size

  override val entityCount: Int
    get() = 0

  override val entryValueBytes: Int
    get() = Int.SIZE_BYTES

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

      result.trim()
      return ClassicRootContainer(result)
    }
  }
}
