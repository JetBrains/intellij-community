// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.facet

import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerBase
import com.intellij.facet.impl.FacetEventsPublisher
import com.intellij.facet.impl.FacetUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.jps.serialization.CustomFacetRelatedEntitySerializer
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.facetMapping
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.mutableFacetMapping
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.ide.legacyBridge.FacetBridge
import com.intellij.workspaceModel.ide.legacyBridge.WorkspaceFacetContributor
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.FacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity

class FacetEntityChangeListener(private val project: Project): Disposable {

  private val publisher
    get() = FacetEventsPublisher.getInstance(project)

  fun initializeFacetBridge(changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    WorkspaceFacetContributor.EP_NAME.extensions.forEach { facetBridgeContributor ->

      val facetType = facetBridgeContributor.rootEntityType
      changes[facetType]?.asSequence()?.filterIsInstance<EntityChange.Added<*>>()?.forEach perFacet@ { facetChange ->
        fun createBridge(entity: WorkspaceEntity): Facet<*> {
          val existingFacetBridge = builder.facetMapping().getDataByEntity(entity)
          if (existingFacetBridge != null) return existingFacetBridge

          val moduleEntity = facetBridgeContributor.getParentModuleEntity(entity)
          val module = builder.moduleMap.getDataByEntity(moduleEntity) ?: error("Module bridge should be available")
          val newFacetBridge = if (facetBridgeContributor.rootEntityType == FacetEntity::class.java) {
            val underlyingFacet = (entity as FacetEntity).underlyingFacet?.let { createBridge(it) }
            (facetBridgeContributor as FacetEntityContributor).createFacetFromEntity(entity, module, underlyingFacet)
          } else {
            facetBridgeContributor.createFacetFromEntity(entity, module)
          }
          builder.mutableFacetMapping().addMapping(entity, newFacetBridge)
          return newFacetBridge
        }

        createBridge(facetChange.newEntity)
      }
    }
  }

