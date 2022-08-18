// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.facet

import com.google.common.collect.HashBiMap
import com.intellij.facet.*
import com.intellij.facet.impl.FacetModelBase
import com.intellij.facet.impl.FacetUtil
import com.intellij.facet.impl.invalid.InvalidFacet
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.util.JDOMExternalizable
import com.intellij.openapi.util.JDOMUtil
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.toExternalSource
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.api.FacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.modifyEntity
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
          val change: FacetEntity.Builder.() -> Unit = { this.configurationXmlTag = facetConfigurationXml }
          module.diff?.modifyEntity(facetEntity, change) ?: WorkspaceModel.getInstance(module.project)
            .updateProjectModel { it.modifyEntity(facetEntity, change) }
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

  fun createModifiableModel(diff: MutableEntityStorage): ModifiableFacetModel {
    return ModifiableFacetModelBridgeImpl(module.entityStorage.current, diff, module, this)
  }

  companion object {
    internal fun <F : Facet<C>, C : FacetConfiguration> createFacetFromStateRaw(module: Module, type: FacetType<F, C>,
                                                                                state: FacetState, underlyingFacet: Facet<*>?): F {
      val configuration: C = type.createDefaultConfiguration()
      val config = state.configuration
      FacetUtil.loadFacetConfiguration(configuration, config)
      val name = state.name
      val facet: F = createFacet(module, type, name, configuration, underlyingFacet)
      if (facet is JDOMExternalizable) {
        //todo[nik] remove
        facet.readExternal(config)
      }
      val externalSystemId = state.externalSystemId
      if (externalSystemId != null) {
        facet.externalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(externalSystemId)
      }
      return facet
    }

    internal fun saveFacetConfiguration(facet: Facet<*>): FacetState? {
      val facetState = createFacetState(facet, facet.module.project)
      if (facet !is InvalidFacet) {
        val config = FacetUtil.saveFacetConfiguration(facet) ?: return null
        facetState.configuration = config
      }
      return facetState
    }

    private fun createFacetState(facet: Facet<*>, project: Project): FacetState {
      return if (facet is InvalidFacet) {
        facet.configuration.facetState
      }
      else {
        val facetState = FacetState()
        val externalSource = facet.externalSource
        if (externalSource != null && project.isExternalStorageEnabled) {
          //set this attribute only if such facets will be stored separately, otherwise we will get modified *.iml files
          facetState.externalSystemId = externalSource.id
        }
        facetState.facetType = facet.type.stringId
        facetState.name = facet.name
        facetState
      }
    }
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
    return facetEntities?.mapNotNull { facetMapping().getDataByEntity(it) }?.toList()?.toTypedArray() ?: emptyArray()
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
    val loadedConfiguration = configurationXmlTag?.let { JDOMUtil.load(it) }
    if (loadedConfiguration != null) {
      FacetUtil.loadFacetConfiguration(configuration, loadedConfiguration)
    }
    val facet = facetType.createFacet(moduleBridge, entity.name, configuration, underlyingFacet)
    facet.externalSource = (entity.entitySource as? JpsImportedEntitySource)?.toExternalSource()

    // JDOM facets should be additionally read
    if (facet is JDOMExternalizable && loadedConfiguration != null) {
      facet.readExternal(loadedConfiguration)
    }
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
      val facetsFromStorage = entityFromMapping.module.facets?.toSet() ?: emptySet()
      if (facetsFromStorage != facetEntitiesSet) {
        throw IllegalStateException("Different set of facets from $entity storage: expected $facetEntitiesSet but was $facetsFromStorage")
      }
    }
    val usedStore = (moduleBridge.diff as? MutableEntityStorage) ?: moduleBridge.entityStorage.current
    val mappedFacets = usedStore.resolve(moduleBridge.moduleEntityId)!!.facets?.toSet() ?: emptySet()
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

    internal fun EntityStorage.facetMapping(): ExternalEntityMapping<Facet<*>> {
      return this.getExternalMapping(FACET_BRIDGE_MAPPING_ID)
    }

    internal fun MutableEntityStorage.facetMapping(): ExternalEntityMapping<Facet<*>> {
      return this.getExternalMapping(FACET_BRIDGE_MAPPING_ID)
    }

    internal fun MutableEntityStorage.mutableFacetMapping(): MutableExternalEntityMapping<Facet<*>> {
      return this.getMutableExternalMapping(FACET_BRIDGE_MAPPING_ID)
    }

    private val obj = Any()
  }
}
