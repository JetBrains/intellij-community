// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.quickFix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference

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
    myFixture.configureFromTempProjectFile("/main/java/pkg/ClassWithFileReference.java")
    myFixture.testHighlighting(true, false, true)

    withFileReferenceInStringLiteral {
      val ref = myFixture.getReferenceAtCaretPosition()
      val fileReference = (ref as PsiMultiReference).references.filterIsInstance<FileReference>()[0]

      assertEquals("my.properties", fileReference.fileNameToCreate)
      val intention = fileReference.quickFixes!![0]

      val options = (intention as CreateFilePathFix).myDirectories

      assertEquals(4, options.size)

      assertEquals("/src/main/resources", getPresentableText(options[0]))
      assertEquals("/src/test/resources", getPresentableText(options[1]))
      assertEquals("/src/main/java", getPresentableText(options[2]))
      assertEquals("/src/test/java", getPresentableText(options[3]))
    }
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
    assertIntentionCreatesFile("my-test.properties", "/test/resources/pkg/my-test.properties",
                               "/test/java/pkg/TestClassWithFileReference.java")
  }

  fun testCreatePathInTestSources() {
    ApplicationManager.getApplication().runWriteAction {
      // only src/test/java and src/main/java will be available for new files
      myFixture.tempDirFixture.getFile("/main/resources")!!.delete(null)
      myFixture.tempDirFixture.getFile("/test/resources")!!.delete(null)
    }

    assertIntentionCreatesFile("my-test.properties", "/test/java/pkg/my-test.properties",
                               "/test/java/pkg/TestClassWithFileReference.java")
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
      val fileReference = (ref as PsiMultiReference).references.filterIsInstance<FileReference>()[0]

      assertEquals(expectedFileName, fileReference.fileNameToCreate)
      val intention = fileReference.quickFixes!![0]

      myFixture.launchAction(intention as IntentionAction)

      val notFoundFile = myFixture.configureFromTempProjectFile(expectedFilePath)
      assertNotNull(notFoundFile)

      myFixture.checkResult("", true)
    }
  }

  private fun getPresentableText(dir: TargetDirectory) : String {
    return dir.directory!!.virtualFile.presentableUrl
  }
}