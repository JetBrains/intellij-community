// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.builders

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.SdkIndexableFilesIteratorImpl
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge.Companion.findSdk
import com.intellij.workspaceModel.storage.EntityStorage

class SdkIndexableIteratorHandler : IndexableIteratorBuilderHandler {
  override fun accepts(builder: IndexableEntityProvider.IndexableIteratorBuilder): Boolean =
    builder is SdkIteratorBuilder || builder is InheritedSdkIteratorBuilder

  override fun instantiate(builders: Collection<IndexableEntityProvider.IndexableIteratorBuilder>,
                           project: Project,
                           entityStorage: EntityStorage): List<IndexableFilesIterator> {
    val unifiedBuilders = mutableMapOf<Pair<String, String>, Roots>()
    var projectSdkRoots: Roots? = null
    for (builder in builders) {
      if (builder is SdkIteratorBuilder) {
        val key = Pair(builder.sdkName, builder.sdkType)
        val newRoot = builderToRoot(builder)
        unifiedBuilders[key] = unifiedBuilders[key]?.merge(newRoot) ?: newRoot
      }
      else {
        if (projectSdkRoots == null) {
          projectSdkRoots = AllRoots
        }
      }
    }

    if (projectSdkRoots != null) {
      val sdk = ProjectRootManager.getInstance(project).projectSdk
      if (sdk != null) {
        val key = Pair(sdk.name, sdk.sdkType.name)
        unifiedBuilders[key] = unifiedBuilders[key]?.merge(projectSdkRoots) ?: projectSdkRoots
      }
    }

    val result = mutableListOf<IndexableFilesIterator>()
    for (entry in unifiedBuilders.entries) {
      findSdk(entry.key.first, entry.key.second)?.apply {
        result.addAll(entry.value.createIterator(this, project))
      }
    }
    return result
  }

  private fun builderToRoot(builder: SdkIteratorBuilder) =
    builder.root?.let { ListOfRoots(it) } ?: AllRoots

  private sealed interface Roots {
    fun merge(newRoot: Roots): Roots
    fun createIterator(sdk: Sdk, project: Project): Collection<IndexableFilesIterator>
  }

  private object AllRoots : Roots {
    override fun merge(newRoot: Roots): Roots = this
    override fun createIterator(sdk: Sdk, project: Project): Collection<IndexableFilesIterator> =
      IndexableEntityProviderMethods.createIterators(sdk, project)
  }

  private class ListOfRoots() : ArrayList<VirtualFile>(), Roots {
    constructor(file: VirtualFile) : this() {
      add(file)
    }

    override fun merge(newRoot: Roots): Roots {
      when (newRoot) {
        AllRoots -> return newRoot
        is ListOfRoots -> {
          addAll(newRoot)
          return this
        }
      }
    }

    override fun createIterator(sdk: Sdk, project: Project): Collection<IndexableFilesIterator> =
      SdkIndexableFilesIteratorImpl.createIterators(sdk, this, project)
  }
}