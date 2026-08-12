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
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.indexing.testEntities.IndexingTestEntity2
import com.intellij.util.indexing.testEntities.IndexingTestEntity2Builder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class IndexingTestEntity2Impl(private val dataSource: IndexingTestEntity2Data) : IndexingTestEntity2,
                                                                                          WorkspaceEntityBase(dataSource) {

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
    return emptyList()
  }

  internal class Builder(result: IndexingTestEntity2Data?) :
    ModifiableWorkspaceEntityBase<IndexingTestEntity2, IndexingTestEntity2Data>(result), IndexingTestEntity2Builder {
    internal constructor() : this(IndexingTestEntity2Data())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isRootsInitialized()) {
        error("Field IndexingTestEntity2#roots should be initialized")
      }
      if (!getEntityData().isExcludedRootsInitialized()) {
        error("Field IndexingTestEntity2#excludedRoots should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
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
      dataSource as IndexingTestEntity2
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.roots != dataSource.roots) this.roots = dataSource.roots.toMutableList()
      if (this.excludedRoots != dataSource.excludedRoots) this.excludedRoots = dataSource.excludedRoots.toMutableList()
      updateChildToParentReferences(parents)
    }

    override fun index() {
      index(this, "roots", this.roots)
      index(this, "excludedRoots", this.excludedRoots)
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

    override fun getEntityClass(): Class<IndexingTestEntity2> = IndexingTestEntity2::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class IndexingTestEntity2Data : WorkspaceEntityData<IndexingTestEntity2>() {
  lateinit var roots: MutableList<VirtualFileUrl>
  lateinit var excludedRoots: MutableList<VirtualFileUrl>
  internal fun isRootsInitialized(): Boolean = ::roots.isInitialized
  internal fun isExcludedRootsInitialized(): Boolean = ::excludedRoots.isInitialized
  override fun newInstance(): IndexingTestEntity2 = IndexingTestEntity2Impl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<IndexingTestEntity2, *> = IndexingTestEntity2Impl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.util.indexing.testEntities.IndexingTestEntity2") as EntityMetadata
  }

  override fun clone(): IndexingTestEntity2Data {
    val clonedEntity = super.clone()
    clonedEntity as IndexingTestEntity2Data
    clonedEntity.roots = clonedEntity.roots.toMutableWorkspaceList()
    clonedEntity.excludedRoots = clonedEntity.excludedRoots.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return IndexingTestEntity2::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return IndexingTestEntity2(roots, excludedRoots, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as IndexingTestEntity2Data
    if (this.entitySource != other.entitySource) return false
    if (this.roots != other.roots) return false
    if (this.excludedRoots != other.excludedRoots) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as IndexingTestEntity2Data
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
