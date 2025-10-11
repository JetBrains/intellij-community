// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.workspaceModel

import com.intellij.platform.workspace.storage.*
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface ExternalProjectEntity: WorkspaceEntityWithSymbolicId {

  val externalProjectPath: String

  override val symbolicId: ExternalProjectEntityId
    get() = ExternalProjectEntityId(externalProjectPath)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ExternalProjectEntity> {
    override var entitySource: EntitySource
    var externalProjectPath: String
  }

  companion object : EntityType<ExternalProjectEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      externalProjectPath: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.externalProjectPath = externalProjectPath
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
@Internal
fun MutableEntityStorage.modifyExternalProjectEntity(
  entity: ExternalProjectEntity,
  modification: ExternalProjectEntity.Builder.() -> Unit,
): ExternalProjectEntity = modifyEntity(ExternalProjectEntity.Builder::class.java, entity, modification)
//endregion
