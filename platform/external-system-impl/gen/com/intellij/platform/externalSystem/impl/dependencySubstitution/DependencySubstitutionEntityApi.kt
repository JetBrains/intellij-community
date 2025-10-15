// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.dependencySubstitution

import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModifiableModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
interface ModifiableDependencySubstitutionEntity : ModifiableWorkspaceEntity<DependencySubstitutionEntity> {
  override var entitySource: EntitySource
  var owner: ModifiableModuleEntity
  var library: LibraryId
  var module: ModuleId
  var scope: DependencyScope
}

internal object DependencySubstitutionEntityType : EntityType<DependencySubstitutionEntity, ModifiableDependencySubstitutionEntity>() {
  override val entityClass: Class<DependencySubstitutionEntity> get() = DependencySubstitutionEntity::class.java
  operator fun invoke(
    library: LibraryId,
    module: ModuleId,
    scope: DependencyScope,
    entitySource: EntitySource,
    init: (ModifiableDependencySubstitutionEntity.() -> Unit)? = null,
  ): ModifiableDependencySubstitutionEntity {
    val builder = builder()
    builder.library = library
    builder.module = module
    builder.scope = scope
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@Internal
fun MutableEntityStorage.modifyDependencySubstitutionEntity(
  entity: DependencySubstitutionEntity,
  modification: ModifiableDependencySubstitutionEntity.() -> Unit,
): DependencySubstitutionEntity = modifyEntity(ModifiableDependencySubstitutionEntity::class.java, entity, modification)

@get:Internal
@set:Internal
var ModifiableModuleEntity.substitutions: List<ModifiableDependencySubstitutionEntity>
  by WorkspaceEntity.extensionBuilder(DependencySubstitutionEntity::class.java)


@Internal
@JvmOverloads
@JvmName("createDependencySubstitutionEntity")
fun DependencySubstitutionEntity(
  library: LibraryId,
  module: ModuleId,
  scope: DependencyScope,
  entitySource: EntitySource,
  init: (ModifiableDependencySubstitutionEntity.() -> Unit)? = null,
): ModifiableDependencySubstitutionEntity = DependencySubstitutionEntityType(library, module, scope, entitySource, init)
