// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.dependencySubstitution.impl

import com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionEntity
import com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionId
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
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
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class DependencySubstitutionEntityImpl(private val dataSource: DependencySubstitutionEntityData) : DependencySubstitutionEntity, WorkspaceEntityBase(
  dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val symbolicId: DependencySubstitutionId = super.symbolicId

  override val owner: ModuleId
    get() {
      readField("owner")
      return dataSource.owner
    }

  override val library: LibraryId
    get() {
      readField("library")
      return dataSource.library
    }

  override val module: ModuleId
    get() {
      readField("module")
      return dataSource.module
    }

  override val scope: DependencyScope
    get() {
      readField("scope")
      return dataSource.scope
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: DependencySubstitutionEntityData?) : ModifiableWorkspaceEntityBase<DependencySubstitutionEntity, DependencySubstitutionEntityData>(
    result), DependencySubstitutionEntity.Builder {
    internal constructor() : this(DependencySubstitutionEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity DependencySubstitutionEntity is already created in a different builder")
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
      if (!getEntityData().isOwnerInitialized()) {
        error("Field DependencySubstitutionEntity#owner should be initialized")
      }
      if (!getEntityData().isLibraryInitialized()) {
        error("Field DependencySubstitutionEntity#library should be initialized")
      }
      if (!getEntityData().isModuleInitialized()) {
        error("Field DependencySubstitutionEntity#module should be initialized")
      }
      if (!getEntityData().isScopeInitialized()) {
        error("Field DependencySubstitutionEntity#scope should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as DependencySubstitutionEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.owner != dataSource.owner) this.owner = dataSource.owner
      if (this.library != dataSource.library) this.library = dataSource.library
      if (this.module != dataSource.module) this.module = dataSource.module
      if (this.scope != dataSource.scope) this.scope = dataSource.scope
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var owner: ModuleId
      get() = getEntityData().owner
      set(value) {
        checkModificationAllowed()
        getEntityData(true).owner = value
        changedProperty.add("owner")

      }

    override var library: LibraryId
      get() = getEntityData().library
      set(value) {
        checkModificationAllowed()
        getEntityData(true).library = value
        changedProperty.add("library")

      }

    override var module: ModuleId
      get() = getEntityData().module
      set(value) {
        checkModificationAllowed()
        getEntityData(true).module = value
        changedProperty.add("module")

      }

    override var scope: DependencyScope
      get() = getEntityData().scope
      set(value) {
        checkModificationAllowed()
        getEntityData(true).scope = value
        changedProperty.add("scope")

      }

    override fun getEntityClass(): Class<DependencySubstitutionEntity> = DependencySubstitutionEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class DependencySubstitutionEntityData : WorkspaceEntityData<DependencySubstitutionEntity>(), SoftLinkable {
  lateinit var owner: ModuleId
  lateinit var library: LibraryId
  lateinit var module: ModuleId
  lateinit var scope: DependencyScope

  internal fun isOwnerInitialized(): Boolean = ::owner.isInitialized
  internal fun isLibraryInitialized(): Boolean = ::library.isInitialized
  internal fun isModuleInitialized(): Boolean = ::module.isInitialized
  internal fun isScopeInitialized(): Boolean = ::scope.isInitialized

  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    result.add(owner)
    result.add(library)
    result.add(module)
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    index.index(this, owner)
    index.index(this, library)
    index.index(this, module)
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    // TODO verify logic
    val mutablePreviousSet = HashSet(prev)
    val removedItem_owner = mutablePreviousSet.remove(owner)
    if (!removedItem_owner) {
      index.index(this, owner)
    }
    val removedItem_library = mutablePreviousSet.remove(library)
    if (!removedItem_library) {
      index.index(this, library)
    }
    val removedItem_module = mutablePreviousSet.remove(module)
    if (!removedItem_module) {
      index.index(this, module)
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    val owner_data = if (owner == oldLink) {
      changed = true
      newLink as ModuleId
    }
    else {
      null
    }
    if (owner_data != null) {
      owner = owner_data
    }
    val library_data = if (library == oldLink) {
      changed = true
      newLink as LibraryId
    }
    else {
      null
    }
    if (library_data != null) {
      library = library_data
    }
    val module_data = if (module == oldLink) {
      changed = true
      newLink as ModuleId
    }
    else {
      null
    }
    if (module_data != null) {
      module = module_data
    }
    return changed
  }

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<DependencySubstitutionEntity> {
    val modifiable = DependencySubstitutionEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): DependencySubstitutionEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = DependencySubstitutionEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return DependencySubstitutionEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return DependencySubstitutionEntity(owner, library, module, scope, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as DependencySubstitutionEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.owner != other.owner) return false
    if (this.library != other.library) return false
    if (this.module != other.module) return false
    if (this.scope != other.scope) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as DependencySubstitutionEntityData

    if (this.owner != other.owner) return false
    if (this.library != other.library) return false
    if (this.module != other.module) return false
    if (this.scope != other.scope) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + owner.hashCode()
    result = 31 * result + library.hashCode()
    result = 31 * result + module.hashCode()
    result = 31 * result + scope.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + owner.hashCode()
    result = 31 * result + library.hashCode()
    result = 31 * result + module.hashCode()
    result = 31 * result + scope.hashCode()
    return result
  }
}
