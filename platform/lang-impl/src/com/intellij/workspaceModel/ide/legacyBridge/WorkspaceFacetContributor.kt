package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.facet.Facet
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface WorkspaceFacetContributor<T: WorkspaceEntity> {
  val rootEntityType: Class<T>
  fun getFacetName(entity: T): String
  fun getRelatedModuleEntity(entity: T): ModuleEntity
  fun getRootEntityByModuleEntity(moduleEntity: ModuleEntity): T?
  fun createFacetBridgeFromEntity(entity: T, project: Project): Facet<*>

  val childEntityTypes: List<Class<out WorkspaceEntity>>
    get() = emptyList()
  fun getRootEntityByChild(childEntity: WorkspaceEntity): T {
    error("Implementation of the method should be overridden because root entity contains children")
  }

  companion object {
    val EP_NAME: ExtensionPointName<WorkspaceFacetContributor<WorkspaceEntity>> = ExtensionPointName.create("com.intellij.workspaceModel.facetBridgeContributor")
  }
}