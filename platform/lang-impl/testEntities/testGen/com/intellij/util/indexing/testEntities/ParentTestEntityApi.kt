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
interface ModifiableParentTestEntity : ModifiableWorkspaceEntity<ParentTestEntity> {
  override var entitySource: EntitySource
  var child: ModifiableChildTestEntity?
  var secondChild: ModifiableSiblingEntity?
  var customParentProperty: String
  var parentEntityRoot: VirtualFileUrl
}

internal object ParentTestEntityType : EntityType<ParentTestEntity, ModifiableParentTestEntity>() {
  override val entityClass: Class<ParentTestEntity> get() = ParentTestEntity::class.java
  operator fun invoke(
    customParentProperty: String,
    parentEntityRoot: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ModifiableParentTestEntity.() -> Unit)? = null,
  ): ModifiableParentTestEntity {
    val builder = builder()
    builder.customParentProperty = customParentProperty
    builder.parentEntityRoot = parentEntityRoot
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentTestEntity(
  entity: ParentTestEntity,
  modification: ModifiableParentTestEntity.() -> Unit,
): ParentTestEntity = modifyEntity(ModifiableParentTestEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentTestEntity")
fun ParentTestEntity(
  customParentProperty: String,
  parentEntityRoot: VirtualFileUrl,
  entitySource: EntitySource,
  init: (ModifiableParentTestEntity.() -> Unit)? = null,
): ModifiableParentTestEntity = ParentTestEntityType(customParentProperty, parentEntityRoot, entitySource, init)
