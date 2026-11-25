// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.psi.PsiMember
import com.intellij.refactoring.util.duplicates.MethodDuplicatesHandler
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.fail

/**
 * The fixture of the tests of the method duplicates refactoring. Create it with [findMethodDuplicatesFixture].
 */
class FindMethodDuplicatesFixture internal constructor(
  val fixture: LightJavaCodeInsightTestFixture,
  private val dataDir: String,
) {
  /**
   * Configures the file `<dataDir><test name>.java`. Then it replaces the duplicates of the member at the caret in this file.
   * With [shouldSucceed], the result must be equal to the file with the `.after` suffix.
   * Without [shouldSucceed], the refactoring must fail.
   */
  @JvmOverloads
  fun doTest(shouldSucceed: Boolean = true) {
    val filePath = dataDir + fixture.getTestName(false) + ".java"
    fixture.configureByFile(filePath)
    fixture.runInCommand {
      val targetElement = TargetElementUtil.findTargetElement(fixture.editor, TargetElementUtil.ELEMENT_NAME_ACCEPTED)
      assertTrue(targetElement is PsiMember, "<caret> is not on method name")
      val psiMethod = targetElement as PsiMember

      assertSame(TestDialog.DEFAULT, TestDialogManager.getTestImplementation())
      try {
        MethodDuplicatesHandler.invokeOnScope(fixture.project, psiMethod, AnalysisScope(fixture.file))
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      }
      catch (_: RuntimeException) {
        if (shouldSucceed) {
          fail("duplicates were not found")
        }
        return@runInCommand
      }
      if (shouldSucceed) {
        fixture.checkResultByFile("$filePath.after")
      }
      else {
        fail("duplicates found; file content: " + fixture.file.text)
      }
    }
  }
}

/**
 * Creates a [FindMethodDuplicatesFixture] for one test in the project of [projectFixture].
 * The test data of the test is in the directory [dataDir] of the Java test data, for example `/refactoring/methodDuplicates/`.
 */
fun findMethodDuplicatesFixture(projectFixture: TestFixture<LightJavaProject>, dataDir: String): TestFixture<FindMethodDuplicatesFixture> =
  testFixture("findMethodDuplicatesFixture") {
    val fixture = lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData").init()
    initialized(FindMethodDuplicatesFixture(fixture, dataDir)) {}
  }
