// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.facet

import com.google.common.collect.HashBiMap
import com.intellij.facet.*
import com.intellij.facet.impl.FacetModelBase
import com.intellij.facet.impl.FacetUtil
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.util.JDOMUtil
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.toExternalSource
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.FacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModifiableFacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.facets
import org.jetbrains.jps.model.serialization.facet.FacetState

class FacetManagerBridge(module: Module) : FacetManagerBase() {
  internal val module = module as ModuleBridge
  internal val model = FacetModelBridge(this.module)

  private fun isThisModule(moduleEntity: ModuleEntity) = moduleEntity.name == module.name

  override fun checkConsistency() {
    model.checkConsistency(module.entityStorage.current.entities(FacetEntity::class.java).filter { isThisModule(it.module) }.toList())
  }

  override fun facetConfigurationChanged(facet: Facet<*>) {
    val facetEntity = model.getEntity(facet)
    if (facetEntity != null) {
      val facetConfigurationXml = FacetUtil.saveFacetConfiguration(facet)?.let { JDOMUtil.write(it) }
      if (facetConfigurationXml != facetEntity.configurationXmlTag) {
        runWriteAction {
          val change: ModifiableFacetEntity.() -> Unit = { this.configurationXmlTag = facetConfigurationXml }
          module.diff?.modifyEntity(ModifiableFacetEntity::class.java, facetEntity, change) ?: WorkspaceModel.getInstance(module.project)
            .updateProjectModel { it.modifyEntity(ModifiableFacetEntity::class.java, facetEntity, change) }
        }
      }
    }
    super.facetConfigurationChanged(facet)
  }

  override fun getModel(): FacetModel = model
  override fun getModule(): Module = module
  override fun createModifiableModel(): ModifiableFacetModel {
    return createModifiableModel(module.entityStorage.current.toBuilder())
  }

  fun createModifiableModel(diff: WorkspaceEntityStorageBuilder): ModifiableFacetModel {
    return ModifiableFacetModelBridgeImpl(module.entityStorage.current, diff, module, this)
  }

}

internal open class FacetModelBridge(protected val moduleBridge: ModuleBridge) : FacetModelBase() {

  init {
    val existingEntities = moduleBridge.entityStorage.current.entities(FacetEntity::class.java)
      .filter { it.moduleId == moduleBridge.moduleEntityId }
    for (facetEntity in existingEntities) {
      getOrCreateFacet(facetEntity)
    }
  }

  override fun getAllFacets(): Array<Facet<*>> {
    val moduleEntity = moduleBridge.entityStorage.current.resolve(moduleBridge.moduleEntityId)
    if (moduleEntity == null) {
      LOG.error("Cannot resolve module entity ${moduleBridge.moduleEntityId}")
      return emptyArray()
    }
    val facetEntities = moduleEntity.facets
    return facetEntities.mapNotNull { facetMapping().getDataByEntity(it) }.toList().toTypedArray()
  }

  internal fun getOrCreateFacet(entity: FacetEntity): Facet<*> {
    return updateDiffOrStorage { this.getOrPutDataByEntity(entity) { createFacet(entity) } }
  }

  internal fun getFacet(entity: FacetEntity): Facet<*>? = facetMapping().getDataByEntity(entity)

  internal fun getEntity(facet: Facet<*>): FacetEntity? = facetMapping().getEntities(facet).singleOrNull() as? FacetEntity

  private fun createFacet(entity: FacetEntity): Facet<*> {
    val registry = FacetTypeRegistry.getInstance()
    val facetType = registry.findFacetType(entity.facetType)
    val underlyingFacet = entity.underlyingFacet?.let { getOrCreateFacet(it) }
    if (facetType == null) {
      return FacetManagerBase.createInvalidFacet(moduleBridge, FacetState().apply {
        name = entity.name
        setFacetType(entity.facetType)
        configuration = entity.configurationXmlTag?.let { JDOMUtil.load(it) }
      }, underlyingFacet, ProjectBundle.message("error.message.unknown.facet.type.0", entity.facetType), true, true)
    }

    val configuration = facetType.createDefaultConfiguration()
    val configurationXmlTag = entity.configurationXmlTag
    if (configurationXmlTag != null) {
      FacetUtil.loadFacetConfiguration(configuration, JDOMUtil.load(configurationXmlTag))
    }
    val facet = facetType.createFacet(moduleBridge, entity.name, configuration, underlyingFacet)
    FacetManagerImpl.setExternalSource(facet, (entity.entitySource as? JpsImportedEntitySource)?.toExternalSource())
    return facet
  }

