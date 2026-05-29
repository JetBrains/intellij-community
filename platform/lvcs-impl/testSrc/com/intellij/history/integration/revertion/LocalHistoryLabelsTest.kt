// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration.revertion

import com.intellij.history.Label
import com.intellij.history.LocalHistory
import com.intellij.history.LocalHistoryException
import com.intellij.history.integration.IntegrationTestCase
import com.intellij.history.integration.LocalHistoryImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import junit.framework.TestCase

class LocalHistoryLabelsTest : IntegrationTestCase() {
  @Throws(Exception::class)
  fun testFileCreation() {
    val fileBeforeLabel = "first.txt"
    val fileAfterLabel = "second.txt"

    createChildData(myRoot, fileBeforeLabel)
    val testLabel = LocalHistory.getInstance().putSystemLabel(myProject, "testLabel")
    createChildData(myRoot, fileAfterLabel)

    testLabel.revert(myProject, myRoot)

    assertNull(myRoot.findChild(fileAfterLabel))
    assertNotNull(myRoot.findChild(fileBeforeLabel))
  }

  @Throws(Exception::class)
  fun testFileCreationAsFirstAction() {
    val beforeFileCreated = LocalHistory.getInstance().putSystemLabel(myProject, "beforeFileCreated")

    val fileName = "foo.txt"
    createChildData(myRoot, fileName)

    beforeFileCreated.revert(myProject, myRoot)

    assertNull(myRoot.findChild(fileName))
  }

  @Throws(Exception::class)
  fun testPutLabelAndRevertInstantly() {
    val fileName = "foo.txt"
    val content: Byte = 123
    createFile(fileName, content)

    val testLabel = LocalHistory.getInstance().putSystemLabel(myProject, "testLabel")
    testLabel.revert(myProject, myRoot)

    val file = myRoot.findChild(fileName)
    assertNotNull(file)
    TestCase.assertEquals(content, file!!.contentsToByteArray()[0])
  }

  @Throws(Exception::class)
  fun testFileDeletion() {
    val fileName = "foo.txt"
    val content: Byte = 123
    var file: VirtualFile? = createFile(fileName, content)

    val beforeDeletion = LocalHistory.getInstance().putSystemLabel(myProject, "testLabel")
    delete(file!!)
    beforeDeletion.revert(myProject, myRoot)

    file = myRoot.findChild(fileName)
    assertNotNull(file)
    TestCase.assertEquals(123, file!!.contentsToByteArray()[0].toInt())
  }

  @Throws(Exception::class)
  fun testFileDeletionWithContent() {
    val fileName = "foo.txt"
    var file: VirtualFile? = createChildData(myRoot, fileName)

    val beforeContentChange = LocalHistory.getInstance().putSystemLabel(myProject, "beforeContentChange")
    setContent(file!!, 123)
    delete(file)
    beforeContentChange.revert(myProject, myRoot)

    file = myRoot.findChild(fileName)
    assertNotNull(file)
    TestCase.assertEquals(0, file!!.contentsToByteArray().size)
  }

  @Throws(Exception::class)
  fun testParentAndChildRename() {
    val oldDirName = "dir"
    val oldFileName = "foo.txt"
    val content: Byte = 123
    var dir: VirtualFile? = createChildDirectory(myRoot, oldDirName)
    var file: VirtualFile? = createChildData(dir!!, oldFileName)
    setContent(file!!, content)

    val newDirName = "dir2"
    val newFileName = "bar.txt"

    val localHistory = LocalHistory.getInstance()
    val beforeDirRename = localHistory.putSystemLabel(myProject, "beforeDirRename")
    rename(dir, newDirName)
    val beforeFileRename = localHistory.putSystemLabel(myProject, "beforeFileRename")
    rename(file, newFileName)

    beforeFileRename.revert(myProject, file)

    dir = myRoot.findChild(newDirName)
    assertNotNull(dir)

    assertNull(dir!!.findChild(newFileName))
    file = dir.findChild(oldFileName)
    assertNotNull(file)
    TestCase.assertEquals(content, file!!.contentsToByteArray()[0])

    beforeDirRename.revert(myProject, myRoot)

    assertNull(myRoot.findChild(newDirName))
    dir = myRoot.findChild(oldDirName)
    assertNotNull(dir)
    assertNull(dir!!.findChild(newFileName))

    file = dir.findChild(oldFileName)
    assertNotNull(file)
    TestCase.assertEquals(content, file!!.contentsToByteArray()[0])
  }

