// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("LibraryMavenCoordinateEntityModifications")

package com.intellij.java.impl.dependencySubstitution

import com.intellij.java.library.MavenCoordinates
import com.intellij.platform.workspace.jps.entities.LibraryEntityBuilder
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity

@GeneratedCodeApiVersion(3)
public interface LibraryMavenCoordinateEntityBuilder : WorkspaceEntityBuilder<LibraryMavenCoordinateEntity> {
  override var entitySource: EntitySource
  public var library: LibraryEntityBuilder
  public var coordinates: MavenCoordinates
}

internal object LibraryMavenCoordinateEntityType : EntityType<LibraryMavenCoordinateEntity, LibraryMavenCoordinateEntityBuilder>() {
  override val entityClass: Class<LibraryMavenCoordinateEntity> get() = LibraryMavenCoordinateEntity::class.java
  operator fun invoke(
    coordinates: MavenCoordinates,
    entitySource: EntitySource,
    init: (LibraryMavenCoordinateEntityBuilder.() -> Unit)? = null,
  ): LibraryMavenCoordinateEntityBuilder {
    val builder = builder()
    builder.coordinates = coordinates
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

public fun MutableEntityStorage.modifyLibraryMavenCoordinateEntity(
  entity: LibraryMavenCoordinateEntity,
  modification: LibraryMavenCoordinateEntityBuilder.() -> Unit,
): LibraryMavenCoordinateEntity = modifyEntity(LibraryMavenCoordinateEntityBuilder::class.java, entity, modification)

public var LibraryEntityBuilder.mavenCoordinates: LibraryMavenCoordinateEntityBuilder?
  by WorkspaceEntity.extensionBuilder(LibraryMavenCoordinateEntity::class.java)


@JvmOverloads
@JvmName("createLibraryMavenCoordinateEntity")
public fun LibraryMavenCoordinateEntity(
  coordinates: MavenCoordinates,
  entitySource: EntitySource,
  init: (LibraryMavenCoordinateEntityBuilder.() -> Unit)? = null,
): LibraryMavenCoordinateEntityBuilder = LibraryMavenCoordinateEntityType(coordinates, entitySource, init)