  fun populateFrom(mapping: HashBiMap<FacetEntity, Facet<*>>) {
    updateDiffOrStorage {
      for ((entity, facet) in mapping) {
        this.addMapping(entity, facet)
      }
    }
    facetsChanged()
  }

  public override fun facetsChanged() {
    super.facetsChanged()
  }

  fun updateEntity(oldEntity: FacetEntity, newEntity: FacetEntity): Facet<*>? {
    val oldFacet = updateDiffOrStorage {
      this.removeMapping(oldEntity)
    }
    var updatedFacet: Facet<*>? = null
    if (oldFacet != null) {
      updatedFacet = updateDiffOrStorage {
        this.addMapping(newEntity, oldFacet)
        oldFacet
      }
    }
    facetsChanged()
    return updatedFacet
  }

  fun checkConsistency(facetEntities: List<FacetEntity>) {
    val facetEntitiesSet = facetEntities.toSet()
    for (entity in facetEntities) {
      val facet = facetMapping().getDataByEntity(entity)
      if (facet == null) {
        throw IllegalStateException("No facet registered for $entity (name = ${entity.name})")
      }
      if (facet.name != entity.name) {
        throw IllegalStateException("Different name")
      }
      val entityFromMapping = facetMapping().getEntities(facet).single() as FacetEntity
      val facetsFromStorage = entityFromMapping.module.facets.toSet()
      if (facetsFromStorage != facetEntitiesSet) {
        throw IllegalStateException("Different set of facets from $entity storage: expected $facetEntitiesSet but was $facetsFromStorage")
      }
    }
    val usedStore = (moduleBridge.diff as? WorkspaceEntityStorageBuilder) ?: moduleBridge.entityStorage.current
    val mappedFacets = usedStore.resolve(moduleBridge.moduleEntityId)!!.facets.toSet()
    val staleEntity = (mappedFacets - facetEntities).firstOrNull()
    if (staleEntity != null) {
      throw IllegalStateException("Stale entity $staleEntity (name = ${staleEntity.name}) in the mapping")
    }
  }

  private fun facetMapping(): ExternalEntityMapping<Facet<*>> {
    return moduleBridge.diff?.facetMapping() ?: moduleBridge.entityStorage.current.facetMapping()
  }

  private inline fun <reified R> updateDiffOrStorage(crossinline updater: MutableExternalEntityMapping<Facet<*>>.() -> R): R {
    val diff = moduleBridge.diff

    return if (diff != null) {
      diff.mutableFacetMapping().updater()
    }
    else {
      synchronized(obj) {
        WorkspaceModel.getInstance(moduleBridge.project).updateProjectModelSilent {
          it.mutableFacetMapping().updater()
        }
      }
    }
  }

  companion object {
    private const val FACET_BRIDGE_MAPPING_ID = "intellij.facets.bridge"
    private val LOG = logger<FacetModelBridge>()

    internal fun WorkspaceEntityStorage.facetMapping(): ExternalEntityMapping<Facet<*>> {
      return this.getExternalMapping(FACET_BRIDGE_MAPPING_ID)
    }

    internal fun WorkspaceEntityStorageDiffBuilder.facetMapping(): ExternalEntityMapping<Facet<*>> {
      return this.getExternalMapping(FACET_BRIDGE_MAPPING_ID)
    }

    internal fun WorkspaceEntityStorageDiffBuilder.mutableFacetMapping(): MutableExternalEntityMapping<Facet<*>> {
      return this.getMutableExternalMapping(FACET_BRIDGE_MAPPING_ID)
    }

    private val obj = Any()
  }
}
