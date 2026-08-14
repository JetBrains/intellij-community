// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.java.impl.dependencySubstitution.impl

import com.intellij.java.impl.dependencySubstitution.ModuleMavenCoordinateEntity
import com.intellij.java.impl.dependencySubstitution.ModuleMavenCoordinateEntityBuilder
import com.intellij.java.library.MavenCoordinates
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
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
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ModuleMavenCoordinateEntityImpl(private val dataSource: ModuleMavenCoordinateEntityData) : ModuleMavenCoordinateEntity,
                                                                                                          WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleEntity::class.java, ModuleMavenCoordinateEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    private val connections = listOf<ConnectionId>(MODULE_CONNECTION_ID)
  }

  override val module: ModuleEntity
    get() = snapshot.instrumentation.getParent(MODULE_CONNECTION_ID, this) as? ModuleEntity
            ?: error("Parent module not found for ModuleMavenCoordinateEntity")
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

  internal class Builder(result: ModuleMavenCoordinateEntityData?) :
    ModifiableWorkspaceEntityBase<ModuleMavenCoordinateEntity, ModuleMavenCoordinateEntityData>(result),
    ModuleMavenCoordinateEntityBuilder {
    internal constructor() : this(ModuleMavenCoordinateEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(MODULE_CONNECTION_ID, this) == null) {
          error("Field ModuleMavenCoordinateEntity#module should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
          error("Field ModuleMavenCoordinateEntity#module should be initialized")
        }
      }
      if (!getEntityData().isCoordinatesInitialized()) {
        error("Field ModuleMavenCoordinateEntity#coordinates should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ModuleMavenCoordinateEntity
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
    override var module: ModuleEntityBuilder
      get() = getParent(MODULE_CONNECTION_ID) as? ModuleEntityBuilder ?: error("module is null for ModuleMavenCoordinateEntity")
      set(value) {
        changeParent(value, MODULE_CONNECTION_ID)
        changedProperty.add("module")
      }
    override var coordinates: MavenCoordinates
      get() = getEntityData().coordinates
      set(value) {
        checkModificationAllowed()
        getEntityData(true).coordinates = value
        changedProperty.add("coordinates")
      }

    override fun getEntityClass(): Class<ModuleMavenCoordinateEntity> = ModuleMavenCoordinateEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ModuleMavenCoordinateEntityData : WorkspaceEntityData<ModuleMavenCoordinateEntity>() {
  lateinit var coordinates: MavenCoordinates
  internal fun isCoordinatesInitialized(): Boolean = ::coordinates.isInitialized
  override fun newInstance(): ModuleMavenCoordinateEntity = ModuleMavenCoordinateEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ModuleMavenCoordinateEntity, *> =
    ModuleMavenCoordinateEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.java.impl.dependencySubstitution.ModuleMavenCoordinateEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ModuleMavenCoordinateEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ModuleMavenCoordinateEntity(coordinates, entitySource) {
      parents.filterIsInstance<ModuleEntityBuilder>().singleOrNull()?.let { this.module = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ModuleEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ModuleMavenCoordinateEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.coordinates != other.coordinates) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ModuleMavenCoordinateEntityData
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
