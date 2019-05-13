/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration.projectRoot

import com.intellij.jarRepository.RepositoryLibraryType
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.ui.configuration.classpath.ClasspathPanel
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditorBase

/**
 * @author nik
 */
class ConvertModuleLibraryToRepositoryLibraryAction(private val classpathPanel: ClasspathPanel,
                                                    context: StructureConfigurableContext)
  : ConvertToRepositoryLibraryActionBase(context) {

  override fun getSelectedLibrary(): LibraryEx? {
    val entry = classpathPanel.selectedEntry as? LibraryOrderEntry
    if (entry == null || !entry.isModuleLevel) return null
    return entry.library as? LibraryEx
  }

  override fun replaceLibrary(library: Library, configureNewLibrary: (LibraryEditorBase) -> Unit) {
    val name = library.name
    val modifiableModel = classpathPanel.getModifiableModelProvider(LibraryTableImplUtil.MODULE_LEVEL).modifiableModel
    val newLibrary = modifiableModel.createLibrary(name, RepositoryLibraryType.getInstance().kind, null)
    OrderEntryUtil.replaceLibraryEntryByAdded(classpathPanel.rootModel, classpathPanel.rootModel.findLibraryOrderEntry(library)!!)

    val editor = ExistingLibraryEditor(newLibrary, null)
    configureNewLibrary(editor)
    editor.commit()
  }
}