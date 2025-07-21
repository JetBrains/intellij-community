// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalSystem

import com.intellij.java.JavaBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryType
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon
import javax.swing.JComponent

public class ImportedLibraryType : LibraryType<ImportedLibraryProperties>(IMPORTED_LIBRARY_KIND) {

  // not supposed to be created manually from project structure dialog
  override fun getCreateActionName(): @NlsContexts.Label String? = null

  // not supposed to be created manually from project structure dialog
  override fun createNewLibrary(parentComponent: JComponent,
                                contextDirectory: VirtualFile?,
                                project: Project): NewLibraryConfiguration? = null

  override fun createPropertiesEditor(component: LibraryEditorComponent<ImportedLibraryProperties>): LibraryPropertiesEditor? {
    return null
  }

  override fun getIcon(properties: ImportedLibraryProperties?): Icon? = null

  override fun getDescription(properties: ImportedLibraryProperties): String {
    val gav = properties.mavenCoordinates
    if (gav == null) {
      return JavaBundle.message("unknown.library")
    } else {
      return "${gav.groupId}:${gav.artifactId}:${gav.version}"
    }
  }

  public companion object {
    public val IMPORTED_LIBRARY_KIND: PersistentLibraryKind<ImportedLibraryProperties> =
      object : PersistentLibraryKind<ImportedLibraryProperties>("java-imported") {
      override fun createDefaultProperties(): ImportedLibraryProperties {
        return ImportedLibraryProperties(null)
      }
    }
  }
}