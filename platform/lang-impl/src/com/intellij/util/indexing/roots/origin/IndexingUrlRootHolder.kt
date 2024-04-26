// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.origin

import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

interface IndexingUrlRootHolder {
  val roots: List<VirtualFileUrl>
  val nonRecursiveRoots: List<VirtualFileUrl>
  fun isEmpty(): Boolean
  fun toRootHolder(): IndexingRootHolder

  companion object {
    fun fromUrls(roots: List<VirtualFileUrl>, nonRecursiveRoots: List<VirtualFileUrl>): IndexingUrlRootHolder {
      return IndexingUrlRootHolderImpl(roots, nonRecursiveRoots)
    }

    fun fromUrls(roots: List<VirtualFileUrl>): IndexingUrlRootHolder {
      return IndexingUrlRootHolderImpl(roots, emptyList())
    }

    fun fromUrl(root: VirtualFileUrl): IndexingUrlRootHolder {
      return IndexingUrlRootHolderImpl(listOf(root), emptyList())
    }
  }
}

internal open class IndexingUrlRootHolderImpl(override val roots: List<VirtualFileUrl>,
                                              override val nonRecursiveRoots: List<VirtualFileUrl>) : IndexingUrlRootHolder {

  override fun isEmpty(): Boolean {
    return roots.isEmpty() && nonRecursiveRoots.isEmpty()
  }

  override fun toRootHolder(): IndexingRootHolder {
    return IndexingRootHolderImpl(roots.mapNotNull { it.virtualFile }, nonRecursiveRoots.mapNotNull { it.virtualFile })
  }

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

internal class MutableIndexingUrlRootHolder(override var roots: MutableList<VirtualFileUrl> = mutableListOf(),
                                            override val nonRecursiveRoots: MutableList<VirtualFileUrl> = mutableListOf()) :
  IndexingUrlRootHolderImpl(roots, nonRecursiveRoots) {
  fun addRoots(value: IndexingUrlRootHolder) {
    roots.addAll(value.roots)
    nonRecursiveRoots.addAll(value.nonRecursiveRoots)
  }

  fun remove(otherHolder: MutableIndexingUrlRootHolder?) {
    otherHolder?.also {
      roots.removeAll(it.roots)
      nonRecursiveRoots.removeAll(it.nonRecursiveRoots)
    }
  }
}


interface IndexingUrlSourceRootHolder {
  val roots: List<VirtualFileUrl>
  val nonRecursiveRoots: List<VirtualFileUrl>
  val sourceRoots: List<VirtualFileUrl>
  val nonRecursiveSourceRoots: List<VirtualFileUrl>
  fun isEmpty(): Boolean
  fun toSourceRootHolder(): IndexingSourceRootHolder

  companion object {
    fun fromUrls(roots: List<VirtualFileUrl>, sourceRoots: List<VirtualFileUrl>): IndexingUrlSourceRootHolder {
      return IndexingUrlSourceRootHolderImpl(roots, emptyList(), sourceRoots, emptyList())
    }

    fun fromUrls(roots: List<VirtualFileUrl>,
                 nonRecursiveRoots: List<VirtualFileUrl>,
                 sourceRoots: List<VirtualFileUrl>,
                 nonRecursiveSourceRoots: List<VirtualFileUrl>): IndexingUrlSourceRootHolder {
      return IndexingUrlSourceRootHolderImpl(roots, nonRecursiveRoots, sourceRoots, nonRecursiveSourceRoots)
    }
  }
}


internal open class IndexingUrlSourceRootHolderImpl(override val roots: List<VirtualFileUrl>,
                                                    override val nonRecursiveRoots: List<VirtualFileUrl>,
                                                    override val sourceRoots: List<VirtualFileUrl>,
                                                    override val nonRecursiveSourceRoots: List<VirtualFileUrl>) : IndexingUrlSourceRootHolder {

  override fun isEmpty(): Boolean {
    return roots.isEmpty() && nonRecursiveRoots.isEmpty() && sourceRoots.isEmpty() && nonRecursiveSourceRoots.isEmpty()
  }

  override fun toSourceRootHolder(): IndexingSourceRootHolder {
    return IndexingSourceRootHolder.fromFiles(roots.mapNotNull { it.virtualFile },
                                              nonRecursiveRoots.mapNotNull { it.virtualFile },
                                              sourceRoots.mapNotNull { it.virtualFile },
                                              nonRecursiveSourceRoots.mapNotNull { it.virtualFile })
  }

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

internal class MutableIndexingUrlSourceRootHolder(override val roots: MutableList<VirtualFileUrl> = mutableListOf(),
                                                  override val nonRecursiveRoots: MutableList<VirtualFileUrl> = mutableListOf(),
                                                  override val sourceRoots: MutableList<VirtualFileUrl> = mutableListOf(),
                                                  override val nonRecursiveSourceRoots: MutableList<VirtualFileUrl> = mutableListOf()) :
  IndexingUrlSourceRootHolderImpl(roots, nonRecursiveRoots, sourceRoots, nonRecursiveSourceRoots) {
  fun addRoots(value: IndexingUrlSourceRootHolder) {
    roots.addAll(value.roots)
    nonRecursiveRoots.addAll(value.nonRecursiveRoots)
    sourceRoots.addAll(value.sourceRoots)
    nonRecursiveSourceRoots.addAll(value.nonRecursiveSourceRoots)
  }

  fun remove(otherHolder: MutableIndexingUrlSourceRootHolder?) {
    otherHolder?.also {
      roots.removeAll(it.roots)
      nonRecursiveRoots.removeAll(it.nonRecursiveRoots)
      sourceRoots.removeAll(it.sourceRoots)
      nonRecursiveSourceRoots.removeAll(it.nonRecursiveSourceRoots)
    }
  }
}