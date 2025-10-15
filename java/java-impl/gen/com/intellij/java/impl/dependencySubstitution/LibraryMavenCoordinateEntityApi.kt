// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.dependencySubstitution

import com.intellij.java.library.MavenCoordinates
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModifiableLibraryEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import org.jetbrains.annotations.ApiStatus.Experimental

@GeneratedCodeApiVersion(3)
public interface ModifiableLibraryMavenCoordinateEntity : ModifiableWorkspaceEntity<LibraryMavenCoordinateEntity> {
  override var entitySource: EntitySource
  public var library: ModifiableLibraryEntity
  public var coordinates: MavenCoordinates
}

internal object LibraryMavenCoordinateEntityType : EntityType<LibraryMavenCoordinateEntity, ModifiableLibraryMavenCoordinateEntity>() {
  override val entityClass: Class<LibraryMavenCoordinateEntity> get() = LibraryMavenCoordinateEntity::class.java
  operator fun invoke(
    coordinates: MavenCoordinates,
    entitySource: EntitySource,
    init: (ModifiableLibraryMavenCoordinateEntity.() -> Unit)? = null,
  ): ModifiableLibraryMavenCoordinateEntity {
    val builder = builder()
    builder.coordinates = coordinates
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

public fun MutableEntityStorage.modifyLibraryMavenCoordinateEntity(
  entity: LibraryMavenCoordinateEntity,
  modification: ModifiableLibraryMavenCoordinateEntity.() -> Unit,
): LibraryMavenCoordinateEntity = modifyEntity(ModifiableLibraryMavenCoordinateEntity::class.java, entity, modification)

public var ModifiableLibraryEntity.mavenCoordinates: ModifiableLibraryMavenCoordinateEntity?
  by WorkspaceEntity.extensionBuilder(LibraryMavenCoordinateEntity::class.java)


@JvmOverloads
@JvmName("createLibraryMavenCoordinateEntity")
public fun LibraryMavenCoordinateEntity(
  coordinates: MavenCoordinates,
  entitySource: EntitySource,
  init: (ModifiableLibraryMavenCoordinateEntity.() -> Unit)? = null,
): ModifiableLibraryMavenCoordinateEntity = LibraryMavenCoordinateEntityType(coordinates, entitySource, init)
