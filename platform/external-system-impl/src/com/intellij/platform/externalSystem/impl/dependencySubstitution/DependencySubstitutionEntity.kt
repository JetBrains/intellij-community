// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.dependencySubstitution

import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface DependencySubstitutionEntity : WorkspaceEntityWithSymbolicId {

  val owner: ModuleId

  val library: LibraryId

  val module: ModuleId

  val scope: DependencyScope

  override val symbolicId: DependencySubstitutionId
    get() = DependencySubstitutionId(owner, module, scope)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<DependencySubstitutionEntity> {
    override var entitySource: EntitySource
    var owner: ModuleId
    var library: LibraryId
    var module: ModuleId
    var scope: DependencyScope
  }

  companion object : EntityType<DependencySubstitutionEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      owner: ModuleId,
      library: LibraryId,
      module: ModuleId,
      scope: DependencyScope,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.owner = owner
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
): DependencySubstitutionEntity {
  return modifyEntity(DependencySubstitutionEntity.Builder::class.java, entity, modification)
}
//endregion
