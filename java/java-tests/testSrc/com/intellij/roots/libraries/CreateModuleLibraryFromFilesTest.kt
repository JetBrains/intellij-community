// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.roots.libraries

import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.ui.OrderRoot
import com.intellij.openapi.roots.ui.configuration.classpath.CreateModuleLibraryChooser
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.project.IntelliJProjectConfiguration.Companion.getProjectLibrary
import com.intellij.roots.ModuleRootManagerTestCase
import com.intellij.testFramework.UsefulTestCase
import org.assertj.core.api.Assertions.assertThat

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
    val library = UsefulTestCase.assertOneElement(createLibraries(OrderRoot(getFastUtilJar(), OrderRootType.CLASSES)))
    assertThat(library.name).isNull()
    assertThat(library.getFiles(OrderRootType.CLASSES)).containsExactly(getFastUtilJar())
    assertThat(library.getFiles(OrderRootType.SOURCES)).isEmpty()
  }

  fun testTwoJars() {
    val libraries = createLibraries(OrderRoot(getFastUtilJar(), OrderRootType.CLASSES),
                                    OrderRoot(asmJar, OrderRootType.CLASSES))
    assertThat(libraries).hasSize(2)
    assertThat(libraries[0].name).isNull()
    assertThat(libraries[0].getFiles(OrderRootType.CLASSES)).containsExactly(getFastUtilJar())
    assertThat(libraries[1].name).isNull()
    assertThat(libraries[1].getFiles(OrderRootType.CLASSES)).containsExactly(asmJar)
  }

  fun testJarAndSources() {
    // classesUrls of gson is used as sources JAR because sources maybe not downloaded in test mode
    val gsonJar = getProjectLibrary("gson")

    val library = createLibraries(
      OrderRoot(getFastUtilJar(), OrderRootType.CLASSES),
      OrderRoot(IntelliJProjectConfiguration.getVirtualFile(gsonJar), OrderRootType.SOURCES),
    ).single()
    assertThat(library.name).isNull()
    assertThat(library.getFiles(OrderRootType.CLASSES)).containsExactly(getFastUtilJar())
    assertThat(library.getUrls(OrderRootType.SOURCES).asList()).isEqualTo(gsonJar.classesUrls)
  }

  fun testJarWithSourcesInside() {
    val fastUtilJar = getFastUtilJar()
    val library = createLibraries(OrderRoot(fastUtilJar, OrderRootType.CLASSES), OrderRoot(fastUtilJar, OrderRootType.SOURCES)).single()
    assertThat(library.name).isNull()
    assertThat(library.getFiles(OrderRootType.CLASSES)).containsExactly(fastUtilJar)
    assertThat(library.getFiles(OrderRootType.SOURCES)).containsExactly(fastUtilJar)
  }

  fun testTwoJarAndSources() {
    val gsonLib = getProjectLibrary("gson")

    val fastUtilJar = getFastUtilJar()
    val libraries = createLibraries(
      OrderRoot(fastUtilJar, OrderRootType.CLASSES),
      OrderRoot(asmJar, OrderRootType.CLASSES),
      OrderRoot(IntelliJProjectConfiguration.getVirtualFile(gsonLib), OrderRootType.SOURCES),
    )
    val library = libraries.single()
    assertThat(library.name).isNull()
    assertThat(library.getFiles(OrderRootType.CLASSES)).containsExactly(fastUtilJar, asmJar)
    assertThat(library.getUrls(OrderRootType.SOURCES).asList()).isEqualTo(gsonLib.classesUrls)
  }

  fun testTwoJarWithSourcesInside() {
    val fastUtilJar = getFastUtilJar()
    val libraries = createLibraries(OrderRoot(fastUtilJar, OrderRootType.CLASSES),
                                    OrderRoot(asmJar, OrderRootType.CLASSES),
                                    OrderRoot(fastUtilJar, OrderRootType.SOURCES),
                                    OrderRoot(asmJar, OrderRootType.SOURCES))
    assertEquals(2, libraries.size)
    assertNull(libraries[0].name)
    UsefulTestCase.assertSameElements(libraries[0].getFiles(OrderRootType.CLASSES), fastUtilJar)
    UsefulTestCase.assertSameElements(libraries[0].getFiles(OrderRootType.SOURCES), fastUtilJar)
    assertNull(libraries[1].name)
    UsefulTestCase.assertSameElements(libraries[1].getFiles(OrderRootType.CLASSES), asmJar)
    UsefulTestCase.assertSameElements(libraries[1].getFiles(OrderRootType.SOURCES), asmJar)
  }

  private fun createLibraries(vararg roots: OrderRoot): List<Library> {
    return CreateModuleLibraryChooser.createLibrariesFromRoots(roots.asList(), modifiableModel)
  }
}