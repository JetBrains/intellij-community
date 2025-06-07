// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

interface IndexingTestEntity : WorkspaceEntity {
  val roots: List<VirtualFileUrl>
  val excludedRoots: List<VirtualFileUrl>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<IndexingTestEntity> {
    override var entitySource: EntitySource
    var roots: MutableList<VirtualFileUrl>
    var excludedRoots: MutableList<VirtualFileUrl>
  }

  companion object : EntityType<IndexingTestEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      roots: List<VirtualFileUrl>,
      excludedRoots: List<VirtualFileUrl>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.roots = roots.toMutableWorkspaceList()
      builder.excludedRoots = excludedRoots.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyIndexingTestEntity(
  entity: IndexingTestEntity,
  modification: IndexingTestEntity.Builder.() -> Unit,
): IndexingTestEntity {
  return modifyEntity(IndexingTestEntity.Builder::class.java, entity, modification)
}
//endregion
