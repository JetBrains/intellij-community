package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.facet.Facet
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import org.jetbrains.annotations.ApiStatus

/**
 * Originally, [com.intellij.facet.Facet] were made to support custom setting for the module, but this solution is not
 * rather flexible and thanks to the workspace model we have an opportunity to make custom entities describing additional
 * module settings. To have a sort of bridge between new approach with declaring custom module settings and [com.intellij.facet.Facet]
 * several extension points were introduced:
 *  1) [com.intellij.workspaceModel.ide.impl.jps.serialization.CustomFacetRelatedEntitySerializer] to add support custom entity
 *  serialization/deserialization as facet tag.
 *  2) [WorkspaceFacetContributor] to have an option to fire different sorts of event related to the [com.intellij.facet.Facet] during
 *  the changes of your custom entity this extension point should be implemented.
 *
 * If you want to use your custom module setting entity under the hood of your facet you also need to implement
 * [com.intellij.workspaceModel.ide.legacyBridge.FacetBridge] to be properly updated.
 *
 * **N.B. Most of the time you need to implement them all to have a correct support all functionality relying on Facets.**
 *
 * Particularly this extension point was introduced to fire all related to [com.intellij.facet.FacetManagerListener] events.
 * We have a universal listener [com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetEntityChangeListener] which base
 * on changes in your entities and data calculated by this EP can fire all these events for us.
 */
@ApiStatus.Internal
@ApiStatus.OverrideOnly
interface WorkspaceFacetContributor<T: WorkspaceEntity> {
  /**
   * Declare class for the main entity associated with [com.intellij.facet.Facet].
   */
  val rootEntityType: Class<T>

  /**
   * Get the name for the associated with entity [com.intellij.facet.Facet]
   */
  fun getFacetName(entity: T): String

  /**
   * Method return the module to which this entity belongs
   */
  fun getParentModuleEntity(entity: T): ModuleEntity

  /**
   * Method return the entity of type declared in [rootEntityType], associated with this module if any
   */
  fun getRootEntityByModuleEntity(moduleEntity: ModuleEntity): T?

  /**
   * Method for creating [com.intellij.facet.Facet] from the given entity of root type
   */
  fun createFacetFromEntity(entity: T, module: Module): Facet<*>

  /**
   * This field should be overridden if root entity can have children which changes can affect e.g. facet configuration
   * otherwise [com.intellij.facet.FacetManagerListener.facetConfigurationChanged] will not be fired correctly. In the same time
   * [getRootEntityByChild] should be implemented
   */
  val childEntityTypes: List<Class<out WorkspaceEntity>>
    get() = emptyList()

  /**
   * Method for getting entity of root type by child.
   *
   * **This method should be overridden only if the root entity have children([childEntityTypes] returns not null list), otherwise it shouldn't be touched.**
   */
  fun getRootEntityByChild(childEntity: WorkspaceEntity): T {
    error("Implementation of the method should be overridden because root entity contains children")
  }

  companion object {
    val EP_NAME: ExtensionPointName<WorkspaceFacetContributor<WorkspaceEntity>> = ExtensionPointName.create("com.intellij.workspaceModel.facetContributor")
  }
}