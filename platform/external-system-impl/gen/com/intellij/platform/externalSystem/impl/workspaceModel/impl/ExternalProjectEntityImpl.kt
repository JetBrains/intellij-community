// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.workspaceModel.impl

import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityBuilder
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ExternalProjectEntityImpl(private val dataSource: ExternalProjectEntityData) : ExternalProjectEntity,
                                                                                              WorkspaceEntityBase(dataSource) {

  private companion object {

    private val connections = listOf<ConnectionId>()

  }

  override val symbolicId: ExternalProjectEntityId = super.symbolicId

  override val externalProjectPath: String
    get() {
      readField("externalProjectPath")
      return dataSource.externalProjectPath
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: ExternalProjectEntityData?) :
    ModifiableWorkspaceEntityBase<ExternalProjectEntity, ExternalProjectEntityData>(result), ExternalProjectEntityBuilder {
    internal constructor() : this(ExternalProjectEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ExternalProjectEntity is already created in a different builder")
        }
      }
      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
// After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
// Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null
// Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isExternalProjectPathInitialized()) {
        error("Field ExternalProjectEntity#externalProjectPath should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ExternalProjectEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.externalProjectPath != dataSource.externalProjectPath) this.externalProjectPath = dataSource.externalProjectPath
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }
    override var externalProjectPath: String
      get() = getEntityData().externalProjectPath
      set(value) {
        checkModificationAllowed()
        getEntityData(true).externalProjectPath = value
        changedProperty.add("externalProjectPath")
      }

    override fun getEntityClass(): Class<ExternalProjectEntity> = ExternalProjectEntity::class.java
  }

}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ExternalProjectEntityData : WorkspaceEntityData<ExternalProjectEntity>() {
  lateinit var externalProjectPath: String

  internal fun isExternalProjectPathInitialized(): Boolean = ::externalProjectPath.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<ExternalProjectEntity> {
    val modifiable = ExternalProjectEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): ExternalProjectEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = ExternalProjectEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ExternalProjectEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ExternalProjectEntity(externalProjectPath, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ExternalProjectEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.externalProjectPath != other.externalProjectPath) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ExternalProjectEntityData
    if (this.externalProjectPath != other.externalProjectPath) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + externalProjectPath.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + externalProjectPath.hashCode()
    return result
  }
}
