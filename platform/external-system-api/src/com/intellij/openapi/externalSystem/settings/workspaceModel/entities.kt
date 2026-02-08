// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
package com.intellij.openapi.externalSystem.settings.workspaceModel

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import org.jetbrains.annotations.ApiStatus

object ExternalProjectsBuildClasspathEntitySource: EntitySource

interface ExternalProjectsBuildClasspathEntity : WorkspaceEntity {
  val projectsBuildClasspath: Map<String, ExternalProjectBuildClasspathEntity>

  //region generated code
  @Deprecated(message = "Use ExternalProjectsBuildClasspathEntityBuilder instead")
  interface Builder : ExternalProjectsBuildClasspathEntityBuilder
  companion object : EntityType<ExternalProjectsBuildClasspathEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      projectsBuildClasspath: Map<String, ExternalProjectBuildClasspathEntity>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = ExternalProjectsBuildClasspathEntityType.compatibilityInvoke(projectsBuildClasspath, entitySource, init)
  }
  //endregion
}

//region generated code
@Deprecated(message = "Use new API instead")
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
