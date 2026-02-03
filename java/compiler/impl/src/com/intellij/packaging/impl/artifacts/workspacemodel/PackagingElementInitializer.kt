// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts.workspacemodel

import com.intellij.java.workspace.entities.CompositePackagingElementEntity
import com.intellij.java.workspace.entities.PackagingElementEntity
import com.intellij.openapi.project.Project
import com.intellij.packaging.elements.ElementInitializer
import com.intellij.packaging.elements.PackagingElement
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.platform.workspace.storage.impl.VersionedEntityStorageOnBuilder
import com.intellij.platform.workspace.storage.impl.VersionedEntityStorageOnSnapshot

object PackagingElementInitializer : ElementInitializer {
  override fun initialize(entity: PackagingElementEntity, project: Project, storage: EntityStorage): PackagingElement<*> =
    entity.toElement(project, versionedEntityStorageOnStorage(storage))

  override fun initialize(entity: CompositePackagingElementEntity, project: Project, storage: EntityStorage): PackagingElement<*> =
    entity.toCompositeElement(project, versionedEntityStorageOnStorage(storage))
}

private fun versionedEntityStorageOnStorage(storage: EntityStorage): VersionedEntityStorage {
  return when (storage) {
    is MutableEntityStorage -> VersionedEntityStorageOnBuilder(storage)
    is ImmutableEntityStorage -> VersionedEntityStorageOnSnapshot(storage)
    else -> error("There is no versioned entity storage on ${storage::class}")
  }
}
