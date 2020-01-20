// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots.libraries

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.*
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.roots.ModuleRootManagerTestCase
import com.intellij.util.xmlb.XmlSerializerUtil
import javax.swing.JComponent

class LibraryPropertiesTest : ModuleRootManagerTestCase() {
  fun `test set type and properties`() {
    val table = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject)
    registerLibraryType(testRootDisposable)
    runWriteAction {
      val model = table.modifiableModel
      val lib = model.createLibrary("custom", MockLibraryType.KIND)
      val libModel = lib.modifiableModel as LibraryEx.ModifiableModelEx
      libModel.properties = MockLibraryProperties("data")
      libModel.commit()
      model.commit()
    }
    val library = table.libraries.single() as LibraryEx
    assertEquals(MockLibraryType.KIND, library.kind)
    assertEquals("data", (library.properties as MockLibraryProperties).data)
  }

  fun `test clear kind when library type is unregistered`() {
    val table = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject)
    val libraryTypeDisposable = Disposer.newDisposable()
    registerLibraryType(libraryTypeDisposable)
    try {
      runWriteAction {
        val model = table.modifiableModel
        val lib = model.createLibrary("custom", MockLibraryType.KIND)
        model.commit()
      }
      val library = table.libraries.single() as LibraryEx
      assertEquals(MockLibraryType.KIND, library.kind)
      assertEquals("default", (library.properties as MockLibraryProperties).data)
    }
    finally {
      Disposer.dispose(libraryTypeDisposable)
    }

    val library = table.libraries.single() as LibraryEx
    assertNull(library.kind)
    assertNull(library.properties)
  }

  private fun registerLibraryType(disposable: Disposable) {
    val libraryTypeDisposable = Disposer.newDisposable()
    Disposer.register(disposable, Disposable {
      runWriteAction {
        Disposer.dispose(libraryTypeDisposable)
      }
    })
    LibraryType.EP_NAME.getPoint(null).registerExtension(MockLibraryType(), libraryTypeDisposable)
  }

}

private class MockLibraryProperties(var data: String = "default") : LibraryProperties<MockLibraryProperties>() {
  override fun getState(): MockLibraryProperties? = this

  override fun loadState(state: MockLibraryProperties) {
    XmlSerializerUtil.copyBean(state, this)
  }

  override fun equals(other: Any?): Boolean = (other as? MockLibraryProperties)?.data == data

  override fun hashCode(): Int = data.hashCode()
}

private class MockLibraryType : LibraryType<MockLibraryProperties>(KIND) {
  companion object {
    val KIND = object : PersistentLibraryKind<MockLibraryProperties>("mock") {
      override fun createDefaultProperties(): MockLibraryProperties = MockLibraryProperties()
    }
  }

  override fun getCreateActionName(): String? = null

  override fun createNewLibrary(parentComponent: JComponent, contextDirectory: VirtualFile?, project: Project): NewLibraryConfiguration? {
    return null
  }

  override fun createPropertiesEditor(editorComponent: LibraryEditorComponent<MockLibraryProperties>): LibraryPropertiesEditor? {
    return null
  }
}