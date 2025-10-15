// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.dependencySubstitution

import com.intellij.java.library.MavenCoordinates
import com.intellij.platform.workspace.jps.entities.ModifiableModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import org.jetbrains.annotations.ApiStatus.Experimental

@GeneratedCodeApiVersion(3)
public interface ModifiableModuleMavenCoordinateEntity : ModifiableWorkspaceEntity<ModuleMavenCoordinateEntity> {
  override var entitySource: EntitySource
  public var module: ModifiableModuleEntity
  public var coordinates: MavenCoordinates
}

internal object ModuleMavenCoordinateEntityType : EntityType<ModuleMavenCoordinateEntity, ModifiableModuleMavenCoordinateEntity>() {
  override val entityClass: Class<ModuleMavenCoordinateEntity> get() = ModuleMavenCoordinateEntity::class.java
  operator fun invoke(
    coordinates: MavenCoordinates,
    entitySource: EntitySource,
    init: (ModifiableModuleMavenCoordinateEntity.() -> Unit)? = null,
  ): ModifiableModuleMavenCoordinateEntity {
    val builder = builder()
    builder.coordinates = coordinates
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

public fun MutableEntityStorage.modifyModuleMavenCoordinateEntity(
  entity: ModuleMavenCoordinateEntity,
  modification: ModifiableModuleMavenCoordinateEntity.() -> Unit,
): ModuleMavenCoordinateEntity = modifyEntity(ModifiableModuleMavenCoordinateEntity::class.java, entity, modification)

public var ModifiableModuleEntity.mavenCoordinates: ModifiableModuleMavenCoordinateEntity?
  by WorkspaceEntity.extensionBuilder(ModuleMavenCoordinateEntity::class.java)


@JvmOverloads
@JvmName("createModuleMavenCoordinateEntity")
public fun ModuleMavenCoordinateEntity(
  coordinates: MavenCoordinates,
  entitySource: EntitySource,
  init: (ModifiableModuleMavenCoordinateEntity.() -> Unit)? = null,
): ModifiableModuleMavenCoordinateEntity = ModuleMavenCoordinateEntityType(coordinates, entitySource, init)
