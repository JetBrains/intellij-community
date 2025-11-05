// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NonRecursiveTestEntityModifications")

package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface NonRecursiveTestEntityBuilder : WorkspaceEntityBuilder<NonRecursiveTestEntity> {
  override var entitySource: EntitySource
  var root: VirtualFileUrl
}

internal object NonRecursiveTestEntityType : EntityType<NonRecursiveTestEntity, NonRecursiveTestEntityBuilder>() {
  override val entityClass: Class<NonRecursiveTestEntity> get() = NonRecursiveTestEntity::class.java
  operator fun invoke(
    root: VirtualFileUrl,
    entitySource: EntitySource,
    init: (NonRecursiveTestEntityBuilder.() -> Unit)? = null,
  ): NonRecursiveTestEntityBuilder {
    val builder = builder()
    builder.root = root
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyNonRecursiveTestEntity(
  entity: NonRecursiveTestEntity,
  modification: NonRecursiveTestEntityBuilder.() -> Unit,
): NonRecursiveTestEntity = modifyEntity(NonRecursiveTestEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createNonRecursiveTestEntity")
fun NonRecursiveTestEntity(
  root: VirtualFileUrl,
  entitySource: EntitySource,
  init: (NonRecursiveTestEntityBuilder.() -> Unit)? = null,
): NonRecursiveTestEntityBuilder = NonRecursiveTestEntityType(root, entitySource, init)
