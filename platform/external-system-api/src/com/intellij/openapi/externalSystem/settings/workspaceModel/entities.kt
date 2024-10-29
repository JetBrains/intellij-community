// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
package com.intellij.openapi.externalSystem.settings.workspaceModel

import com.intellij.platform.workspace.storage.*
import org.jetbrains.annotations.ApiStatus

object ExternalProjectsBuildClasspathEntitySource: EntitySource

interface ExternalProjectsBuildClasspathEntity : WorkspaceEntity {
  val projectsBuildClasspath: Map<String, ExternalProjectBuildClasspathEntity>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ExternalProjectsBuildClasspathEntity> {
    override var entitySource: EntitySource
    var projectsBuildClasspath: Map<String, ExternalProjectBuildClasspathEntity>
  }

  companion object : EntityType<ExternalProjectsBuildClasspathEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      projectsBuildClasspath: Map<String, ExternalProjectBuildClasspathEntity>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.projectsBuildClasspath = projectsBuildClasspath
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyExternalProjectsBuildClasspathEntity(
  entity: ExternalProjectsBuildClasspathEntity,
  modification: ExternalProjectsBuildClasspathEntity.Builder.() -> Unit,
): ExternalProjectsBuildClasspathEntity {
  return modifyEntity(ExternalProjectsBuildClasspathEntity.Builder::class.java, entity, modification)
}
//endregion

data class ExternalModuleBuildClasspathEntity(val path: String, val entries: List<String>)

data class ExternalProjectBuildClasspathEntity(val name: String,
                                               val projectBuildClasspath: List<String>,
                                               val moduleBuildClasspath: Map<String, ExternalModuleBuildClasspathEntity>)
