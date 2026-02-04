// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IndexingTestEntity2Modifications")

package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
interface IndexingTestEntity2Builder : WorkspaceEntityBuilder<IndexingTestEntity2> {
  override var entitySource: EntitySource
  var roots: MutableList<VirtualFileUrl>
  var excludedRoots: MutableList<VirtualFileUrl>
}

internal object IndexingTestEntity2Type : EntityType<IndexingTestEntity2, IndexingTestEntity2Builder>() {
  override val entityClass: Class<IndexingTestEntity2> get() = IndexingTestEntity2::class.java
  operator fun invoke(
    roots: List<VirtualFileUrl>,
    excludedRoots: List<VirtualFileUrl>,
    entitySource: EntitySource,
    init: (IndexingTestEntity2Builder.() -> Unit)? = null,
  ): IndexingTestEntity2Builder {
    val builder = builder()
    builder.roots = roots.toMutableWorkspaceList()
    builder.excludedRoots = excludedRoots.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyIndexingTestEntity2(
  entity: IndexingTestEntity2,
  modification: IndexingTestEntity2Builder.() -> Unit,
): IndexingTestEntity2 = modifyEntity(IndexingTestEntity2Builder::class.java, entity, modification)

@JvmOverloads
@JvmName("createIndexingTestEntity2")
fun IndexingTestEntity2(
  roots: List<VirtualFileUrl>,
  excludedRoots: List<VirtualFileUrl>,
  entitySource: EntitySource,
  init: (IndexingTestEntity2Builder.() -> Unit)? = null,
): IndexingTestEntity2Builder = IndexingTestEntity2Type(roots, excludedRoots, entitySource, init)
