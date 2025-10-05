// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.testEntities.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.indexing.testEntities.ReferredTestEntity
import com.intellij.util.indexing.testEntities.ReferredTestEntityId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ReferredTestEntityImpl(private val dataSource: ReferredTestEntityData) : ReferredTestEntity, WorkspaceEntityBase(
  dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val symbolicId: ReferredTestEntityId = super.symbolicId

  override val name: String
    get() {
      readField("name")
      return dataSource.name
    }

  override val file: VirtualFileUrl
    get() {
      readField("file")
      return dataSource.file
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: ReferredTestEntityData?) : ModifiableWorkspaceEntityBase<ReferredTestEntity, ReferredTestEntityData>(
    result), ReferredTestEntity.Builder {
    internal constructor() : this(ReferredTestEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ReferredTestEntity is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      index(this, "file", this.file)
      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isNameInitialized()) {
        error("Field ReferredTestEntity#name should be initialized")
      }
      if (!getEntityData().isFileInitialized()) {
        error("Field ReferredTestEntity#file should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ReferredTestEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.file != dataSource.file) this.file = dataSource.file
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var name: String
      get() = getEntityData().name
      set(value) {
        checkModificationAllowed()
        getEntityData(true).name = value
        changedProperty.add("name")
      }

    override var file: VirtualFileUrl
      get() = getEntityData().file
      set(value) {
        checkModificationAllowed()
        getEntityData(true).file = value
        changedProperty.add("file")
        val _diff = diff
        if (_diff != null) index(this, "file", value)
      }

    override fun getEntityClass(): Class<ReferredTestEntity> = ReferredTestEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ReferredTestEntityData : WorkspaceEntityData<ReferredTestEntity>() {
  lateinit var name: String
  lateinit var file: VirtualFileUrl

  internal fun isNameInitialized(): Boolean = ::name.isInitialized
  internal fun isFileInitialized(): Boolean = ::file.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ReferredTestEntity> {
    val modifiable = ReferredTestEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): ReferredTestEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = ReferredTestEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.util.indexing.testEntities.ReferredTestEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ReferredTestEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return ReferredTestEntity(name, file, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ReferredTestEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.name != other.name) return false
    if (this.file != other.file) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ReferredTestEntityData

    if (this.name != other.name) return false
    if (this.file != other.file) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + file.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + file.hashCode()
    return result
  }
}
