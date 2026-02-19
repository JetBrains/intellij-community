// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalSystem

import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.LibraryDataServiceExtension
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.PersistentLibraryKind

public class JavaLibraryDataServiceExtension : LibraryDataServiceExtension {

  override fun getLibraryKind(libraryData: LibraryData): PersistentLibraryKind<*>? {
    if (libraryData.toMavenCoordinates() != null) {
      return ImportedLibraryType.IMPORTED_LIBRARY_KIND
    }
    return null
  }

  override fun prepareLibrary(modelsProvider: IdeModifiableModelsProvider, library: Library, libraryData: LibraryData) {
    modelsProvider.setBridgeLibraryCoordinates(library, libraryData)
    modelsProvider.setLibraryCoordinates(library, libraryData)
  }

  private fun IdeModifiableModelsProvider.setBridgeLibraryCoordinates(library: Library, libraryData: LibraryData) {
    val libraryCoordinates = libraryData.toMavenCoordinates() ?: return
    val libraryModel = getModifiableLibraryModel(library) as LibraryEx.ModifiableModelEx
    if (libraryModel.properties is ImportedLibraryProperties) {
      libraryModel.properties = ImportedLibraryProperties(libraryCoordinates)
    }
  }
}