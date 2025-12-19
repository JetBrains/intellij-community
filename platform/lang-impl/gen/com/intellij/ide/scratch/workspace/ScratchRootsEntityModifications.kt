// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ScratchRootsEntityModifications")

package com.intellij.ide.scratch.workspace

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface ScratchRootsEntityBuilder : WorkspaceEntityBuilder<ScratchRootsEntity> {
  override var entitySource: EntitySource
  var roots: MutableList<VirtualFileUrl>
}

internal object ScratchRootsEntityType : EntityType<ScratchRootsEntity, ScratchRootsEntityBuilder>() {
  override val entityClass: Class<ScratchRootsEntity> get() = ScratchRootsEntity::class.java
  operator fun invoke(
    roots: List<VirtualFileUrl>,
    entitySource: EntitySource,
    init: (ScratchRootsEntityBuilder.() -> Unit)? = null,
  ): ScratchRootsEntityBuilder {
    val builder = builder()
    builder.roots = roots.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyScratchRootsEntity(
  entity: ScratchRootsEntity,
  modification: ScratchRootsEntityBuilder.() -> Unit,
): ScratchRootsEntity = modifyEntity(ScratchRootsEntityBuilder::class.java, entity, modification)

@JvmOverloads
@JvmName("createScratchRootsEntity")
fun ScratchRootsEntity(
  roots: List<VirtualFileUrl>,
  entitySource: EntitySource,
  init: (ScratchRootsEntityBuilder.() -> Unit)? = null,
): ScratchRootsEntityBuilder = ScratchRootsEntityType(roots, entitySource, init)
