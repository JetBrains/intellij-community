// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.facet

import com.intellij.facet.Facet
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.facetMapping
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.mutableFacetMapping
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
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
    val settingsEntity = config.initUnderlyingEntity(moduleEntity, entitySource)
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
   * Attaches facet to [module]
   */
  fun attachToModule(mutableStorage: MutableEntityStorage, module: ModuleBridge) {
    val moduleEntity = mutableStorage.resolve(module.moduleEntityId) ?: return

    val existingFacetEntity = getFacetEntityOptional(mutableStorage)

    if (null == existingFacetEntity) return

    config.attachUnderlyingEntityToModule(moduleEntity)
  }

  /**
   * Apply changes from the entity which used under the hood into the builder passed as a parameter
   */
  fun applyChangesToStorage(mutableStorage: MutableEntityStorage) {
    val existingFacetEntity = getFacetEntityOptional(mutableStorage)

    if (null == existingFacetEntity) return

    config.applyChangesToExistingEntityInStorage(existingFacetEntity, mutableStorage)
  }

  /**
   * Update facet configuration base on the data from the related entity
   */
  fun updateFacetConfiguration(relatedEntity: T) {
    config.updateUnderlyingEntity(relatedEntity)
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
   * Initializes this config settings from [moduleEntity] and [entitySource]. Returns an entity to add to the storage
   */
  fun initUnderlyingEntity(moduleEntity: ModuleEntity, entitySource: EntitySource): T

  /**
   * Returns the entity used under the hood
   */
  fun getUnderlyingEntity(): T

  /**
   * Updates this config settings from [diff]
   */
  fun updateUnderlyingEntity(diff: T)

  /**
   * Updates this config name setting
   */
  fun renameUnderlyingEntity(newName: String) {
    (getUnderlyingEntity() as ModuleSettingsBase.Builder<*>).name = newName
  }

  /**
   * Attaches the settings to [moduleEntity] module
   */
  fun attachUnderlyingEntityToModule(moduleEntity: ModuleEntity)

  /**
   * Stores this config settings to [existingFacetEntity] in [mutableStorage]
   */
  fun applyChangesToExistingEntityInStorage(existingFacetEntity: T, mutableStorage: MutableEntityStorage)
}