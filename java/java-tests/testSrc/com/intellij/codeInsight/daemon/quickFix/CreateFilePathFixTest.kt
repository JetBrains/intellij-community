// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.quickFix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.ApplicationManager

class CreateFilePathFixTest : CreateFileQuickFixTestCase() {

  override fun setUp() {
    super.setUp()

    myFixture.copyDirectoryToProject("src", "")
  }

  override fun getTestCasePath(): String {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createFilePath"
  }

  fun testCreatePathWithSingleSourceRoot() {
    ApplicationManager.getApplication().runWriteAction {
      // only src/main/java will be available for new files
      myFixture.tempDirFixture.getFile("/main/resources")!!.delete(null)
      myFixture.tempDirFixture.getFile("/test/resources")!!.delete(null)
      myFixture.tempDirFixture.getFile("/test/java")!!.delete(null)
    }

    assertIntentionCreatesFile("my.properties", "/main/java/pkg/my.properties",
                               "/main/java/pkg/ClassWithFileReference.java")
  }

  fun testCreatePathExcludesGeneratedSources() {
    assertCreateFileOptions("my.properties",
                            listOf("/src/main/resources", "/src/main/java"),
                            "/main/java/pkg/ClassWithFileReference.java")
  }

  fun testCreatePathExcludesGeneratedSourcesIfNoResourceRootsPresent() {
    ApplicationManager.getApplication().runWriteAction {
      // only src/main/java and src/main/gen will be available for new files
      myFixture.tempDirFixture.getFile("/main/resources")!!.delete(null)
    }
    assertCreateFileOptions("my.properties",
                            listOf("/src/main/java"),
                            "/main/java/pkg/ClassWithFileReference.java")
  }

  fun testCreatePathInSources() {
    ApplicationManager.getApplication().runWriteAction {
      // only src/main/java and /src/test/java will be available for new files
      myFixture.tempDirFixture.getFile("/main/resources")!!.delete(null)
      myFixture.tempDirFixture.getFile("/test/resources")!!.delete(null)
    }

    assertIntentionCreatesFile("my.properties", "/main/java/pkg/my.properties",
                               "/main/java/pkg/ClassWithFileReference.java")
  }

  fun testCreatePathInResources() {
    assertIntentionCreatesFile("my.properties", "/main/resources/pkg/my.properties",
                               "/main/java/pkg/ClassWithFileReference.java")
  }

  fun testCreatePathInTestResources() {
    val testSource = "/test/java/pkg/TestClassWithFileReference.java"

    assertCreateFileOptions("my-test.properties",
                            listOf("/src/test/resources", "/src/main/resources", "/src/test/java", "/src/main/java"),
                            testSource)

    assertIntentionCreatesFile("my-test.properties", "/test/resources/pkg/my-test.properties", testSource)
  }

  fun testCreatePathInTestSources() {
    ApplicationManager.getApplication().runWriteAction {
      // only src/test/java and src/main/java will be available for new files
      myFixture.tempDirFixture.getFile("/main/resources")!!.delete(null)
      myFixture.tempDirFixture.getFile("/test/resources")!!.delete(null)
    }

    val testSource = "/test/java/pkg/TestClassWithFileReference.java"

    assertCreateFileOptions("my-test.properties",
                            listOf("/src/test/java", "/src/main/java"),
                            testSource)

    assertIntentionCreatesFile("my-test.properties", "/test/java/pkg/my-test.properties", testSource)
  }

  fun testCreateIntermediateSourcePathAutomatically() {
    assertIntentionCreatesFile("my.properties", "/main/resources/long/path/source/my.properties",
                               "/main/java/pkg/ClassWithLongFileReference.java")
  }

  fun testCreateIntermediateTestPathAutomatically() {
    assertIntentionCreatesFile("my-test.properties", "/test/resources/long/path/source/my-test.properties",
                               "/test/java/pkg/TestClassWithLongFileReference.java")
  }

  private fun assertIntentionCreatesFile(expectedFileName: String, expectedFilePath: String, javaSourcePath: String) {
    myFixture.configureFromTempProjectFile(javaSourcePath)
    myFixture.testHighlighting(true, false, true)

    withFileReferenceInStringLiteral {
      val ref = myFixture.getReferenceAtCaretPosition()
      val fileReference = findFileReference(ref)

      assertEquals(expectedFileName, fileReference.fileNameToCreate)
      val intention = fileReference.quickFixes!![0]

      myFixture.launchAction(intention as IntentionAction)

      val notFoundFile = myFixture.configureFromTempProjectFile(expectedFilePath)
      assertNotNull(notFoundFile)

      myFixture.checkResult("", true)
    }
  }

  private fun assertCreateFileOptions(expectedFileName: String, expectedOptions: List<String>, javaSourcePath: String) {
    myFixture.configureFromTempProjectFile(javaSourcePath)
    myFixture.testHighlighting(true, false, true)

    withFileReferenceInStringLiteral {
      val ref = myFixture.getReferenceAtCaretPosition()
      val fileReference = findFileReference(ref)

      assertEquals(expectedFileName, fileReference.fileNameToCreate)
      val intention = fileReference.quickFixes!![0]

      val options = (intention as AbstractCreateFileFix).myDirectories

      assertEquals(expectedOptions.size, options.size)

      assertEquals(expectedOptions, options.map { getTargetPath(it) })
    }
  }

  private fun getTargetPath(dir: TargetDirectory) : String {
    return dir.directory!!.virtualFile.path
  }
}