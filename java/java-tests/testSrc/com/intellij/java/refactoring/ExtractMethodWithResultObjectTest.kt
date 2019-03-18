// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.extractMethodWithResultObject.ExtractMethodWithResultObjectHandler
import com.intellij.refactoring.extractMethodWithResultObject.ExtractMethodWithResultObjectProcessor
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase
import com.intellij.testFramework.LightCodeInsightTestCase

/**
 * @author Pavel.Dolgov
 */
class ExtractMethodWithResultObjectTest : LightCodeInsightTestCase() {
  private val BASE_PATH = "/refactoring/extractMethodWithResultObject/"

  override fun getTestDataPath(): String {
    return JavaTestUtil.getJavaTestDataPath()
  }

  fun testSimple() {
    doTest()
  }

  private fun doTest() {
    configureByFile(BASE_PATH + getTestName(false) + ".java")

    performAction()

    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java")
  }

  private fun performAction() {
    val editor = getEditor()
    val project = getProject()
    val elements: Array<PsiElement> = getSelectedElements(editor, project)
    assertTrue("elements", elements.isNotEmpty())

    val processor = ExtractMethodWithResultObjectProcessor(project, editor, elements)
    val prepared = processor.prepare()
    assertTrue("prepare", prepared)

    ExtractMethodWithResultObjectHandler.extractMethodWithResultObjectImpl(processor)
  }

  private fun getSelectedElements(editor: Editor, project: Project): Array<PsiElement> {
    val file = getFile()
    val startOffset = editor.selectionModel.selectionStart
    val endOffset = editor.selectionModel.selectionEnd

    val expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset)
    if (expr != null) {
      return arrayOf(expr)
    }
    val elements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset)
    if (elements.isEmpty()) {
      val expression = IntroduceVariableBase.getSelectedExpression(project, file, startOffset, endOffset)
      if (expression != null) {
        return arrayOf(expression)
      }
    }
    return elements
  }
}
