// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.facet

import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerBase
import com.intellij.facet.impl.FacetEventsPublisher
import com.intellij.facet.impl.FacetUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.jps.serialization.CustomFacetRelatedEntitySerializer
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.facetMapping
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.mutableFacetMapping
import com.intellij.workspaceModel.ide.legacyBridge.FacetBridge
import com.intellij.workspaceModel.ide.legacyBridge.WorkspaceFacetContributor
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity

class FacetEntityChangeListener(private val project: Project): Disposable {
  private val publisher
    get() = FacetEventsPublisher.getInstance(project)

  init {
    if (!project.isDefault) {
      val busConnection = project.messageBus.connect(this)
      WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(busConnection, object : WorkspaceModelChangeListener {
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
          val facetBridge = if (existingFacetBridge == null) {
            val workspaceModel = WorkspaceModel.getInstance(project)
            val moduleEntity = workspaceFacetContributor.getRelatedModuleEntity(change.newEntity)
            val module = ModuleManager.getInstance(project).findModuleByName(moduleEntity.name) ?: error("Module bridge should be available")
            val newFacetBridge = workspaceFacetContributor.createFacetFromEntity(change.newEntity, module)
            workspaceModel.updateProjectModelSilent {
              it.mutableFacetMapping().addMapping(change.newEntity, newFacetBridge)
            }
            newFacetBridge
          } else existingFacetBridge
          publisher.fireBeforeFacetAdded(facetBridge)
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
          val facet = if (existingFacetBridge == null) {
            val workspaceModel = WorkspaceModel.getInstance(project)
            val moduleEntity = workspaceFacetContributor.getRelatedModuleEntity(change.newEntity)
            val module = ModuleManager.getInstance(project).findModuleByName(moduleEntity.name) ?: error("Module bridge should be available")
            val newFacetBridge = workspaceFacetContributor.createFacetFromEntity(change.newEntity, module)
            workspaceModel.updateProjectModelSilent {
              it.mutableFacetMapping().addMapping(change.newEntity, newFacetBridge)
            }
            newFacetBridge
          } else existingFacetBridge
          val moduleEntity = workspaceFacetContributor.getRelatedModuleEntity(change.newEntity)
          getFacetManager(moduleEntity)?.model?.facetsChanged()

          FacetManagerBase.setFacetName(facet, workspaceFacetContributor.getFacetName(change.entity))
          facet.initFacet()

          // We should not send an event if the associated module was added in the same transaction.
          // Event will be sent with "moduleAdded" event.
          val addedModulesNames = event.getChanges(ModuleEntity::class.java)
            .filterIsInstance<EntityChange.Added<ModuleEntity>>()
            .mapTo(HashSet()) { it.entity.name }
          if (moduleEntity.name !in addedModulesNames) {
            publisher.fireFacetAdded(facet)
          }
        }
        is EntityChange.Removed -> {
          val moduleEntity = workspaceFacetContributor.getRelatedModuleEntity(change.oldEntity)
          val manager = getFacetManager(moduleEntity) ?: return@forEach
          // Mapping to facet isn't saved in manager.model after addDiff. But you can get an object from the older version of the store
          manager.model.facetsChanged()
          val facet = event.storageBefore.facetMapping().getDataByEntity(change.oldEntity) ?: return@forEach
          Disposer.dispose(facet)
          publisher.fireFacetRemoved(manager.module, facet)
        }
        is EntityChange.Replaced -> {
          val workspaceModel = WorkspaceModel.getInstance(project)
          val facetToOldEntity = workspaceModel.entityStorage.current.facetMapping().getDataByEntity(change.oldEntity)
          workspaceModel.updateProjectModelSilent {
            if (facetToOldEntity != null) {
              it.mutableFacetMapping().removeMapping(change.oldEntity)
              it.mutableFacetMapping().addMapping(change.newEntity, facetToOldEntity)
            }
          }
          val facet = workspaceModel.entityStorage.current.facetMapping().getDataByEntity(change.newEntity) ?: error("Facet should be available")
          val moduleEntity = workspaceFacetContributor.getRelatedModuleEntity(change.newEntity)
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
              val actualStorageSnapshot = event.storageAfter
              val facet = actualStorageSnapshot.facetMapping().getDataByEntity(rootEntity) ?: error("Facet should be available")
              val actualModuleEntity = actualStorageSnapshot.resolve(workspaceFacetContributor.getRelatedModuleEntity(rootEntity).persistentId)
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
}