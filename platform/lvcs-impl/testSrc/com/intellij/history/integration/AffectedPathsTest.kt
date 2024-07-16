// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration

import com.intellij.history.LocalHistory
import com.intellij.history.core.ChangeAndPathProcessor
import com.intellij.history.core.collectChanges
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase

class AffectedPathsTest : IntegrationTestCase() {
  private val facade get() = LocalHistoryImpl.getInstanceImpl().facade!!

  fun testSimpleFile() {
    val file = createFile("file.txt", "old")

    setContent(file, "new")
    setContent(file, "even newer")

    assertEquals(setOf(myGateway.getPathOrUrl(file)), getPaths(file))
  }

  fun testRenamedFile() {
    val file = createFile("file.txt", "old")
    val oldPath = myGateway.getPathOrUrl(file)

    HeavyPlatformTestCase.rename(file, "newFile.txt")

    setContent(file, "current")

    assertEquals(setOf(myGateway.getPathOrUrl(file), oldPath), getPaths(file))
  }

  fun testDirectory() {
    val directory = createDirectory("test")
    HeavyPlatformTestCase.createChildData(directory, "file.txt")

    assertEquals(setOf(myGateway.getPathOrUrl(directory)), getPaths(directory))
  }

  fun testRenameDirectory() {
    val directory = createDirectory("test")
    val file = HeavyPlatformTestCase.createChildData(directory, "file.txt")

    val oldDirectoryPath = myGateway.getPathOrUrl(directory)
    val oldFilePath = myGateway.getPathOrUrl(file)

    HeavyPlatformTestCase.rename(directory, "newName")

    assertEquals(setOf(myGateway.getPathOrUrl(directory), oldDirectoryPath), getPaths(directory))
    assertEquals(setOf(myGateway.getPathOrUrl(file), oldFilePath), getPaths(file))
  }

  fun testManyRenamesInTheSameChangeSet() {
    val file = createFile("file.txt", "content0")
    val paths = mutableSetOf(myGateway.getPathOrUrl(file))

    val action = LocalHistory.getInstance().startAction("action")

    val names = listOf("file1.txt", "file2.txt", "file3.txt", "file4.txt")
    for (name in names) {
      HeavyPlatformTestCase.rename(file, name)
      paths.add(myGateway.getPathOrUrl(file))
    }

    action.finish()

    assertEquals(paths, getPaths(file))
  }

  private fun getPaths(file: VirtualFile): Set<String> {
    val paths = mutableSetOf<String>()
    facade.collectChanges(myGateway.getPathOrUrl(file), ChangeAndPathProcessor(myProject.locationHash, null, paths::add) {})
    return paths
  }
}