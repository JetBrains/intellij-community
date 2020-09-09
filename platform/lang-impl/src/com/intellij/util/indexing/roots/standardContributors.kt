// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.util.indexing.IndexableSetContributor
import java.util.*

internal class DefaultProjectIndexableFilesContributor : IndexableFilesContributor {
  override fun getIndexableFiles(project: Project): List<IndexableFilesIterator> {
    val seenLibraries: MutableSet<Library> = HashSet()
    val seenSdks: MutableSet<Sdk> = HashSet()
    val modules = ModuleManager.getInstance(project).sortedModules

    val providers: MutableList<IndexableFilesIterator> = mutableListOf()
    for (module in modules) {
      providers.add(ModuleIndexableFilesIterator(module))

      val orderEntries = ModuleRootManager.getInstance(module).orderEntries
      for (orderEntry in orderEntries) {
        when (orderEntry) {
          is LibraryOrderEntry -> {
            val library = orderEntry.library
            if (library != null && seenLibraries.add(library)) {
              providers.add(LibraryIndexableFilesIterator(library))
            }
          }
          is JdkOrderEntry -> {
            val sdk = orderEntry.jdk
            if (sdk != null && seenSdks.add(sdk)) {
              providers.add(SdkIndexableFilesIterator(sdk))
            }
          }
        }
      }
    }
    return providers
  }
}

internal class AdditionalFilesContributor : IndexableFilesContributor {
  override fun getIndexableFiles(project: Project): List<IndexableFilesIterator> {
    return IndexableSetContributor.EP_NAME.extensionList.map { IndexableSetContributorFilesIterator(it) }
  }
}

internal class AdditionalLibraryRootsContributor : IndexableFilesContributor {
  override fun getIndexableFiles(project: Project): List<IndexableFilesIterator> {
    return AdditionalLibraryRootsProvider.EP_NAME
      .extensionList
      .flatMap { it.getAdditionalProjectLibraries(project) }
      .map { SyntheticLibraryIndexableFilesIterator(it) }
  }
}