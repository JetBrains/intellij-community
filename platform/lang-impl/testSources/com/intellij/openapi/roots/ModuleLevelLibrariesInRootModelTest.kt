// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.impl.ModifiableModelCommitter
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.workspaceModel.ide.impl.legacyBridge.RootConfigurationAccessorForWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
class ModuleLevelLibrariesInRootModelTest {
  @JvmField
  @RegisterExtension
  val projectModel = ProjectModelExtension()

  lateinit var module: Module

  @BeforeEach
  fun setUp() {
    module = projectModel.createModule()
  }

  @Test
  fun `add edit remove unnamed module library`() {
    run {
      val model = createModifiableModel(module)
      val library = model.moduleLibraryTable.createLibrary() as LibraryEx
      assertThat(library.presentableName).isEqualTo("Empty Library")
      assertThat(model.moduleLibraryTable.libraries.single()).isEqualTo(library)
      val libraryEntry = getSingleLibraryOrderEntry(model)
      assertThat(libraryEntry.ownerModule).isEqualTo(module)
      assertThat(libraryEntry.isModuleLevel).isTrue()
      assertThat(libraryEntry.libraryName).isNull()
      assertThat(libraryEntry.presentableName).isEqualTo("Empty Library")
      assertThat(libraryEntry.library).isEqualTo(library)
      assertThat(libraryEntry.scope).isEqualTo(DependencyScope.COMPILE)
      assertThat(libraryEntry.isExported).isFalse()
      assertThat(libraryEntry.isSynthetic).isTrue()
      assertThat(libraryEntry.isValid).isTrue()
      assertThat(libraryEntry.libraryLevel).isEqualTo(LibraryTableImplUtil.MODULE_LEVEL)
      assertThat(model.findLibraryOrderEntry(library)).isEqualTo(libraryEntry)
      assertThat(library.isDisposed).isFalse()
      assertThat(library.module).isEqualTo(module)

      val committed = commitModifiableRootModel(model)
      val committedEntry = getSingleLibraryOrderEntry(committed)
      assertThat(committedEntry.scope).isEqualTo(DependencyScope.COMPILE)
      assertThat(committedEntry.isExported).isFalse()
      assertThat(committedEntry.isModuleLevel).isTrue()
      assertThat(committedEntry.libraryName).isNull()
      assertThat(committedEntry.libraryLevel).isEqualTo(LibraryTableImplUtil.MODULE_LEVEL)
      assertThat((committedEntry.library as LibraryEx).module).isEqualTo(module)
      assertThat((committedEntry.library as LibraryEx).isDisposed).isFalse()
    }

    val root = projectModel.baseProjectDir.newVirtualDirectory("lib")
    run {
      val model = createModifiableModel(module)
      val library = model.moduleLibraryTable.libraries.single()
      val libraryModel = library.modifiableModel
      libraryModel.addRoot(root, OrderRootType.CLASSES)
      val libraryEntryForUncommitted = getSingleLibraryOrderEntry(model)
      assertThat(libraryEntryForUncommitted.getRootFiles(OrderRootType.CLASSES)).isEmpty()
      libraryModel.commit()
      assertThat(library.presentableName).isEqualTo("lib")
      val libraryEntry = getSingleLibraryOrderEntry(model)
      assertThat(libraryEntry.getRootFiles(OrderRootType.CLASSES).single()).isEqualTo(root)
      assertThat(libraryEntry.presentableName).isEqualTo(root.presentableUrl)
      libraryEntry.scope = DependencyScope.RUNTIME
      libraryEntry.isExported = true
      assertThat(model.findLibraryOrderEntry(library)).isEqualTo(libraryEntry)
      val committed = commitModifiableRootModel(model)
      val committedEntry = getSingleLibraryOrderEntry(committed)
      assertThat(committedEntry.getRootFiles(OrderRootType.CLASSES).single()).isEqualTo(root)
      assertThat((committedEntry.library as LibraryEx).isDisposed).isFalse()
      assertThat(committedEntry.scope).isEqualTo(DependencyScope.RUNTIME)
      assertThat(committedEntry.isExported).isTrue()
    }

    run {
      val model = createModifiableModel(module)
      val library = model.moduleLibraryTable.libraries.single() as LibraryEx
      model.moduleLibraryTable.removeLibrary(library)
      assertThat(model.moduleLibraryTable.libraries).isEmpty()
      assertThat(model.orderEntries).hasSize(1)
      assertThat(model.findLibraryOrderEntry(library)).isNull()
      assertThat(library.isDisposed).isTrue()
      val committed = commitModifiableRootModel(model)
      assertThat(committed.orderEntries).hasSize(1)
    }
  }

