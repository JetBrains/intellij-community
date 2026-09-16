// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeBytes
import com.intellij.openapi.vfs.writeText
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestUtil.TEST_EXTERNAL_SYSTEM_ID
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.utils.vfs.createFile
import com.intellij.testFramework.utils.vfs.getDocument
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class CrcUtilsTest {

  private val project by projectFixture()

  private val testRoot by testFixture {
    initialized(
      tempPathFixture().init()
        .refreshAndGetVirtualDirectory()
    ) {}
  }

  private val calculator by extensionPointFixture(ExternalSystemCrcCalculator.EP_NAME) { RecordingCrcCalculator() }

  @Test
  fun `crc is cached for a file until its content changes`(): Unit =
    timeoutRunBlocking(context = Dispatchers.IO) {
      val file = edtWriteAction {
        testRoot.createFile("build.txt")
          .apply { writeText("content-1") }
      }

      val firstCrc = getOrCalculateCrc(project, TEST_EXTERNAL_SYSTEM_ID, file)
      val secondCrc = getOrCalculateCrc(project, TEST_EXTERNAL_SYSTEM_ID, file)

      assertThat(secondCrc).isEqualTo(firstCrc)
      assertThat(calculator.getInvocationCount()).isEqualTo(1)

      edtWriteAction { file.writeText("content-2") }
      val thirdCrc = getOrCalculateCrc(project, TEST_EXTERNAL_SYSTEM_ID, file)

      assertThat(thirdCrc).isNotEqualTo(firstCrc)
      assertThat(calculator.getInvocationCount()).isEqualTo(2)
    }

  @Test
  fun `crc is cached for a document until its content changes`(): Unit =
    timeoutRunBlocking(context = Dispatchers.IO) {
      val file = edtWriteAction {
        testRoot.createFile("build.txt")
          .apply { writeText("content-1") }
      }

      val document = readAction { file.getDocument() }

      val firstCrc = getOrCalculateCrc(project, TEST_EXTERNAL_SYSTEM_ID, file)
      val secondCrc = getOrCalculateCrc(project, TEST_EXTERNAL_SYSTEM_ID, file)

      assertThat(secondCrc).isEqualTo(firstCrc)
      assertThat(calculator.getInvocationCount()).isEqualTo(1)

      edtWriteAction { document.setText("content-2") }
      val thirdCrc = getOrCalculateCrc(project, TEST_EXTERNAL_SYSTEM_ID, file)

      assertThat(thirdCrc).isNotEqualTo(firstCrc)
      assertThat(calculator.getInvocationCount()).isEqualTo(2)
    }

  @Test
  fun `crc is null for directory`(): Unit =
    timeoutRunBlocking(context = Dispatchers.IO) {
      val crc = getOrCalculateCrc(project, TEST_EXTERNAL_SYSTEM_ID, testRoot)
      assertThat(crc).isNull()
    }

  @Test
  fun `crc is not null for binary file`(): Unit =
    timeoutRunBlocking(context = Dispatchers.IO) {
      associateExtension(ArchiveFileType.INSTANCE, "bin") {
        val file = edtWriteAction {
          testRoot.createFile("data.bin")
            .apply { writeBytes(byteArrayOf(1, 2, 3)) }
        }

        val crc = getOrCalculateCrc(project, TEST_EXTERNAL_SYSTEM_ID, file)

        assertThat(crc).isNotNull()
        assertThat(crc).isNotEqualTo(0L)
        assertThat(calculator.getInvocationCount()).isEqualTo(0)
      }
    }

  @Test
  fun `crc is not changed by blank lines`(): Unit =
    timeoutRunBlocking(context = Dispatchers.IO) {
      val file = edtWriteAction {
        testRoot.createFile("build.properties")
          .apply { writeText("key=value") }
      }

      val originalCrc = getOrCalculateCrc(project, TEST_EXTERNAL_SYSTEM_ID, file)
      assertThat(calculator.getInvocationCount()).isEqualTo(1)

      edtWriteAction { file.writeText("\n\nkey=value\n\n") }
      val crcWithBlankLines = getOrCalculateCrc(project, TEST_EXTERNAL_SYSTEM_ID, file)

      assertThat(crcWithBlankLines).isEqualTo(originalCrc)
      assertThat(calculator.getInvocationCount()).isEqualTo(2)
    }

  @Test
  fun `crc is not changed by comments`(): Unit =
    timeoutRunBlocking(context = Dispatchers.IO) {
      val file = edtWriteAction {
        testRoot.createFile("build.properties")
          .apply { writeText("key=value") }
      }

      val originalCrc = getOrCalculateCrc(project, TEST_EXTERNAL_SYSTEM_ID, file)
      assertThat(calculator.getInvocationCount()).isEqualTo(1)

      edtWriteAction { file.writeText("# a comment\nkey=value\n! another comment") }
      val crcWithComments = getOrCalculateCrc(project, TEST_EXTERNAL_SYSTEM_ID, file)

      assertThat(crcWithComments).isEqualTo(originalCrc)
      assertThat(calculator.getInvocationCount()).isEqualTo(2)
    }

  @Test
  fun `crc of empty file is 0`(): Unit =
    timeoutRunBlocking(context = Dispatchers.IO) {
      val file = edtWriteAction { testRoot.createFile("build.properties") }

      val crc = getOrCalculateCrc(project, TEST_EXTERNAL_SYSTEM_ID, file)

      assertThat(crc).isEqualTo(0L)
      assertThat(calculator.getInvocationCount()).isEqualTo(1)
    }

  private class RecordingCrcCalculator : ExternalSystemCrcCalculator by DefaultCrcCalculator {

    private var invocationCount = 0

    fun getInvocationCount() = invocationCount

    override fun isApplicable(systemId: ProjectSystemId, file: VirtualFile): Boolean {
      return DefaultCrcCalculator.isApplicable(systemId, file)
    }

    override fun calculateCrc(project: Project, file: VirtualFile, fileText: CharSequence): Long? {
      return DefaultCrcCalculator.calculateCrc(project, file, fileText)
        .also { invocationCount++ }
    }
  }

  companion object {

    @Suppress("SameParameterValue")
    private suspend fun associateExtension(type: FileType, extension: String, action: suspend () -> Unit) {
      val fileTypeManager = FileTypeManager.getInstance()
      edtWriteAction {
        fileTypeManager.associateExtension(type, extension)
      }
      try {
        action()
      }
      finally {
        edtWriteAction {
          fileTypeManager.removeAssociatedExtension(type, extension)
        }
      }
    }
  }
}
