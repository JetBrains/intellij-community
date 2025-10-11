// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.dependencySubstitution

import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface DependencySubstitutionEntity : WorkspaceEntity {

  @Parent
  val owner: ModuleEntity

  val library: LibraryId

  val module: ModuleId

  val scope: DependencyScope

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<DependencySubstitutionEntity> {
    override var entitySource: EntitySource
    var owner: ModuleEntity.Builder
    var library: LibraryId
    var module: ModuleId
    var scope: DependencyScope
  }

  companion object : EntityType<DependencySubstitutionEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      library: LibraryId,
      module: ModuleId,
      scope: DependencyScope,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.library = library
      builder.module = module
      builder.scope = scope
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
@Internal
fun MutableEntityStorage.modifyDependencySubstitutionEntity(
  entity: DependencySubstitutionEntity,
  modification: DependencySubstitutionEntity.Builder.() -> Unit,
): DependencySubstitutionEntity = modifyEntity(DependencySubstitutionEntity.Builder::class.java, entity, modification)

@get:Internal
@set:Internal
var ModuleEntity.Builder.substitutions: List<DependencySubstitutionEntity.Builder>
  by WorkspaceEntity.extensionBuilder(DependencySubstitutionEntity::class.java)
//endregion

@get:Internal
val ModuleEntity.substitutions: List<DependencySubstitutionEntity>
  by WorkspaceEntity.extension()