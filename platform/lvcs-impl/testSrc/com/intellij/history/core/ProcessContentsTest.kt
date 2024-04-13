// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core

import com.intellij.history.LocalHistory
import com.intellij.history.integration.IntegrationTestCase
import com.intellij.history.integration.LocalHistoryImpl
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import junit.framework.TestCase

class ProcessContentsTest : IntegrationTestCase() {

  private val facade get() = LocalHistoryImpl.getInstanceImpl().facade!!

  fun testContentChange() {
    val file = HeavyPlatformTestCase.createChildData(myRoot, "file.txt")

    val contents = listOf("content1", "content2", "content3")
    for (c in contents) {
      setContent(file, c)
    }

    setContent(file, "currentContent")

    val actualContents = collectLocalHistoryContents(file)

    TestCase.assertEquals(contents.asReversed() + listOf(""), actualContents)
  }

  fun testTwoFileContentChange() {
    val file1 = HeavyPlatformTestCase.createChildData(myRoot, "file1.txt")
    val file2 = HeavyPlatformTestCase.createChildData(myRoot, "file2.txt")

    val contents1 = listOf("123", "4567", "891011")
    val contents2 = listOf("abc", "def", "ghijk")

    for (i in contents1.indices) {
      setContent(file1, contents1[i])
      setContent(file2, contents2[i])
    }

    setContent(file1, "currentContent1")
    setContent(file2, "currentContent2")

    val actualContents1 = collectLocalHistoryContents(file1)
    val actualContents2 = collectLocalHistoryContents(file2)

    TestCase.assertEquals(contents1.asReversed() + listOf(""), actualContents1)
    TestCase.assertEquals(contents2.asReversed() + listOf(""), actualContents2)
  }

  fun testManyChangesInTheSameChangeSet() {
    val file = HeavyPlatformTestCase.createChildData(myRoot, "file.txt")

    val action = LocalHistory.getInstance().startAction("action")

    val contents = listOf("content1", "content2", "content3")
    for (c in contents) {
      setContent(file, c)
    }

    action.finish()

    setContent(file, "currentContent")

    val actualContents = collectLocalHistoryContents(file)

    TestCase.assertEquals(listOf(contents.last(), ""), actualContents)
  }

  fun testRename() {
    val file = HeavyPlatformTestCase.createChildData(myRoot, "file.txt")
    val initialContent = "initialContent"
    setContent(file, initialContent)

    HeavyPlatformTestCase.rename(file, "file1.txt")

    val intermediateContent = "intermediateContent"
    setContent(file, intermediateContent)
    setContent(file, "currentContent")

    val actualContents = collectLocalHistoryContents(file)
    TestCase.assertEquals(listOf(intermediateContent, initialContent, initialContent, ""), actualContents)
  }

  private fun collectLocalHistoryContents(file: VirtualFile): MutableList<String> {
    val path = myGateway.getPathOrUrl(file)
    val changeSets = mutableSetOf<Long>()
    facade.collectChanges(myProject.locationHash, path, null) { changeSet -> changeSets.add(changeSet.id) }

    val actualContents = mutableListOf<String>()
    val rootEntry = runReadAction { myGateway.createTransientRootEntry() }
    facade.processContents(myGateway, rootEntry, path, changeSets, true) { _, content ->
      if (content != null) actualContents.add(content)
      true
    }
    return actualContents
  }

}