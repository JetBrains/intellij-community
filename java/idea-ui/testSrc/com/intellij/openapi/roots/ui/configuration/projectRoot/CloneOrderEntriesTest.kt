// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.projectRoot

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.testFramework.JavaModuleTestCase
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat

class CloneOrderEntriesTest : JavaModuleTestCase() {
  override fun isRunInWriteAction(): Boolean = true

  fun `test copied jdk and module source`() {
    WriteAction.run<RuntimeException> {
      val copyToModifiableModel = ModuleRootManager.getInstance(createModule("Copy To Module")).modifiableModel
      val originalModifiableModel = ModuleRootManager.getInstance(module).modifiableModel

      val path = originalModifiableModel.module.moduleNioFile

      // Tested methods
      val builder = ModuleStructureConfigurable.CopiedModuleBuilder(originalModifiableModel, path, project)
      builder.setupRootModel(copyToModifiableModel)

      originalModifiableModel.commit()
      copyToModifiableModel.commit()

      val originalOrderEntries = originalModifiableModel.orderEntries
      val copiedOrderEntries = copyToModifiableModel.orderEntries

      assertThat(copiedOrderEntries).hasSize(2)

      val originalSdk = originalOrderEntries.filterIsInstance<JdkOrderEntry>().first()
      val copiedSdk = copiedOrderEntries.filterIsInstance<JdkOrderEntry>().first()

      TestCase.assertEquals(originalSdk.jdk, copiedSdk.jdk)

      val originalModuleSource = originalOrderEntries.filterIsInstance<ModuleSourceOrderEntry>().first()
      val copiedModuleSource = copiedOrderEntries.filterIsInstance<ModuleSourceOrderEntry>().first()

      TestCase.assertEquals(originalModuleSource.presentableName, copiedModuleSource.presentableName)
    }
  }

  fun `test copy module with module library`() {
    WriteAction.run<RuntimeException> {
      val copyToModifiableModel = ModuleRootManager.getInstance(createModule("Copy To Module")).modifiableModel

      val modifiableRootModel = ModuleRootManager.getInstance(module).modifiableModel
      val library = modifiableRootModel.moduleLibraryTable.createLibrary("My Library") as LibraryEx
      val path = modifiableRootModel.module.moduleNioFile

      // Tested methods
      val builder = ModuleStructureConfigurable.CopiedModuleBuilder(modifiableRootModel, path, project)
      builder.setupRootModel(copyToModifiableModel)

      modifiableRootModel.commit()
      copyToModifiableModel.commit()

      val copiedOrderEntries = copyToModifiableModel.moduleLibraryTable.libraries

      TestCase.assertEquals(1, copiedOrderEntries.size)

      TestCase.assertNotSame(library, copiedOrderEntries.first())
      TestCase.assertEquals(library.name, copiedOrderEntries.first().name)
    }
  }

  fun `test copy module with project library`() {
    WriteAction.run<RuntimeException> {
      val projectLibrary = LibraryTablesRegistrar.getInstance().getLibraryTable(project).createLibrary("My Library")
      val copyToModifiableModel = ModuleRootManager.getInstance(createModule("My Module")).modifiableModel
      ModuleRootModificationUtil.updateModel(module) {
        it.addLibraryEntry(projectLibrary)
      }

      val modifiableRootModel = ModuleRootManager.getInstance(module).modifiableModel
      val path = modifiableRootModel.module.moduleNioFile

      // Tested methods
      val builder = ModuleStructureConfigurable.CopiedModuleBuilder(modifiableRootModel, path, project)
      builder.setupRootModel(copyToModifiableModel)

      copyToModifiableModel.commit()
      modifiableRootModel.commit()

      val originalOrderEntries = modifiableRootModel.orderEntries.filterIsInstance<LibraryOrderEntry>()
      val copiedOrderEntries = copyToModifiableModel.orderEntries.filterIsInstance<LibraryOrderEntry>()

      TestCase.assertEquals(1, copiedOrderEntries.size)

      TestCase.assertSame(projectLibrary, copiedOrderEntries.first().library)
      TestCase.assertNotSame(originalOrderEntries.first(), copiedOrderEntries.first())
    }
  }

  fun `test copy module with global library`() {
    WriteAction.run<RuntimeException> {
      val copyToModifiableModel = ModuleRootManager.getInstance(createModule("My Module")).modifiableModel

      val globalLibraryTable = LibraryTablesRegistrar.getInstance().libraryTable
      val globalLibrary = globalLibraryTable.createLibrary("My Library")

      ModuleRootModificationUtil.updateModel(module) {
        it.addLibraryEntry(globalLibrary)
      }

      val modifiableRootModel = ModuleRootManager.getInstance(module).modifiableModel
      val path = modifiableRootModel.module.moduleNioFile

      // Tested methods
      val builder = ModuleStructureConfigurable.CopiedModuleBuilder(modifiableRootModel, path, project)
      builder.setupRootModel(copyToModifiableModel)

      copyToModifiableModel.commit()
      modifiableRootModel.commit()

      val originalOrderEntries = modifiableRootModel.orderEntries.filterIsInstance<LibraryOrderEntry>()
      val copiedOrderEntries = copyToModifiableModel.orderEntries.filterIsInstance<LibraryOrderEntry>()

      TestCase.assertEquals(1, copiedOrderEntries.size)

      TestCase.assertSame(globalLibrary, copiedOrderEntries.first().library)
      TestCase.assertNotSame(originalOrderEntries.first(), copiedOrderEntries.first())

      runWriteAction {
        globalLibraryTable.removeLibrary(globalLibrary)
      }
    }
  }

  fun `test copy module with module`() {
    WriteAction.run<RuntimeException> {
      val mainModule = createModule("Copy To Model")
      val depModule = createModule("My Module")
      val copyToModifiableModel = ModuleRootManager.getInstance(mainModule).modifiableModel

      ModuleRootModificationUtil.updateModel(module) {
        it.addModuleOrderEntry(depModule)
      }

      val modifiableRootModel = ModuleRootManager.getInstance(module).modifiableModel
      val path = modifiableRootModel.module.moduleNioFile

      // Tested methods
      val builder = ModuleStructureConfigurable.CopiedModuleBuilder(modifiableRootModel, path, project)
      builder.setupRootModel(copyToModifiableModel)

      copyToModifiableModel.commit()
      modifiableRootModel.commit()

      val originalOrderEntries = modifiableRootModel.orderEntries
      val copiedOrderEntries = copyToModifiableModel.orderEntries

      TestCase.assertEquals(originalOrderEntries.size, copiedOrderEntries.size)

      val originalModule = originalOrderEntries.filterIsInstance<ModuleOrderEntry>().first()
      val copiedModule = copiedOrderEntries.filterIsInstance<ModuleOrderEntry>().first()

      TestCase.assertNotSame(originalModule, copiedModule)
      TestCase.assertEquals(originalModule.module, copiedModule.module)
      TestCase.assertEquals(originalModule.scope, copiedModule.scope)
    }
  }
}
