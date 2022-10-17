// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.facet

import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.openapi.module.Module
import com.intellij.workspaceModel.ide.legacyBridge.WorkspaceFacetContributor
import com.intellij.workspaceModel.storage.bridgeEntities.FacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity

class FacetEntityContributor: WorkspaceFacetContributor<FacetEntity> {
  override val rootEntityType: Class<FacetEntity>
    get() = FacetEntity::class.java

  override fun getRootEntityByModuleEntity(moduleEntity: ModuleEntity): FacetEntity = error("Unsupported operation")

  override fun createFacetFromEntity(entity: FacetEntity, module: Module): Facet<*> {
    val facetManagerBridge = FacetManager.getInstance(module) as FacetManagerBridge
    return facetManagerBridge.model.createFacet(entity)
  }

  override fun getParentModuleEntity(entity: FacetEntity): ModuleEntity = entity.module

  override fun getFacetName(entity: FacetEntity): String = entity.name
}