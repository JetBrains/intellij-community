// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.origin

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.storage.EntityReference
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.roots.kind.*
import org.jetbrains.annotations.NonNls
import kotlin.math.min

internal data class ModuleRootOriginImpl(override val module: Module,
                                         override val roots: List<VirtualFile>?,
                                         override val nonRecursiveRoots: List<VirtualFile>?) : ModuleRootOrigin

internal data class LibraryOriginImpl(override val classRoots: List<VirtualFile>,
                                      override val sourceRoots: List<VirtualFile>) : LibraryOrigin

internal data class SyntheticLibraryOriginImpl(override val syntheticLibrary: SyntheticLibrary,
                                               override val rootsToIndex: Collection<VirtualFile>) : SyntheticLibraryOrigin

internal data class SdkOriginImpl(override val sdk: Sdk,
                                  override val rootsToIndex: Collection<VirtualFile>) : SdkOrigin

internal data class IndexableSetContributorOriginImpl(override val indexableSetContributor: IndexableSetContributor,
                                                      override val rootsToIndex: Set<VirtualFile>) : IndexableSetContributorOrigin

internal data class ProjectFileOrDirOriginImpl(override val fileOrDir: VirtualFile) : ProjectFileOrDirOrigin

internal data class ModuleAwareContentEntityOriginImpl(override val module: Module,
                                                       override val reference: EntityReference<*>,
                                                       override val rootHolder: IndexingRootHolder) : ModuleAwareContentEntityOrigin

internal data class GenericContentEntityOriginImpl(override val reference: EntityReference<*>,
                                                   override val rootHolder: IndexingRootHolder) : GenericContentEntityOrigin

internal data class ExternalEntityOriginImpl(override val reference: EntityReference<*>,
                                             override val rootHolder: IndexingSourceRootHolder) : ExternalEntityOrigin

internal open class IndexingRootHolderImpl(override val roots: List<VirtualFile>,
                                           override val nonRecursiveRoots: List<VirtualFile>) : IndexingRootHolder {

  override fun immutableCopyOf(): IndexingRootHolder {
    return IndexingRootHolderImpl(java.util.List.copyOf(roots), java.util.List.copyOf(nonRecursiveRoots))
  }

  override fun getRootsDebugStr(): String {
    return getRootsDebugStr(roots) + ", " + getRootsDebugStr(nonRecursiveRoots)
  }

  override fun isEmpty(): Boolean {
    return roots.isEmpty() && nonRecursiveRoots.isEmpty()
  }

  override fun size(): Int {
    return roots.size + nonRecursiveRoots.size
  }

  override fun split(maxSizeOfHolder: Int): Collection<IndexingRootHolder> {
    val rootsSize = roots.size
    val nonRecursiveRootsSize = nonRecursiveRoots.size
    if (rootsSize + nonRecursiveRootsSize <= maxSizeOfHolder) {
      return listOf(this)
    }
    val half = maxSizeOfHolder / 2
    val result = mutableListOf<IndexingRootHolder>()
    var i = rootsSize
    var j = nonRecursiveRootsSize
    while (i > 0 || j > 0) {
      if (i >= half && j >= half) {
        result.add(IndexingRootHolder.fromFiles(roots.subList(i - half, i), nonRecursiveRoots.subList(j - half, j)))
        i -= half
        j -= half
      }
      else if (i < half) {
        val jDiff = min(j, maxSizeOfHolder - i)
        result.add(IndexingRootHolder.fromFiles(roots.subList(0, i), nonRecursiveRoots.subList(j - jDiff, j)))
        i = 0
        j -= jDiff
      }
      else /*if (j < half )*/ {
        val iDiff = min(i, maxSizeOfHolder - j)
        result.add(IndexingRootHolder.fromFiles(roots.subList(i - iDiff, i), nonRecursiveRoots.subList(0, j)))
        i -= iDiff
        j = 0
      }
    }
    return result
  }

  override val rootUrls: Set<String>
    get() = ContainerUtil.map2Set(roots + nonRecursiveRoots) { obj: VirtualFile -> obj.url }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is IndexingRootHolderImpl) return false

    if (roots != other.roots) return false
    return nonRecursiveRoots == other.nonRecursiveRoots
  }

  override fun hashCode(): Int {
    var result = roots.hashCode()
    result = 31 * result + nonRecursiveRoots.hashCode()
    return result
  }
}