  @Test
  fun `add rename remove module library`() {
    run {
      val model = createModifiableModel(module)
      val library = model.moduleLibraryTable.createLibrary("foo") as LibraryEx
      assertThat(library.isDisposed).isFalse()
      assertThat(model.moduleLibraryTable.libraries.single()).isEqualTo(library)
      val libraryEntry = getSingleLibraryOrderEntry(model)
      assertThat(libraryEntry.libraryName).isEqualTo("foo")
      assertThat(libraryEntry.presentableName).isEqualTo("foo")
      assertThat(libraryEntry.library).isEqualTo(library)

      val committed = commitModifiableRootModel(model)
      val committedEntry = getSingleLibraryOrderEntry(committed)
      assertThat(committedEntry.libraryName).isEqualTo("foo")
      assertThat((committedEntry.library as LibraryEx).isDisposed).isFalse()
    }

    run {
      val model = createModifiableModel(module)
      val library = model.moduleLibraryTable.libraries.single()
      val libraryModel = library.modifiableModel
      libraryModel.name = "bar"
      libraryModel.commit()
      val libraryEntry = getSingleLibraryOrderEntry(model)
      assertThat(libraryEntry.libraryName).isEqualTo("bar")
      val committed = commitModifiableRootModel(model)
      val committedEntry = getSingleLibraryOrderEntry(committed)
      assertThat(committedEntry.libraryName).isEqualTo("bar")
    }

    run {
      val model = createModifiableModel(module)
      val libraryEntry = getSingleLibraryOrderEntry(model)
      val library = libraryEntry.library as LibraryEx
      model.removeOrderEntry(libraryEntry)
      assertThat(library.isDisposed).isTrue()
      assertThat(model.moduleLibraryTable.libraries).isEmpty()
      assertThat(model.orderEntries).hasSize(1)
      val committed = commitModifiableRootModel(model)
      assertThat(committed.orderEntries).hasSize(1)
    }
  }

  @Test
  fun `add module library and dispose`() {
    val model = createModifiableModel(module)
    val library = model.moduleLibraryTable.createLibrary("foo") as LibraryEx
    val libraryEntry = getSingleLibraryOrderEntry(model)
    assertThat(libraryEntry.library).isEqualTo(library)
    assertThat(library.isDisposed).isFalse()
    model.dispose()

    dropModuleSourceEntry(ModuleRootManager.getInstance(module), 0)
    assertThat(library.isDisposed).isTrue()
  }

  @Test
  fun `remove previously created module library`() {
    val library = runWriteActionAndWait {
      val model = ModuleRootManager.getInstance(module).modifiableModel
      val table = model.moduleLibraryTable
      val lib = table.createLibrary("lib")
      model.commit()
      lib as LibraryEx
    }

    runWriteActionAndWait {
      val model = ModuleRootManager.getInstance(module).modifiableModel
      val table = model.moduleLibraryTable
      table.removeLibrary(library)
      assertThat(library.isDisposed).isTrue()
      model.commit()
    }
  }

