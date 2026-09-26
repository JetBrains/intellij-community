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
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.indexing.testEntities.ChildTestEntity
import com.intellij.util.indexing.testEntities.ChildTestEntityBuilder
import com.intellij.util.indexing.testEntities.ParentTestEntity
import com.intellij.util.indexing.testEntities.ParentTestEntityBuilder
import com.intellij.util.indexing.testEntities.SiblingEntity
import com.intellij.util.indexing.testEntities.SiblingEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ParentTestEntityImpl(private val dataSource: ParentTestEntityData) : ParentTestEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CHILD_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ParentTestEntity::class.java, ChildTestEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    internal val SECONDCHILD_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ParentTestEntity::class.java, SiblingEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    private val connections = listOf<ConnectionId>(CHILD_CONNECTION_ID, SECONDCHILD_CONNECTION_ID)
  }

  override val child: ChildTestEntity?
    get() = snapshot.instrumentation.getOneChild(CHILD_CONNECTION_ID, this) as? ChildTestEntity
  override val secondChild: SiblingEntity?
    get() = snapshot.instrumentation.getOneChild(SECONDCHILD_CONNECTION_ID, this) as? SiblingEntity
  override val customParentProperty: String
    get() {
      readField("customParentProperty")
      return dataSource.customParentProperty
    }
  override val parentEntityRoot: VirtualFileUrl
    get() {
      readField("parentEntityRoot")
      return dataSource.parentEntityRoot
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ParentTestEntityData?) : ModifiableWorkspaceEntityBase<ParentTestEntity, ParentTestEntityData>(result),
                                                          ParentTestEntityBuilder {
    internal constructor() : this(ParentTestEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isCustomParentPropertyInitialized()) {
        error("Field ParentTestEntity#customParentProperty should be initialized")
      }
      if (!getEntityData().isParentEntityRootInitialized()) {
        error("Field ParentTestEntity#parentEntityRoot should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ParentTestEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.customParentProperty != dataSource.customParentProperty) this.customParentProperty = dataSource.customParentProperty
      if (this.parentEntityRoot != dataSource.parentEntityRoot) this.parentEntityRoot = dataSource.parentEntityRoot
      updateChildToParentReferences(parents)
    }

    override fun index() {
      index(this, "parentEntityRoot", this.parentEntityRoot)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var child: ChildTestEntityBuilder?
      get() = getChild(CHILD_CONNECTION_ID) as? ChildTestEntityBuilder?
      set(value) {
        changeChild(value, CHILD_CONNECTION_ID)
        changedProperty.add("child")
      }
    override var secondChild: SiblingEntityBuilder?
      get() = getChild(SECONDCHILD_CONNECTION_ID) as? SiblingEntityBuilder?
      set(value) {
        changeChild(value, SECONDCHILD_CONNECTION_ID)
        changedProperty.add("secondChild")
      }
    override var customParentProperty: String
      get() = getEntityData().customParentProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).customParentProperty = value
        changedProperty.add("customParentProperty")
      }
    override var parentEntityRoot: VirtualFileUrl
      get() = getEntityData().parentEntityRoot
      set(value) {
        checkModificationAllowed()
        getEntityData(true).parentEntityRoot = value
        changedProperty.add("parentEntityRoot")
        val _diff = diff
        if (_diff != null) index(this, "parentEntityRoot", value)
      }

    override fun getEntityClass(): Class<ParentTestEntity> = ParentTestEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ParentTestEntityData : WorkspaceEntityData<ParentTestEntity>() {
  lateinit var customParentProperty: String
  lateinit var parentEntityRoot: VirtualFileUrl
  internal fun isCustomParentPropertyInitialized(): Boolean = ::customParentProperty.isInitialized
  internal fun isParentEntityRootInitialized(): Boolean = ::parentEntityRoot.isInitialized
  override fun newInstance(): ParentTestEntity = ParentTestEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ParentTestEntity, *> = ParentTestEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.util.indexing.testEntities.ParentTestEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ParentTestEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ParentTestEntity(customParentProperty, parentEntityRoot, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ParentTestEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.customParentProperty != other.customParentProperty) return false
    if (this.parentEntityRoot != other.parentEntityRoot) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ParentTestEntityData
    if (this.customParentProperty != other.customParentProperty) return false
    if (this.parentEntityRoot != other.parentEntityRoot) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + customParentProperty.hashCode()
    result = 31 * result + parentEntityRoot.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + customParentProperty.hashCode()
    result = 31 * result + parentEntityRoot.hashCode()
    return result
  }
}
