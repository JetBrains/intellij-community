// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar

class NonIndexableKindFileSetTestContributor : WorkspaceFileIndexContributor<NonIndexableTestEntity> {
  override val entityClass: Class<NonIndexableTestEntity> = NonIndexableTestEntity::class.java

  override fun registerFileSets(entity: NonIndexableTestEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    registrar.registerFileSet(entity.root, WorkspaceFileKind.CONTENT_NON_INDEXABLE, entity, null)
  }
}

class IndexableKindFileSetTestContributor : WorkspaceFileIndexContributor<IndexingTestEntity> {
  override val entityClass: Class<IndexingTestEntity> = IndexingTestEntity::class.java

  override fun registerFileSets(entity: IndexingTestEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    for (root in entity.roots) {
      registrar.registerFileSet(root, WorkspaceFileKind.CONTENT, entity, null)
    }
    for (excludedRoot in entity.excludedRoots) {
      registrar.registerExcludedRoot(excludedRoot, entity)
    }
  }
}

class NonRecursiveFileSetContributor : WorkspaceFileIndexContributor<NonRecursiveTestEntity> {
  override val entityClass: Class<NonRecursiveTestEntity>
    get() = NonRecursiveTestEntity::class.java

  override fun registerFileSets(entity: NonRecursiveTestEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    registrar.registerNonRecursiveFileSet(entity.root, WorkspaceFileKind.CONTENT, entity, NonRecursiveFileCustomData())
  }
}

class NonRecursiveFileCustomData : WorkspaceFileSetData
