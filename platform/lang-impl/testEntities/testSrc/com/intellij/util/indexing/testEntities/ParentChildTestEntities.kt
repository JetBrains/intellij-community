// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child


interface ParentTestEntity : WorkspaceEntity {
  val child: @Child ChildTestEntity?
  val customParentProperty: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ParentTestEntity> {
    override var entitySource: EntitySource
    var child: ChildTestEntity.Builder?
    var customParentProperty: String
  }

  companion object : EntityType<ParentTestEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      customParentProperty: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.customParentProperty = customParentProperty
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyParentTestEntity(
  entity: ParentTestEntity,
  modification: ParentTestEntity.Builder.() -> Unit,
): ParentTestEntity {
  return modifyEntity(ParentTestEntity.Builder::class.java, entity, modification)
}
//endregion

interface ChildTestEntity : WorkspaceEntity {
  val parent: ParentTestEntity
  val customChildProperty: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildTestEntity> {
    override var entitySource: EntitySource
    var parent: ParentTestEntity.Builder
    var customChildProperty: String
  }

  companion object : EntityType<ChildTestEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      customChildProperty: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.customChildProperty = customChildProperty
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyChildTestEntity(
  entity: ChildTestEntity,
  modification: ChildTestEntity.Builder.() -> Unit,
): ChildTestEntity {
  return modifyEntity(ChildTestEntity.Builder::class.java, entity, modification)
}
//endregion
