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
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditorBase
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.LibraryProjectStructureElement

/**
 * @author nik
 */
class ConvertProjectLibraryToRepositoryLibraryAction(private val librariesConfigurable: BaseLibrariesConfigurable,
                                                     context: StructureConfigurableContext)
  : ConvertToRepositoryLibraryActionBase(context) {

  override fun getSelectedLibrary() = (librariesConfigurable.selectedElement as? LibraryProjectStructureElement)?.library as? LibraryEx

  override fun replaceLibrary(library: Library, configureNewLibrary: (LibraryEditorBase) -> Unit) {
    val name = library.name
    val modifiableModel = librariesConfigurable.modelProvider.modifiableModel

    val usages = context.daemonAnalyzer.getUsages(LibraryProjectStructureElement(context, library))
    modifiableModel.removeLibrary(library)
    val newLibrary = modifiableModel.createLibrary(name, RepositoryLibraryType.getInstance().kind, null)
    usages.forEach { it.replaceElement(LibraryProjectStructureElement(context, newLibrary)) }

    val editor = modifiableModel.getLibraryEditor(newLibrary)
    configureNewLibrary(editor)
    ProjectStructureConfigurable.getInstance(project).selectProjectOrGlobalLibrary(newLibrary, true)
  }
}