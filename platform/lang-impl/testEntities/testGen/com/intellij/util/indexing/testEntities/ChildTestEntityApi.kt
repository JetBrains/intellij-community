// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ModifiableChildTestEntity : ModifiableWorkspaceEntity<ChildTestEntity> {
  override var entitySource: EntitySource
  var parent: ModifiableParentTestEntity
  var customChildProperty: String
}

internal object ChildTestEntityType : EntityType<ChildTestEntity, ModifiableChildTestEntity>() {
  override val entityClass: Class<ChildTestEntity> get() = ChildTestEntity::class.java
  operator fun invoke(
    customChildProperty: String,
    entitySource: EntitySource,
    init: (ModifiableChildTestEntity.() -> Unit)? = null,
  ): ModifiableChildTestEntity {
    val builder = builder()
    builder.customChildProperty = customChildProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildTestEntity(
  entity: ChildTestEntity,
  modification: ModifiableChildTestEntity.() -> Unit,
): ChildTestEntity = modifyEntity(ModifiableChildTestEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildTestEntity")
fun ChildTestEntity(
  customChildProperty: String,
  entitySource: EntitySource,
  init: (ModifiableChildTestEntity.() -> Unit)? = null,
): ModifiableChildTestEntity = ChildTestEntityType(customChildProperty, entitySource, init)
