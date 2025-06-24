// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalSystem

import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.LibraryDataServiceExtension
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.PersistentLibraryKind

class JavaLibraryDataServiceExtension : LibraryDataServiceExtension {
  override fun getLibraryKind(libraryData: LibraryData): PersistentLibraryKind<*>? {
    if (libraryData.toMavenCoordinates() != null) {
      return ImportedLibraryType.IMPORTED_LIBRARY_KIND
    }
    return null
  }

  override fun prepareNewLibrary(modelsProvider: IdeModifiableModelsProvider, library: Library, libraryData: LibraryData) {
    val libraryModel = modelsProvider.getModifiableLibraryModel(library)
    val properties = (libraryModel as? LibraryEx)?.properties
    val coords = libraryData.toMavenCoordinates()
    if (properties is ImportedLibraryProperties && coords != null) {
      (libraryModel as? LibraryEx.ModifiableModelEx)?.properties = ImportedLibraryProperties(coords)
    }

    modelsProvider.setLibraryCoordinates(library, libraryData)
  }
}