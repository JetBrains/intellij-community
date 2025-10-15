// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ModifiableNonIndexableTestEntity : ModifiableWorkspaceEntity<NonIndexableTestEntity> {
  override var entitySource: EntitySource
  var root: VirtualFileUrl
}

internal object NonIndexableTestEntityType : EntityType<NonIndexableTestEntity, ModifiableNonIndexableTestEntity>() {
  override val entityClass: Class<NonIndexableTestEntity> get() = NonIndexableTestEntity::class.java
  operator fun invoke(
    root: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ModifiableNonIndexableTestEntity.() -> Unit)? = null,
  ): ModifiableNonIndexableTestEntity {
    val builder = builder()
    builder.root = root
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyNonIndexableTestEntity(
  entity: NonIndexableTestEntity,
  modification: ModifiableNonIndexableTestEntity.() -> Unit,
): NonIndexableTestEntity = modifyEntity(ModifiableNonIndexableTestEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createNonIndexableTestEntity")
fun NonIndexableTestEntity(
  root: VirtualFileUrl,
  entitySource: EntitySource,
  init: (ModifiableNonIndexableTestEntity.() -> Unit)? = null,
): ModifiableNonIndexableTestEntity = NonIndexableTestEntityType(root, entitySource, init)
