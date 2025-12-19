// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WithReferenceTestEntityModifications")

package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface WithReferenceTestEntityBuilder : WorkspaceEntityBuilder<WithReferenceTestEntity> {
  override var entitySource: EntitySource
  var name: String
  var references: MutableList<DependencyItem>
}

internal object WithReferenceTestEntityType : EntityType<WithReferenceTestEntity, WithReferenceTestEntityBuilder>() {
  override val entityClass: Class<WithReferenceTestEntity> get() = WithReferenceTestEntity::class.java
  operator fun invoke(
    name: String,
    references: List<DependencyItem>,
    entitySource: EntitySource,
    init: (WithReferenceTestEntityBuilder.() -> Unit)? = null,
  ): WithReferenceTestEntityBuilder {
    val builder = builder()
    builder.name = name
    builder.references = references.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyWithReferenceTestEntity(
  entity: WithReferenceTestEntity,
  modification: WithReferenceTestEntityBuilder.() -> Unit,
): WithReferenceTestEntity = modifyEntity(WithReferenceTestEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createWithReferenceTestEntity")
fun WithReferenceTestEntity(
  name: String,
  references: List<DependencyItem>,
  entitySource: EntitySource,
  init: (WithReferenceTestEntityBuilder.() -> Unit)? = null,
): WithReferenceTestEntityBuilder = WithReferenceTestEntityType(name, references, entitySource, init)
