// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.quickFix

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.quickFix.AbstractCreateFileFix
import com.intellij.codeInsight.daemon.quickFix.TargetDirectory
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class CreateFilePathFixTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(CreateFileQuickFixTestUtil.STANDARD_CONTENT_ROOTS_DESCRIPTOR)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/createFilePath"
    myFixture.copyDirectoryToProject("src", "")
  }

  @Test
  fun testCreatePathWithSingleSourceRoot(): Unit = timeoutRunBlocking {
    val dirs = listOf("/main/resources", "/test/resources", "/test/java").map { myFixture.tempDirFixture.getFile(it)!! }
    edtWriteAction {
      // only src/main/java will be available for new files
      dirs.forEach { it.delete(null) }
    }

    assertIntentionCreatesFile("my.properties", "/main/java/pkg/my.properties",
                               "/main/java/pkg/ClassWithFileReference.java")
  }

  @Test
  fun testCreatePathExcludesGeneratedSources(): Unit = timeoutRunBlocking {
    assertCreateFileOptions("my.properties",
                            listOf("/src/main/resources", "/src/main/java"),
                            "/main/java/pkg/ClassWithFileReference.java")
  }

  @Test
  fun testCreatePathExcludesGeneratedSourcesIfNoResourceRootsPresent(): Unit = timeoutRunBlocking {
    val dirs = listOf("/main/resources").map { myFixture.tempDirFixture.getFile(it)!! }
    edtWriteAction {
      // only src/main/java and src/main/gen will be available for new files
      dirs.forEach { it.delete(null) }
    }
    assertCreateFileOptions("my.properties",
                            listOf("/src/main/java"),
                            "/main/java/pkg/ClassWithFileReference.java")
  }

  @Test
  fun testCreatePathInSources(): Unit = timeoutRunBlocking {
    val dirs = listOf("/main/resources", "/test/resources").map { myFixture.tempDirFixture.getFile(it)!! }
    edtWriteAction {
      // only src/main/java and /src/test/java will be available for new files
      dirs.forEach { it.delete(null) }
    }

    assertIntentionCreatesFile("my.properties", "/main/java/pkg/my.properties",
                               "/main/java/pkg/ClassWithFileReference.java")
  }

  @Test
  fun testCreatePathInResources(): Unit = timeoutRunBlocking {
    assertIntentionCreatesFile("my.properties", "/main/resources/pkg/my.properties",
                               "/main/java/pkg/ClassWithFileReference.java")
  }

  @Test
  fun testCreatePathInTestResources(): Unit = timeoutRunBlocking {
    val testSource = "/test/java/pkg/TestClassWithFileReference.java"

    assertCreateFileOptions("my-test.properties",
                            listOf("/src/test/resources", "/src/main/resources", "/src/test/java", "/src/main/java"),
                            testSource)

    assertIntentionCreatesFile("my-test.properties", "/test/resources/pkg/my-test.properties", testSource)
  }

  @Test
  fun testCreatePathInTestSources(): Unit = timeoutRunBlocking {
    val dirs = listOf("/main/resources", "/test/resources").map { myFixture.tempDirFixture.getFile(it)!! }
    edtWriteAction {
      // only src/test/java and src/main/java will be available for new files
      dirs.forEach { it.delete(null) }
    }

    val testSource = "/test/java/pkg/TestClassWithFileReference.java"

    assertCreateFileOptions("my-test.properties",
                            listOf("/src/test/java", "/src/main/java"),
                            testSource)

    assertIntentionCreatesFile("my-test.properties", "/test/java/pkg/my-test.properties", testSource)
  }

  @Test
  fun testCreateIntermediateSourcePathAutomatically(): Unit = timeoutRunBlocking {
    assertIntentionCreatesFile("my.properties", "/main/resources/long/path/source/my.properties",
                               "/main/java/pkg/ClassWithLongFileReference.java")
  }

  @Test
  fun testCreateIntermediateTestPathAutomatically(): Unit = timeoutRunBlocking {
    assertIntentionCreatesFile("my-test.properties", "/test/resources/long/path/source/my-test.properties",
                               "/test/java/pkg/TestClassWithLongFileReference.java")
  }

  private suspend fun assertIntentionCreatesFile(expectedFileName: String, expectedFilePath: String, javaSourcePath: String) {
    myFixture.configureFromTempProjectFile(javaSourcePath)
    myFixture.testHighlighting(true, false, true)

    withContext(Dispatchers.EDT) {
      CreateFileQuickFixTestUtil.withFileReferenceInStringLiteral(myFixture.project) {
        val ref = myFixture.getReferenceAtCaretPosition()
        val fileReference = CreateFileQuickFixTestUtil.findFileReference(ref)

        assertEquals(expectedFileName, fileReference.fileNameToCreate)
        val intention = fileReference.quickFixes!![0]

        myFixture.launchAction(intention as IntentionAction)

        val notFoundFile = myFixture.configureFromTempProjectFile(expectedFilePath)
        assertNotNull(notFoundFile)

        myFixture.checkResult("", true)
      }
    }
  }

  private suspend fun assertCreateFileOptions(expectedFileName: String, expectedOptions: List<String>, javaSourcePath: String) {
    myFixture.configureFromTempProjectFile(javaSourcePath)
    myFixture.testHighlighting(true, false, true)

    withContext(Dispatchers.EDT) {
      CreateFileQuickFixTestUtil.withFileReferenceInStringLiteral(myFixture.project) {
        val ref = myFixture.getReferenceAtCaretPosition()
        val fileReference = CreateFileQuickFixTestUtil.findFileReference(ref)

        assertEquals(expectedFileName, fileReference.fileNameToCreate)
        val intention = fileReference.quickFixes!![0]

        val options = (intention as AbstractCreateFileFix).directories

        assertEquals(expectedOptions.size, options.size)

        assertEquals(expectedOptions, options.map { getTargetPath(it) })
      }
    }
  }

  private fun getTargetPath(dir: TargetDirectory) : String {
    return "/" + VfsUtilCore.getRelativePath(dir.directory!!.virtualFile, ModuleRootManager.getInstance(myFixture.module).contentRoots.single().parent)
  }
}