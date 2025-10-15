// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.workspaceModel

import com.intellij.platform.workspace.storage.*
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
interface ModifiableExternalProjectEntity : ModifiableWorkspaceEntity<ExternalProjectEntity> {
  override var entitySource: EntitySource
  var externalProjectPath: String
}

internal object ExternalProjectEntityType : EntityType<ExternalProjectEntity, ModifiableExternalProjectEntity>() {
  override val entityClass: Class<ExternalProjectEntity> get() = ExternalProjectEntity::class.java
  operator fun invoke(
    externalProjectPath: String,
    entitySource: EntitySource,
    init: (ModifiableExternalProjectEntity.() -> Unit)? = null,
  ): ModifiableExternalProjectEntity {
    val builder = builder()
    builder.externalProjectPath = externalProjectPath
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@Internal
fun MutableEntityStorage.modifyExternalProjectEntity(
  entity: ExternalProjectEntity,
  modification: ModifiableExternalProjectEntity.() -> Unit,
): ExternalProjectEntity = modifyEntity(ModifiableExternalProjectEntity::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createExternalProjectEntity")
fun ExternalProjectEntity(
  externalProjectPath: String,
  entitySource: EntitySource,
  init: (ModifiableExternalProjectEntity.() -> Unit)? = null,
): ModifiableExternalProjectEntity = ExternalProjectEntityType(externalProjectPath, entitySource, init)
