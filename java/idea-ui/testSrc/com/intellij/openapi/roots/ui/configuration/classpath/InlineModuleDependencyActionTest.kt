// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.classpath

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.ModuleConfigurationStateImpl
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.testFramework.JavaModuleTestCase
import com.intellij.testFramework.TestActionEvent
import junit.framework.TestCase

class InlineModuleDependencyActionTest : JavaModuleTestCase() {

  override fun tearDown() {
    ProjectStructureConfigurable.getInstance(myProject).context.modulesConfigurator.disposeUIResources()
    super.tearDown()
  }

  fun `test inline module with module library`() {
    WriteAction.run<RuntimeException> {
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
  }

  fun `test inline module with project library`() {
    WriteAction.run<RuntimeException> {
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
  }

  fun `test inline module with global library`() {
    WriteAction.run<RuntimeException> {
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

      runWriteAction {
        LibraryTablesRegistrar.getInstance().libraryTable.removeLibrary(globalLibraryEntry.library!!)
      }
    }
  }

  fun `test inline module with submodule`() {
    WriteAction.run<RuntimeException> {
      doTestInlineModuleWithSubModule(DependencyScope.COMPILE)
    }
  }

  fun `test inline module with submodule with runtime scope`() {
    WriteAction.run<RuntimeException> {
      doTestInlineModuleWithSubModule(DependencyScope.RUNTIME)
    }
  }

  private fun doTestInlineModuleWithSubModule(scope: DependencyScope) {
    val newCreatedModule = createModule("My Module")
    val submodule = createModule("My Submodule")

    val submoduleEntry = newCreatedModule.update { it.addModuleOrderEntry(submodule) }

    val moduleOrderEntry = module.update { it.addModuleOrderEntry(newCreatedModule).also { dep -> dep.scope = scope } }

    val inlinedModule = module.update { modifiableRootModel ->
      val classPathPanel = setUpClasspathPanel(modifiableRootModel, moduleOrderEntry)

      val action = InlineModuleDependencyAction(classPathPanel)
      action.actionPerformed(TestActionEvent())

      val orderEntries = modifiableRootModel.orderEntries
      assertEquals(3, orderEntries.size)
      orderEntries.filterIsInstance<ModuleOrderEntry>().first()
    }

    assertEquals(submoduleEntry.moduleName, inlinedModule.moduleName)
    assertEquals(scope, inlinedModule.scope)
  }

  private fun setUpClasspathPanel(modifiableRootModel: ModifiableRootModel, entryToSelect: ModuleOrderEntry): ClasspathPanelImpl {
    val moduleConfigurationState = object : ModuleConfigurationStateImpl(project, ProjectStructureConfigurable.getInstance(myProject).context.modulesConfigurator) {
      override fun getModifiableRootModel(): ModifiableRootModel = modifiableRootModel
      override fun getCurrentRootModel(): ModuleRootModel = modifiableRootModel
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

