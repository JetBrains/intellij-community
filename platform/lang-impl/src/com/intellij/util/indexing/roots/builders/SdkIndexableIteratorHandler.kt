// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots.builders

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge.Companion.findSdk
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage

class SdkIndexableIteratorHandler : IndexableIteratorBuilderHandler {
  override fun accepts(builder: IndexableEntityProvider.IndexableIteratorBuilder): Boolean =
    builder is SdkIteratorBuilder || builder is InheritedSdkIteratorBuilder

  override fun instantiate(builders: Collection<IndexableEntityProvider.IndexableIteratorBuilder>,
                           project: Project,
                           entityStorage: WorkspaceEntityStorage): List<IndexableFilesIterator> {
    var hasProjectIterator = false
    val builderSet = builders.toSet()
    val result = mutableListOf<IndexableFilesIterator>()
    builderSet.forEach { builder ->
      if (builder is SdkIteratorBuilder) {
        findSdk(builder.sdkName, builder.sdkType)?.apply { result.addAll(IndexableEntityProviderMethods.createIterators(this)) }

      }
      else {
        if (!hasProjectIterator) {
          val sdk = ProjectRootManager.getInstance(project).projectSdk
          if (sdk != null && !builderSet.contains(SdkIteratorBuilder(sdk.name, sdk.sdkType.name))) { //todo[lene] check here literals
            result.addAll(IndexableEntityProviderMethods.createIterators(sdk))
          }
          hasProjectIterator = true
        }
      }
    }
    return result
  }
}