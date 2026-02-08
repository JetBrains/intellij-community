// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ExcludedTestEntityModifications")

package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ExcludedTestEntityBuilder : WorkspaceEntityBuilder<ExcludedTestEntity> {
  override var entitySource: EntitySource
  var root: VirtualFileUrl
}

internal object ExcludedTestEntityType : EntityType<ExcludedTestEntity, ExcludedTestEntityBuilder>() {
  override val entityClass: Class<ExcludedTestEntity> get() = ExcludedTestEntity::class.java
  operator fun invoke(
    root: VirtualFileUrl,
    entitySource: EntitySource,
    init: (ExcludedTestEntityBuilder.() -> Unit)? = null,
  ): ExcludedTestEntityBuilder {
    val builder = builder()
    builder.root = root
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyExcludedTestEntity(
  entity: ExcludedTestEntity,
  modification: ExcludedTestEntityBuilder.() -> Unit,
): ExcludedTestEntity = modifyEntity(ExcludedTestEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createExcludedTestEntity")
fun ExcludedTestEntity(
  root: VirtualFileUrl,
  entitySource: EntitySource,
  init: (ExcludedTestEntityBuilder.() -> Unit)? = null,
): ExcludedTestEntityBuilder = ExcludedTestEntityType(root, entitySource, init)
