// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.roots.libraries

import com.intellij.java.roots.ModuleRootManagerTestCase
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.ui.OrderRoot
import com.intellij.openapi.roots.ui.configuration.classpath.CreateModuleLibraryChooser
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.UsefulTestCase
import org.assertj.core.api.Assertions.assertThat
import java.io.File

class CreateModuleLibraryFromFilesTest : ModuleRootManagerTestCase() {
  private var modifiableModel: LibraryTable.ModifiableModel? = null
  private var modifiableRootModel: ModifiableRootModel? = null

  override fun setUp() {
    super.setUp()
    modifiableRootModel = ModuleRootManager.getInstance(myModule).modifiableModel
    modifiableModel = modifiableRootModel!!.moduleLibraryTable.modifiableModel
  }

  override fun tearDown() {
    try {
      modifiableRootModel!!.dispose()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      modifiableModel = null
      modifiableRootModel = null
      super.tearDown()
    }
  }

  fun testSingleJar() {
    val jar = createJar("library.jar")
    val library = UsefulTestCase.assertOneElement(createLibraries(OrderRoot(jar, OrderRootType.CLASSES)))
    assertThat(library.name).isNull()
    assertThat(library.getFiles(OrderRootType.CLASSES)).containsExactly(jar)
    assertThat(library.getFiles(OrderRootType.SOURCES)).isEmpty()
  }

  fun testTwoJars() {
    val firstJar = createJar("first.jar")
    val secondJar = createJar("second.jar")
    val libraries = createLibraries(OrderRoot(firstJar, OrderRootType.CLASSES),
                                    OrderRoot(secondJar, OrderRootType.CLASSES))
    assertThat(libraries).hasSize(2)
    assertThat(libraries[0].name).isNull()
    assertThat(libraries[0].getFiles(OrderRootType.CLASSES)).containsExactly(firstJar)
    assertThat(libraries[1].name).isNull()
    assertThat(libraries[1].getFiles(OrderRootType.CLASSES)).containsExactly(secondJar)
  }

  fun testJarAndSources() {
    val classesJar = createJar("library.jar")
    val sourcesJar = createJar("library-sources.jar")
    val library = createLibraries(
      OrderRoot(classesJar, OrderRootType.CLASSES),
      OrderRoot(sourcesJar, OrderRootType.SOURCES),
    ).single()
    assertThat(library.name).isNull()
    assertThat(library.getFiles(OrderRootType.CLASSES)).containsExactly(classesJar)
    assertThat(library.getUrls(OrderRootType.SOURCES)).containsExactly(sourcesJar.url)
  }

  fun testJarWithSourcesInside() {
    val jar = createJar("library.jar")
    val library = createLibraries(OrderRoot(jar, OrderRootType.CLASSES), OrderRoot(jar, OrderRootType.SOURCES)).single()
    assertThat(library.name).isNull()
    assertThat(library.getFiles(OrderRootType.CLASSES)).containsExactly(jar)
    assertThat(library.getFiles(OrderRootType.SOURCES)).containsExactly(jar)
  }

  fun testTwoJarAndSources() {
    val firstJar = createJar("first.jar")
    val secondJar = createJar("second.jar")
    val sourcesJar = createJar("sources.jar")
    val libraries = createLibraries(
      OrderRoot(firstJar, OrderRootType.CLASSES),
      OrderRoot(secondJar, OrderRootType.CLASSES),
      OrderRoot(sourcesJar, OrderRootType.SOURCES),
    )
    val library = libraries.single()
    assertThat(library.name).isNull()
    assertThat(library.getFiles(OrderRootType.CLASSES)).containsExactly(firstJar, secondJar)
    assertThat(library.getUrls(OrderRootType.SOURCES)).containsExactly(sourcesJar.url)
  }

  fun testTwoJarWithSourcesInside() {
    val firstJar = createJar("first.jar")
    val secondJar = createJar("second.jar")
    val libraries = createLibraries(OrderRoot(firstJar, OrderRootType.CLASSES),
                                    OrderRoot(secondJar, OrderRootType.CLASSES),
                                    OrderRoot(firstJar, OrderRootType.SOURCES),
                                    OrderRoot(secondJar, OrderRootType.SOURCES))
    assertEquals(2, libraries.size)
    assertNull(libraries[0].name)
    UsefulTestCase.assertSameElements(libraries[0].getFiles(OrderRootType.CLASSES), firstJar)
    UsefulTestCase.assertSameElements(libraries[0].getFiles(OrderRootType.SOURCES), firstJar)
    assertNull(libraries[1].name)
    UsefulTestCase.assertSameElements(libraries[1].getFiles(OrderRootType.CLASSES), secondJar)
    UsefulTestCase.assertSameElements(libraries[1].getFiles(OrderRootType.SOURCES), secondJar)
  }

  private fun createJar(name: String): VirtualFile {
    val jar = IoTestUtil.createTestJar(File(getOrCreateProjectBaseDir().path, name))
    return requireNotNull(JarFileSystem.getInstance().getJarRootForLocalFile(getVirtualFile(jar)))
  }

  private fun createLibraries(vararg roots: OrderRoot): List<Library> {
    return CreateModuleLibraryChooser.createLibrariesFromRoots(roots.asList(), modifiableModel)
  }
}