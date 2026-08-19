// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.openapi.externalSystem.settings.workspaceModel.impl

import com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalProjectBuildClasspathEntity
import com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalProjectsBuildClasspathEntity
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

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ExternalProjectsBuildClasspathEntityImpl(private val dataSource: ExternalProjectsBuildClasspathEntityData) :
  ExternalProjectsBuildClasspathEntity, WorkspaceEntityBase(dataSource) {

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
    return emptyList()
  }

  internal class Builder(result: ExternalProjectsBuildClasspathEntityData?) :
    ModifiableWorkspaceEntityBase<ExternalProjectsBuildClasspathEntity, ExternalProjectsBuildClasspathEntityData>(result),
    ExternalProjectsBuildClasspathEntity.Builder {
    internal constructor() : this(ExternalProjectsBuildClasspathEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isProjectsBuildClasspathInitialized()) {
        error("Field ExternalProjectsBuildClasspathEntity#projectsBuildClasspath should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
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
  override fun newInstance(): ExternalProjectsBuildClasspathEntity = ExternalProjectsBuildClasspathEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ExternalProjectsBuildClasspathEntity, *> =
    ExternalProjectsBuildClasspathEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalProjectsBuildClasspathEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ExternalProjectsBuildClasspathEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ExternalProjectsBuildClasspathEntity(projectsBuildClasspath, entitySource)
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
