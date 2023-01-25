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

/**
 * Bridge interface for facet which uses custom module settings entity under the hood
 */
interface FacetBridge<T: ModuleSettingsBase> {
  /**
   * Add root entity which [FacetBridge] uses under the hood, into the storage
   * @param mutableStorage for saving root entity and it's children in it
   * @param moduleEntity corresponds to this [FacetBridge]
   * @param entitySource which should be used for such entities
   */
  fun addToStorage(mutableStorage: MutableEntityStorage, moduleEntity: ModuleEntity, entitySource: EntitySource) {
    val settingsEntity = config.initSettings(moduleEntity, entitySource)
    mutableStorage.addEntity(settingsEntity)
    mutableStorage.mutableFacetMapping().addMapping(settingsEntity, this as Facet<*>)
  }

  /**
   * Removes all associated with this bridge entities from the storage
   */
  fun removeFromStorage(mutableStorage: MutableEntityStorage) {
    mutableStorage.removeEntity(getFacetEntity(mutableStorage))
  }

  /**
   * Rename entity associated with this bridge
   */
  fun rename(mutableStorage: MutableEntityStorage, newName: String)

  /**
   * Apply changes from the entity which used under the hood into the builder passed as a parameter
   */
  fun applyChangesToStorage(mutableStorage: MutableEntityStorage, module: ModuleBridge) {
    val moduleEntity = mutableStorage.resolve(module.moduleEntityId) ?: return

    val existingFacetEntity = getFacetEntityOptional(mutableStorage)

    if (null == existingFacetEntity) return

    config.applyChangesToStorage(mutableStorage, existingFacetEntity, moduleEntity)
  }

  /**
   * Update facet configuration base on the data from the related entity
   */
  fun updateFacetConfiguration(rootEntity: T) {
    config.updateData(rootEntity)
  }

  /**
   * Method returns the entity which is used under the hood of this [FacetBridge]
   */
  fun getRootEntity(): T {
    return config.getFacetEntity()
  }

  private fun getFacetEntities(mutableStorage: MutableEntityStorage) = mutableStorage.facetMapping().getEntities(this as Facet<*>).map { it as T }

  private fun getFacetEntity(mutableStorage: MutableEntityStorage) : T = getFacetEntities(mutableStorage).single()

  fun getFacetEntityOptional(mutableStorage: MutableEntityStorage) : T? = getFacetEntities(mutableStorage).firstOrNull()

  val config: FacetConfigurationBridge<T>
}

interface FacetConfigurationBridge<T: ModuleSettingsBase> {
  fun initSettings(moduleEntity: ModuleEntity, entitySource: EntitySource) : T
  fun applyChangesToStorage(mutableStorage: MutableEntityStorage, existingFacetEntity: T, moduleEntity: ModuleEntity)
  fun getFacetEntity(): T
  fun updateData(rootEntity: T)
  fun rename(newName: String) {
    (getFacetEntity() as ModuleSettingsBase.Builder<*>).name = newName
  }
}