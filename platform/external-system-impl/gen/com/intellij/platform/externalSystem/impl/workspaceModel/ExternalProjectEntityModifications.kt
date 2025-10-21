// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ExternalProjectEntityModifications")

package com.intellij.platform.externalSystem.impl.workspaceModel

import com.intellij.platform.workspace.storage.*
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
interface ExternalProjectEntityBuilder : WorkspaceEntityBuilder<ExternalProjectEntity> {
  override var entitySource: EntitySource
  var externalProjectPath: String
}

internal object ExternalProjectEntityType : EntityType<ExternalProjectEntity, ExternalProjectEntityBuilder>() {
  override val entityClass: Class<ExternalProjectEntity> get() = ExternalProjectEntity::class.java
  operator fun invoke(
    externalProjectPath: String,
    entitySource: EntitySource,
    init: (ExternalProjectEntityBuilder.() -> Unit)? = null,
  ): ExternalProjectEntityBuilder {
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
  modification: ExternalProjectEntityBuilder.() -> Unit,
): ExternalProjectEntity = modifyEntity(ExternalProjectEntityBuilder::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createExternalProjectEntity")
fun ExternalProjectEntity(
  externalProjectPath: String,
  entitySource: EntitySource,
  init: (ExternalProjectEntityBuilder.() -> Unit)? = null,
): ExternalProjectEntityBuilder = ExternalProjectEntityType(externalProjectPath, entitySource, init)
