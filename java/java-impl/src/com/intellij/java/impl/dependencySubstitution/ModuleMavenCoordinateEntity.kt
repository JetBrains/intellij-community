// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental
package com.intellij.java.impl.dependencySubstitution

import com.intellij.java.library.MavenCoordinates
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
interface ModuleMavenCoordinateEntity : WorkspaceEntity {

  @Parent
  val module: ModuleEntity

  val coordinates: MavenCoordinates

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ModuleMavenCoordinateEntity> {
    override var entitySource: EntitySource
    var module: ModuleEntity.Builder
    var coordinates: MavenCoordinates
  }

  companion object : EntityType<ModuleMavenCoordinateEntity, Builder>() {
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
fun MutableEntityStorage.modifyModuleMavenCoordinateEntity(
  entity: ModuleMavenCoordinateEntity,
  modification: ModuleMavenCoordinateEntity.Builder.() -> Unit,
): ModuleMavenCoordinateEntity {
  return modifyEntity(ModuleMavenCoordinateEntity.Builder::class.java, entity, modification)
}

var ModuleEntity.Builder.mavenCoordinates: ModuleMavenCoordinateEntity.Builder?
  by WorkspaceEntity.extensionBuilder(ModuleMavenCoordinateEntity::class.java)
//endregion

val ModuleEntity.mavenCoordinates: ModuleMavenCoordinateEntity?
  by WorkspaceEntity.extension()