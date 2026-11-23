// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.problems

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.daemon.problems.pass.ProjectProblemUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.assertj.core.api.Assertions.assertThat

/**
 * The fixture of the project problems tests. Create it with [projectProblemsFixture].
 *
 * The members open a class in the editor, highlight it, and read the problems that the highlighting reports.
 * Call them on the EDT, as JUnit 3 did.
 */
internal class ProjectProblemsFixture(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
) {
  /**
   * Opens [targetClass] in the editor, checks that the highlighting reports no problems, and runs [testBody].
   */
  fun doTest(targetClass: PsiClass, testBody: () -> Unit) {
    fixture.openFileInEditor(targetClass.containingFile.virtualFile)
    fixture.project.putUserData(CodeVisionHost.isCodeVisionTestKey, true)
    fixture.doHighlighting()

    assertThat(getProblems()).isEmpty()

    testBody()
  }

  /**
   * Returns the elements of the problems that the highlighting reported for [editor].
   */
  fun getProblems(editor: Editor = fixture.editor): List<PsiElement> =
    ProjectProblemUtils.getReportedProblems(editor).flatMap { it.value }.map { it.reportedElement }

  /**
   * Returns `true` when a problem is reported inside the first element of type [T] of each class of [refClasses].
   */
  inline fun <reified T : PsiElement> hasReportedProblems(vararg refClasses: PsiClass): Boolean {
    for (refClass in refClasses) {
      val element = PsiTreeUtil.findChildOfAnyType(refClass, false, T::class.java)!!
      val problems = getProblems()
      if (problems.none { problemElement -> PsiTreeUtil.isAncestor(element, problemElement, false) }) return false
    }
    return true
  }
}

/**
 * Creates the project of the project problems tests, with the [LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK] descriptor.
 * Declare it in a companion object.
 *
 * The fixture turns on the code vision and the project problems in the test mode before it opens the project.
 * The startup activity of the project problems registers its listeners only in this mode.
 */
internal fun projectProblemsProjectFixture(): TestFixture<LightJavaProject> = testFixture("projectProblemsProjectFixture") {
  val flagsDisposable = Disposer.newDisposable("projectProblemsProjectFixture")
  TestModeFlags.set(CodeVisionHost.isCodeVisionTestKey, true, flagsDisposable)
  TestModeFlags.set(ProjectProblemUtils.ourTestingProjectProblems, true, flagsDisposable)
  val lightProject = try {
    lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK).init()
  }
  catch (e: Throwable) {
    Disposer.dispose(flagsDisposable)
    throw e
  }
  initialized(lightProject) {
    Disposer.dispose(flagsDisposable)
  }
}

/**
 * Creates a [ProjectProblemsFixture] for one test. Declare it as an instance field.
 *
 * The fixture turns on the code vision and the project problems in the test mode for the test.
 * After the test, it removes the code vision key from the project.
 */
internal fun projectProblemsFixture(projectFixture: TestFixture<LightJavaProject>): TestFixture<ProjectProblemsFixture> =
  testFixture("projectProblemsFixture") {
    val fixture = lightJavaCodeInsightFixture(projectFixture).init()
    TestModeFlags.set(CodeVisionHost.isCodeVisionTestKey, true, fixture.testRootDisposable)
    TestModeFlags.set(ProjectProblemUtils.ourTestingProjectProblems, true, fixture.testRootDisposable)
    initialized(ProjectProblemsFixture(fixture)) {
      fixture.project.putUserData(CodeVisionHost.isCodeVisionTestKey, null)
    }
  }
