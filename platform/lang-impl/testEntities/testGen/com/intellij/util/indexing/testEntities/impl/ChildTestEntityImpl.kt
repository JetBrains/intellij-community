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
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.util.indexing.testEntities.ChildTestEntity
import com.intellij.util.indexing.testEntities.ChildTestEntityBuilder
import com.intellij.util.indexing.testEntities.ParentTestEntity
import com.intellij.util.indexing.testEntities.ParentTestEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildTestEntityImpl(private val dataSource: ChildTestEntityData) : ChildTestEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENT_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ParentTestEntity::class.java, ChildTestEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    private val connections = listOf<ConnectionId>(PARENT_CONNECTION_ID)
  }

  override val parent: ParentTestEntity
    get() = snapshot.instrumentation.getParent(PARENT_CONNECTION_ID, this) as? ParentTestEntity
            ?: error("Parent parent not found for ChildTestEntity")
  override val customChildProperty: String
    get() {
      readField("customChildProperty")
      return dataSource.customChildProperty
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: ChildTestEntityData?) : ModifiableWorkspaceEntityBase<ChildTestEntity, ChildTestEntityData>(result),
                                                         ChildTestEntityBuilder {
    internal constructor() : this(ChildTestEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(PARENT_CONNECTION_ID, this) == null) {
          error("Field ChildTestEntity#parent should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] == null) {
          error("Field ChildTestEntity#parent should be initialized")
        }
      }
      if (!getEntityData().isCustomChildPropertyInitialized()) {
        error("Field ChildTestEntity#customChildProperty should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ChildTestEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.customChildProperty != dataSource.customChildProperty) this.customChildProperty = dataSource.customChildProperty
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var parent: ParentTestEntityBuilder
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(PARENT_CONNECTION_ID, this) as? ParentTestEntityBuilder)
          ?: (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] as? ParentTestEntityBuilder)
          ?: error("parent is null for ChildTestEntity")
        }
        else {
          (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] as? ParentTestEntityBuilder)
          ?: error("parent is null for ChildTestEntity")
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          value.entityLinks[EntityLink(true, PARENT_CONNECTION_ID)] = this
          @Suppress("UNCHECKED_CAST")
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.addChild(PARENT_CONNECTION_ID, value, this)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, PARENT_CONNECTION_ID)] = this
          }
          this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] = value
        }
        changedProperty.add("parent")
      }
    override var customChildProperty: String
      get() = getEntityData().customChildProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).customChildProperty = value
        changedProperty.add("customChildProperty")
      }

    override fun getEntityClass(): Class<ChildTestEntity> = ChildTestEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildTestEntityData : WorkspaceEntityData<ChildTestEntity>() {
  lateinit var customChildProperty: String
  internal fun isCustomChildPropertyInitialized(): Boolean = ::customChildProperty.isInitialized
  override fun newInstance(): ChildTestEntity = ChildTestEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ChildTestEntity, *> = ChildTestEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.util.indexing.testEntities.ChildTestEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ChildTestEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ChildTestEntity(customChildProperty, entitySource) {
      parents.filterIsInstance<ParentTestEntityBuilder>().singleOrNull()?.let { this.parent = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ParentTestEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ChildTestEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.customChildProperty != other.customChildProperty) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ChildTestEntityData
    if (this.customChildProperty != other.customChildProperty) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + customChildProperty.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + customChildProperty.hashCode()
    return result
  }
}
