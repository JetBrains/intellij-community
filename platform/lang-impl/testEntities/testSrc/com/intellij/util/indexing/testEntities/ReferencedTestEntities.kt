// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.testEntities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

data class WithReferenceTestEntityId(val name: String) : SymbolicEntityId<WithReferenceTestEntity> {
  override val presentableName: String
    get() = name
}

data class ReferredTestEntityId(val name: String) : SymbolicEntityId<ReferredTestEntity> {
  override val presentableName: String
    get() = name
}

data class DependencyItem(val reference: ReferredTestEntityId)

interface OneMoreWithReferenceTestEntity : WorkspaceEntity {
  val references: List<DependencyItem>
}

interface WithReferenceTestEntity : WorkspaceEntityWithSymbolicId {
  val name: @NlsSafe String

  override val symbolicId: WithReferenceTestEntityId
    get() = WithReferenceTestEntityId(name)

  val references: List<DependencyItem>
}


interface ReferredTestEntity : WorkspaceEntityWithSymbolicId {
  val name: @NlsSafe String
  val file: VirtualFileUrl

  override val symbolicId: ReferredTestEntityId
    get() = ReferredTestEntityId(name)

}
