// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.roots.impl.RootDescriptor
import com.intellij.openapi.vfs.VirtualFile
import it.unimi.dsi.fastutil.objects.Object2IntMap
import org.jetbrains.annotations.TestOnly

internal sealed interface RootContainer {
  /**
   * @return known priority or 0 if root is not in the container
   */
  fun getPriority(root: VirtualFile): Int

  @Deprecated("use getRootDescriptor instead")
  fun containsRoot(root: VirtualFile): Boolean

  // todo ijpl-339 support several roots for a single root in a scope
  fun getRootDescriptor(root: RootDescriptor): ScopeRootDescriptor?

  fun getRootDescriptor(root: VirtualFile): ScopeRootDescriptor?

  fun getRoots(): Collection<VirtualFile>

  val size: Int

  @TestOnly fun getSortedRoots(): Collection<VirtualFile>
}

internal class ClassicRootContainer(private val roots: Object2IntMap<VirtualFile>) : RootContainer {
  override fun getPriority(root: VirtualFile): Int = roots.getInt(root)

  override fun containsRoot(root: VirtualFile): Boolean = roots.containsKey(root)

  override fun getRoots(): Collection<VirtualFile> = getRoots(sorted = false)

  override fun getSortedRoots(): Collection<VirtualFile> = getRoots(sorted = true)

  override fun getRootDescriptor(root: RootDescriptor): ScopeRootDescriptor? =
    throw UnsupportedOperationException()

  override fun getRootDescriptor(root: VirtualFile): ScopeRootDescriptor? =
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
}

// todo ijpl-339 this does not support multiple modules per root file
internal class MultiverseRootContainer(
  private val roots: Map<VirtualFile, ScopeRootDescriptor>
) : RootContainer {
  override fun getPriority(root: VirtualFile): Int =
    roots[root]?.orderIndex ?: 0

  override fun containsRoot(root: VirtualFile): Boolean =
    roots.containsKey(root)

  override fun getRootDescriptor(root: RootDescriptor): ScopeRootDescriptor? =
    roots[root.root]?.takeIf { descriptor ->
      descriptor.correspondTo(root)
    }

  override fun getRootDescriptor(root: VirtualFile): ScopeRootDescriptor? =
    roots[root]

  override fun getRoots(): Collection<VirtualFile> =
    roots.keys

  override val size: Int
    get() = roots.size

  override fun getSortedRoots(): Collection<VirtualFile> =
    roots.keys.sortedBy { root -> roots[root]!!.orderIndex }
}
