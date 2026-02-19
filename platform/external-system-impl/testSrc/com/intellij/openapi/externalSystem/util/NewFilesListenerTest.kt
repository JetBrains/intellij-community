// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.externalSystem.autoimport.changes.NewFilesListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.writeText
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.ConcurrentSkipListSet

@TestApplication
internal class NewFilesListenerTest {

  /**
   * Generate files, watch, and make sure all newly created files are reported
   */
  @Test
  fun testFileNotification(@TestDisposable disposable: Disposable, @TempDir dir: Path): Unit = timeoutRunBlocking {
    val result = ConcurrentSkipListSet<String>()
    NewFilesListener.whenNewFilesCreated(action = { files ->
      val fileNames = files.map { it.name }
      Assertions.assertThat(fileNames).doesNotHaveDuplicates()
      assertThat("File already reported", result.intersect(fileNames.toSet()), Matchers.empty())
      result.addAll(fileNames)
    }, disposable)
    val dir = VirtualFileManager.getInstance().findFileByNioPath(dir)!!
    val canonicalFileNames = (0..100).map { "$it.txt" }
    for (fileName in canonicalFileNames) {
      val f = VfsTestUtil.createFile(dir, fileName)
      writeAction {
        f.writeText("hello")
      }
    }
    for (i in (0..10)) {
      if (result.size == canonicalFileNames.size) {
        break
      }
      delay(500)
    }
    assertThat("Wrong file names reported", result, Matchers.containsInAnyOrder(*canonicalFileNames.toTypedArray()))
  }
}
