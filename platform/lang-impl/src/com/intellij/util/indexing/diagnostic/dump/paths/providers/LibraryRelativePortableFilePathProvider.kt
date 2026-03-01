// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dump.paths.providers

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.serialization.impl.LibraryNameGenerator
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath

object LibraryRelativePortableFilePathProvider : PortableFilePathProvider {
  override fun getRelativePortableFilePath(project: Project, virtualFile: VirtualFile): PortableFilePath.RelativePath? {
    val library = ProjectFileIndex.getInstance(project).findContainingLibraries(virtualFile).firstOrNull()
    if (library == null) return null
    val libraryName = LibraryNameGenerator.getLegacyLibraryName(library.symbolicId)
    if (libraryName == null) return null

    val libraryType = when (library.tableId.level) {
      LibraryTablesRegistrar.APPLICATION_LEVEL -> PortableFilePath.LibraryRoot.LibraryType.APPLICATION
      LibraryTablesRegistrar.PROJECT_LEVEL -> PortableFilePath.LibraryRoot.LibraryType.PROJECT
      LibraryTableImplUtil.MODULE_LEVEL -> PortableFilePath.LibraryRoot.LibraryType.MODULE
      else -> return null
    }
    val moduleName = if (libraryType == PortableFilePath.LibraryRoot.LibraryType.MODULE) {
      WorkspaceModel.getInstance(project).currentSnapshot.referrers(library.symbolicId, ModuleEntity::class.java).firstOrNull()?.name
    }
    else {
      null
    }

    for ((rootIndex, libraryRoot) in library.roots.withIndex()) {
      val inClassFiles = libraryRoot.type == LibraryRootTypeId.COMPILED
      val rootFile = libraryRoot.url.virtualFile ?: continue
      val relativePath = VfsUtilCore.getRelativePath(virtualFile, rootFile)
      if (relativePath == null) continue
      return PortableFilePath.RelativePath(
        PortableFilePath.LibraryRoot(libraryType, libraryName, moduleName, rootIndex, inClassFiles),
        relativePath
      )
    }
    return null
  }
}