  @Throws(Exception::class)
  fun testRevertContentChange() {
    val fileName = "foo.txt"
    val initialContent: Byte = 1
    var file: VirtualFile? = createFile(fileName, initialContent)

    val beforeFileModified = LocalHistory.getInstance().putSystemLabel(myProject, "initialFileContent")
    for (content in byteArrayOf(2, 3, 4)) {
      setContent(file!!, content)
    }
    beforeFileModified.revert(myProject, myRoot)

    file = myRoot.findChild(fileName)
    assertNotNull(file)
    TestCase.assertEquals(initialContent, file!!.contentsToByteArray()[0])
  }

  @Throws(Exception::class)
  fun testRevertContentChangeOnlyForFile() {
    val fileName1 = "foo.txt"
    val fileName2 = "foo2.txt"
    val initialContent: Byte = 1
    var file1: VirtualFile? = createFile(fileName1, initialContent)
    var file2: VirtualFile? = createFile(fileName2, initialContent)

    val beforeModifications = LocalHistory.getInstance().putSystemLabel(myProject, "beforeModifications")
    val lastContent: Byte = 10
    for (content in byteArrayOf(2, 3, 4, lastContent)) {
      setContent(file1!!, content)
      setContent(file2!!, content)
    }
    beforeModifications.revert(myProject, file1!!)

    file1 = myRoot.findChild(fileName1)
    assertNotNull(file1)
    TestCase.assertEquals(initialContent, file1!!.contentsToByteArray()[0])

    file2 = myRoot.findChild(fileName2)
    assertNotNull(file2)
    TestCase.assertEquals(lastContent, file2!!.contentsToByteArray()[0])
  }

  fun testValidLabel() {
    createChildData(myRoot, "foo.txt")
    val label = LocalHistory.getInstance().putSystemLabel(myProject, "testLabel")

    val found = runWithModalProgressBlocking(project, "Find") { LocalHistory.getInstance().isLabelValid(myProject, label.id) }

    assertTrue(found)
  }

  fun testInvalidLabels() {
    runWithModalProgressBlocking(project, "Find") { LocalHistory.getInstance().isLabelValid(myProject, Label.NON_EXISTENT_ID) }.let {
      assertFalse(it)
    }

    runWithModalProgressBlocking(project, "Find") { LocalHistory.getInstance().isLabelValid(myProject, "not-a-number") }.let {
      assertFalse(it)
    }

    runWithModalProgressBlocking(project, "Find") { LocalHistory.getInstance().isLabelValid(myProject, "999999999") }.let {
      assertFalse(it)
    }
  }

  fun testPurgedLabelIsInvalid() {
    val lh = LocalHistoryImpl.getInstanceImpl()
    runWithModalProgressBlocking(project, "Test purge") {
      val label = lh.putSystemLabel(myProject, "testLabel")
      lh.cleanupForNextTest()
      assertFalse(lh.isLabelValid(myProject, label.id))
    }
  }

  fun testRevertToLabelByIdRevertsContentChange() {
    val fileName = "foo.txt"
    val initialContent: Byte = 1
    val file = createFile(fileName, initialContent)

    val label = LocalHistory.getInstance().putSystemLabel(myProject, "initialFileContent")
    for (content in byteArrayOf(2, 3, 4)) {
      setContent(file, content)
    }

    runWithModalProgressBlocking(project, "Revert") {
      LocalHistory.getInstance().revertToLabel(myProject, label.id, myRoot)
    }

    val reverted = myRoot.findChild(fileName)
    assertNotNull(reverted)
    assertEquals(initialContent, reverted!!.contentsToByteArray()[0])
  }

  fun testRevertToLabelByIdThrowsForNullInstanceId() {
    assertThrows(IllegalArgumentException::class.java) {
      runWithModalProgressBlocking(project, "Revert") {
        LocalHistory.getInstance().revertToLabel(myProject, Label.NON_EXISTENT_ID, myRoot)
      }
    }
  }

  fun testRevertToLabelByIdThrowsForNonNumericId() {
    assertThrows(IllegalArgumentException::class.java) {
      runWithModalProgressBlocking(project, "Revert") {
        LocalHistory.getInstance().revertToLabel(myProject, "not-a-number", myRoot)
      }
    }
  }

  fun testRevertToLabelByIdThrowsForUnknownNumericId() {
    assertThrows(LocalHistoryException::class.java) {
      runWithModalProgressBlocking(this.project, "Revert") {
        LocalHistory.getInstance().revertToLabel(myProject, "999999999", myRoot)
      }
    }
  }

  private fun createFile(fileName: String, content: Byte): VirtualFile {
    val file = createChildData(myRoot, fileName)
    setContent(file, content)
    return file
  }
}
