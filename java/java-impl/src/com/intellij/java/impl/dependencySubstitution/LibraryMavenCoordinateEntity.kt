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
import com.intellij.platform.workspace.storage.annotations.Child
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
interface LibraryMavenCoordinateEntity : WorkspaceEntity {

  val library: LibraryEntity

  val coordinates: MavenCoordinates

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<LibraryMavenCoordinateEntity> {
    override var entitySource: EntitySource
    var library: LibraryEntity.Builder
    var coordinates: MavenCoordinates
  }

  companion object : EntityType<LibraryMavenCoordinateEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
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
fun MutableEntityStorage.modifyLibraryMavenCoordinateEntity(
  entity: LibraryMavenCoordinateEntity,
  modification: LibraryMavenCoordinateEntity.Builder.() -> Unit,
): LibraryMavenCoordinateEntity {
  return modifyEntity(LibraryMavenCoordinateEntity.Builder::class.java, entity, modification)
}

var LibraryEntity.Builder.mavenCoordinates: @Child LibraryMavenCoordinateEntity.Builder?
  by WorkspaceEntity.extensionBuilder(LibraryMavenCoordinateEntity::class.java)
//endregion

val LibraryEntity.mavenCoordinates: @Child LibraryMavenCoordinateEntity?
  by WorkspaceEntity.extension()