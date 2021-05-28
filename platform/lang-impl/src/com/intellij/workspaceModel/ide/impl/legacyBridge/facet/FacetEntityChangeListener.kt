// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.facet

import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerBase
import com.intellij.facet.impl.FacetEventsPublisher
import com.intellij.facet.impl.FacetUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.facetMapping
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.FacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import org.jdom.Element

internal class FacetEntityChangeListener(private val project: Project) {

  fun processBeforeChange(change: EntityChange<FacetEntity>) {
    when (change) {
      is EntityChange.Added -> {
        val manager = getFacetManager(change.entity.module) ?: return
        publisher.fireBeforeFacetAdded(manager.model.getOrCreateFacet(change.entity))
      }
      is EntityChange.Removed -> {
        val manager = getFacetManager(change.entity.module) ?: return
        val facet = manager.model.getFacet(change.entity) ?: return
        publisher.fireBeforeFacetRemoved(facet)
      }
      is EntityChange.Replaced -> {
        val manager = getFacetManager(change.newEntity.module) ?: return
        if (change.newEntity.name != change.oldEntity.name) {
          publisher.fireBeforeFacetRenamed(manager.model.getOrCreateFacet(change.newEntity))
        }
      }
    }
  }

  private val publisher
    get() = FacetEventsPublisher.getInstance(project)

  fun processChange(change: EntityChange<FacetEntity>, storageBefore: WorkspaceEntityStorage, addedModulesNames: Set<String>) {
    when (change) {
      is EntityChange.Added -> {
        val manager = getFacetManager(change.entity.module) ?: return
        val facet = manager.model.getOrCreateFacet(change.entity)
        manager.model.facetsChanged()
        FacetManagerBase.setFacetName(facet, change.entity.name)
        facet.initFacet()

        // We should not send an event if the associated module was added in the same transaction
        // Event will be sent with "moduleAdded" event.
        if (facet.module.name !in addedModulesNames) publisher.fireFacetAdded(facet)
      }
      is EntityChange.Removed -> {
        val manager = getFacetManager(change.entity.module) ?: return

        // Mapping to facet isn't saved in manager.model after addDiff. But you can get an object from the older version of the store
        manager.model.facetsChanged()
        val facet = storageBefore.facetMapping().getDataByEntity(change.entity) ?: return
        Disposer.dispose(facet)
        publisher.fireFacetRemoved(manager.module, facet)
      }
      is EntityChange.Replaced -> {
        val manager = getFacetManager(change.newEntity.module) ?: return
        val facet = manager.model.updateEntity(change.oldEntity, change.newEntity) ?: return
        val newConfigurationXml = change.newEntity.configurationXmlTag
        if (change.oldEntity.configurationXmlTag != newConfigurationXml) {
          val facetConfigurationXml = FacetUtil.saveFacetConfiguration(facet)?.let { JDOMUtil.write(it) }
          //if this change is performed in FacetManagerBridge.facetConfigurationChanged, FacetConfiguration is already updated and there is no need to update it again
          if (facetConfigurationXml != newConfigurationXml) {
            val configurationTag = newConfigurationXml?.let { JDOMUtil.load(it) }
            FacetUtil.loadFacetConfiguration(facet.configuration, configurationTag ?: Element("configuration"))
            publisher.fireFacetConfigurationChanged(facet)
          }
        }
        FacetManagerBase.setFacetName(facet, change.newEntity.name)
        if (change.oldEntity.name != change.newEntity.name) {
          publisher.fireFacetRenamed(facet, change.oldEntity.name)
        }
      }
    }
  }

  private fun getFacetManager(entity: ModuleEntity): FacetManagerBridge? {
    val module = ModuleManager.getInstance(project).findModuleByName(entity.name) ?: return null
    return FacetManager.getInstance(module) as? FacetManagerBridge
  }

  companion object {
    fun getInstance(project: Project) = project.service<FacetEntityChangeListener>()
  }
}
