// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DependencySubstitutionEntityModifications")

package com.intellij.platform.externalSystem.impl.dependencySubstitution

import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
interface DependencySubstitutionEntityBuilder : WorkspaceEntityBuilder<DependencySubstitutionEntity> {
  override var entitySource: EntitySource
  var owner: ModuleEntityBuilder
  var library: LibraryId
  var module: ModuleId
  var scope: DependencyScope
}

internal object DependencySubstitutionEntityType : EntityType<DependencySubstitutionEntity, DependencySubstitutionEntityBuilder>() {
  override val entityClass: Class<DependencySubstitutionEntity> get() = DependencySubstitutionEntity::class.java
  operator fun invoke(
    library: LibraryId,
    module: ModuleId,
    scope: DependencyScope,
    entitySource: EntitySource,
    init: (DependencySubstitutionEntityBuilder.() -> Unit)? = null,
  ): DependencySubstitutionEntityBuilder {
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
  modification: DependencySubstitutionEntityBuilder.() -> Unit,
): DependencySubstitutionEntity = modifyEntity(DependencySubstitutionEntityBuilder::class.java, entity, modification)

@get:Internal
@set:Internal
var ModuleEntityBuilder.substitutions: List<DependencySubstitutionEntityBuilder>
  by WorkspaceEntity.extensionBuilder(DependencySubstitutionEntity::class.java)


@Internal
@JvmOverloads
@JvmName("createDependencySubstitutionEntity")
fun DependencySubstitutionEntity(
  library: LibraryId,
  module: ModuleId,
  scope: DependencyScope,
  entitySource: EntitySource,
  init: (DependencySubstitutionEntityBuilder.() -> Unit)? = null,
): DependencySubstitutionEntityBuilder = DependencySubstitutionEntityType(library, module, scope, entitySource, init)
