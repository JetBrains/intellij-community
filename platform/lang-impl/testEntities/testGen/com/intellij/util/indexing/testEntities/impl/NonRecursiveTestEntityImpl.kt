// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.util.indexing.testEntities.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.indexing.testEntities.NonRecursiveTestEntity
import com.intellij.util.indexing.testEntities.NonRecursiveTestEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class NonRecursiveTestEntityImpl(private val dataSource: NonRecursiveTestEntityData) : NonRecursiveTestEntity,
                                                                                                WorkspaceEntityBase(dataSource) {

  override val root: VirtualFileUrl
    get() {
      readField("root")
      return dataSource.root
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return emptyList()
  }

  internal class Builder(result: NonRecursiveTestEntityData?) :
    ModifiableWorkspaceEntityBase<NonRecursiveTestEntity, NonRecursiveTestEntityData>(result), NonRecursiveTestEntityBuilder {
    internal constructor() : this(NonRecursiveTestEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isRootInitialized()) {
        error("Field NonRecursiveTestEntity#root should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as NonRecursiveTestEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.root != dataSource.root) this.root = dataSource.root
      updateChildToParentReferences(parents)
    }

    override fun index() {
      index(this, "root", this.root)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var root: VirtualFileUrl
      get() = getEntityData().root
      set(value) {
        checkModificationAllowed()
        getEntityData(true).root = value
        changedProperty.add("root")
        val _diff = diff
        if (_diff != null) index(this, "root", value)
      }

    override fun getEntityClass(): Class<NonRecursiveTestEntity> = NonRecursiveTestEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class NonRecursiveTestEntityData : WorkspaceEntityData<NonRecursiveTestEntity>() {
  lateinit var root: VirtualFileUrl
  internal fun isRootInitialized(): Boolean = ::root.isInitialized
  override fun newInstance(): NonRecursiveTestEntity = NonRecursiveTestEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<NonRecursiveTestEntity, *> = NonRecursiveTestEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.util.indexing.testEntities.NonRecursiveTestEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return NonRecursiveTestEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return NonRecursiveTestEntity(root, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as NonRecursiveTestEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.root != other.root) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as NonRecursiveTestEntityData
    if (this.root != other.root) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + root.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + root.hashCode()
    return result
  }
}
