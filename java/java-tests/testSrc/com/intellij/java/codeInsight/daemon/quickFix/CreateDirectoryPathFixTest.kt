// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.quickFix

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class CreateDirectoryPathFixTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(CreateFileQuickFixTestUtil.STANDARD_CONTENT_ROOTS_DESCRIPTOR)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/createDirectoryPath"
    myFixture.copyDirectoryToProject("src", "")
  }

  @Test
  fun testCreateSourceDirectory(): Unit = timeoutRunBlocking {
    assertIntentionCreatesDir("srcdir", "/main/java/long/srcdir", "/main/java/pkg/ClassWithDirectoryReference.java")
  }

  @Test
  fun testCreateTestDirectory(): Unit = timeoutRunBlocking {
    assertIntentionCreatesDir("testdir", "/test/java/long/testdir", "/test/java/pkg/TestClassWithDirectoryReference.java")
  }

  private suspend fun assertIntentionCreatesDir(expectedDirName: String, expectedDirPath: String, javaSourcePath: String) {
    myFixture.configureFromTempProjectFile(javaSourcePath)
    myFixture.testHighlighting(true, false, true)

    withContext(Dispatchers.EDT) {
      CreateFileQuickFixTestUtil.withFileReferenceInStringLiteral(myFixture.project) {
        val ref = myFixture.getReferenceAtCaretPosition()
        val fileReference = CreateFileQuickFixTestUtil.findFileReference(ref)

        assertEquals(expectedDirName, fileReference.fileNameToCreate)
        val intention = fileReference.quickFixes!![0]

        myFixture.launchAction(intention as IntentionAction)

        val notFoundDirectory = myFixture.findFileInTempDir(expectedDirPath)
        assertTrue(notFoundDirectory.exists())
      }
    }
  }
}