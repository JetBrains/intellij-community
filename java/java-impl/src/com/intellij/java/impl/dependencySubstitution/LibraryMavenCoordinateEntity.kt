// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental
package com.intellij.java.impl.dependencySubstitution

import com.intellij.java.library.MavenCoordinates
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
public interface LibraryMavenCoordinateEntity : WorkspaceEntity {

  @Parent
  public val library: LibraryEntity

  public val coordinates: MavenCoordinates

  //region generated code
  @GeneratedCodeApiVersion(3)
  public interface Builder : WorkspaceEntity.Builder<LibraryMavenCoordinateEntity> {
    override var entitySource: EntitySource
    public var library: LibraryEntity.Builder
    public var coordinates: MavenCoordinates
  }

  public companion object : EntityType<LibraryMavenCoordinateEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    public operator fun invoke(
      coordinates: MavenCoordinates,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.coordinates = coordinates
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
public fun MutableEntityStorage.modifyLibraryMavenCoordinateEntity(
  entity: LibraryMavenCoordinateEntity,
  modification: LibraryMavenCoordinateEntity.Builder.() -> Unit,
): LibraryMavenCoordinateEntity {
  return modifyEntity(LibraryMavenCoordinateEntity.Builder::class.java, entity, modification)
}

public var LibraryEntity.Builder.mavenCoordinates: LibraryMavenCoordinateEntity.Builder?
  by WorkspaceEntity.extensionBuilder(LibraryMavenCoordinateEntity::class.java)
//endregion

public val LibraryEntity.mavenCoordinates: LibraryMavenCoordinateEntity?
  by WorkspaceEntity.extension()