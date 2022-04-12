// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PackagingElementProcessing")
package com.intellij.packaging.impl.artifacts

import com.intellij.workspaceModel.storage.bridgeEntities.api.ArtifactEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.CompositePackagingElementEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.FileOrDirectoryPackagingElementEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.PackagingElementEntity

fun processFileOrDirectoryCopyElements(artifact: ArtifactEntity, processor: (FileOrDirectoryPackagingElementEntity) -> Boolean) {
  val rootElement = artifact.rootElement ?: return
  processPackagingElementsRecursively(rootElement) {
    if (it is FileOrDirectoryPackagingElementEntity) {
      return@processPackagingElementsRecursively processor(it)
    }
    true
  }
}

private fun processPackagingElementsRecursively(element: PackagingElementEntity, processor: (PackagingElementEntity) -> Boolean): Boolean {
  if (!processor(element)) return false
  if (element is CompositePackagingElementEntity) {
    element.children.forEach { 
      if (!processPackagingElementsRecursively(it, processor)) {
        return false
      }
    }
  }
  return true
}