  @Test
  fun `rename library before committing root model`() {
    val model = createModifiableModel(module)
    val library = model.moduleLibraryTable.createLibrary("foo") as LibraryEx
    val libraryModel = library.modifiableModel
    assertThat(getSingleLibraryOrderEntry(model).libraryName).isEqualTo("foo")
    libraryModel.name = "bar"
    assertThat(getSingleLibraryOrderEntry(model).libraryName).isEqualTo("foo")
    assertThat(library.isDisposed).isFalse()
    libraryModel.commit()
    assertThat(library.isDisposed).isFalse()
    assertThat(getSingleLibraryOrderEntry(model).libraryName).isEqualTo("bar")
    val committed = commitModifiableRootModel(model)
    assertThat(getSingleLibraryOrderEntry(committed).libraryName).isEqualTo("bar")
  }

  @Test
  fun `discard changes in library on disposing its modifiable model`() {
    val model = createModifiableModel(module)
    val library = model.moduleLibraryTable.createLibrary("foo")
    val libraryModel = library.modifiableModel
    val classesRoot = projectModel.baseProjectDir.newVirtualDirectory("classes")
    libraryModel.addRoot(classesRoot, OrderRootType.CLASSES)
    assertThat(libraryModel.getFiles(OrderRootType.CLASSES)).containsExactly(classesRoot)
    assertThat((getSingleLibraryOrderEntry(model)).getRootFiles(OrderRootType.CLASSES)).isEmpty()
    Disposer.dispose(libraryModel)
    val committed = commitModifiableRootModel(model)
    assertThat(getSingleLibraryOrderEntry(committed).getRootFiles(OrderRootType.CLASSES)).isEmpty()
  }

  @ValueSource(booleans = [false, true])
  @ParameterizedTest(name = "obtainViaLibraryTable = {0}")
  fun `discard changes in library on disposing modifiable root model`(obtainViaLibraryTable: Boolean) {
    addLibrary("foo")
    val model = createModifiableModel(module)
    val library =
      if (obtainViaLibraryTable) model.moduleLibraryTable.libraries.single()
      else getSingleLibraryOrderEntry(model).library!!
    val libraryModel = library.modifiableModel
    val classesRoot = projectModel.baseProjectDir.newVirtualDirectory("classes")
    libraryModel.addRoot(classesRoot, OrderRootType.CLASSES)
    assertThat(libraryModel.getFiles(OrderRootType.CLASSES)).containsExactly(classesRoot)
    libraryModel.commit()
    assertThat(getSingleLibraryOrderEntry(model).getRootFiles(OrderRootType.CLASSES)).containsExactly(classesRoot)
    model.dispose()
    assertThat(getSingleLibraryOrderEntry(ModuleRootManager.getInstance(module)).getRootFiles(OrderRootType.CLASSES)).isEmpty()
  }

  @Test
  fun `edit library without creating modifiable root model`() {
    addLibrary("foo")
    val library = getSingleLibraryOrderEntry(ModuleRootManager.getInstance(module)).library!!
    val libraryModel = library.modifiableModel
    val classesRoot = projectModel.baseProjectDir.newVirtualDirectory("classes")
    libraryModel.addRoot(classesRoot, OrderRootType.CLASSES)
    runWriteActionAndWait { libraryModel.commit() }
    val entry = getSingleLibraryOrderEntry(ModuleRootManager.getInstance(module))
    assertThat(entry.getRootFiles(OrderRootType.CLASSES)).containsExactly(classesRoot)
  }

  @Test
  fun `replace module library by module library`() {
    run {
      val model = createModifiableModel(module)
      model.moduleLibraryTable.createLibrary("foo")
      commitModifiableRootModel(model)
    }
    val model = createModifiableModel(module)
    val oldEntry = getSingleLibraryOrderEntry(model)
    val newLibrary = model.moduleLibraryTable.createLibrary("bar")
    OrderEntryUtil.replaceLibraryEntryByAdded(model, oldEntry)
    assertThat(getSingleLibraryOrderEntry(model).library).isEqualTo(newLibrary)
    val committed = commitModifiableRootModel(model)
    assertThat(getSingleLibraryOrderEntry(committed).libraryName).isEqualTo("bar")
  }

