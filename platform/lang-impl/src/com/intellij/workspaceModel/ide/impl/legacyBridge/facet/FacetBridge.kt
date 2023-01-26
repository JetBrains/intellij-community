// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.facet

import com.intellij.facet.Facet
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.facetMapping
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.mutableFacetMapping
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleSettingsBase
import org.jetbrains.annotations.ApiStatus

/**
 * Bridge interface for facet which uses custom module settings entity under the hood.
 * Most often, its implementation comes together with a corresponding [FacetConfigurationBridge] implementation.
 */
@ApiStatus.Internal
interface FacetBridge<T : ModuleSettingsBase> {
  /**
   * Add root entity which [FacetBridge] uses under the hood, into the storage
   * @param mutableStorage for saving root entity and it's children in it
   * @param moduleEntity corresponds to this [FacetBridge]
   * @param entitySource which should be used for such entities
   */
  fun addToStorage(mutableStorage: MutableEntityStorage, moduleEntity: ModuleEntity, entitySource: EntitySource) {
    config.init(moduleEntity, entitySource)
    val settingsEntity = config.getEntity()
    mutableStorage.addEntity(settingsEntity)
    mutableStorage.mutableFacetMapping().addMapping(settingsEntity, this as Facet<*>)
  }

  /**
   * Removes all associated with this bridge entities from the storage
   */
  fun removeFromStorage(mutableStorage: MutableEntityStorage) {
    getFacetEntities(mutableStorage).forEach { mutableStorage.removeEntity(it) }
  }

  /**
   * Rename entity associated with this bridge
   */
  fun rename(mutableStorage: MutableEntityStorage, newName: String)

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
  fun getFacetEntityOptional(mutableStorage: MutableEntityStorage): T? = getFacetEntities(mutableStorage).firstOrNull()

  /**
   * Facet configuration
   */
  val config: FacetConfigurationBridge<T>
}

/**
 * Interface for facet bridge configuration.
 * Most often, its implementation comes together with a corresponding [FacetBridge] implementation.
 */
@ApiStatus.Internal
interface FacetConfigurationBridge<T : ModuleSettingsBase> {
  /**
   * Initializes this config settings from [moduleEntity] and [entitySource]
   */
  fun init(moduleEntity: ModuleEntity, entitySource: EntitySource)

  /**
   * Returns the entity holding current configuration
   */
  fun getEntity(): T

  /**
   * Updates this config settings from [diff]
   */
  fun update(diff: T)

  /**
   * Updates this config name setting
   */
  fun rename(newName: String)
}