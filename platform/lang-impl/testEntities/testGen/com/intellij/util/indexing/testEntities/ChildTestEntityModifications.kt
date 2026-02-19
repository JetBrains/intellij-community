// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ChildTestEntityModifications")

package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface ChildTestEntityBuilder : WorkspaceEntityBuilder<ChildTestEntity> {
  override var entitySource: EntitySource
  var parent: ParentTestEntityBuilder
  var customChildProperty: String
}

internal object ChildTestEntityType : EntityType<ChildTestEntity, ChildTestEntityBuilder>() {
  override val entityClass: Class<ChildTestEntity> get() = ChildTestEntity::class.java
  operator fun invoke(
    customChildProperty: String,
    entitySource: EntitySource,
    init: (ChildTestEntityBuilder.() -> Unit)? = null,
  ): ChildTestEntityBuilder {
    val builder = builder()
    builder.customChildProperty = customChildProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildTestEntity(
  entity: ChildTestEntity,
  modification: ChildTestEntityBuilder.() -> Unit,
): ChildTestEntity = modifyEntity(ChildTestEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildTestEntity")
fun ChildTestEntity(
  customChildProperty: String,
  entitySource: EntitySource,
  init: (ChildTestEntityBuilder.() -> Unit)? = null,
): ChildTestEntityBuilder = ChildTestEntityType(customChildProperty, entitySource, init)
