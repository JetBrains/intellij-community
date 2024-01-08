// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.origin

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.util.indexing.roots.kind.ContentOrigin
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.indexing.roots.kind.ModuleContentOrigin

interface ModuleAwareContentEntityOrigin : ModuleContentOrigin {
  val reference: EntityPointer<*>
  val rootHolder: IndexingRootHolder
}

interface GenericContentEntityOrigin : ContentOrigin {
  val reference: EntityPointer<*>
  val rootHolder: IndexingRootHolder
}

interface ExternalEntityOrigin : IndexableSetOrigin {
  val reference: EntityPointer<*>
  val rootHolder: IndexingSourceRootHolder
}

interface CustomKindEntityOrigin : IndexableSetOrigin {
  val reference: EntityPointer<*>
  val rootHolder: IndexingRootHolder
}

interface IndexingRootHolder {
  val roots: List<VirtualFile>
  val nonRecursiveRoots: List<VirtualFile>
  val rootUrls: Set<String>
  fun immutableCopyOf(): IndexingRootHolder
  fun getDebugDescription(): String
  fun isEmpty(): Boolean
  fun size(): Int
  fun split(maxSizeOfHolder: Int): Collection<IndexingRootHolder>

  companion object {
    fun fromFiles(roots: List<VirtualFile>, nonRecursiveRoots: List<VirtualFile>): IndexingRootHolder {
      return IndexingRootHolderImpl(roots, nonRecursiveRoots)
    }

    fun fromFiles(roots: List<VirtualFile>): IndexingRootHolder {
      return IndexingRootHolderImpl(roots, emptyList())
    }

    fun fromFile(root: VirtualFile): IndexingRootHolder {
      return IndexingRootHolderImpl(listOf(root), emptyList())
    }
  }
}

interface IndexingSourceRootHolder {
  val roots: List<VirtualFile>
  val nonRecursiveRoots: List<VirtualFile>
  val sourceRoots: List<VirtualFile>
  val nonRecursiveSourceRoots: List<VirtualFile>
  val rootUrls: Set<String>
  fun immutableCopyOf(): IndexingSourceRootHolder
  fun getRootsDebugStr(): String
  fun isEmpty(): Boolean

  companion object {
    fun fromFiles(roots: List<VirtualFile>, sourceRoots: List<VirtualFile>): IndexingSourceRootHolder {
      return IndexingSourceRootHolderImpl(roots, emptyList(), sourceRoots, emptyList())
    }

    fun fromFiles(roots: List<VirtualFile>,
                  nonRecursiveRoots: List<VirtualFile>,
                  sourceRoots: List<VirtualFile>,
                  nonRecursiveSourceRoots: List<VirtualFile>): IndexingSourceRootHolder {
      return IndexingSourceRootHolderImpl(roots, nonRecursiveRoots, sourceRoots, nonRecursiveSourceRoots)
    }
  }
}