// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.problems

import com.intellij.codeInsight.daemon.problems.pass.ProjectProblemPassUtils
import com.intellij.codeInsight.hints.BlockInlayRenderer
import com.intellij.codeInsight.hints.presentation.*
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageViewManager
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JPanel

internal abstract class ProjectProblemsViewTest : LightJavaCodeInsightFixtureTestCase() {
  protected fun doTest(targetClass: PsiClass, testBody: () -> Unit) {
    myFixture.openFileInEditor(targetClass.containingFile.virtualFile)
    myFixture.doHighlighting()

    assertEmpty(getProblems(targetClass.containingFile))

    testBody()
  }

  protected fun getProblems(psiFile: PsiFile): List<PsiElement> {
    val reportedChanges: MutableMap<PsiMember, Inlay<*>> = ProjectProblemPassUtils.getInlays(myFixture.editor)
    val targetFile = psiFile.virtualFile
    val problems: MutableList<PsiElement> = mutableListOf()

    val usageViewManager = UsageViewManager.getInstance(project)
    val editorManager = FileEditorManager.getInstance(project)
    val clickEvent = MouseEvent(JPanel(), 0, 0, 0, 0, 0, 0, true, MouseEvent.BUTTON1)
    val point = Point(0, 0)
    for (inlay in reportedChanges.values) {
      val renderer = inlay.renderer as BlockInlayRenderer
      val presentation = renderer.getConstrainedPresentations()[0]
      val rootPresentation = presentation.root as RecursivelyUpdatingRootPresentation
      val hoverPresentation = rootPresentation.content as OnHoverPresentation
      hoverPresentation.mouseMoved(clickEvent, point)
      val usagesSequencePresentation = (hoverPresentation.presentation as SequencePresentation).presentations[0] as SequencePresentation
      val usagesHoverPresentation = usagesSequencePresentation.presentations[1] as OnHoverPresentation
      usagesHoverPresentation.mouseMoved(clickEvent, point)
      val delegatePresentation = usagesHoverPresentation.presentation as DynamicDelegatePresentation
      val onClickPresentation = delegatePresentation.delegate as OnClickPresentation
      onClickPresentation.mouseClicked(clickEvent, point)
      val editor = editorManager.selectedTextEditor!!
      val openedFile = FileDocumentManager.getInstance().getFile(editor.document)!!
      if (openedFile != targetFile) {
        // one problem is reported in inlay, we navigated to this problem
        val usagePsiFile = PsiManager.getInstance(project).findFile(openedFile)!!
        val psiElement = usagePsiFile.findElementAt(editor.caretModel.offset)!!
        problems.add(psiElement)
        // restore file for the next iteration
        editorManager.openFile(targetFile, true)
      }
      else {
        // multiple problems, usage tool window was opened
        val usageView = usageViewManager.selectedUsageView!!
        usageView.usages.mapTo(problems) { (it as UsageInfo2UsageAdapter).usageInfo.element!! }
      }
    }
    return problems
  }

  protected inline fun <reified T : PsiElement> hasReportedProblems(targetClass: PsiClass, vararg refClasses: PsiClass): Boolean {
    for (refClass in refClasses) {
      val element = PsiTreeUtil.findChildOfAnyType(refClass, false, T::class.java)!!
      val problems = getProblems(targetClass.containingFile)
      if (problems.none { problemElement -> PsiTreeUtil.isAncestor(element, problemElement, false) }) return false
    }
    return true
  }
}