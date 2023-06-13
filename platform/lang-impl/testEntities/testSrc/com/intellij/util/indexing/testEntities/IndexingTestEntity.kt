// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.ObjBuilder
import com.intellij.platform.workspace.storage.Type

interface IndexingTestEntity : WorkspaceEntity {
  val roots: List<VirtualFileUrl>
  val excludedRoots: List<VirtualFileUrl>

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : IndexingTestEntity, WorkspaceEntity.Builder<IndexingTestEntity>, ObjBuilder<IndexingTestEntity> {
    override var entitySource: EntitySource
    override var roots: MutableList<VirtualFileUrl>
    override var excludedRoots: MutableList<VirtualFileUrl>
  }

  companion object : Type<IndexingTestEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(roots: List<VirtualFileUrl>,
                        excludedRoots: List<VirtualFileUrl>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): IndexingTestEntity {
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
fun MutableEntityStorage.modifyEntity(entity: IndexingTestEntity, modification: IndexingTestEntity.Builder.() -> Unit) = modifyEntity(
  IndexingTestEntity.Builder::class.java, entity, modification)
//endregion

