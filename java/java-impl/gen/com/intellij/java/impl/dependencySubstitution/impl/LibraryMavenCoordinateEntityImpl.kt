// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental
package com.intellij.java.impl.dependencySubstitution.impl

import com.intellij.java.impl.dependencySubstitution.LibraryMavenCoordinateEntity
import com.intellij.java.library.MavenCoordinates
import com.intellij.platform.workspace.jps.entities.LibraryEntity
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
import com.intellij.platform.workspace.storage.impl.extractOneToOneParent
import com.intellij.platform.workspace.storage.impl.updateOneToOneParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import org.jetbrains.annotations.ApiStatus.Experimental

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class LibraryMavenCoordinateEntityImpl(private val dataSource: LibraryMavenCoordinateEntityData) : LibraryMavenCoordinateEntity, WorkspaceEntityBase(
  dataSource) {

  private companion object {
    internal val LIBRARY_CONNECTION_ID: ConnectionId = ConnectionId.create(LibraryEntity::class.java,
                                                                           LibraryMavenCoordinateEntity::class.java,
                                                                           ConnectionId.ConnectionType.ONE_TO_ONE, false)

    private val connections = listOf<ConnectionId>(
      LIBRARY_CONNECTION_ID,
    )

  }

  override val library: LibraryEntity
    get() = snapshot.extractOneToOneParent(LIBRARY_CONNECTION_ID, this)!!

  override val coordinates: MavenCoordinates
    get() {
      readField("coordinates")
      return dataSource.coordinates
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: LibraryMavenCoordinateEntityData?) : ModifiableWorkspaceEntityBase<LibraryMavenCoordinateEntity, LibraryMavenCoordinateEntityData>(
    result), LibraryMavenCoordinateEntity.Builder {
    internal constructor() : this(LibraryMavenCoordinateEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity LibraryMavenCoordinateEntity is already created in a different builder")
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
      if (_diff != null) {
        if (_diff.extractOneToOneParent<WorkspaceEntityBase>(LIBRARY_CONNECTION_ID, this) == null) {
          error("Field LibraryMavenCoordinateEntity#library should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, LIBRARY_CONNECTION_ID)] == null) {
          error("Field LibraryMavenCoordinateEntity#library should be initialized")
        }
      }
      if (!getEntityData().isCoordinatesInitialized()) {
        error("Field LibraryMavenCoordinateEntity#coordinates should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as LibraryMavenCoordinateEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.coordinates != dataSource.coordinates) this.coordinates = dataSource.coordinates
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var library: LibraryEntity.Builder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(LIBRARY_CONNECTION_ID, this) as? LibraryEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, LIBRARY_CONNECTION_ID)]!! as LibraryEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, LIBRARY_CONNECTION_ID)]!! as LibraryEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, LIBRARY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneParentOfChild(LIBRARY_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, LIBRARY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, LIBRARY_CONNECTION_ID)] = value
        }
        changedProperty.add("library")
      }

    override var coordinates: MavenCoordinates
      get() = getEntityData().coordinates
      set(value) {
        checkModificationAllowed()
        getEntityData(true).coordinates = value
        changedProperty.add("coordinates")

      }

    override fun getEntityClass(): Class<LibraryMavenCoordinateEntity> = LibraryMavenCoordinateEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class LibraryMavenCoordinateEntityData : WorkspaceEntityData<LibraryMavenCoordinateEntity>() {
  lateinit var coordinates: MavenCoordinates

  internal fun isCoordinatesInitialized(): Boolean = ::coordinates.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<LibraryMavenCoordinateEntity> {
    val modifiable = LibraryMavenCoordinateEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): LibraryMavenCoordinateEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = LibraryMavenCoordinateEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.java.impl.dependencySubstitution.LibraryMavenCoordinateEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return LibraryMavenCoordinateEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return LibraryMavenCoordinateEntity(coordinates, entitySource) {
      parents.filterIsInstance<LibraryEntity.Builder>().singleOrNull()?.let { this.library = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(LibraryEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as LibraryMavenCoordinateEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.coordinates != other.coordinates) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as LibraryMavenCoordinateEntityData

    if (this.coordinates != other.coordinates) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + coordinates.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + coordinates.hashCode()
    return result
  }
}
