// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.containers.ContainerUtil
import com.intellij.workspaceModel.ide.CustomModuleEntitySource
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.facetMapping
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.mutableFacetMapping
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.findModuleEntity
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableFacetModelBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.*

class ModifiableFacetModelBridgeImpl(private val initialStorage: WorkspaceEntityStorage,
                                     private val diff: WorkspaceEntityStorageDiffBuilder,
                                     val moduleBridge: ModuleBridge,
                                     private val facetManager: FacetManagerBridge)
  : FacetModelBase(), ModifiableFacetModelBridge {
  private val listeners: MutableList<ModifiableFacetModel.Listener> = ContainerUtil.createLockFreeCopyOnWriteList()

  private val moduleEntity: ModuleEntity
    get() = (diff as WorkspaceEntityStorageBuilder).findModuleEntity(moduleBridge) ?: error("Cannot find module entity for '$moduleBridge'")

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
    val facetConfigurationXml = FacetUtil.saveFacetConfiguration(facet)?.let { JDOMUtil.write(it) }
    val underlyingEntity = facet.underlyingFacet?.let { diff.facetMapping().getEntities(it).single() as FacetEntity }
    val facetTypeId = if (facet !is InvalidFacet) facet.type.stringId else facet.configuration.facetState.facetType
    val entity = diff.addFacetEntity(facet.name, facetTypeId, facetConfigurationXml, moduleEntity, underlyingEntity, source)
    diff.mutableFacetMapping().addMapping(entity, facet)
    facet.externalSource = externalSource
    facetsChanged()
  }

  override fun removeFacet(facet: Facet<*>?) {
    if (facet == null) return
    val facetEntity = diff.facetMapping().getEntities(facet).singleOrNull() as? FacetEntity ?: return
    removeFacetEntityWithSubFacets(facetEntity)
    facetsChanged()
  }

  override fun replaceFacet(original: Facet<*>, replacement: Facet<*>) {
    removeFacet(original)
    addFacet(replacement)
  }

  private fun removeFacetEntityWithSubFacets(entity: FacetEntity) {
    if (diff.facetMapping().getDataByEntity(entity) == null) return

    entity.subFacets.forEach {
      removeFacetEntityWithSubFacets(it)
    }
    diff.mutableFacetMapping().removeMapping(entity)
    diff.removeEntity(entity)
  }

  override fun rename(facet: Facet<*>, newName: String) {
    val entity = diff.facetMapping().getEntities(facet).single() as FacetEntity
    val newEntity = diff.modifyEntity(ModifiableFacetEntity::class.java, entity) {
      this.name = newName
    }
    diff.mutableFacetMapping().removeMapping(entity)
    diff.mutableFacetMapping().addMapping(newEntity, facet)
    facetsChanged()
  }

  override fun getNewName(facet: Facet<*>): String {
    val entity = diff.facetMapping().getEntities(facet).single() as FacetEntity
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
      WorkspaceModel.getInstance(moduleBridge.project).updateProjectModel {
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
      val newEntity = diff.modifyEntity(ModifiableFacetEntity::class.java, facetEntity) {
        this.configurationXmlTag = newFacetConfiguration
      }
      changes.add(Triple(facetEntity, newEntity, facet))
    }
    for ((facetEntity, newEntity, facet) in changes) {
      diff.mutableFacetMapping().removeMapping(facetEntity)
      diff.mutableFacetMapping().addMapping(newEntity, facet)
    }
  }

  override fun getAllFacets(): Array<Facet<*>> {
    val facetMapping = diff.facetMapping()
    val facetEntities = moduleEntity.facets
    return facetEntities.mapNotNull { facetMapping.getDataByEntity(it) }.toList().toTypedArray()
  }

  fun getEntity(facet: Facet<*>): FacetEntity? = diff.facetMapping().getEntities(facet).singleOrNull() as FacetEntity?

  override fun isModified(): Boolean {
    return !diff.isEmpty()
  }

  override fun isNewFacet(facet: Facet<*>): Boolean {
    val entity = diff.facetMapping().getEntities(facet).singleOrNull() as FacetEntity?
    return entity != null && initialStorage.resolve(entity.persistentId()) == null
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
