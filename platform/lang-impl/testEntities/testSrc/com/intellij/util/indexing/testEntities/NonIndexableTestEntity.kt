// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

interface NonIndexableTestEntity : WorkspaceEntity {
  val root: VirtualFileUrl

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<NonIndexableTestEntity> {
    override var entitySource: EntitySource
    var root: VirtualFileUrl
  }

  companion object : EntityType<NonIndexableTestEntity, Builder>() {
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
fun MutableEntityStorage.modifyNonIndexableTestEntity(
  entity: NonIndexableTestEntity,
  modification: NonIndexableTestEntity.Builder.() -> Unit,
): NonIndexableTestEntity {
  return modifyEntity(NonIndexableTestEntity.Builder::class.java, entity, modification)
}
//endregion
