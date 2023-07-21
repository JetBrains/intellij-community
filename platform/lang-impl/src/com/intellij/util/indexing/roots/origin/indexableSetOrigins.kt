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

internal data class ModuleRootOriginImpl(override val module: Module,
                                         override val roots: List<VirtualFile>?) : ModuleRootOrigin

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

internal open class IndexingRootHolderImpl(override val roots: Collection<VirtualFile>) : IndexingRootHolder {

  override fun immutableCopyOf(): IndexingRootHolder {
    return IndexingRootHolderImpl(java.util.List.copyOf(roots))
  }

  override fun getRootsDebugStr(): String {
    return getRootsDebugStr(roots)
  }

  override fun isEmpty(): Boolean {
    return roots.isEmpty()
  }

  override val rootUrls: Set<String>
    get() = ContainerUtil.map2Set(roots) { obj: VirtualFile -> obj.url }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is IndexingRootHolderImpl) return false

    return roots == other.roots
  }

  override fun hashCode(): Int {
    return roots.hashCode()
  }
}

internal class MutableIndexingRootHolder(override var roots: MutableCollection<VirtualFile> = mutableListOf()) : IndexingRootHolderImpl(roots) {
  fun addRoots(value: IndexingRootHolder) {
    roots.addAll(value.roots)
  }

  fun remove(otherHolder: MutableIndexingRootHolder?) {
    otherHolder?.also { roots.removeAll(it.roots) }
  }
}

internal open class IndexingSourceRootHolderImpl(override val roots: Collection<VirtualFile>,
                                                 override val sourceRoots: Collection<VirtualFile>) : IndexingSourceRootHolder {
  override fun immutableCopyOf(): IndexingSourceRootHolder {
    return IndexingSourceRootHolderImpl(java.util.List.copyOf(roots), java.util.List.copyOf(sourceRoots))
  }

  override fun getRootsDebugStr(): String {
    return getRootsDebugStr(roots) + "; " + getRootsDebugStr(sourceRoots)
  }

  override fun isEmpty(): Boolean {
    return roots.isEmpty() && sourceRoots.isEmpty()
  }

  override val rootUrls: Set<String>
    get() = (roots.map { root -> root.url } + sourceRoots.map { root -> root.url }).toSet()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is IndexingSourceRootHolderImpl) return false

    if (roots != other.roots) return false
    return sourceRoots == other.sourceRoots
  }

  override fun hashCode(): Int {
    var result = roots.hashCode()
    result = 31 * result + sourceRoots.hashCode()
    return result
  }
}

internal class MutableIndexingSourceRootHolder(override val roots: MutableCollection<VirtualFile> = mutableListOf(),
                                               override val sourceRoots: MutableCollection<VirtualFile> = mutableListOf()) : IndexingSourceRootHolderImpl(
  roots, sourceRoots) {
  fun addRoots(value: IndexingSourceRootHolder) {
    roots.addAll(value.roots)
    sourceRoots.addAll(value.sourceRoots)
  }

  fun remove(otherHolder: MutableIndexingSourceRootHolder?) {
    otherHolder?.also {
      roots.removeAll(it.roots)
      sourceRoots.removeAll(it.sourceRoots)
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