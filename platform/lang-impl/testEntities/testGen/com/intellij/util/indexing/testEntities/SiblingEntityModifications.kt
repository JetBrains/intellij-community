// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SiblingEntityModifications")

package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface SiblingEntityBuilder : WorkspaceEntityBuilder<SiblingEntity> {
  override var entitySource: EntitySource
  var parent: ParentTestEntityBuilder
  var customSiblingProperty: String
}

internal object SiblingEntityType : EntityType<SiblingEntity, SiblingEntityBuilder>() {
  override val entityClass: Class<SiblingEntity> get() = SiblingEntity::class.java
  operator fun invoke(
    customSiblingProperty: String,
    entitySource: EntitySource,
    init: (SiblingEntityBuilder.() -> Unit)? = null,
  ): SiblingEntityBuilder {
    val builder = builder()
    builder.customSiblingProperty = customSiblingProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifySiblingEntity(
  entity: SiblingEntity,
  modification: SiblingEntityBuilder.() -> Unit,
): SiblingEntity = modifyEntity(SiblingEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createSiblingEntity")
fun SiblingEntity(
  customSiblingProperty: String,
  entitySource: EntitySource,
  init: (SiblingEntityBuilder.() -> Unit)? = null,
): SiblingEntityBuilder = SiblingEntityType(customSiblingProperty, entitySource, init)