internal class MutableIndexingRootHolder(override var roots: MutableList<VirtualFile> = mutableListOf(),
                                         override val nonRecursiveRoots: MutableList<VirtualFile> = mutableListOf()) :
  IndexingRootHolderImpl(roots, nonRecursiveRoots) {
  fun addRoots(value: IndexingRootHolder) {
    roots.addAll(value.roots)
    nonRecursiveRoots.addAll(value.nonRecursiveRoots)
  }

  fun remove(otherHolder: MutableIndexingRootHolder?) {
    otherHolder?.also {
      roots.removeAll(it.roots)
      nonRecursiveRoots.removeAll(it.nonRecursiveRoots)
    }
  }
}

internal open class IndexingSourceRootHolderImpl(override val roots: List<VirtualFile>,
                                                 override val nonRecursiveRoots: List<VirtualFile>,
                                                 override val sourceRoots: List<VirtualFile>,
                                                 override val nonRecursiveSourceRoots: List<VirtualFile>) : IndexingSourceRootHolder {
  override fun immutableCopyOf(): IndexingSourceRootHolder {
    return IndexingSourceRootHolderImpl(java.util.List.copyOf(roots), java.util.List.copyOf(nonRecursiveRoots),
                                        java.util.List.copyOf(sourceRoots), java.util.List.copyOf(nonRecursiveSourceRoots))
  }

  override fun getRootsDebugStr(): String {
    return getRootsDebugStr(roots) + "; " +
           getRootsDebugStr(nonRecursiveRoots) + "; " +
           getRootsDebugStr(sourceRoots) + "; " +
           getRootsDebugStr(nonRecursiveSourceRoots)
  }

  override fun isEmpty(): Boolean {
    return roots.isEmpty() && nonRecursiveRoots.isEmpty() && sourceRoots.isEmpty() && nonRecursiveSourceRoots.isEmpty()
  }

  override val rootUrls: Set<String>
    get() = (roots + nonRecursiveRoots + sourceRoots + nonRecursiveSourceRoots).map { root -> root.url }.toSet()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is IndexingSourceRootHolderImpl) return false

    if (roots != other.roots) return false
    if (nonRecursiveRoots != other.nonRecursiveRoots) return false
    if (sourceRoots != other.sourceRoots) return false
    return nonRecursiveSourceRoots == other.nonRecursiveSourceRoots
  }

  override fun hashCode(): Int {
    var result = roots.hashCode()
    result = 31 * result + nonRecursiveRoots.hashCode()
    result = 31 * result + sourceRoots.hashCode()
    result = 31 * result + nonRecursiveSourceRoots.hashCode()
    return result
  }
}

internal class MutableIndexingSourceRootHolder(override val roots: MutableList<VirtualFile> = mutableListOf(),
                                               override val nonRecursiveRoots: MutableList<VirtualFile> = mutableListOf(),
                                               override val sourceRoots: MutableList<VirtualFile> = mutableListOf(),
                                               override val nonRecursiveSourceRoots: MutableList<VirtualFile> = mutableListOf()) :
  IndexingSourceRootHolderImpl(roots, nonRecursiveRoots, sourceRoots, nonRecursiveSourceRoots) {
  fun addRoots(value: IndexingSourceRootHolder) {
    roots.addAll(value.roots)
    nonRecursiveRoots.addAll(value.nonRecursiveRoots)
    sourceRoots.addAll(value.sourceRoots)
    nonRecursiveSourceRoots.addAll(value.nonRecursiveSourceRoots)
  }

  fun remove(otherHolder: MutableIndexingSourceRootHolder?) {
    otherHolder?.also {
      roots.removeAll(it.roots)
      nonRecursiveRoots.removeAll(it.nonRecursiveRoots)
      sourceRoots.removeAll(it.sourceRoots)
      nonRecursiveSourceRoots.removeAll(it.nonRecursiveSourceRoots)
    }
  }
}


@NonNls
private fun getRootsDebugStr(files: Collection<VirtualFile?>): String {
  return if (files.isEmpty()) {
    "empty"
  }
  else files.joinToString(", ", "", "", 3, "...") { file: VirtualFile? -> file!!.name }
}