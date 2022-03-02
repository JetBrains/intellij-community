// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.xmlb.XmlSerializer

class LibraryUsageStatisticStorageTest : LightJavaCodeInsightFixtureTestCase() {
  fun test() {
    val txtFile = myFixture.addFileToProject("Text.txt", "").virtualFile
    val javaFile = myFixture.addFileToProject("JavaFile.java", "").virtualFile
    val storageService = LibraryUsageStatisticsStorageService.getInstance(project)
    val state = storageService.state
    val timestamps = state.timestamps

    assertFalse(storageService.isVisited(txtFile))
    storageService.visit(txtFile)
    assertTrue(storageService.isVisited(txtFile))

    assertTrue(storageService.isVisited(txtFile))
    storageService.visit(txtFile)
    assertTrue(storageService.isVisited(txtFile))

    assertFalse(storageService.isVisited(javaFile))
    storageService.visit(javaFile)
    assertTrue(storageService.isVisited(javaFile))

    assertTrue(timestamps.isNotEmpty())

    val serializedState = XmlSerializer.serialize(state)
    val deserializedState = XmlSerializer.deserialize(
      serializedState,
      LibraryUsageStatisticsStorageService.MyState::class.java,
    )

    assertEquals(timestamps, deserializedState.timestamps)
    storageService.loadState(deserializedState)
    assertEquals(timestamps, storageService.state.timestamps)
  }
}
