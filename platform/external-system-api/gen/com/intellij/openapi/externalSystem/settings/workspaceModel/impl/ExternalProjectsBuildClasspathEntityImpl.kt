// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
package com.intellij.openapi.externalSystem.settings.workspaceModel.impl

import com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalProjectBuildClasspathEntity
import com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalProjectsBuildClasspathEntity
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import org.jetbrains.annotations.ApiStatus

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ExternalProjectsBuildClasspathEntityImpl(private val dataSource: ExternalProjectsBuildClasspathEntityData) :
  ExternalProjectsBuildClasspathEntity, WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val projectsBuildClasspath: Map<String, ExternalProjectBuildClasspathEntity>
    get() {
      readField("projectsBuildClasspath")
      return dataSource.projectsBuildClasspath
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: ExternalProjectsBuildClasspathEntityData?) :
    ModifiableWorkspaceEntityBase<ExternalProjectsBuildClasspathEntity, ExternalProjectsBuildClasspathEntityData>(result),
    ExternalProjectsBuildClasspathEntity.Builder {
    internal constructor() : this(ExternalProjectsBuildClasspathEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ExternalProjectsBuildClasspathEntity is already created in a different builder")
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
      if (!getEntityData().isProjectsBuildClasspathInitialized()) {
        error("Field ExternalProjectsBuildClasspathEntity#projectsBuildClasspath should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ExternalProjectsBuildClasspathEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.projectsBuildClasspath != dataSource.projectsBuildClasspath) this.projectsBuildClasspath =
        dataSource.projectsBuildClasspath.toMutableMap()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var projectsBuildClasspath: Map<String, ExternalProjectBuildClasspathEntity>
      get() = getEntityData().projectsBuildClasspath
      set(value) {
        checkModificationAllowed()
        getEntityData(true).projectsBuildClasspath = value
        changedProperty.add("projectsBuildClasspath")
      }

    override fun getEntityClass(): Class<ExternalProjectsBuildClasspathEntity> = ExternalProjectsBuildClasspathEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ExternalProjectsBuildClasspathEntityData : WorkspaceEntityData<ExternalProjectsBuildClasspathEntity>() {
  lateinit var projectsBuildClasspath: Map<String, ExternalProjectBuildClasspathEntity>

  internal fun isProjectsBuildClasspathInitialized(): Boolean = ::projectsBuildClasspath.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ExternalProjectsBuildClasspathEntity> {
    val modifiable = ExternalProjectsBuildClasspathEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): ExternalProjectsBuildClasspathEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = ExternalProjectsBuildClasspathEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalProjectsBuildClasspathEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ExternalProjectsBuildClasspathEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return ExternalProjectsBuildClasspathEntity(projectsBuildClasspath, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ExternalProjectsBuildClasspathEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.projectsBuildClasspath != other.projectsBuildClasspath) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ExternalProjectsBuildClasspathEntityData

    if (this.projectsBuildClasspath != other.projectsBuildClasspath) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + projectsBuildClasspath.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + projectsBuildClasspath.hashCode()
    return result
  }
}