  @Test
  fun `replace project library with custom scope by module library`() {
    ModuleRootModificationUtil.addDependency(module, projectModel.addProjectLevelLibrary("foo"), DependencyScope.TEST, true)
    val model = createModifiableModel(module)
    val oldEntry = getSingleLibraryOrderEntry(model)
    assertThat(oldEntry.libraryName).isEqualTo("foo")
    val newLibrary = model.moduleLibraryTable.createLibrary("bar")
    OrderEntryUtil.replaceLibraryEntryByAdded(model, oldEntry)
    assertThat(getSingleLibraryOrderEntry(model).library).isEqualTo(newLibrary)
    val committed = commitModifiableRootModel(model)
    assertThat(getSingleLibraryOrderEntry(committed).libraryName).isEqualTo("bar")
  }

  private fun addLibrary(name: String, configure: (Library) -> Unit = {}) {
    val model = createModifiableModel(module)
    val library = model.moduleLibraryTable.createLibrary(name)
    configure(library)
    commitModifiableRootModel(model)
  }

  @Test
  fun `change library scope`() {
    addLibrary("foo") {
      val model = it.modifiableModel
      model.addRoot(projectModel.baseProjectDir.newVirtualDirectory("root"), OrderRootType.CLASSES)
      model.commit()
    }
    val library = getSingleLibraryOrderEntry(ModuleRootManager.getInstance(module)).library
    val model = createModifiableModel(module)
    getSingleLibraryOrderEntry(model).scope = DependencyScope.TEST
    val committed = commitModifiableRootModel(model)
    val library2 = getSingleLibraryOrderEntry(committed).library
    assertThat(library).isNotSameAs(library2)
  }

  @Test
  fun `access removed library entry`() {
    addLibrary("a")
    val model = createModifiableModel(module)
    val libraryEntry = getSingleLibraryOrderEntry(model)
    model.removeOrderEntry(libraryEntry)
    libraryEntry.library.hashCode()
    commitModifiableRootModel(model)
    libraryEntry.library.hashCode()
  }

  @Test
  fun `delete and add module with the same name and module libraries inside`() {
    addLibrary("a")
    addLibrary("b")

    val builder = MutableEntityStorage.from(WorkspaceModel.getInstance(projectModel.project).currentSnapshot)
    val moduleModel = (projectModel.moduleManager as ModuleManagerBridgeImpl).getModifiableModel(builder)
    moduleModel.disposeModule(module)
    val newModule = projectModel.createModule("module", moduleModel)
    val rootModel = ModuleRootManagerEx.getInstanceEx(newModule).getModifiableModelForMultiCommit(RootAccessorWithWorkspaceModel(builder))
    rootModel.moduleLibraryTable.createLibrary("a")
    runWriteActionAndWait { ModifiableModelCommitter.multiCommit(listOf(rootModel), moduleModel) }

    assertThat(projectModel.moduleManager.modules).containsExactly(newModule)
    val libraryEntry = dropModuleSourceEntry(ModuleRootManager.getInstance(projectModel.moduleManager.modules.single()), 1).single()
    assertThat((libraryEntry as LibraryOrderEntry).library!!.name).isEqualTo("a")
  }

  @Test
  fun `two libraries with the same name`() {
    val root1 = projectModel.baseProjectDir.newVirtualDirectory("lib1")
    val root2 = projectModel.baseProjectDir.newVirtualDirectory("lib2")
    val model = createModifiableModel(module)
    val lib1 = model.moduleLibraryTable.createLibrary("foo")
    addClassesRoot(lib1, root1)
    val lib2 = model.moduleLibraryTable.createLibrary("foo")
    addClassesRoot(lib2, root2)
    val (entry1, entry2) = dropModuleSourceEntry(model, 2)
    assertThat((entry1 as LibraryOrderEntry).library).isEqualTo(lib1)
    assertThat((entry2 as LibraryOrderEntry).library).isEqualTo(lib2)
    val committed = commitModifiableRootModel(model)
    val (committedEntry1, committedEntry2) = dropModuleSourceEntry(committed, 2)
    assertThat((committedEntry1 as LibraryOrderEntry).libraryName).isEqualTo("foo")
    assertThat(committedEntry1.getRootFiles(OrderRootType.CLASSES)).containsExactly(root1)
    assertThat((committedEntry2 as LibraryOrderEntry).libraryName).isEqualTo("foo")
    assertThat(committedEntry2.getRootFiles(OrderRootType.CLASSES)).containsExactly(root2)
  }

