// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

interface NonRecursiveTestEntity : WorkspaceEntity {
  val root: VirtualFileUrl

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<NonRecursiveTestEntity> {
    override var entitySource: EntitySource
    var root: VirtualFileUrl
  }

  companion object : EntityType<NonRecursiveTestEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      root: VirtualFileUrl,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.root = root
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyNonRecursiveTestEntity(
  entity: NonRecursiveTestEntity,
  modification: NonRecursiveTestEntity.Builder.() -> Unit,
): NonRecursiveTestEntity {
  return modifyEntity(NonRecursiveTestEntity.Builder::class.java, entity, modification)
}
//endregion
