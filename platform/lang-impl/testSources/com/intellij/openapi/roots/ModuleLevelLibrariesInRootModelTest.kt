// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.ProjectModelRule
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

@Suppress("UsePropertyAccessSyntax")
class ModuleLevelLibrariesInRootModelTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  lateinit var module: Module

  @Before
  fun setUp() {
    module = projectModel.createModule()
  }

  @Test
  fun `add edit remove unnamed module library`() {
    run {
      val model = createModifiableModel(module)
      val library = model.moduleLibraryTable.createLibrary() as LibraryEx
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
      assertThat(library.isDisposed).isTrue()
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
      assertThat(libraryEntryForUncommitted.getFiles(OrderRootType.CLASSES)).isEmpty()
      libraryModel.commit()
      val libraryEntry = getSingleLibraryOrderEntry(model)
      assertThat(libraryEntry.getFiles(OrderRootType.CLASSES).single()).isEqualTo(root)
      assertThat(libraryEntry.presentableName).isEqualTo(root.presentableUrl)
      libraryEntry.scope = DependencyScope.RUNTIME
      libraryEntry.isExported = true
      assertThat(model.findLibraryOrderEntry(library)).isEqualTo(libraryEntry)
      val committed = commitModifiableRootModel(model)
      val committedEntry = getSingleLibraryOrderEntry(committed)
      assertThat(committedEntry.getFiles(OrderRootType.CLASSES).single()).isEqualTo(root)
      assertThat((library as LibraryEx).isDisposed).isTrue()
      assertThat((committedEntry.library as LibraryEx).isDisposed).isFalse()
      assertThat(committedEntry.scope).isEqualTo(DependencyScope.RUNTIME)
      assertThat(committedEntry.isExported).isTrue()
    }

    run {
      val model = createModifiableModel(module)
      val library = model.moduleLibraryTable.libraries.single()
      model.moduleLibraryTable.removeLibrary(library)
      assertThat(model.moduleLibraryTable.libraries).isEmpty()
      assertThat(model.orderEntries).hasSize(1)
      assertThat(model.findLibraryOrderEntry(library)).isNull()
      val committed = commitModifiableRootModel(model)
      assertThat(committed.orderEntries).hasSize(1)
      assertThat((library as LibraryEx).isDisposed).isTrue()
    }
  }

  @Test
  fun `add rename remove module library`() {
    run {
      val model = createModifiableModel(module)
      val library = model.moduleLibraryTable.createLibrary("foo")
      assertThat(model.moduleLibraryTable.libraries.single()).isEqualTo(library)
      val libraryEntry = getSingleLibraryOrderEntry(model)
      assertThat(libraryEntry.libraryName).isEqualTo("foo")
      assertThat(libraryEntry.presentableName).isEqualTo("foo")
      assertThat(libraryEntry.library).isEqualTo(library)

      val committed = commitModifiableRootModel(model)
      val committedEntry = getSingleLibraryOrderEntry(committed)
      assertThat(committedEntry.libraryName).isEqualTo("foo")
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
      model.removeOrderEntry(libraryEntry)
      assertThat(model.moduleLibraryTable.libraries).isEmpty()
      assertThat(model.orderEntries).hasSize(1)
      val committed = commitModifiableRootModel(model)
      assertThat(committed.orderEntries).hasSize(1)
    }
  }

  @Test
  fun `add module library and dispose`() {
    val model = createModifiableModel(module)
    val library = model.moduleLibraryTable.createLibrary("foo")
    val libraryEntry = getSingleLibraryOrderEntry(model)
    assertThat(libraryEntry.library).isEqualTo(library)
    model.dispose()

    dropModuleSourceEntry(ModuleRootManager.getInstance(module), 0)
    assertThat((library as LibraryEx).isDisposed).isTrue()
  }

  @Test
  fun `rename library before committing root model`() {
    val model = createModifiableModel(module)
    val library = model.moduleLibraryTable.createLibrary("foo")
    val libraryModel = library.modifiableModel
    assertThat(getSingleLibraryOrderEntry(model).libraryName).isEqualTo("foo")
    libraryModel.name = "bar"
    assertThat(getSingleLibraryOrderEntry(model).libraryName).isEqualTo("foo")
    libraryModel.commit()
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

  @Test
  fun `discard changes in library on disposing modifiable root model`() {
    run {
      val model = createModifiableModel(module)
      model.moduleLibraryTable.createLibrary("foo")
      commitModifiableRootModel(model)
    }
    val model = createModifiableModel(module)
    val library = model.moduleLibraryTable.libraries.single()
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
    run {
      val model = createModifiableModel(module)
      model.moduleLibraryTable.createLibrary("foo")
      commitModifiableRootModel(model)
    }
    val library = getSingleLibraryOrderEntry(ModuleRootManager.getInstance(module)).library!!
    val libraryModel = library.modifiableModel
    val classesRoot = projectModel.baseProjectDir.newVirtualDirectory("classes")
    libraryModel.addRoot(classesRoot, OrderRootType.CLASSES)
    runWriteActionAndWait { libraryModel.commit() }
    val entry = getSingleLibraryOrderEntry(ModuleRootManager.getInstance(module))
    assertThat(entry.getFiles(OrderRootType.CLASSES)).containsExactly(classesRoot)
  }

  @Test
  fun `replace library`() {
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
    assertThat(committedEntry1.getFiles(OrderRootType.CLASSES)).containsExactly(root1)
    assertThat((committedEntry2 as LibraryOrderEntry).libraryName).isEqualTo("foo")
    assertThat(committedEntry2.getFiles(OrderRootType.CLASSES)).containsExactly(root2)
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
    assertThat(committedEntry1.getFiles(OrderRootType.CLASSES)).containsExactly(root1)
    assertThat((committedEntry2 as LibraryOrderEntry).library).isNotNull()
    assertThat(committedEntry2.getFiles(OrderRootType.CLASSES)).containsExactly(root2)
  }

  private fun addClassesRoot(library: Library, root: VirtualFile) {
    val model = library.modifiableModel
    model.addRoot(root, OrderRootType.CLASSES)
    model.commit()
  }
}