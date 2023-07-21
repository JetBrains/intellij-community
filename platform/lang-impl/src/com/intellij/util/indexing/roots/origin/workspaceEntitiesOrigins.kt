// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.origin

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.roots.kind.ContentOrigin
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.indexing.roots.kind.ModuleContentOrigin
import com.intellij.platform.workspace.storage.EntityReference

interface ModuleAwareContentEntityOrigin : ModuleContentOrigin {
  val reference: EntityReference<*>
  val rootHolder: IndexingRootHolder
}

interface GenericContentEntityOrigin : ContentOrigin {
  val reference: EntityReference<*>
  val rootHolder: IndexingRootHolder
}

interface ExternalEntityOrigin : IndexableSetOrigin {
  val reference: EntityReference<*>
  val rootHolder: IndexingSourceRootHolder
}

interface IndexingRootHolder {
  val roots: Collection<VirtualFile>
  val rootUrls: Set<String>
  fun immutableCopyOf(): IndexingRootHolder
  fun getRootsDebugStr(): String
  fun isEmpty(): Boolean

  companion object {
    fun fromFiles(roots: Collection<VirtualFile>): IndexingRootHolder {
      return IndexingRootHolderImpl(roots)
    }

    fun fromFile(root: VirtualFile): IndexingRootHolder {
      return IndexingRootHolderImpl(listOf(root))
    }
  }
}

interface IndexingSourceRootHolder {
  val roots: Collection<VirtualFile>
  val sourceRoots: Collection<VirtualFile>
  val rootUrls: Set<String>
  fun immutableCopyOf(): IndexingSourceRootHolder
  fun getRootsDebugStr(): String
  fun isEmpty(): Boolean

  companion object {
    fun fromFiles(roots: Collection<VirtualFile>, sourceRoots: Collection<VirtualFile>): IndexingSourceRootHolder {
      return IndexingSourceRootHolderImpl(roots, sourceRoots)
    }
  }
}