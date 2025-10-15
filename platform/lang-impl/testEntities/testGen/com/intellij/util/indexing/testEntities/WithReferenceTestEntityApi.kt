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
interface ModifiableWithReferenceTestEntity : ModifiableWorkspaceEntity<WithReferenceTestEntity> {
  override var entitySource: EntitySource
  var name: String
  var references: MutableList<DependencyItem>
}

internal object WithReferenceTestEntityType : EntityType<WithReferenceTestEntity, ModifiableWithReferenceTestEntity>() {
  override val entityClass: Class<WithReferenceTestEntity> get() = WithReferenceTestEntity::class.java
  operator fun invoke(
    name: String,
    references: List<DependencyItem>,
    entitySource: EntitySource,
    init: (ModifiableWithReferenceTestEntity.() -> Unit)? = null,
  ): ModifiableWithReferenceTestEntity {
    val builder = builder()
    builder.name = name
    builder.references = references.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyWithReferenceTestEntity(
  entity: WithReferenceTestEntity,
  modification: ModifiableWithReferenceTestEntity.() -> Unit,
): WithReferenceTestEntity = modifyEntity(ModifiableWithReferenceTestEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createWithReferenceTestEntity")
fun WithReferenceTestEntity(
  name: String,
  references: List<DependencyItem>,
  entitySource: EntitySource,
  init: (ModifiableWithReferenceTestEntity.() -> Unit)? = null,
): ModifiableWithReferenceTestEntity = WithReferenceTestEntityType(name, references, entitySource, init)