  @Test
  fun `two unnamed libraries`() {
    val root1 = projectModel.baseProjectDir.newVirtualDirectory("lib1")
    val root2 = projectModel.baseProjectDir.newVirtualDirectory("lib2")
    val model = createModifiableModel(module)
    val lib1 = model.moduleLibraryTable.createLibrary()
    addClassesRoot(lib1, root1)
    val lib2 = model.moduleLibraryTable.createLibrary()
    addClassesRoot(lib2, root2)
    val (entry1, entry2) = dropModuleSourceEntry(model, 2)
    assertThat((entry1 as LibraryOrderEntry).library).isEqualTo(lib1)
    assertThat((entry2 as LibraryOrderEntry).library).isEqualTo(lib2)
    val committed = commitModifiableRootModel(model)
    val (committedEntry1, committedEntry2) = dropModuleSourceEntry(committed, 2)
    assertThat((committedEntry1 as LibraryOrderEntry).library).isNotNull()
    assertThat(committedEntry1.getRootFiles(OrderRootType.CLASSES)).containsExactly(root1)
    assertThat((committedEntry2 as LibraryOrderEntry).library).isNotNull()
    assertThat(committedEntry2.getRootFiles(OrderRootType.CLASSES)).containsExactly(root2)
  }

  @Test
  fun `commit module libraries via multi-commit`() {
    doTestMultiCommitForModuleLevelLibrary(DependencyScope.TEST)
  }

  @Test
  fun `multi-commit without changes in module-level libraries`() {
    doTestMultiCommitForModuleLevelLibrary(DependencyScope.COMPILE)
  }

  private fun doTestMultiCommitForModuleLevelLibrary(newScope: DependencyScope) {
    addLibrary("a")
    val builder = MutableEntityStorage.from(WorkspaceModel.getInstance(projectModel.project).currentSnapshot)
    val moduleModel = (projectModel.moduleManager as ModuleManagerBridgeImpl).getModifiableModel(builder)
    val rootModel = ModuleRootManagerEx.getInstanceEx(module).getModifiableModelForMultiCommit(RootAccessorWithWorkspaceModel(builder))
    getSingleLibraryOrderEntry(rootModel).scope = newScope
    runWriteActionAndWait { ModifiableModelCommitter.multiCommit(listOf(rootModel), moduleModel) }

    //this emulates behavior of Project Structure dialog: after changes are applied, it immediately recreates modifiable model to show 'Dependencies' panel
    val newModel = ModuleRootManagerEx.getInstanceEx(module).getModifiableModelForMultiCommit(RootAccessorWithWorkspaceModel(builder))
    newModel.dispose()

    val libraryEntry = getSingleLibraryOrderEntry(ModuleRootManager.getInstance(module))
    assertThat(libraryEntry.library!!.name).isEqualTo("a")
    assertThat(libraryEntry.scope).isEqualTo(newScope)
    assertThat(libraryEntry.library!!.getFiles(OrderRootType.CLASSES)).isEmpty()
  }

  private fun addClassesRoot(library: Library, root: VirtualFile) {
    val model = library.modifiableModel
    model.addRoot(root, OrderRootType.CLASSES)
    model.commit()
  }

  class RootAccessorWithWorkspaceModel(override val actualDiffBuilder: MutableEntityStorage?)
    : RootConfigurationAccessor(), RootConfigurationAccessorForWorkspaceModel
}