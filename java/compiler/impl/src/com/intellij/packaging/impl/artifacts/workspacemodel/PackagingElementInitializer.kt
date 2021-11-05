// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts.workspacemodel

import com.intellij.openapi.project.Project
import com.intellij.packaging.elements.ElementInitializer
import com.intellij.packaging.elements.PackagingElement
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.CompositePackagingElementEntity
import com.intellij.workspaceModel.storage.bridgeEntities.PackagingElementEntity
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageOnStorage

object PackagingElementInitializer : ElementInitializer {
  override fun initialize(entity: PackagingElementEntity, project: Project, storage: WorkspaceEntityStorage): PackagingElement<*> =
    entity.toElement(project, VersionedEntityStorageOnStorage(storage))

  override fun initialize(entity: CompositePackagingElementEntity, project: Project, storage: WorkspaceEntityStorage): PackagingElement<*> =
    entity.toCompositeElement(project, VersionedEntityStorageOnStorage(storage))
}
