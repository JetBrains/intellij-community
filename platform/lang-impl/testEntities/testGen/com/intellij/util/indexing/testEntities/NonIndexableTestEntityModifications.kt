// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NonIndexableTestEntityModifications")

package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface NonIndexableTestEntityBuilder : WorkspaceEntityBuilder<NonIndexableTestEntity> {
  override var entitySource: EntitySource
  var root: VirtualFileUrl
}

internal object NonIndexableTestEntityType : EntityType<NonIndexableTestEntity, NonIndexableTestEntityBuilder>() {
  override val entityClass: Class<NonIndexableTestEntity> get() = NonIndexableTestEntity::class.java
  operator fun invoke(
    root: VirtualFileUrl,
    entitySource: EntitySource,
    init: (NonIndexableTestEntityBuilder.() -> Unit)? = null,
  ): NonIndexableTestEntityBuilder {
    val builder = builder()
    builder.root = root
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyNonIndexableTestEntity(
  entity: NonIndexableTestEntity,
  modification: NonIndexableTestEntityBuilder.() -> Unit,
): NonIndexableTestEntity = modifyEntity(NonIndexableTestEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createNonIndexableTestEntity")
fun NonIndexableTestEntity(
  root: VirtualFileUrl,
  entitySource: EntitySource,
  init: (NonIndexableTestEntityBuilder.() -> Unit)? = null,
): NonIndexableTestEntityBuilder = NonIndexableTestEntityType(root, entitySource, init)
