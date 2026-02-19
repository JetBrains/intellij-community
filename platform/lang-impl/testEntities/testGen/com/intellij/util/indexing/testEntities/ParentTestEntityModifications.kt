// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ParentTestEntityModifications")

package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ParentTestEntityBuilder : WorkspaceEntityBuilder<ParentTestEntity> {
  override var entitySource: EntitySource
  var child: ChildTestEntityBuilder?
  var secondChild: SiblingEntityBuilder?
  var customParentProperty: String
  var parentEntityRoot: VirtualFileUrl
}

internal object ParentTestEntityType : EntityType<ParentTestEntity, ParentTestEntityBuilder>() {
  override val entityClass: Class<ParentTestEntity> get() = ParentTestEntity::class.java
  operator fun invoke(
    customParentProperty: String,
    parentEntityRoot: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ParentTestEntityBuilder.() -> Unit)? = null,
  ): ParentTestEntityBuilder {
    val builder = builder()
    builder.customParentProperty = customParentProperty
    builder.parentEntityRoot = parentEntityRoot
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyParentTestEntity(
  entity: ParentTestEntity,
  modification: ParentTestEntityBuilder.() -> Unit,
): ParentTestEntity = modifyEntity(ParentTestEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createParentTestEntity")
fun ParentTestEntity(
  customParentProperty: String,
  parentEntityRoot: VirtualFileUrl,
  entitySource: EntitySource,
  init: (ParentTestEntityBuilder.() -> Unit)? = null,
): ParentTestEntityBuilder = ParentTestEntityType(customParentProperty, parentEntityRoot, entitySource, init)
