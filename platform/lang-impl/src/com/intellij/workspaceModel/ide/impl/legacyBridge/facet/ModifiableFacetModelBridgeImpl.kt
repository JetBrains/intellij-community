// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.facet

import com.intellij.facet.Facet
import com.intellij.facet.ModifiableFacetModel
import com.intellij.facet.impl.FacetModelBase
import com.intellij.facet.impl.FacetUtil
import com.intellij.facet.impl.invalid.InvalidFacet
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.workspaceModel.jps.CustomModuleEntitySource
import com.intellij.platform.workspaceModel.jps.JpsFileEntitySource
import com.intellij.platform.workspaceModel.jps.JpsImportedEntitySource
import com.intellij.platform.workspaceModel.jps.JpsProjectFileEntitySource
import com.intellij.util.containers.ContainerUtil
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.facetMapping
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.mutableFacetMapping
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModuleEntity
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableFacetModelBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.legacyBridge.WorkspaceFacetContributor
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.*
import org.jetbrains.annotations.TestOnly

class ModifiableFacetModelBridgeImpl(private val initialStorage: EntityStorage,
                                     private val diff: MutableEntityStorage,
                                     private val moduleBridge: ModuleBridge,
                                     private val facetManager: FacetManagerBridge)
  : FacetModelBase(), ModifiableFacetModelBridge {
  private val listeners: MutableList<ModifiableFacetModel.Listener> = ContainerUtil.createLockFreeCopyOnWriteList()

  private val moduleEntity: ModuleEntity
    get() = moduleBridge.findModuleEntity(diff) ?: error("Cannot find module entity for '$moduleBridge'")

  override fun addFacet(facet: Facet<*>) {
    addFacet(facet, null)
  }

  override fun addFacet(facet: Facet<*>, externalSource: ProjectModelExternalSource?) {
    val moduleSource = moduleEntity.entitySource
    val source = when {
      moduleSource is JpsFileEntitySource && externalSource != null ->
        JpsImportedEntitySource(moduleSource, externalSource.id, moduleBridge.project.isExternalStorageEnabled)
      moduleSource is JpsImportedEntitySource && externalSource != null && moduleSource.externalSystemId != externalSource.id ->
        JpsImportedEntitySource(moduleSource.internalFile, externalSource.id, moduleBridge.project.isExternalStorageEnabled)
      moduleSource is JpsImportedEntitySource && externalSource == null ->
        moduleSource.internalFile
      moduleSource is CustomModuleEntitySource && externalSource == null ->
        moduleSource.internalSource
      else -> moduleSource
    }
    if (facet is FacetBridge<*>) {
      facet.addToStorage(diff, moduleEntity, source)
    } else {
      val facetConfigurationXml = FacetUtil.saveFacetConfiguration(facet)?.let { JDOMUtil.write(it) }
      val underlyingEntity = facet.underlyingFacet?.let { diff.facetMapping().getEntities(it).single() as FacetEntity }
      val facetTypeId = if (facet !is InvalidFacet) facet.type.stringId else facet.configuration.facetState.facetType
      val entity = diff.addFacetEntity(facet.name, facetTypeId, facetConfigurationXml, moduleEntity, underlyingEntity, source)
      diff.mutableFacetMapping().addMapping(entity, facet)
      facet.externalSource = externalSource
    }
    facetsChanged()
  }

  override fun removeFacet(facet: Facet<*>?) {
    if (facet == null) return
    if (facet is FacetBridge<*>) {
      facet.removeFromStorage(diff)
    } else {
      val facetEntity = diff.facetMapping().getEntities(facet).singleOrNull() as? FacetEntity ?: return
      removeFacetEntityWithSubFacets(facetEntity)
    }
    facetsChanged()
  }

  override fun replaceFacet(original: Facet<*>, replacement: Facet<*>) {
    removeFacet(original)
    addFacet(replacement)
  }

  private fun removeFacetEntityWithSubFacets(entity: FacetEntity) {
    if (diff.facetMapping().getDataByEntity(entity) == null) return

    entity.childrenFacets.forEach {
      removeFacetEntityWithSubFacets(it)
    }
    diff.mutableFacetMapping().removeMapping(entity)
    diff.removeEntity(entity)
  }

  override fun rename(facet: Facet<*>, newName: String) {
    if (facet is FacetBridge<*>) {
      facet.rename(diff, newName)
    } else {
      val entity = diff.facetMapping().getEntities(facet).single() as FacetEntity
      val newEntity = diff.modifyEntity(entity) {
        this.name = newName
      }
      diff.mutableFacetMapping().removeMapping(entity)
      diff.mutableFacetMapping().addMapping(newEntity, facet)
    }
    facetsChanged()
  }

  override fun getNewName(facet: Facet<*>): String {
    val entity = diff.facetMapping().getEntities(facet).single() as ModuleSettingsBase
    return entity.name
  }

  override fun commit() {
    val moduleDiff = moduleBridge.diff
    prepareForCommit()
    facetManager.model.facetsChanged()
    if (moduleDiff != null) {
      moduleDiff.addDiff(diff)
    }
    else {
      WorkspaceModel.getInstance(moduleBridge.project).updateProjectModel("Facet model commit") {
        it.addDiff(diff)
      }
    }
  }

  override fun prepareForCommit() {
    // In some cases configuration for newly added facets changes before the actual commit e.g. MavenProjectImportHandler#configureFacet.
    val changes = ArrayList<Triple<FacetEntity, FacetEntity, Facet<*>>>()
    val mapping = diff.facetMapping()
    moduleEntity.facets.forEach { facetEntity ->
      val facet = mapping.getDataByEntity(facetEntity) ?: return@forEach
      val newFacetConfiguration = FacetUtil.saveFacetConfiguration(facet)?.let { JDOMUtil.write(it) }
      if (facetEntity.configurationXmlTag == newFacetConfiguration) return@forEach
      val newEntity = diff.modifyEntity(facetEntity) {
        this.configurationXmlTag = newFacetConfiguration
      }
      changes.add(Triple(facetEntity, newEntity, facet))
    }
    for ((facetEntity, newEntity, facet) in changes) {
      diff.mutableFacetMapping().removeMapping(facetEntity)
      diff.mutableFacetMapping().addMapping(newEntity, facet)
    }
    val (facetBridges, commonFacets) = allFacets.partition { it is FacetBridge<*> }
      facetBridges.forEach { facet ->
        facet as FacetBridge<*>
        facet.updateInStorage(diff)
      }
    commonFacets.forEach { facet ->
      mapping.getEntities(facet).forEach { facetEntity ->

        // Update external system of existing facets
        facetEntity as FacetEntity
        val facetExternalSource = facet.externalSource
        val newSource = getUpdatedEntitySource(facetExternalSource, facetEntity)
        if (newSource != null) {
          diff.modifyEntity(facetEntity) {
            this.entitySource = newSource
          }
        }
      }
    }
  }

  /**
   * This method returns an updated entity source to have the same external source as [facetExternalSource]
   * It'll return null if no update is required
   */
  private fun getUpdatedEntitySource(facetExternalSource: ProjectModelExternalSource?,
                                     facetEntity: FacetEntity): EntitySource? {
    val entitySource = facetEntity.entitySource
    val newSource = if (facetExternalSource == null) {
      if (entitySource is JpsImportedEntitySource) {
        entitySource.internalFile
      }
      else null
    }
    else {
      if (entitySource !is JpsImportedEntitySource) {
        if (entitySource is JpsProjectFileEntitySource) JpsImportedEntitySource(entitySource, facetExternalSource.id,
                                                                                moduleBridge.project.isExternalStorageEnabled)
        else null
      }
      else {
        if (facetExternalSource.id == entitySource.externalSystemId) null
        else entitySource.copy(externalSystemId = facetExternalSource.id)
      }
    }
    return newSource
  }

  override fun getAllFacets(): Array<Facet<*>> {
    val facetMapping = diff.facetMapping()
    val facetEntities: MutableList<WorkspaceEntity> = mutableListOf()
    facetEntities.addAll(moduleEntity.facets)
    WorkspaceFacetContributor.EP_NAME.extensions.forEach {
      if (it.rootEntityType != FacetEntity::class.java) {
        facetEntities.addAll(it.getRootEntitiesByModuleEntity(moduleEntity))
      }
    }
    return facetEntities.mapNotNull { facetMapping.getDataByEntity(it) }.toList().toTypedArray()
  }

  @TestOnly
  fun getEntity(facet: Facet<*>): FacetEntity? = diff.facetMapping().getEntities(facet).singleOrNull() as FacetEntity?

  override fun isModified(): Boolean {
    return diff.hasChanges()
  }

  override fun isNewFacet(facet: Facet<*>): Boolean {
    val entity = diff.facetMapping().getEntities(facet).singleOrNull()
    if (entity == null) return false
    return if (entity is FacetEntity) {
      entity.symbolicId !in initialStorage
    } else true
  }

  override fun addListener(listener: ModifiableFacetModel.Listener, parentDisposable: Disposable) {
    listeners += listener
    Disposer.register(parentDisposable, Disposable { listeners -= listener })
  }

  override fun facetsChanged() {
    super.facetsChanged()
    listeners.forEach { it.onChanged() }
  }
}
