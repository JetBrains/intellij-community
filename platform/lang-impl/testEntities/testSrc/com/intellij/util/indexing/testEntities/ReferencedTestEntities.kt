// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.testEntities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
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

interface WithReferenceTestEntity : WorkspaceEntityWithSymbolicId {
  val name: @NlsSafe String

  override val symbolicId: WithReferenceTestEntityId
    get() = WithReferenceTestEntityId(name)

  val references: List<DependencyItem>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<WithReferenceTestEntity> {
    override var entitySource: EntitySource
    var name: String
    var references: MutableList<DependencyItem>
  }

  companion object : EntityType<WithReferenceTestEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      name: String,
      references: List<DependencyItem>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.name = name
      builder.references = references.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyWithReferenceTestEntity(
  entity: WithReferenceTestEntity,
  modification: WithReferenceTestEntity.Builder.() -> Unit,
): WithReferenceTestEntity {
  return modifyEntity(WithReferenceTestEntity.Builder::class.java, entity, modification)
}
//endregion


interface ReferredTestEntity : WorkspaceEntityWithSymbolicId {
  val name: @NlsSafe String
  val file: VirtualFileUrl

  override val symbolicId: ReferredTestEntityId
    get() = ReferredTestEntityId(name)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ReferredTestEntity> {
    override var entitySource: EntitySource
    var name: String
    var file: VirtualFileUrl
  }

  companion object : EntityType<ReferredTestEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      name: String,
      file: VirtualFileUrl,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.name = name
      builder.file = file
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyReferredTestEntity(
  entity: ReferredTestEntity,
  modification: ReferredTestEntity.Builder.() -> Unit,
): ReferredTestEntity {
  return modifyEntity(ReferredTestEntity.Builder::class.java, entity, modification)
}
//endregion
