// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl.libraries

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.libraries.*

internal class LibraryKindRegistryImpl private constructor() : LibraryKindRegistry() {
  init {
    //todo[nik] this is temporary workaround for IDEA-98118: we need to initialize all library types to ensure that their kinds are created and registered in LibraryKind.ourAllKinds
    //In order to properly fix the problem we should extract all UI-related methods from LibraryType to a separate class and move LibraryType to intellij.platform.projectModel.impl module
    LibraryType.EP_NAME.extensionList

    LibraryType.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<LibraryType<*>?> {
      override fun extensionAdded(extension: LibraryType<*>, pluginDescriptor: PluginDescriptor) {
        WriteAction.run<RuntimeException> {
          LibraryKind.registerKind(extension.kind)
          processAllLibraries { rememberKind(extension.kind, it) }
        }
      }

      override fun extensionRemoved(extension: LibraryType<*>, pluginDescriptor: PluginDescriptor) {
        LibraryKind.unregisterKind(extension.kind)
        processAllLibraries { forgetKind(extension.kind, it) }
      }
    }, null)
  }

  private fun processAllLibraries(processor: (Library) -> Unit) {
    LibraryTablesRegistrar.getInstance().libraryTable.libraries.forEach(processor)
    for (table in LibraryTablesRegistrar.getInstance().customLibraryTables) {
      table.libraries.forEach(processor)
    }
    for (project in ProjectManager.getInstance().openProjects) {
      LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.forEach(processor)
      for (module in ModuleManager.getInstance(project).modules) {
        for (library in OrderEntryUtil.getModuleLibraries(ModuleRootManager.getInstance(module))) {
          processor(library)
        }
      }
    }
  }

  private fun forgetKind(kind: PersistentLibraryKind<*>, library: Library) {
    if (kind == (library as LibraryEx).kind) {
      val model = library.modifiableModel
      model.forgetKind()
      model.commit()
    }
  }

  private fun rememberKind(kind: PersistentLibraryKind<*>, library: Library) {
    if (((library as LibraryEx).kind as? UnknownLibraryKind)?.kindId == kind.kindId) {
      val model = library.modifiableModel
      model.restoreKind()
      model.commit()
    }
  }
}