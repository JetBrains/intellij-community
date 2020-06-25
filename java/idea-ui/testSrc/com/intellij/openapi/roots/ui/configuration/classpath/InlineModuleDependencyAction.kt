// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.classpath

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.ModuleConfigurationStateImpl
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.testFramework.JavaModuleTestCase
import com.intellij.testFramework.TestActionEvent
import junit.framework.TestCase

class InlineModuleDependencyActionTest : JavaModuleTestCase() {

  override fun isRunInWriteAction(): Boolean = true

  fun `test inline module with module library`() {
    val newCreatedModule = createModule("My Module")

    val moduleLibrary = newCreatedModule.update { it.moduleLibraryTable.createLibrary("My Library") }

    val newModuleOrderEntry = module.update { it.addModuleOrderEntry(newCreatedModule) }

    val inlinedLibrary = module.update { modifiableRootModel ->
      val classPathPanel = setUpClasspathPanel(modifiableRootModel, newModuleOrderEntry)

      val action = InlineModuleDependencyAction(classPathPanel)
      action.actionPerformed(TestActionEvent())

      val orderEntries = modifiableRootModel.moduleLibraryTable.libraries
      TestCase.assertEquals(1, orderEntries.size)
      orderEntries.first()
    }

    TestCase.assertNotSame(moduleLibrary, inlinedLibrary)
    TestCase.assertEquals(moduleLibrary.name, inlinedLibrary.name)
  }

  fun `test inline module with project library`() {
    val newCreatedModule = createModule("My Module")

    val library = LibraryTablesRegistrar.getInstance().getLibraryTable(project).createLibrary("My Library")
    newCreatedModule.update {
      it.addLibraryEntry(library)
    }

    val newModuleOrderEntry = module.update { it.addModuleOrderEntry(newCreatedModule) }

    val inlinedLibrary = module.update { modifiableRootModel ->
      val classPathPanel = setUpClasspathPanel(modifiableRootModel, newModuleOrderEntry)

      val action = InlineModuleDependencyAction(classPathPanel)
      action.actionPerformed(TestActionEvent())

      val orderEntries = modifiableRootModel.orderEntries.filterIsInstance<LibraryOrderEntry>()
      TestCase.assertEquals(1, orderEntries.size)
      orderEntries.first()
    }

    TestCase.assertSame(library, inlinedLibrary.library)
  }

  fun `test inline module with global library`() {
    val newCreatedModule = createModule("My Module")

    val globalLibraryEntry = newCreatedModule.update {
      it.addLibraryEntry(LibraryTablesRegistrar.getInstance().libraryTable.createLibrary("My Library"))
    }

    val newModuleOrderEntry = module.update { it.addModuleOrderEntry(newCreatedModule) }

    val inlinedLibrary = module.update { modifiableRootModel ->
      val classPathPanel = setUpClasspathPanel(modifiableRootModel, newModuleOrderEntry)

      val action = InlineModuleDependencyAction(classPathPanel)
      action.actionPerformed(TestActionEvent())

      val orderEntries = modifiableRootModel.orderEntries.filterIsInstance<LibraryOrderEntry>()
      TestCase.assertEquals(1, orderEntries.size)
      orderEntries.first()
    }

    TestCase.assertSame(globalLibraryEntry.library, inlinedLibrary.library)
  }

  fun `test inline module with submodule`() {
    val newCreatedModule = createModule("My Module")
    val submodule = createModule("My Submodule")

    val submoduleEntry = newCreatedModule.update { it.addModuleOrderEntry(submodule) }

    val moduleOrderEntry = module.update { it.addModuleOrderEntry(newCreatedModule) }

    val inlinedModule = module.update { modifiableRootModel ->
      val classPathPanel = setUpClasspathPanel(modifiableRootModel, moduleOrderEntry)

      val action = InlineModuleDependencyAction(classPathPanel)
      action.actionPerformed(TestActionEvent())

      val orderEntries = modifiableRootModel.orderEntries
      TestCase.assertEquals(3, orderEntries.size)
      orderEntries.filterIsInstance<ModuleOrderEntry>().first()
    }

    TestCase.assertEquals(submoduleEntry.moduleName, inlinedModule.moduleName)
  }

  private fun setUpClasspathPanel(modifiableRootModel: ModifiableRootModel, entryToSelect: ModuleOrderEntry): ClasspathPanelImpl {
    val moduleConfigurationState = object : ModuleConfigurationStateImpl(project, ModulesProvider.EMPTY_MODULES_PROVIDER) {
      override fun getRootModel(): ModifiableRootModel = modifiableRootModel
    }
    return ClasspathPanelImpl(moduleConfigurationState).apply { selectOrderEntry(entryToSelect) }
  }

  private fun <T> Module.update(updater: (ModifiableRootModel) -> T): T {
    var orderCapture: T? = null
    ModuleRootModificationUtil.updateModel(this) {
      orderCapture = updater(it)
    }
    return orderCapture ?: error("Entry not initialized")
  }
}

