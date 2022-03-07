// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.problems

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.daemon.problems.pass.ProjectProblemUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

internal abstract class ProjectProblemsViewTest : LightJavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    TestModeFlags.set(ProjectProblemUtils.ourTestingProjectProblems, true, testRootDisposable)
    super.setUp()
  }

  protected fun doTest(targetClass: PsiClass, testBody: () -> Unit) {
    myFixture.openFileInEditor(targetClass.containingFile.virtualFile)
    project.putUserData(CodeVisionHost.isCodeVisionTestKey, true)
    myFixture.doHighlighting()

    assertEmpty(getProblems())

    testBody()
  }

  override fun tearDown() {
    project.putUserData(CodeVisionHost.isCodeVisionTestKey, null)
    super.tearDown()
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_16
  }

  protected fun getProblems(editor: Editor = myFixture.editor) =
    ProjectProblemUtils.getReportedProblems(editor).flatMap { it.value }.map { it.reportedElement }

  protected inline fun <reified T : PsiElement> hasReportedProblems(vararg refClasses: PsiClass): Boolean {
    for (refClass in refClasses) {
      val element = PsiTreeUtil.findChildOfAnyType(refClass, false, T::class.java)!!
      val problems = getProblems()
      if (problems.none { problemElement -> PsiTreeUtil.isAncestor(element, problemElement, false) }) return false
    }
    return true
  }
}