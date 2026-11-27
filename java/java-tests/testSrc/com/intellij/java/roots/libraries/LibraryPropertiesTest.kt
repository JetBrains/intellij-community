// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.roots.libraries

import com.intellij.java.roots.ModuleRootManagerFixture.moduleRootManagerFixture
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.UnknownLibraryKind
import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.roots.libraries.LibraryType
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.xmlb.XmlSerializerUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import javax.swing.JComponent

@TestApplication
class LibraryPropertiesTest {
  private val fixture by moduleRootManagerFixture()
  @Test
  fun `test set type and properties`() {
    registerLibraryType(fixture.testRootDisposable)
    addLibrary("custom", "data")
    val library = LibraryTablesRegistrar.getInstance().getLibraryTable(fixture.project).libraries.single() as LibraryEx
    assertEquals(MockLibraryType.KIND, library.kind)
    assertEquals("data", (library.properties as MockLibraryProperties).data)
    assertEquals("""
       <library name="custom" type="mock">
         <properties>
           <option name="data" value="data" />
         </properties>
         <CLASSES />
         <JAVADOC />
         <SOURCES />
       </library>
       """.trimIndent(), LibraryTest.serializeLibraries(fixture.project))
  }

  private fun addLibrary(name: String, data: String) {
    val table = LibraryTablesRegistrar.getInstance().getLibraryTable(fixture.project)
    runWriteActionAndWait {
      val model = table.modifiableModel
      val lib = model.createLibrary(name, MockLibraryType.KIND)
      val libModel = lib.modifiableModel as LibraryEx.ModifiableModelEx
      libModel.properties = MockLibraryProperties(data)
      libModel.commit()
      model.commit()
    }
  }

  @Test
  fun `test convert to unknown library type is unregistered`() {
    val table = LibraryTablesRegistrar.getInstance().getLibraryTable(fixture.project)
    runWithRegisteredType {
      addLibrary("lib", "hello")
      val library = table.libraries.single() as LibraryEx
      assertEquals(MockLibraryType.KIND, library.kind)
      assertEquals("hello", (library.properties as MockLibraryProperties).data)
    }

    val unknown = table.libraries.single() as LibraryEx
    assertInstanceOf(UnknownLibraryKind::class.java, unknown.kind)
    assertNotNull(unknown.properties)

    runWithRegisteredType {
      val library = table.libraries.single() as LibraryEx
      assertEquals(MockLibraryType.KIND, library.kind)
      assertEquals("hello", (library.properties as MockLibraryProperties).data)
    }
  }

  @Test
  fun `test set library properties in modifiable model`() {
    registerLibraryType(fixture.testRootDisposable)
    ModuleRootModificationUtil.updateModel(fixture.module) {
      val library = it.moduleLibraryTable.createLibrary("foo")
      val model = library.modifiableModel as LibraryEx.ModifiableModelEx
      model.kind = MockLibraryType.KIND
      model.properties = MockLibraryProperties("1")
      runWriteAction { model.commit() }
    }

    assertEquals("1", getMockLibraryData())

    ModuleRootModificationUtil.updateModel(fixture.module) {
      val library = it.moduleLibraryTable.libraries.single()
      val model = library.modifiableModel as LibraryEx.ModifiableModelEx
      model.properties = MockLibraryProperties("2")
      runWriteAction { model.commit() }
    }

    assertEquals("2", getMockLibraryData())

    ModuleRootModificationUtil.updateModel(fixture.module) {
      val library = it.moduleLibraryTable.libraries.single()
      val model = library.modifiableModel as LibraryEx.ModifiableModelEx
      model.kind = null
      runWriteAction { model.commit() }
    }
    assertNull(getMockLibraryData())
  }

  private fun getMockLibraryData(): String? {
    val libEntries = ModuleRootManager.getInstance(fixture.module).orderEntries.filterIsInstance<LibraryOrderEntry>()
    return ((libEntries.single().library as LibraryEx).properties as? MockLibraryProperties)?.data
  }

  private fun runWithRegisteredType(action: () -> Unit) {
    val libraryTypeDisposable = Disposer.newDisposable()
    registerLibraryType(libraryTypeDisposable)
    try {
      action()
    }
    finally {
      Disposer.dispose(libraryTypeDisposable)
    }
  }

  private fun registerLibraryType(disposable: Disposable) {
    val libraryTypeDisposable = Disposer.newDisposable()
    Disposer.register(disposable, Disposable {
      runWriteActionAndWait {
        Disposer.dispose(libraryTypeDisposable)
      }
    })
    LibraryType.EP_NAME.point.registerExtension(MockLibraryType(), libraryTypeDisposable)
  }

}

private class MockLibraryProperties(var data: String = "default") : LibraryProperties<MockLibraryProperties>() {
  override fun getState(): MockLibraryProperties = this

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