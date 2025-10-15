// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.testEntities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ModifiableReferredTestEntity : ModifiableWorkspaceEntity<ReferredTestEntity> {
  override var entitySource: EntitySource
  var name: String
  var file: VirtualFileUrl
}

internal object ReferredTestEntityType : EntityType<ReferredTestEntity, ModifiableReferredTestEntity>() {
  override val entityClass: Class<ReferredTestEntity> get() = ReferredTestEntity::class.java
  operator fun invoke(
    name: String,
    file: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ModifiableReferredTestEntity.() -> Unit)? = null,
  ): ModifiableReferredTestEntity {
    val builder = builder()
    builder.name = name
    builder.file = file
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyReferredTestEntity(
  entity: ReferredTestEntity,
  modification: ModifiableReferredTestEntity.() -> Unit,
): ReferredTestEntity = modifyEntity(ModifiableReferredTestEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createReferredTestEntity")
fun ReferredTestEntity(
  name: String,
  file: VirtualFileUrl,
  entitySource: EntitySource,
  init: (ModifiableReferredTestEntity.() -> Unit)? = null,
): ModifiableReferredTestEntity = ReferredTestEntityType(name, file, entitySource, init)
