// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.ide.analysisignore.impl

import com.intellij.ide.analysisignore.AnalysisIgnoreEntity
import com.intellij.ide.analysisignore.AnalysisIgnoreEntityBuilder
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
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class AnalysisIgnoreEntityImpl(private val dataSource: AnalysisIgnoreEntityData) : AnalysisIgnoreEntity,
                                                                                            WorkspaceEntityBase(dataSource) {

  override val baseDir: VirtualFileUrl
    get() {
      readField("baseDir")
      return dataSource.baseDir
    }
  override val patterns: List<String>
    get() {
      readField("patterns")
      return dataSource.patterns
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return emptyList()
  }

  internal class Builder(result: AnalysisIgnoreEntityData?) :
    ModifiableWorkspaceEntityBase<AnalysisIgnoreEntity, AnalysisIgnoreEntityData>(result), AnalysisIgnoreEntityBuilder {
    internal constructor() : this(AnalysisIgnoreEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isBaseDirInitialized()) {
        error("Field AnalysisIgnoreEntity#baseDir should be initialized")
      }
      if (!getEntityData().isPatternsInitialized()) {
        error("Field AnalysisIgnoreEntity#patterns should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
    }

    override fun afterModification() {
      val collection_patterns = getEntityData().patterns
      if (collection_patterns is MutableWorkspaceList<*>) {
        collection_patterns.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as AnalysisIgnoreEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.baseDir != dataSource.baseDir) this.baseDir = dataSource.baseDir
      if (this.patterns != dataSource.patterns) this.patterns = dataSource.patterns.toMutableList()
      updateChildToParentReferences(parents)
    }

    override fun index() {
      index(this, "baseDir", this.baseDir)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var baseDir: VirtualFileUrl
      get() = getEntityData().baseDir
      set(value) {
        checkModificationAllowed()
        getEntityData(true).baseDir = value
        changedProperty.add("baseDir")
        val _diff = diff
        if (_diff != null) index(this, "baseDir", value)
      }
    private val patternsUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("patterns")
    }
    override var patterns: MutableList<String>
      get() {
        val collection_patterns = getEntityData().patterns
        if (collection_patterns !is MutableWorkspaceList) return collection_patterns
        if (diff == null || modifiable.get()) {
          collection_patterns.setModificationUpdateAction(patternsUpdater)
        }
        else {
          collection_patterns.cleanModificationUpdateAction()
        }
        return collection_patterns
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).patterns = value
        patternsUpdater.invoke(value)
      }

    override fun getEntityClass(): Class<AnalysisIgnoreEntity> = AnalysisIgnoreEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class AnalysisIgnoreEntityData : WorkspaceEntityData<AnalysisIgnoreEntity>() {
  lateinit var baseDir: VirtualFileUrl
  lateinit var patterns: MutableList<String>
  internal fun isBaseDirInitialized(): Boolean = ::baseDir.isInitialized
  internal fun isPatternsInitialized(): Boolean = ::patterns.isInitialized
  override fun newInstance(): AnalysisIgnoreEntity = AnalysisIgnoreEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<AnalysisIgnoreEntity, *> = AnalysisIgnoreEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.ide.analysisignore.AnalysisIgnoreEntity") as EntityMetadata
  }

  override fun clone(): AnalysisIgnoreEntityData {
    val clonedEntity = super.clone()
    clonedEntity as AnalysisIgnoreEntityData
    clonedEntity.patterns = clonedEntity.patterns.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return AnalysisIgnoreEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return AnalysisIgnoreEntity(baseDir, patterns, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as AnalysisIgnoreEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.baseDir != other.baseDir) return false
    if (this.patterns != other.patterns) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as AnalysisIgnoreEntityData
    if (this.baseDir != other.baseDir) return false
    if (this.patterns != other.patterns) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + baseDir.hashCode()
    result = 31 * result + patterns.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + baseDir.hashCode()
    result = 31 * result + patterns.hashCode()
    return result
  }
}
