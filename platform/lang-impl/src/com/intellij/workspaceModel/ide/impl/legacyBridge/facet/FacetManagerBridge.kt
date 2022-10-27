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
import com.intellij.util.containers.addIfNotNull
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.impl.jps.serialization.CustomFacetRelatedEntitySerializer
import com.intellij.workspaceModel.ide.legacyBridge.FacetBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.legacyBridge.WorkspaceFacetContributor
import com.intellij.workspaceModel.ide.toExternalSource
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.FacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.modifyEntity
import org.jetbrains.jps.model.serialization.facet.FacetState

class FacetManagerBridge(module: Module) : FacetManagerBase() {
  internal val module = module as ModuleBridge
  internal val model = FacetModelBridge(this.module)

  private fun isThisModule(moduleEntity: ModuleEntity) = moduleEntity.name == module.name

  override fun checkConsistency() {
    val entityTypeToFacetContributor = WorkspaceFacetContributor.EP_NAME.extensions.associateBy { it.rootEntityType }
    val facetRelatedEntities = entityTypeToFacetContributor.flatMap { module.entityStorage.current.entities(it.key) }.filter { entity ->
      val facetContributor = entityTypeToFacetContributor[entity.getEntityInterface()]!!
      isThisModule(facetContributor.getParentModuleEntity(entity))
    }.toList()
    model.checkConsistency(facetRelatedEntities, entityTypeToFacetContributor)
  }

