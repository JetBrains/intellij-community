// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ModifiableIndexingTestEntity : ModifiableWorkspaceEntity<IndexingTestEntity> {
  override var entitySource: EntitySource
  var roots: MutableList<VirtualFileUrl>
  var excludedRoots: MutableList<VirtualFileUrl>
}

internal object IndexingTestEntityType : EntityType<IndexingTestEntity, ModifiableIndexingTestEntity>() {
  override val entityClass: Class<IndexingTestEntity> get() = IndexingTestEntity::class.java
  operator fun invoke(
    roots: List<VirtualFileUrl>,
    excludedRoots: List<VirtualFileUrl>,
    entitySource: EntitySource,
    init: (ModifiableIndexingTestEntity.() -> Unit)? = null,
  ): ModifiableIndexingTestEntity {
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
  modification: ModifiableIndexingTestEntity.() -> Unit,
): IndexingTestEntity = modifyEntity(ModifiableIndexingTestEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createIndexingTestEntity")
fun IndexingTestEntity(
  roots: List<VirtualFileUrl>,
  excludedRoots: List<VirtualFileUrl>,
  entitySource: EntitySource,
  init: (ModifiableIndexingTestEntity.() -> Unit)? = null,
): ModifiableIndexingTestEntity = IndexingTestEntityType(roots, excludedRoots, entitySource, init)
