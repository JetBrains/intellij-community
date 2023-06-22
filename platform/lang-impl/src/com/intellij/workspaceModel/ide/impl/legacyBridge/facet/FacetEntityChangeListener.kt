// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.facet

import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerBase
import com.intellij.facet.impl.FacetEventsPublisher
import com.intellij.facet.impl.FacetUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.diagnostic.telemetry.helpers.addElapsedTimeMs
import com.intellij.platform.jps.model.diagnostic.JpsMetrics
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.jps.serialization.BaseIdeSerializationContext
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.facetMapping
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.mutableFacetMapping
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.ide.legacyBridge.WorkspaceFacetContributor
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleSettingsBase
import com.intellij.platform.workspace.storage.orderToRemoveReplaceAdd
import io.opentelemetry.api.metrics.Meter
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicLong

@Service(Service.Level.PROJECT)
internal class FacetEntityChangeListener(private val project: Project, coroutineScope: CoroutineScope) {
  private val publisher: FacetEventsPublisher
    get() = FacetEventsPublisher.getInstance(project)

  fun initializeFacetBridge(changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    val start = System.currentTimeMillis()

    for (facetBridgeContributor in WorkspaceFacetContributor.EP_NAME.extensions) {
      val facetType = facetBridgeContributor.rootEntityType
      changes[facetType]?.asSequence()?.filterIsInstance<EntityChange.Added<*>>()?.forEach perFacet@{ facetChange ->
        fun createBridge(entity: ModuleSettingsBase): Facet<*> {
          val existingFacetBridge = builder.facetMapping().getDataByEntity(entity)
          if (existingFacetBridge != null) return existingFacetBridge

          val moduleEntity = facetBridgeContributor.getParentModuleEntity(entity)
          val module = builder.moduleMap.getDataByEntity(moduleEntity) ?: error("Module bridge should be available")
          val newFacetBridge = if (facetBridgeContributor.rootEntityType == FacetEntity::class.java) {
            val underlyingFacet = (entity as FacetEntity).underlyingFacet?.let { createBridge(it) }
            (facetBridgeContributor as FacetEntityContributor).createFacetFromEntity(entity, module, underlyingFacet)
          }
          else {
            facetBridgeContributor.createFacetFromEntity(entity, module)
          }
          builder.mutableFacetMapping().addMapping(entity, newFacetBridge)
          return newFacetBridge
        }

        createBridge(facetChange.newEntity as ModuleSettingsBase)
      }
    }

    initializeFacetBridgeTimeMs.addElapsedTimeMs(start)
  }

  init {
    if (!project.isDefault) {
      project.messageBus.connect(coroutineScope).subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
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

  private fun processBeforeChangeEvents(event: VersionedStorageChange,
                                        workspaceFacetContributor: WorkspaceFacetContributor<ModuleSettingsBase>) {
    val start = System.currentTimeMillis()

    event
      .getChanges(workspaceFacetContributor.rootEntityType)
      // There are no actual implementations of the facet listener that care about the order of fireFacet* events,
      //   but since the listener is not deprecated, it will be better to keep the order of events.
      .orderToRemoveReplaceAdd()
      .forEach { change ->
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
            if (change.oldEntity.name != change.newEntity.name) {
              val facetBridge = event.storageAfter.facetMapping().getDataByEntity(change.newEntity) ?: error("Facet should be available")
              publisher.fireBeforeFacetRenamed(facetBridge)
            }
          }
        }
      }

    processBeforeChangeEventsMs.addElapsedTimeMs(start)
  }