  override fun facetConfigurationChanged(facet: Facet<*>) {
    if (facet is FacetBridge<*>) {
      runWriteAction {
        val mutableEntityStorage = module.diff ?: WorkspaceModel.getInstance(module.project).entityStorage.current.toBuilder()
        facet.applyChangesToStorage(mutableEntityStorage, module)
        if (module.diff == null) {
          WorkspaceModel.getInstance(module.project).updateProjectModel("Update facet configuration") { it.addDiff(mutableEntityStorage) }
        }
      }
    } else {
      val facetEntity = model.getEntity(facet)
      if (facetEntity != null) {
        val facetConfigurationXml = FacetUtil.saveFacetConfiguration(facet)?.let { JDOMUtil.write(it) }
        if (facetConfigurationXml != facetEntity.configurationXmlTag) {
          runWriteAction {
            val change: FacetEntity.Builder.() -> Unit = { this.configurationXmlTag = facetConfigurationXml }
            module.diff?.modifyEntity(facetEntity, change) ?: WorkspaceModel.getInstance(module.project)
              .updateProjectModel("Update facet configuration (not bridge)") { it.modifyEntity(facetEntity, change) }
          }
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

open class FacetModelBridge(private val moduleBridge: ModuleBridge) : FacetModelBase() {

  init {
    // Initialize facet bridges after loading from cache
    val moduleEntity = (moduleBridge.diff ?: moduleBridge.entityStorage.current).resolve(moduleBridge.moduleEntityId)
                       ?: error("Module entity should be available")
    val facetTypeToSerializer = CustomFacetRelatedEntitySerializer.EP_NAME.extensionList.associateBy { it.supportedFacetType }
    WorkspaceFacetContributor.EP_NAME.extensions.forEach { facetContributor ->
      if (facetContributor.rootEntityType != FacetEntity::class.java) {
        facetContributor.getRootEntityByModuleEntity(moduleEntity)?.let {
          updateDiffOrStorage{ this.getOrPutDataByEntity(it) { facetContributor.createFacetFromEntity(it, moduleBridge) }}
        }
      } else {
        moduleEntity.facets.filter { !facetTypeToSerializer.containsKey(it.facetType) }.forEach { getOrCreateFacet(it) }
      }
    }
  }

  override fun getAllFacets(): Array<Facet<*>> {
    val moduleEntity = (moduleBridge.diff ?: moduleBridge.entityStorage.current).resolve(moduleBridge.moduleEntityId)
    if (moduleEntity == null) {
      LOG.error("Cannot resolve module entity ${moduleBridge.moduleEntityId}")
      return emptyArray()
    }
    val facetEntities: MutableList<WorkspaceEntity> = mutableListOf()
    facetEntities.addAll(moduleEntity.facets)
    WorkspaceFacetContributor.EP_NAME.extensions.forEach {
      if (it.rootEntityType != FacetEntity::class.java) {
        facetEntities.addIfNotNull(it.getRootEntityByModuleEntity(moduleEntity))
      }
    }
    return facetEntities.mapNotNull { facetMapping().getDataByEntity(it) }.toList().toTypedArray()
  }

  internal fun getOrCreateFacet(entity: FacetEntity): Facet<*> {
    return updateDiffOrStorage { this.getOrPutDataByEntity(entity) { createFacet(entity) } }
  }

  internal fun getFacet(entity: FacetEntity): Facet<*>? = facetMapping().getDataByEntity(entity)

  internal fun getEntity(facet: Facet<*>): FacetEntity? = facetMapping().getEntities(facet).singleOrNull() as? FacetEntity

  internal fun createFacet(entity: FacetEntity): Facet<*> {
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

  fun checkConsistency(facetRelatedEntities: List<WorkspaceEntity>,
                       entityTypeToFacetContributor: Map<Class<WorkspaceEntity>, WorkspaceFacetContributor<WorkspaceEntity>>) {
    val facetEntitiesSet = facetRelatedEntities.toSet()
    for (entity in facetRelatedEntities) {
      val facetContributor = entityTypeToFacetContributor[entity.getEntityInterface()]!!
      val facet = facetMapping().getDataByEntity(entity)
      val facetName = facetContributor.getFacetName(entity)
      if (facet == null) {
        throw IllegalStateException("No facet registered for $entity (name = $facetName)")
      }
      if (facet.name != facetName) {
        throw IllegalStateException("Different name")
      }
      val entityFromMapping = facetMapping().getEntities(facet).single() as FacetEntity
      val moduleEntity = entityFromMapping.module
      val facetsFromStorage = entityTypeToFacetContributor.values.filter { it.rootEntityType != FacetEntity::class.java }
        .mapNotNull { it.getRootEntityByModuleEntity(moduleEntity) }
        .toMutableSet()
      facetsFromStorage.addAll(moduleEntity.facets.toSet())
      if (facetsFromStorage != facetEntitiesSet) {
        throw IllegalStateException("Different set of facets from $entity storage: expected $facetEntitiesSet but was $facetsFromStorage")
      }
    }
    val usedStore = moduleBridge.diff ?: moduleBridge.entityStorage.current
    val resolvedModuleEntity = usedStore.resolve(moduleBridge.moduleEntityId)!!
    val mappedFacets = entityTypeToFacetContributor.values.filter { it.rootEntityType != FacetEntity::class.java }
      .mapNotNull { it.getRootEntityByModuleEntity(resolvedModuleEntity) }
      .toMutableSet()
    mappedFacets.addAll(resolvedModuleEntity.facets.toSet())
    val staleEntity = (mappedFacets - facetRelatedEntities).firstOrNull()
    if (staleEntity != null) {
      val facetContributor = entityTypeToFacetContributor[staleEntity.getEntityInterface()]!!
      val facetName = facetContributor.getFacetName(staleEntity)
      throw IllegalStateException("Stale entity $staleEntity (name = $facetName) in the mapping")
    }
  }

  private fun facetMapping(): ExternalEntityMapping<Facet<*>> {
    return moduleBridge.diff?.facetMapping() ?: moduleBridge.entityStorage.current.facetMapping()
  }

  private inline fun <reified R> updateBuilder(builder: MutableEntityStorage, crossinline updater: MutableExternalEntityMapping<Facet<*>>.() -> R): R {
    return builder.mutableFacetMapping().updater()
  }

  private inline fun <reified R> updateDiffOrStorage(crossinline updater: MutableExternalEntityMapping<Facet<*>>.() -> R): R {
    val diff = moduleBridge.diff

    return if (diff != null) {
      synchronized(diff) {
        diff.mutableFacetMapping().updater()
      }
    }
    else {
      (WorkspaceModel.getInstance(moduleBridge.project) as WorkspaceModelImpl).updateProjectModelSilent("Facet manager update storage") {
        it.mutableFacetMapping().updater()
      }
    }
  }

  companion object {
    private const val FACET_BRIDGE_MAPPING_ID = "intellij.facets.bridge"
    private val LOG = logger<FacetModelBridge>()

    internal fun EntityStorage.facetMapping(): ExternalEntityMapping<Facet<*>> {
      return this.getExternalMapping(FACET_BRIDGE_MAPPING_ID)
    }

    fun MutableEntityStorage.facetMapping(): ExternalEntityMapping<Facet<*>> {
      return this.getExternalMapping(FACET_BRIDGE_MAPPING_ID)
    }

    fun MutableEntityStorage.mutableFacetMapping(): MutableExternalEntityMapping<Facet<*>> {
      return this.getMutableExternalMapping(FACET_BRIDGE_MAPPING_ID)
    }
  }
}
