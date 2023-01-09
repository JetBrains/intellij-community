// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.facet

import com.intellij.facet.Facet
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.facetMapping
import com.intellij.workspaceModel.ide.legacyBridge.FacetBridge
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity

object FacetBridgeEx {
  private fun <T: WorkspaceEntity> FacetBridge<T>.getFacetEntities(mutableStorage: MutableEntityStorage) =
    mutableStorage.facetMapping().getEntities(this as Facet<*>).map { it as T }

  fun <T: WorkspaceEntity> FacetBridge<T>.getFacetEntity(mutableStorage: MutableEntityStorage) : T {
    return getFacetEntities(mutableStorage).single()
  }

  fun <T: WorkspaceEntity> FacetBridge<T>.getFacetEntityOptional(mutableStorage: MutableEntityStorage) : T? {
    val entity = this.getFacetEntities(mutableStorage).firstOrNull()
    if (null == entity) return null
    return entity
  }
}