  private fun processChangeEvents(event: VersionedStorageChange, workspaceFacetContributor: WorkspaceFacetContributor<ModuleSettingsBase>) {
    val start = System.currentTimeMillis()
    val changedFacets = mutableMapOf<Facet<*>, ModuleSettingsBase>()

    val addedModulesNames by lazy {
      val result = mutableSetOf<String>()
      for (entityChange in event.getChanges(ModuleEntity::class.java)) {
        if (entityChange is EntityChange.Added) {
          result.add(entityChange.entity.name)
        }
      }
      result
    }

    event.getChanges(workspaceFacetContributor.rootEntityType)
      // There are no actual implementations of the facet listener that care about the order of fireFacet* events,
      //   but since the listener is not deprecated, it will be better to keep the order of events.
      .orderToRemoveReplaceAdd()
      .forEach { change ->
        when (change) {
          is EntityChange.Added -> {
            val existingFacetBridge = event.storageAfter.facetMapping().getDataByEntity(change.entity)
                                      ?: error("Facet bridge should be already initialized")
            val moduleEntity = workspaceFacetContributor.getParentModuleEntity(change.newEntity)
            getFacetManager(moduleEntity)?.model?.facetsChanged()

            FacetManagerBase.setFacetName(existingFacetBridge, change.entity.name)
            existingFacetBridge.initFacet()

            // We should not send an event if the associated module was added in the same transaction.
            // Event will be sent with "moduleAdded" event.
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
            val newFacetName = change.newEntity.name
            val oldFacetName = change.oldEntity.name
            FacetManagerBase.setFacetName(facet, newFacetName)
            changedFacets[facet] = change.newEntity
            if (oldFacetName != newFacetName) {
              publisher.fireFacetRenamed(facet, oldFacetName)
            }
          }
        }
      }

    val newlyAddedFacets by lazy {
      event.getChanges(workspaceFacetContributor.rootEntityType)
        .filterIsInstance<EntityChange.Added<*>>().map { it.newEntity }
    }

    val removedFacets by lazy {
      event.getChanges(workspaceFacetContributor.rootEntityType)
        .filterIsInstance<EntityChange.Removed<*>>().map { it.oldEntity }
    }

    workspaceFacetContributor.childEntityTypes.forEach { entityType ->
      event.getChanges(entityType).orderToRemoveReplaceAdd().forEach { change ->
        when (change) {
          is EntityChange.Added -> {
            // We shouldn't fire `facetConfigurationChanged` for newly added spring settings
            val rootEntity = workspaceFacetContributor.getRootEntityByChild(change.newEntity)
            if (!newlyAddedFacets.contains(rootEntity)) {
              val facet = event.storageAfter.facetMapping().getDataByEntity(rootEntity) ?: error("Facet should be available")
              changedFacets[facet] = rootEntity
            }
          }
          is EntityChange.Removed -> {
            // We shouldn't fire `facetConfigurationChanged` for removed spring settings
            val rootEntity = workspaceFacetContributor.getRootEntityByChild(change.oldEntity)
            if (!removedFacets.contains(rootEntity)) {
              val facet = event.storageBefore.facetMapping().getDataByEntity(rootEntity) ?: error("Facet should be available")
              val actualRootElement = event.storageAfter.facetMapping().getEntities(facet).single()
              changedFacets[facet] = actualRootElement as ModuleSettingsBase
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

    val entityTypeToSerializer = BaseIdeSerializationContext.CUSTOM_FACET_RELATED_ENTITY_SERIALIZER_EP.extensions.associateBy { it.rootEntityType }
    changedFacets.forEach { (facet, rootEntity) ->
      val serializer = entityTypeToSerializer[rootEntity.getEntityInterface()]
                       ?: error("Unavailable XML serializer for ${rootEntity.getEntityInterface()}")
      val rootElement = serializer.serializeIntoXml(rootEntity)

      val facetConfigurationElement = if (facet is FacetBridge<*>)
        serializer.serializeIntoXml(facet.config.getEntity())
      else
        FacetUtil.saveFacetConfiguration(facet)
      val facetConfigurationXml = facetConfigurationElement?.let { JDOMUtil.write(it) }
      // If this change is performed in FacetManagerBridge.facetConfigurationChanged,
      // FacetConfiguration is already updated and there is no need to update it again
      if (facetConfigurationXml != JDOMUtil.write(rootElement)) {
        @Suppress("UNCHECKED_CAST")
        (facet as? FacetBridge<ModuleSettingsBase>)?.updateFacetConfiguration(rootEntity)
        ?: FacetUtil.loadFacetConfiguration(facet.configuration, rootElement)
        publisher.fireFacetConfigurationChanged(facet)
      }
    }

    processChangeEventsMs.addElapsedTimeMs(start)
  }

  private fun getFacetManager(entity: ModuleEntity): FacetManagerBridge? {
    val module = ModuleManager.getInstance(project).findModuleByName(entity.name) ?: return null
    return FacetManager.getInstance(module) as? FacetManagerBridge
  }

  // TODO: 12.09.2022 Rewrite and extract init bridges from this listener
  companion object {
    fun getInstance(project: Project): FacetEntityChangeListener = project.service<FacetEntityChangeListener>()

    private val initializeFacetBridgeTimeMs = AtomicLong()
    private val processBeforeChangeEventsMs = AtomicLong()
    private val processChangeEventsMs = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val initializeFacetBridgeTimeGauge = meter.gaugeBuilder("jps.facet.change.listener.init.bridge.ms")
        .ofLongs().buildObserver()

      val processBeforeChangeEventsTimeGauge = meter.gaugeBuilder("jps.facet.change.listener.before.change.events.ms")
        .ofLongs().buildObserver()

      val processChangeEventsTimeGauge = meter.gaugeBuilder("jps.facet.change.listener.process.change.events.ms")
        .ofLongs().buildObserver()

      meter.batchCallback(
        {
          initializeFacetBridgeTimeGauge.record(initializeFacetBridgeTimeMs.get())
          processBeforeChangeEventsTimeGauge.record(processBeforeChangeEventsMs.get())
          processChangeEventsTimeGauge.record(processChangeEventsMs.get())
        },
        initializeFacetBridgeTimeGauge, processBeforeChangeEventsTimeGauge, processChangeEventsTimeGauge
      )
    }

    init {
      setupOpenTelemetryReporting(JpsMetrics.getInstance().meter)
    }
  }
}
