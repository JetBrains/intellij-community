// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.testEntities.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToOneChild
import com.intellij.platform.workspace.storage.impl.updateOneToOneChildOfParent
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.indexing.testEntities.ChildTestEntity
import com.intellij.util.indexing.testEntities.ParentTestEntity
import com.intellij.util.indexing.testEntities.SiblingEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ParentTestEntityImpl(private val dataSource: ParentTestEntityData) : ParentTestEntity, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val CHILD_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentTestEntity::class.java, ChildTestEntity::class.java,
                                                                         ConnectionId.ConnectionType.ONE_TO_ONE, false)
    internal val SECONDCHILD_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentTestEntity::class.java, SiblingEntity::class.java,
                                                                               ConnectionId.ConnectionType.ONE_TO_ONE, false)

    private val connections = listOf<ConnectionId>(
      CHILD_CONNECTION_ID,
      SECONDCHILD_CONNECTION_ID,
    )

  }

  override val child: ChildTestEntity?
    get() = snapshot.extractOneToOneChild(CHILD_CONNECTION_ID, this)

  override val secondChild: SiblingEntity?
    get() = snapshot.extractOneToOneChild(SECONDCHILD_CONNECTION_ID, this)

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


  internal class Builder(result: ParentTestEntityData?) : ModifiableWorkspaceEntityBase<ParentTestEntity, ParentTestEntityData>(
    result), ParentTestEntity.Builder {
    internal constructor() : this(ParentTestEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ParentTestEntity is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      index(this, "parentEntityRoot", this.parentEntityRoot)
      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
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


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var child: ChildTestEntity.Builder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getOneChildBuilder(CHILD_CONNECTION_ID, this) as? ChildTestEntity.Builder)
          ?: (this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] as? ChildTestEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] as? ChildTestEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(CHILD_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)] = value
        }
        changedProperty.add("child")
      }

    override var secondChild: SiblingEntity.Builder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getOneChildBuilder(SECONDCHILD_CONNECTION_ID, this) as? SiblingEntity.Builder)
          ?: (this.entityLinks[EntityLink(true, SECONDCHILD_CONNECTION_ID)] as? SiblingEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(true, SECONDCHILD_CONNECTION_ID)] as? SiblingEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, SECONDCHILD_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(SECONDCHILD_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, SECONDCHILD_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, SECONDCHILD_CONNECTION_ID)] = value
        }
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

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ParentTestEntity> {
    val modifiable = ParentTestEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): ParentTestEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = ParentTestEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.util.indexing.testEntities.ParentTestEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ParentTestEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return ParentTestEntity(customParentProperty, parentEntityRoot, entitySource) {
    }
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
