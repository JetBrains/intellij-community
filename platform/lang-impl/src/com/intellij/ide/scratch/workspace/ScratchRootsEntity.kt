// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch.workspace

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

interface ScratchRootsEntity : WorkspaceEntity {
  val roots: List<VirtualFileUrl>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ScratchRootsEntity, WorkspaceEntity.Builder<ScratchRootsEntity> {
    override var entitySource: EntitySource
    override var roots: MutableList<VirtualFileUrl>
  }

  companion object : EntityType<ScratchRootsEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(roots: List<VirtualFileUrl>, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ScratchRootsEntity {
      val builder = builder()
      builder.roots = roots.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ScratchRootsEntity,
                                      modification: ScratchRootsEntity.Builder.() -> Unit): ScratchRootsEntity = modifyEntity(
  ScratchRootsEntity.Builder::class.java, entity, modification)
//endregion

internal object ScratchRootsEntitySource : EntitySource