// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.facet

import com.intellij.facet.Facet
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleSettingsBase
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.facetMapping
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.mutableFacetMapping
import org.jetbrains.annotations.ApiStatus

/**
 * Bridge interface for facet which uses custom module settings entity under the hood.
 * Most often, its implementation comes together with a corresponding [FacetConfigurationBridge] implementation.
 */
@ApiStatus.Internal
interface FacetBridge<T : ModuleSettingsBase> {
  /**
   * Facet configuration
   */
  val config: FacetConfigurationBridge<T>

  /**
   * Add root entity which [FacetBridge] uses under the hood, to [mutableStorage]
   * @param mutableStorage for saving root entity and it's children in it
   * @param moduleEntity corresponds to this [FacetBridge]
   * @param entitySource which should be used for such entities
   */
  fun addToStorage(mutableStorage: MutableEntityStorage, moduleEntity: ModuleEntity, entitySource: EntitySource) {
    config.init(moduleEntity, entitySource)
    val settingsEntity = config.getEntity(moduleEntity)
    mutableStorage.addEntity(settingsEntity)
    mutableStorage.mutableFacetMapping().addMapping(settingsEntity, this as Facet<*>)
  }

  /**
   * Removes all associated with this bridge entities from [mutableStorage]
   */
  fun removeFromStorage(mutableStorage: MutableEntityStorage) {
    getFacetEntities(mutableStorage).forEach { mutableStorage.removeEntity(it) }
  }

  /**
   * Applies changes from the underlying entity to [mutableStorage]
   */
  fun updateInStorage(mutableStorage: MutableEntityStorage) {
    val existingFacetEntity = getFacetEntityOptional(mutableStorage) ?: return
    updateExistingEntityInStorage(existingFacetEntity, mutableStorage)
  }

  /**
   * Applies changes from the underlying entity to [existingFacetEntity] in [mutableStorage]
   */
  fun updateExistingEntityInStorage(existingFacetEntity: T, mutableStorage: MutableEntityStorage)

  /**
   * Rename entity associated with this bridge
   */
  fun rename(mutableStorage: MutableEntityStorage, newName: String) {
    config.rename(newName)
    val existingFacetEntity = getFacetEntityOptional(mutableStorage) ?: return
    updateExistingEntityInStorage(existingFacetEntity, mutableStorage)
  }

  /**
   * Update facet configuration base on the data from the related entity
   */
  fun updateFacetConfiguration(relatedEntity: T) {
    config.update(relatedEntity)
  }

  private fun getFacetEntities(mutableStorage: MutableEntityStorage) = mutableStorage.facetMapping().getEntities(
    this as Facet<*>).map { it as T }

  /**
   * Returns the entity associated with this bridge in [mutableStorage], if it exists
   */
  private fun getFacetEntityOptional(mutableStorage: MutableEntityStorage): T? = getFacetEntities(mutableStorage).firstOrNull()
}

/**
 * Interface for facet bridge configuration.
 * Most often, its implementation comes together with a corresponding [FacetBridge] implementation.
 */
@ApiStatus.Internal
interface FacetConfigurationBridge<T : ModuleSettingsBase> {
  /**
   * Initializes this config settings with [moduleEntity] and [entitySource]
   */
  fun init(moduleEntity: ModuleEntity, entitySource: EntitySource)

  /**
   * Updates this config settings from [diffEntity]
   */
  fun update(diffEntity: T)

  /**
   * Updates this config name setting
   */
  fun rename(newName: String)

  /**
   * Returns the entity holding current configuration
   */
  fun getEntity(moduleEntity: ModuleEntity): T
}