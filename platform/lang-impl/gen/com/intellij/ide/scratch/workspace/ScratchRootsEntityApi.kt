// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch.workspace

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ModifiableScratchRootsEntity : ModifiableWorkspaceEntity<ScratchRootsEntity> {
  override var entitySource: EntitySource
  var roots: MutableList<VirtualFileUrl>
}

internal object ScratchRootsEntityType : EntityType<ScratchRootsEntity, ModifiableScratchRootsEntity>() {
  override val entityClass: Class<ScratchRootsEntity> get() = ScratchRootsEntity::class.java
  operator fun invoke(
    roots: List<VirtualFileUrl>,
    entitySource: EntitySource,
    init: (ModifiableScratchRootsEntity.() -> Unit)? = null,
  ): ModifiableScratchRootsEntity {
    val builder = builder()
    builder.roots = roots.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyScratchRootsEntity(
  entity: ScratchRootsEntity,
  modification: ModifiableScratchRootsEntity.() -> Unit,
): ScratchRootsEntity = modifyEntity(ModifiableScratchRootsEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createScratchRootsEntity")
fun ScratchRootsEntity(
  roots: List<VirtualFileUrl>,
  entitySource: EntitySource,
  init: (ModifiableScratchRootsEntity.() -> Unit)? = null,
): ModifiableScratchRootsEntity = ScratchRootsEntityType(roots, entitySource, init)
