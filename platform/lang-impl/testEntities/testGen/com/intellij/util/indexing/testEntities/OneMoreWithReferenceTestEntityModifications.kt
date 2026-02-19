// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OneMoreWithReferenceTestEntityModifications")

package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

@GeneratedCodeApiVersion(3)
interface OneMoreWithReferenceTestEntityBuilder : WorkspaceEntityBuilder<OneMoreWithReferenceTestEntity> {
  override var entitySource: EntitySource
  var references: MutableList<DependencyItem>
}

internal object OneMoreWithReferenceTestEntityType : EntityType<OneMoreWithReferenceTestEntity, OneMoreWithReferenceTestEntityBuilder>() {
  override val entityClass: Class<OneMoreWithReferenceTestEntity> get() = OneMoreWithReferenceTestEntity::class.java
  operator fun invoke(
    references: List<DependencyItem>,
    entitySource: EntitySource,
    init: (OneMoreWithReferenceTestEntityBuilder.() -> Unit)? = null,
  ): OneMoreWithReferenceTestEntityBuilder {
    val builder = builder()
    builder.references = references.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyOneMoreWithReferenceTestEntity(
  entity: OneMoreWithReferenceTestEntity,
  modification: OneMoreWithReferenceTestEntityBuilder.() -> Unit,
): OneMoreWithReferenceTestEntity = modifyEntity(OneMoreWithReferenceTestEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createOneMoreWithReferenceTestEntity")
fun OneMoreWithReferenceTestEntity(
  references: List<DependencyItem>,
  entitySource: EntitySource,
  init: (OneMoreWithReferenceTestEntityBuilder.() -> Unit)? = null,
): OneMoreWithReferenceTestEntityBuilder = OneMoreWithReferenceTestEntityType(references, entitySource, init)
