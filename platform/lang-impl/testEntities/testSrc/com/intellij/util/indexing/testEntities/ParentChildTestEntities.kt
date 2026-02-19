// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.testEntities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl


interface ParentTestEntity : WorkspaceEntity {
  val child: ChildTestEntity?
  val secondChild: SiblingEntity?
  val customParentProperty: String
  val parentEntityRoot: VirtualFileUrl
}

interface ChildTestEntity : WorkspaceEntity {
  @Parent
  val parent: ParentTestEntity
  val customChildProperty: String
}

/**
 * Sibling means [ParentTestEntity] has this entity as its child
 * so it is sibling for [ChildTestEntity]
 */
interface SiblingEntity : WorkspaceEntity {
  @Parent
  val parent: ParentTestEntity
  val customSiblingProperty: String
}
