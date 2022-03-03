// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.xmlb.XmlSerializer

class LibraryUsageStatisticStorageTest : LightJavaCodeInsightFixtureTestCase() {
  fun test() {
    val txtFile = myFixture.addFileToProject("Text.txt", "").virtualFile
    val javaFile = myFixture.addFileToProject("JavaFile.java", "").virtualFile
    val storageService = LibraryUsageStatisticsStorageService.getInstance(project)

    assertFalse(storageService.isVisited(txtFile))
    assertTrue(storageService.visit(txtFile))
    assertTrue(storageService.isVisited(txtFile))
    assertFalse(storageService.visit(txtFile))

    assertFalse(storageService.isVisited(javaFile))
    assertTrue(storageService.visit(javaFile))

    assertTrue(storageService.state.statistics.isEmpty())
    assertTrue(storageService.isVisited(txtFile))
    assertTrue(storageService.isVisited(javaFile))

    assertTrue(storageService.getStatisticsAndResetState().isEmpty())
    assertFalse(storageService.isVisited(txtFile))
    assertFalse(storageService.isVisited(javaFile))

    val txtLib = createLib(txtFile)
    storageService.increaseUsage(txtFile, txtLib)
    storageService.increaseUsage(txtFile, txtLib)
    storageService.increaseUsage(javaFile, createLib(javaFile))
    storageService.increaseUsages(txtFile, listOf(txtLib, createLib(txtFile, version = "1.0.0"), createLib(txtFile, name = "otherName")))

    val copyOfState = storageService.state
    assertTrue(copyOfState.statistics.isNotEmpty())
    assertEquals(copyOfState.statistics, storageService.state.statistics)

    val expectedSet = mapOf(
      LibraryUsage("myLibName", "1.1.1", "PLAIN_TEXT") to 3,
      LibraryUsage("myLibName", "1.0.0", "PLAIN_TEXT") to 1,
      LibraryUsage("myLibName", "1.1.1", "JAVA") to 1,
      LibraryUsage("otherName", "1.1.1", "PLAIN_TEXT") to 1,
    )

    assertEquals(expectedSet, copyOfState.statistics)
    assertEquals(expectedSet, storageService.getStatisticsAndResetState())
    assertTrue(storageService.state.statistics.isEmpty())

    storageService.loadState(copyOfState)

    val state = storageService.state
    val statistics = storageService.getStatisticsAndResetState()
    assertEquals(expectedSet, statistics)
    assertTrue(storageService.state.statistics.isEmpty())
    assertFalse(storageService.isVisited(txtFile))
    assertFalse(storageService.isVisited(javaFile))

    val serializedState = XmlSerializer.serialize(state)
    val deserializedState = XmlSerializer.deserialize(
      serializedState,
      LibraryUsageStatisticsStorageService.LibraryUsageStatisticsState::class.java,
    )

    assertEquals(expectedSet, deserializedState.statistics)
    storageService.loadState(deserializedState)
    assertEquals(expectedSet, storageService.state.statistics)
  }
}

private fun createLib(
  vFile: VirtualFile,
  name: String = "myLibName",
  version: String = "1.1.1",
): LibraryUsage = LibraryUsage(name = name, version = version, fileType = vFile.fileType)