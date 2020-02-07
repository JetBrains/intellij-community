// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.problems

import com.intellij.codeInsight.daemon.problems.ui.ProjectProblemsView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.replaceService
import java.util.concurrent.Executor

internal abstract class ProjectProblemsViewTest : LightJavaCodeInsightFixtureTestCase() {
  protected fun doTest(targetClass: PsiClass, testBody: (ProblemsCollector) -> Unit) {
    val problemsCollector = ProblemsCollector()
    myFixture.project.replaceService(ProjectProblemsView::class.java, problemsCollector, testRootDisposable)

    myFixture.openFileInEditor(targetClass.containingFile.virtualFile)
    myFixture.doHighlighting()

    assertEmpty(problemsCollector.getProblems(targetClass.containingFile.virtualFile))

    testBody(problemsCollector)
  }

  internal data class Problem(val file: VirtualFile, val message: String, val place: Navigatable)

  internal class ProblemsCollector : ProjectProblemsView {

    val problems: MutableSet<Problem> = mutableSetOf()

    override fun addProblem(file: VirtualFile, message: String, place: Navigatable) {
      problems.add(Problem(file, message, place))
    }

    override fun removeProblems(file: VirtualFile, place: Navigatable?) {
      problems.removeIf { it.file == file && (place == null || it.place == place) }
    }

    override fun getProblems(file: VirtualFile): List<Navigatable> = problems.filter { it.file == file }.map { it.place }

    override fun init(toolWindow: ToolWindow) {
    }

    override fun executor(): Executor = Executor { ApplicationManager.getApplication().invokeAndWait(it) }
  }

  companion object {
    internal inline fun<reified T: PsiElement> hasReportedProblems(refClass: PsiClass, problemsCollector: ProblemsCollector): Boolean {
      val elements = PsiTreeUtil.findChildrenOfType(refClass, T::class.java)
      assertSize(1, elements)
      val element = elements.first()
      val refFile = refClass.containingFile.virtualFile
      return problemsCollector.getProblems(refFile).any { it is PsiElement && PsiTreeUtil.isAncestor(element, it, false) }
    }
  }
}