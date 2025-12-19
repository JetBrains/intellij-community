// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IndexingTestEntityModifications")

package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface IndexingTestEntityBuilder : WorkspaceEntityBuilder<IndexingTestEntity> {
  override var entitySource: EntitySource
  var roots: MutableList<VirtualFileUrl>
  var excludedRoots: MutableList<VirtualFileUrl>
}

internal object IndexingTestEntityType : EntityType<IndexingTestEntity, IndexingTestEntityBuilder>() {
  override val entityClass: Class<IndexingTestEntity> get() = IndexingTestEntity::class.java
  operator fun invoke(
    roots: List<VirtualFileUrl>,
    excludedRoots: List<VirtualFileUrl>,
    entitySource: EntitySource,
    init: (IndexingTestEntityBuilder.() -> Unit)? = null,
  ): IndexingTestEntityBuilder {
    val builder = builder()
    builder.roots = roots.toMutableWorkspaceList()
    builder.excludedRoots = excludedRoots.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyIndexingTestEntity(
  entity: IndexingTestEntity,
  modification: IndexingTestEntityBuilder.() -> Unit,
): IndexingTestEntity = modifyEntity(IndexingTestEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createIndexingTestEntity")
fun IndexingTestEntity(
  roots: List<VirtualFileUrl>,
  excludedRoots: List<VirtualFileUrl>,
  entitySource: EntitySource,
  init: (IndexingTestEntityBuilder.() -> Unit)? = null,
): IndexingTestEntityBuilder = IndexingTestEntityType(roots, excludedRoots, entitySource, init)
