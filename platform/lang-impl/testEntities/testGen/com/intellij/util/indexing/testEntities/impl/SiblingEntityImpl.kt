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
import com.intellij.util.indexing.testEntities.ParentTestEntity
import com.intellij.util.indexing.testEntities.ParentTestEntityBuilder
import com.intellij.util.indexing.testEntities.SiblingEntity
import com.intellij.util.indexing.testEntities.SiblingEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class SiblingEntityImpl(private val dataSource: SiblingEntityData) : SiblingEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENT_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ParentTestEntity::class.java, SiblingEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    private val connections = listOf<ConnectionId>(PARENT_CONNECTION_ID)
  }

  override val parent: ParentTestEntity
    get() = snapshot.instrumentation.getParent(PARENT_CONNECTION_ID, this) as? ParentTestEntity
            ?: error("Parent parent not found for SiblingEntity")
  override val customSiblingProperty: String
    get() {
      readField("customSiblingProperty")
      return dataSource.customSiblingProperty
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: SiblingEntityData?) : ModifiableWorkspaceEntityBase<SiblingEntity, SiblingEntityData>(result),
                                                       SiblingEntityBuilder {
    internal constructor() : this(SiblingEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(PARENT_CONNECTION_ID, this) == null) {
          error("Field SiblingEntity#parent should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] == null) {
          error("Field SiblingEntity#parent should be initialized")
        }
      }
      if (!getEntityData().isCustomSiblingPropertyInitialized()) {
        error("Field SiblingEntity#customSiblingProperty should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SiblingEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.customSiblingProperty != dataSource.customSiblingProperty) this.customSiblingProperty = dataSource.customSiblingProperty
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
          ?: error("parent is null for SiblingEntity")
        }
        else {
          (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] as? ParentTestEntityBuilder)
          ?: error("parent is null for SiblingEntity")
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
    override var customSiblingProperty: String
      get() = getEntityData().customSiblingProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).customSiblingProperty = value
        changedProperty.add("customSiblingProperty")
      }

    override fun getEntityClass(): Class<SiblingEntity> = SiblingEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class SiblingEntityData : WorkspaceEntityData<SiblingEntity>() {
  lateinit var customSiblingProperty: String
  internal fun isCustomSiblingPropertyInitialized(): Boolean = ::customSiblingProperty.isInitialized
  override fun newInstance(): SiblingEntity = SiblingEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<SiblingEntity, *> = SiblingEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.util.indexing.testEntities.SiblingEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SiblingEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return SiblingEntity(customSiblingProperty, entitySource) {
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
    other as SiblingEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.customSiblingProperty != other.customSiblingProperty) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as SiblingEntityData
    if (this.customSiblingProperty != other.customSiblingProperty) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + customSiblingProperty.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + customSiblingProperty.hashCode()
    return result
  }
}
