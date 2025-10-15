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
interface ModifiableSiblingEntity : ModifiableWorkspaceEntity<SiblingEntity> {
  override var entitySource: EntitySource
  var parent: ModifiableParentTestEntity
  var customSiblingProperty: String
}

internal object SiblingEntityType : EntityType<SiblingEntity, ModifiableSiblingEntity>() {
  override val entityClass: Class<SiblingEntity> get() = SiblingEntity::class.java
  operator fun invoke(
    customSiblingProperty: String,
    entitySource: EntitySource,
    init: (ModifiableSiblingEntity.() -> Unit)? = null,
  ): ModifiableSiblingEntity {
    val builder = builder()
    builder.customSiblingProperty = customSiblingProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySiblingEntity(
  entity: SiblingEntity,
  modification: ModifiableSiblingEntity.() -> Unit,
): SiblingEntity = modifyEntity(ModifiableSiblingEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createSiblingEntity")
fun SiblingEntity(
  customSiblingProperty: String,
  entitySource: EntitySource,
  init: (ModifiableSiblingEntity.() -> Unit)? = null,
): ModifiableSiblingEntity = SiblingEntityType(customSiblingProperty, entitySource, init)
