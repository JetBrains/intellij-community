// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.xmlb.XmlSerializer

class LibraryUsageStatisticStorageTest : LightJavaCodeInsightFixtureTestCase() {
  fun testFiles() {
    val txtFile = myFixture.addFileToProject("Text.txt", "").virtualFile
    val javaFile = myFixture.addFileToProject("JavaFile.java", "").virtualFile
    val filesStorageService = ProcessedFilesStorageService.getInstance(project)
    val state = filesStorageService.state
    val timestamps = state.timestamps

    assertFalse(filesStorageService.isVisited(txtFile))
    assertTrue(filesStorageService.visit(txtFile))
    assertTrue(filesStorageService.isVisited(txtFile))
    assertFalse(filesStorageService.visit(txtFile))

    assertTrue(filesStorageService.isVisited(txtFile))
    filesStorageService.visit(txtFile)
    assertTrue(filesStorageService.isVisited(txtFile))

    assertFalse(filesStorageService.isVisited(javaFile))
    assertTrue(filesStorageService.visit(javaFile))
    assertTrue(filesStorageService.isVisited(javaFile))

    assertTrue(timestamps.isNotEmpty())

    val serializedState = XmlSerializer.serialize(state)
    val deserializedState = XmlSerializer.deserialize(
      serializedState,
      ProcessedFilesStorageService.MyState::class.java,
    )

    assertEquals(timestamps, deserializedState.timestamps)
    filesStorageService.loadState(deserializedState)
    assertEquals(timestamps, filesStorageService.state.timestamps)
  }

  fun testLibraries() {
    val txtFile = myFixture.addFileToProject("Text.txt", "").virtualFile
    val javaFile = myFixture.addFileToProject("JavaFile.java", "").virtualFile
    val storageService = LibraryUsageStatisticsStorageService.getInstance(project)

    val txtLib = createLib(txtFile)
    storageService.increaseUsage(txtLib)
    storageService.increaseUsage(txtLib)
    storageService.increaseUsage(createLib(javaFile))
    storageService.increaseUsages(listOf(txtLib, createLib(txtFile, version = "1.0.0"), createLib(txtFile, name = "otherName")))

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

private fun LibraryUsageStatisticsStorageService.increaseUsage(lib: LibraryUsage) {
  increaseUsages(listOf(lib))
}

private fun createLib(
  vFile: VirtualFile,
  name: String = "myLibName",
  version: String = "1.1.1",
): LibraryUsage = LibraryUsage(name = name, version = version, fileType = vFile.fileType)