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
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.util.indexing.testEntities.DependencyItem
import com.intellij.util.indexing.testEntities.ReferredTestEntityId
import com.intellij.util.indexing.testEntities.WithReferenceTestEntity
import com.intellij.util.indexing.testEntities.WithReferenceTestEntityId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class WithReferenceTestEntityImpl(private val dataSource: WithReferenceTestEntityData) : WithReferenceTestEntity, WorkspaceEntityBase(
  dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val symbolicId: WithReferenceTestEntityId = super.symbolicId

  override val name: String
    get() {
      readField("name")
      return dataSource.name
    }

  override val references: List<DependencyItem>
    get() {
      readField("references")
      return dataSource.references
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: WithReferenceTestEntityData?) : ModifiableWorkspaceEntityBase<WithReferenceTestEntity, WithReferenceTestEntityData>(
    result), WithReferenceTestEntity.Builder {
    internal constructor() : this(WithReferenceTestEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity WithReferenceTestEntity is already created in a different builder")
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
      if (!getEntityData().isNameInitialized()) {
        error("Field WithReferenceTestEntity#name should be initialized")
      }
      if (!getEntityData().isReferencesInitialized()) {
        error("Field WithReferenceTestEntity#references should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_references = getEntityData().references
      if (collection_references is MutableWorkspaceList<*>) {
        collection_references.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as WithReferenceTestEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.references != dataSource.references) this.references = dataSource.references.toMutableList()
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

    private val referencesUpdater: (value: List<DependencyItem>) -> Unit = { value ->

      changedProperty.add("references")
    }
    override var references: MutableList<DependencyItem>
      get() {
        val collection_references = getEntityData().references
        if (collection_references !is MutableWorkspaceList) return collection_references
        if (diff == null || modifiable.get()) {
          collection_references.setModificationUpdateAction(referencesUpdater)
        }
        else {
          collection_references.cleanModificationUpdateAction()
        }
        return collection_references
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).references = value
        referencesUpdater.invoke(value)
      }

    override fun getEntityClass(): Class<WithReferenceTestEntity> = WithReferenceTestEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class WithReferenceTestEntityData : WorkspaceEntityData<WithReferenceTestEntity>(), SoftLinkable {
  lateinit var name: String
  lateinit var references: MutableList<DependencyItem>

  internal fun isNameInitialized(): Boolean = ::name.isInitialized
  internal fun isReferencesInitialized(): Boolean = ::references.isInitialized

  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    for (item in references) {
      result.add(item.reference)
    }
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    for (item in references) {
      index.index(this, item.reference)
    }
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    // TODO verify logic
    val mutablePreviousSet = HashSet(prev)
    for (item in references) {
      val removedItem_item_reference = mutablePreviousSet.remove(item.reference)
      if (!removedItem_item_reference) {
        index.index(this, item.reference)
      }
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    val references_data = references.map {
      val it_reference_data = if (it.reference == oldLink) {
        changed = true
        newLink as ReferredTestEntityId
      }
      else {
        null
      }
      var it_data = it
      if (it_reference_data != null) {
        it_data = it_data.copy(reference = it_reference_data)
      }
      if (it_data != null) {
        it_data
      }
      else {
        it
      }
    }
    if (references_data != null) {
      references = references_data as MutableList<DependencyItem>
    }
    return changed
  }

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<WithReferenceTestEntity> {
    val modifiable = WithReferenceTestEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): WithReferenceTestEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = WithReferenceTestEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.util.indexing.testEntities.WithReferenceTestEntity") as EntityMetadata
  }

  override fun clone(): WithReferenceTestEntityData {
    val clonedEntity = super.clone()
    clonedEntity as WithReferenceTestEntityData
    clonedEntity.references = clonedEntity.references.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return WithReferenceTestEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return WithReferenceTestEntity(name, references, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as WithReferenceTestEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.name != other.name) return false
    if (this.references != other.references) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as WithReferenceTestEntityData

    if (this.name != other.name) return false
    if (this.references != other.references) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + references.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + references.hashCode()
    return result
  }
}
