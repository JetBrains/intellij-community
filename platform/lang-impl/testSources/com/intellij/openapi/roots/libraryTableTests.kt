// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.CustomLibraryTableDescription
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test

class ProjectLibraryTableTest : LibraryTableTestCase() {
  override val libraryTable: LibraryTable
    get() = projectModel.projectLibraryTable

  override fun createLibrary(name: String, setup: (LibraryEx.ModifiableModelEx) -> Unit): LibraryEx {
    return projectModel.addProjectLevelLibrary(name, setup)
  }

  @Test
  fun `do not add library with existing name`() {
    val a = createLibrary("a")
    val model1 = libraryTable.modifiableModel
    val b = createLibrary("b", model1)
    val model2 = libraryTable.modifiableModel
    createLibrary("b", model2)
    runWriteActionAndWait {
      model1.commit()
      model2.commit()
    }
    Assertions.assertThat(libraryTable.libraries).containsExactlyInAnyOrder(a, b)
  }
}

class ApplicationLibraryTableTest : LibraryTableTestCase() {
  override val libraryTable: LibraryTable
    get() = LibraryTablesRegistrar.getInstance().libraryTable

  override fun createLibrary(name: String, setup: (LibraryEx.ModifiableModelEx) -> Unit): LibraryEx {
    return projectModel.addApplicationLevelLibrary(name, setup)
  }

  override fun createLibrary(name: String, model: LibraryTable.ModifiableModel): LibraryEx {
    return projectModel.createLibraryAndDisposeOnTearDown(name, model)
  }
}

class CustomLibraryTableTest : LibraryTableTestCase() {
  override val libraryTable: LibraryTable
    get() = LibraryTablesRegistrar.getInstance().getCustomLibraryTableByLevel("mock")!!

  @Before
  fun registerCustomLibraryTable() {
    ExtensionPointName.create<CustomLibraryTableDescription>("com.intellij.customLibraryTable").point.registerExtension(
      MockCustomLibraryTableDescription(), disposableRule.disposable)
  }

  override fun createLibrary(name: String, setup: (LibraryEx.ModifiableModelEx) -> Unit): LibraryEx {
    return projectModel.addLibrary(name, libraryTable)
  }
}
