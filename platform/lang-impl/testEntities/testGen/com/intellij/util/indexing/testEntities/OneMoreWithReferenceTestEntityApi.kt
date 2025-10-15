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
interface ModifiableOneMoreWithReferenceTestEntity : ModifiableWorkspaceEntity<OneMoreWithReferenceTestEntity> {
  override var entitySource: EntitySource
  var references: MutableList<DependencyItem>
}

internal object OneMoreWithReferenceTestEntityType : EntityType<OneMoreWithReferenceTestEntity, ModifiableOneMoreWithReferenceTestEntity>() {
  override val entityClass: Class<OneMoreWithReferenceTestEntity> get() = OneMoreWithReferenceTestEntity::class.java
  operator fun invoke(
    references: List<DependencyItem>,
    entitySource: EntitySource,
    init: (ModifiableOneMoreWithReferenceTestEntity.() -> Unit)? = null,
  ): ModifiableOneMoreWithReferenceTestEntity {
    val builder = builder()
    builder.references = references.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOneMoreWithReferenceTestEntity(
  entity: OneMoreWithReferenceTestEntity,
  modification: ModifiableOneMoreWithReferenceTestEntity.() -> Unit,
): OneMoreWithReferenceTestEntity = modifyEntity(ModifiableOneMoreWithReferenceTestEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createOneMoreWithReferenceTestEntity")
fun OneMoreWithReferenceTestEntity(
  references: List<DependencyItem>,
  entitySource: EntitySource,
  init: (ModifiableOneMoreWithReferenceTestEntity.() -> Unit)? = null,
): ModifiableOneMoreWithReferenceTestEntity = OneMoreWithReferenceTestEntityType(references, entitySource, init)
