// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ExternalProjectsBuildClasspathEntityModifications")

package com.intellij.openapi.externalSystem.settings.workspaceModel

import com.intellij.platform.workspace.storage.*

@GeneratedCodeApiVersion(3)
interface ExternalProjectsBuildClasspathEntityBuilder : WorkspaceEntityBuilder<ExternalProjectsBuildClasspathEntity> {
  override var entitySource: EntitySource
  var projectsBuildClasspath: Map<String, ExternalProjectBuildClasspathEntity>
}

internal object ExternalProjectsBuildClasspathEntityType :
  EntityType<ExternalProjectsBuildClasspathEntity, ExternalProjectsBuildClasspathEntityBuilder>() {
  override val entityClass: Class<ExternalProjectsBuildClasspathEntity> get() = ExternalProjectsBuildClasspathEntity::class.java
  operator fun invoke(
    projectsBuildClasspath: Map<String, ExternalProjectBuildClasspathEntity>,
    entitySource: EntitySource,
    init: (ExternalProjectsBuildClasspathEntityBuilder.() -> Unit)? = null,
  ): ExternalProjectsBuildClasspathEntityBuilder {
    val builder = builder()
    builder.projectsBuildClasspath = projectsBuildClasspath
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }

  @Deprecated(message = "Use new API instead")
  fun compatibilityInvoke(
    projectsBuildClasspath: Map<String, ExternalProjectBuildClasspathEntity>,
    entitySource: EntitySource,
    init: (ExternalProjectsBuildClasspathEntity.Builder.() -> Unit)? = null,
  ): ExternalProjectsBuildClasspathEntity.Builder {
    val builder = builder() as ExternalProjectsBuildClasspathEntity.Builder
    builder.projectsBuildClasspath = projectsBuildClasspath
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyExternalProjectsBuildClasspathEntity(
  entity: ExternalProjectsBuildClasspathEntity,
  modification: ExternalProjectsBuildClasspathEntityBuilder.() -> Unit,
): ExternalProjectsBuildClasspathEntity = modifyEntity(ExternalProjectsBuildClasspathEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createExternalProjectsBuildClasspathEntity")
fun ExternalProjectsBuildClasspathEntity(
  projectsBuildClasspath: Map<String, ExternalProjectBuildClasspathEntity>,
  entitySource: EntitySource,
  init: (ExternalProjectsBuildClasspathEntityBuilder.() -> Unit)? = null,
): ExternalProjectsBuildClasspathEntityBuilder = ExternalProjectsBuildClasspathEntityType(projectsBuildClasspath, entitySource, init)
