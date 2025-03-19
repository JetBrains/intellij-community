// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalSystem

import com.intellij.java.library.MavenCoordinates
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.service.project.manage.LibraryDataServiceExtension
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.PersistentLibraryKind

class JavaLibraryDataServiceExtension : LibraryDataServiceExtension {
  override fun getLibraryKind(libraryData: LibraryData): PersistentLibraryKind<*>? {
    if (getMavenCoordinates(libraryData) != null) {
      return ImportedLibraryType.IMPORTED_LIBRARY_KIND
    }
    return null
  }

  override fun prepareNewLibrary(libraryData: LibraryData,
                                 libraryModel: Library.ModifiableModel) {
    val properties = (libraryModel as? LibraryEx)?.properties
    val coords = getMavenCoordinates(libraryData)
    if (properties is ImportedLibraryProperties && coords != null) {
      (libraryModel as? LibraryEx.ModifiableModelEx)?.properties = ImportedLibraryProperties(coords)
    }
  }

  private fun getMavenCoordinates(libraryData: LibraryData): MavenCoordinates? {
    val groupId = libraryData.groupId ?: return null
    val artifactId = libraryData.artifactId ?: return null
    val version = libraryData.version ?: return null
    return MavenCoordinates(groupId, artifactId, version)
  }
}