  init {
    if (!project.isDefault) {
      project.messageBus.connect(this).subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
        override fun beforeChanged(event: VersionedStorageChange) {
          WorkspaceFacetContributor.EP_NAME.extensions.forEach { facetBridgeContributor ->
            processBeforeChangeEvents(event, facetBridgeContributor)
          }
        }

        override fun changed(event: VersionedStorageChange) {
          WorkspaceFacetContributor.EP_NAME.extensions.forEach { facetBridgeContributor ->
            processChangeEvents(event, facetBridgeContributor)
          }
        }
      })
    }
  }

  private fun processBeforeChangeEvents(event: VersionedStorageChange, workspaceFacetContributor: WorkspaceFacetContributor<WorkspaceEntity>) {
    event.getChanges(workspaceFacetContributor.rootEntityType).forEach { change ->
      when (change) {
        is EntityChange.Added -> {
          val existingFacetBridge = event.storageAfter.facetMapping().getDataByEntity(change.entity)
                                    ?: error("Facet bridge should be already initialized")
          publisher.fireBeforeFacetAdded(existingFacetBridge)
        }
        is EntityChange.Removed -> {
          val facet = event.storageBefore.facetMapping().getDataByEntity(change.entity) ?: return@forEach
          publisher.fireBeforeFacetRemoved(facet)
        }
        is EntityChange.Replaced -> {
          if (workspaceFacetContributor.getFacetName(change.oldEntity) != workspaceFacetContributor.getFacetName(change.newEntity)) {
            val facetBridge = event.storageAfter.facetMapping().getDataByEntity(change.newEntity) ?: error("Facet should be available")
            publisher.fireBeforeFacetRenamed(facetBridge)
          }
        }
      }
    }
  }

  private fun processChangeEvents(event: VersionedStorageChange, workspaceFacetContributor: WorkspaceFacetContributor<WorkspaceEntity>) {
    val changedFacets = mutableMapOf<Facet<*>, WorkspaceEntity>()

    event.getChanges(workspaceFacetContributor.rootEntityType).forEach { change ->
      when (change) {
        is EntityChange.Added -> {
          val existingFacetBridge = event.storageAfter.facetMapping().getDataByEntity(change.entity)
                                    ?: error("Facet bridge should be already initialized")
          val moduleEntity = workspaceFacetContributor.getParentModuleEntity(change.newEntity)
          getFacetManager(moduleEntity)?.model?.facetsChanged()

          FacetManagerBase.setFacetName(existingFacetBridge, workspaceFacetContributor.getFacetName(change.entity))
          existingFacetBridge.initFacet()

          // We should not send an event if the associated module was added in the same transaction.
          // Event will be sent with "moduleAdded" event.
          val addedModulesNames = event.getChanges(ModuleEntity::class.java)
            .filterIsInstance<EntityChange.Added<ModuleEntity>>()
            .mapTo(HashSet()) { it.entity.name }
          if (moduleEntity.name !in addedModulesNames) {
            publisher.fireFacetAdded(existingFacetBridge)
          }
        }
        is EntityChange.Removed -> {
          val moduleEntity = workspaceFacetContributor.getParentModuleEntity(change.oldEntity)
          val manager = getFacetManager(moduleEntity) ?: return@forEach
          // Mapping to facet isn't saved in manager.model after addDiff. But you can get an object from the older version of the store
          manager.model.facetsChanged()
          val facet = event.storageBefore.facetMapping().getDataByEntity(change.oldEntity) ?: return@forEach
          Disposer.dispose(facet)
          publisher.fireFacetRemoved(manager.module, facet)
        }
        is EntityChange.Replaced -> {
          val facet = event.storageAfter.facetMapping().getDataByEntity(change.newEntity) ?: error("Facet should be available")
          val moduleEntity = workspaceFacetContributor.getParentModuleEntity(change.newEntity)
          getFacetManager(moduleEntity)?.model?.facetsChanged()
          val newFacetName = workspaceFacetContributor.getFacetName(change.newEntity)
          val oldFacetName = workspaceFacetContributor.getFacetName(change.oldEntity)
          FacetManagerBase.setFacetName(facet, newFacetName)
          changedFacets[facet] = change.newEntity
          if (oldFacetName != newFacetName) {
            publisher.fireFacetRenamed(facet, oldFacetName)
          }
        }
      }
    }

    workspaceFacetContributor.childEntityTypes.forEach { entityType ->
      event.getChanges(entityType).forEach { change ->
        when (change) {
          is EntityChange.Added -> {
            // We shouldn't fire `facetConfigurationChanged` for newly added spring settings
            val newlyAddedFacets = event.getChanges(workspaceFacetContributor.rootEntityType)
              .filterIsInstance<EntityChange.Added<*>>().map { it.newEntity }
            val rootEntity = workspaceFacetContributor.getRootEntityByChild(change.newEntity)
            if (!newlyAddedFacets.contains(rootEntity)) {
              val facet = event.storageAfter.facetMapping().getDataByEntity(rootEntity) ?: error("Facet should be available")
              changedFacets[facet] = rootEntity
            }
          }
          is EntityChange.Removed -> {
            // We shouldn't fire `facetConfigurationChanged` for removed spring settings
            val removedFacets = event.getChanges(workspaceFacetContributor.rootEntityType)
              .filterIsInstance<EntityChange.Removed<*>>().map { it.oldEntity }
            val rootEntity = workspaceFacetContributor.getRootEntityByChild(change.oldEntity)
            if (!removedFacets.contains(rootEntity)) {
              //val moduleEntity = facetBridgeContributor.getRelatedModuleEntity(rootEntity, event.storageAfter)
              val storageAfter = event.storageAfter
              val facet = storageAfter.facetMapping().getDataByEntity(rootEntity) ?: error("Facet should be available")
              val actualModuleEntity = storageAfter.resolve(workspaceFacetContributor.getParentModuleEntity(rootEntity).symbolicId)
                                       ?: error("Module should be available in actual storage")
              val actualRootElement = workspaceFacetContributor.getRootEntityByModuleEntity(actualModuleEntity)!!
              changedFacets[facet] = actualRootElement
            }
          }
          is EntityChange.Replaced -> {
            val rootEntity = workspaceFacetContributor.getRootEntityByChild(change.newEntity)
            val facet = event.storageAfter.facetMapping().getDataByEntity(rootEntity) ?: error("Facet should be available")
            changedFacets[facet] = rootEntity
          }
        }
      }
    }

    val entityTypeToSerializer = CustomFacetRelatedEntitySerializer.EP_NAME.extensions.associateBy { it.rootEntityType }
    changedFacets.forEach { (facet, rootEntity) ->
      val serializer = entityTypeToSerializer[rootEntity.getEntityInterface()] ?: error("Unavailable XML serializer for ${rootEntity.getEntityInterface()}")
      val rootElement = serializer.serializeIntoXml(rootEntity)

      val facetConfigurationElement = if (facet is FacetBridge<*>)
        serializer.serializeIntoXml(facet.getRootEntity())
      else
        FacetUtil.saveFacetConfiguration(facet)
      val facetConfigurationXml = facetConfigurationElement?.let { JDOMUtil.write(it) }
      // If this change is performed in FacetManagerBridge.facetConfigurationChanged,
      // FacetConfiguration is already updated and there is no need to update it again
      if (facetConfigurationXml != JDOMUtil.write(rootElement)) {
        (facet as? FacetBridge<WorkspaceEntity>)?.updateFacetConfiguration(rootEntity) ?:
        FacetUtil.loadFacetConfiguration(facet.configuration, rootElement)
        publisher.fireFacetConfigurationChanged(facet)
      }
    }
  }

  // TODO:: Check
  override fun dispose() { }

  private fun getFacetManager(entity: ModuleEntity): FacetManagerBridge? {
    val module = ModuleManager.getInstance(project).findModuleByName(entity.name) ?: return null
    return FacetManager.getInstance(module) as? FacetManagerBridge
  }

  // TODO: 12.09.2022 Rewrite and extract init bridges from this listener
  companion object {
    fun getInstance(project: Project) = project.service<FacetEntityChangeListener>()
  }
}
