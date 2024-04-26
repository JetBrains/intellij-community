// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.facet

import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.openapi.module.Module
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.workspaceModel.ide.legacyBridge.WorkspaceFacetContributor

class FacetEntityContributor: WorkspaceFacetContributor<FacetEntity> {
  override val rootEntityType: Class<FacetEntity>
    get() = FacetEntity::class.java

  override fun getRootEntitiesByModuleEntity(moduleEntity: ModuleEntity): List<FacetEntity> = error("Unsupported operation")

  override fun createFacetFromEntity(entity: FacetEntity, module: Module): Facet<*> {
    error("For this implementation please use method that accepts underlyingFacetBridge")
  }

  fun createFacetFromEntity(entity: FacetEntity, module: Module, underlyingFacet: Facet<*>?): Facet<*> {
    val facetManagerBridge = FacetManager.getInstance(module) as FacetManagerBridge
    return facetManagerBridge.model.createFacet(entity, underlyingFacet)
  }

  override fun getParentModuleEntity(entity: FacetEntity): ModuleEntity = entity.module
}