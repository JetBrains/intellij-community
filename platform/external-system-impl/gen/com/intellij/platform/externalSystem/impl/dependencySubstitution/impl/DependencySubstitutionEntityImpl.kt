// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.dependencySubstitution.impl

import com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionEntity
import com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionEntityBuilder
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToManyParent
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.impl.updateOneToManyParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class DependencySubstitutionEntityImpl(private val dataSource: DependencySubstitutionEntityData) : DependencySubstitutionEntity,
                                                                                                            WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val OWNER_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java,
                                                                         DependencySubstitutionEntity::class.java,
                                                                         ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                         false)
    private val connections = listOf<ConnectionId>(OWNER_CONNECTION_ID)

  }

  override val owner: ModuleEntity
    get() = snapshot.extractOneToManyParent(OWNER_CONNECTION_ID, this)!!
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


  internal class Builder(result: DependencySubstitutionEntityData?) :
    ModifiableWorkspaceEntityBase<DependencySubstitutionEntity, DependencySubstitutionEntityData>(result),
    DependencySubstitutionEntityBuilder {
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
      if (_diff != null) {
        if (_diff.extractOneToManyParent<WorkspaceEntityBase>(OWNER_CONNECTION_ID, this) == null) {
          error("Field DependencySubstitutionEntity#owner should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, OWNER_CONNECTION_ID)] == null) {
          error("Field DependencySubstitutionEntity#owner should be initialized")
        }
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
    override var owner: ModuleEntityBuilder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(OWNER_CONNECTION_ID, this) as? ModuleEntityBuilder)
          ?: (this.entityLinks[EntityLink(false, OWNER_CONNECTION_ID)]!! as ModuleEntityBuilder)
        }
        else {
          this.entityLinks[EntityLink(false, OWNER_CONNECTION_ID)]!! as ModuleEntityBuilder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
// Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, OWNER_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, OWNER_CONNECTION_ID)] = data
          }
// else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(OWNER_CONNECTION_ID, this, value)
        }
        else {
// Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, OWNER_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, OWNER_CONNECTION_ID)] = data
          }
// else you're attaching a new entity to an existing entity that is not modifiable
          this.entityLinks[EntityLink(false, OWNER_CONNECTION_ID)] = value
        }
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
  lateinit var library: LibraryId
  lateinit var module: ModuleId
  lateinit var scope: DependencyScope

  internal fun isLibraryInitialized(): Boolean = ::library.isInitialized
  internal fun isModuleInitialized(): Boolean = ::module.isInitialized
  internal fun isScopeInitialized(): Boolean = ::scope.isInitialized

  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    result.add(library)
    result.add(module)
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    index.index(this, library)
    index.index(this, module)
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
// TODO verify logic
    val mutablePreviousSet = HashSet(prev)
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

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<DependencySubstitutionEntity> {
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
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return DependencySubstitutionEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return DependencySubstitutionEntity(library, module, scope, entitySource) {
      parents.filterIsInstance<ModuleEntityBuilder>().singleOrNull()?.let { this.owner = it }
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
    other as DependencySubstitutionEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.library != other.library) return false
    if (this.module != other.module) return false
    if (this.scope != other.scope) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as DependencySubstitutionEntityData
    if (this.library != other.library) return false
    if (this.module != other.module) return false
    if (this.scope != other.scope) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + library.hashCode()
    result = 31 * result + module.hashCode()
    result = 31 * result + scope.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + library.hashCode()
    result = 31 * result + module.hashCode()
    result = 31 * result + scope.hashCode()
    return result
  }
}
