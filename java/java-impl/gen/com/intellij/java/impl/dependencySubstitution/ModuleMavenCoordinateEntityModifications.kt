// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleMavenCoordinateEntityModifications")

package com.intellij.java.impl.dependencySubstitution

import com.intellij.java.library.MavenCoordinates
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

@GeneratedCodeApiVersion(3)
public interface ModuleMavenCoordinateEntityBuilder : WorkspaceEntityBuilder<ModuleMavenCoordinateEntity> {
  override var entitySource: EntitySource
  public var module: ModuleEntityBuilder
  public var coordinates: MavenCoordinates
}

internal object ModuleMavenCoordinateEntityType : EntityType<ModuleMavenCoordinateEntity, ModuleMavenCoordinateEntityBuilder>() {
  override val entityClass: Class<ModuleMavenCoordinateEntity> get() = ModuleMavenCoordinateEntity::class.java
  operator fun invoke(
    coordinates: MavenCoordinates,
    entitySource: EntitySource,
    init: (ModuleMavenCoordinateEntityBuilder.() -> Unit)? = null,
  ): ModuleMavenCoordinateEntityBuilder {
    val builder = builder()
    builder.coordinates = coordinates
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

public fun MutableEntityStorage.modifyModuleMavenCoordinateEntity(
  entity: ModuleMavenCoordinateEntity,
  modification: ModuleMavenCoordinateEntityBuilder.() -> Unit,
): ModuleMavenCoordinateEntity = modifyEntity(ModuleMavenCoordinateEntityBuilder::class.java, entity, modification)

public var ModuleEntityBuilder.mavenCoordinates: ModuleMavenCoordinateEntityBuilder?
  by WorkspaceEntity.extensionBuilder(ModuleMavenCoordinateEntity::class.java)


@JvmOverloads
@JvmName("createModuleMavenCoordinateEntity")
public fun ModuleMavenCoordinateEntity(
  coordinates: MavenCoordinates,
  entitySource: EntitySource,
  init: (ModuleMavenCoordinateEntityBuilder.() -> Unit)? = null,
): ModuleMavenCoordinateEntityBuilder = ModuleMavenCoordinateEntityType(coordinates, entitySource, init)
