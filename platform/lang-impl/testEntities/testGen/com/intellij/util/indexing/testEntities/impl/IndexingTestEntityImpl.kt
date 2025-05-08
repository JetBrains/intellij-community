// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.testEntities.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.indexing.testEntities.IndexingTestEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class IndexingTestEntityImpl(private val dataSource: IndexingTestEntityData) : IndexingTestEntity,
                                                                                        WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val roots: List<VirtualFileUrl>
    get() {
      readField("roots")
      return dataSource.roots
    }

  override val excludedRoots: List<VirtualFileUrl>
    get() {
      readField("excludedRoots")
      return dataSource.excludedRoots
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: IndexingTestEntityData?) :
    ModifiableWorkspaceEntityBase<IndexingTestEntity, IndexingTestEntityData>(result), IndexingTestEntity.Builder {
    internal constructor() : this(IndexingTestEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity IndexingTestEntity is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      index(this, "roots", this.roots)
      index(this, "excludedRoots", this.excludedRoots)
      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isRootsInitialized()) {
        error("Field IndexingTestEntity#roots should be initialized")
      }
      if (!getEntityData().isExcludedRootsInitialized()) {
        error("Field IndexingTestEntity#excludedRoots should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_roots = getEntityData().roots
      if (collection_roots is MutableWorkspaceList<*>) {
        collection_roots.cleanModificationUpdateAction()
      }
      val collection_excludedRoots = getEntityData().excludedRoots
      if (collection_excludedRoots is MutableWorkspaceList<*>) {
        collection_excludedRoots.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as IndexingTestEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.roots != dataSource.roots) this.roots = dataSource.roots.toMutableList()
      if (this.excludedRoots != dataSource.excludedRoots) this.excludedRoots = dataSource.excludedRoots.toMutableList()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    private val rootsUpdater: (value: List<VirtualFileUrl>) -> Unit = { value ->
      val _diff = diff
      if (_diff != null) index(this, "roots", value)
      changedProperty.add("roots")
    }
    override var roots: MutableList<VirtualFileUrl>
      get() {
        val collection_roots = getEntityData().roots
        if (collection_roots !is MutableWorkspaceList) return collection_roots
        if (diff == null || modifiable.get()) {
          collection_roots.setModificationUpdateAction(rootsUpdater)
        }
        else {
          collection_roots.cleanModificationUpdateAction()
        }
        return collection_roots
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).roots = value
        rootsUpdater.invoke(value)
      }

    private val excludedRootsUpdater: (value: List<VirtualFileUrl>) -> Unit = { value ->
      val _diff = diff
      if (_diff != null) index(this, "excludedRoots", value)
      changedProperty.add("excludedRoots")
    }
    override var excludedRoots: MutableList<VirtualFileUrl>
      get() {
        val collection_excludedRoots = getEntityData().excludedRoots
        if (collection_excludedRoots !is MutableWorkspaceList) return collection_excludedRoots
        if (diff == null || modifiable.get()) {
          collection_excludedRoots.setModificationUpdateAction(excludedRootsUpdater)
        }
        else {
          collection_excludedRoots.cleanModificationUpdateAction()
        }
        return collection_excludedRoots
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).excludedRoots = value
        excludedRootsUpdater.invoke(value)
      }

    override fun getEntityClass(): Class<IndexingTestEntity> = IndexingTestEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class IndexingTestEntityData : WorkspaceEntityData<IndexingTestEntity>() {
  lateinit var roots: MutableList<VirtualFileUrl>
  lateinit var excludedRoots: MutableList<VirtualFileUrl>

  internal fun isRootsInitialized(): Boolean = ::roots.isInitialized
  internal fun isExcludedRootsInitialized(): Boolean = ::excludedRoots.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<IndexingTestEntity> {
    val modifiable = IndexingTestEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): IndexingTestEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = IndexingTestEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.util.indexing.testEntities.IndexingTestEntity") as EntityMetadata
  }

  override fun clone(): IndexingTestEntityData {
    val clonedEntity = super.clone()
    clonedEntity as IndexingTestEntityData
    clonedEntity.roots = clonedEntity.roots.toMutableWorkspaceList()
    clonedEntity.excludedRoots = clonedEntity.excludedRoots.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return IndexingTestEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return IndexingTestEntity(roots, excludedRoots, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as IndexingTestEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.roots != other.roots) return false
    if (this.excludedRoots != other.excludedRoots) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as IndexingTestEntityData

    if (this.roots != other.roots) return false
    if (this.excludedRoots != other.excludedRoots) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + roots.hashCode()
    result = 31 * result + excludedRoots.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + roots.hashCode()
    result = 31 * result + excludedRoots.hashCode()
    return result
  }
}
