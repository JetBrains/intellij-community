// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.roots.libraries

import com.intellij.java.roots.ModuleRootManagerFixture.moduleRootManagerFixture
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
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.HeavyTestHelper
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
class CreateModuleLibraryFromFilesTest {
  private val fixture by moduleRootManagerFixture()
  private var modifiableModel: LibraryTable.ModifiableModel? = null
  private var modifiableRootModel: ModifiableRootModel? = null

  @BeforeEach
  fun setUp() {
    modifiableRootModel = ModuleRootManager.getInstance(fixture.module).modifiableModel
    modifiableModel = modifiableRootModel!!.moduleLibraryTable.modifiableModel
  }

  @AfterEach
  fun tearDown() {
    modifiableRootModel?.dispose()
  }

  @Test
  fun testSingleJar() {
    val jar = createJar("library.jar")
    val library = assertThat(createLibraries(OrderRoot(jar, OrderRootType.CLASSES))).singleElement().actual()
    assertThat(library.name).isNull()
    assertThat(library.getFiles(OrderRootType.CLASSES)).containsExactly(jar)
    assertThat(library.getFiles(OrderRootType.SOURCES)).isEmpty()
  }

  @Test
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

  @Test
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

  @Test
  fun testJarWithSourcesInside() {
    val jar = createJar("library.jar")
    val library = createLibraries(OrderRoot(jar, OrderRootType.CLASSES), OrderRoot(jar, OrderRootType.SOURCES)).single()
    assertThat(library.name).isNull()
    assertThat(library.getFiles(OrderRootType.CLASSES)).containsExactly(jar)
    assertThat(library.getFiles(OrderRootType.SOURCES)).containsExactly(jar)
  }

  @Test
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

  @Test
  fun testTwoJarWithSourcesInside() {
    val firstJar = createJar("first.jar")
    val secondJar = createJar("second.jar")
    val libraries = createLibraries(OrderRoot(firstJar, OrderRootType.CLASSES),
                                    OrderRoot(secondJar, OrderRootType.CLASSES),
                                    OrderRoot(firstJar, OrderRootType.SOURCES),
                                    OrderRoot(secondJar, OrderRootType.SOURCES))
    assertEquals(2, libraries.size)
    assertNull(libraries[0].name)
    assertThat(libraries[0].getFiles(OrderRootType.CLASSES)).containsExactlyInAnyOrder(firstJar)
    assertThat(libraries[0].getFiles(OrderRootType.SOURCES)).containsExactlyInAnyOrder(firstJar)
    assertNull(libraries[1].name)
    assertThat(libraries[1].getFiles(OrderRootType.CLASSES)).containsExactlyInAnyOrder(secondJar)
    assertThat(libraries[1].getFiles(OrderRootType.SOURCES)).containsExactlyInAnyOrder(secondJar)
  }

  private fun createJar(name: String): VirtualFile {
    val jar = IoTestUtil.createTestJar(File(HeavyTestHelper.getOrCreateProjectBaseDir(fixture.project).path, name))
    return requireNotNull(JarFileSystem.getInstance().getJarRootForLocalFile(requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar))))
  }

  private fun createLibraries(vararg roots: OrderRoot): List<Library> {
    return CreateModuleLibraryChooser.createLibrariesFromRoots(roots.asList(), modifiableModel)
  }
}