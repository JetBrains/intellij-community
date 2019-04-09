// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.psi.PsiElement
import com.intellij.refactoring.extractMethodWithResultObject.ExtractMethodWithResultObjectHandler
import com.intellij.refactoring.extractMethodWithResultObject.ExtractMethodWithResultObjectProcessor
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author Pavel.Dolgov
 */
class ExtractMethodWithResultObjectTest : LightCodeInsightFixtureTestCase() {
  private val BASE_PATH = "/refactoring/extractMethodWithResultObject/"

  override fun getTestDataPath(): String {
    return JavaTestUtil.getJavaTestDataPath()
  }

  fun testSimple() = doTest()
  fun testCodeDuplicatesWithMultiOccurrences() = doTest()
  fun testAnonInner() = doTest()
  fun testArgumentFoldingMethodCall() = doTest()
  fun testArgumentFoldingWholeStatement() = doTest()
  fun testArgumentFoldingWholeStatementForUpdate() = doTest()
  fun testArgumentFoldingWholeStatementForUpdateList() = doTest()
  fun testArrayAccess() = doTest()
  fun testArrayAccess1() = doTest()
  fun testArrayAccessWithDuplicates() = doTest()
  fun testArrayAccessWithLocalIndex() = doTest()
  fun testArrayAccessWithTopExpression() = doTest()
  fun testArrayReturnType() = doTest()
  fun testAvoidGenericArgumentCast() = doTest()
  fun testAvoidGenericArgumentCastLocalClass() = doTest()
  fun testBeforeCommentAfterSelectedFragment() = doTest()
  fun testBooleanExpression() = doTest()
  fun testBoxedConditionalReturn() = doTest()
  fun testBuilderChainWith2DimArrayAccess() = doTest()
  fun testBuilderChainWithArrayAccess() = doTest()
  fun testBuilderChainWithArrayAccessExpr() = doTest()
  fun testBuilderChainWithArrayAccessIf() = doTest()
  fun testCallChainExpression() = doTest()
  fun testCallOn2DimArrayElement() = doTest()
  fun testCallOnArrayElement() = doTest()
  fun testCallOnFieldArrayElement() = doTest()
  fun testCantPassFieldAsParameter() = doTest()
  fun testCast4ParamGeneration() = doTest()
  fun testCastWhenDuplicateReplacement() = doTest()
  fun testChainedConstructor() = doTest()
  fun testChainedConstructorDuplicates() = doTest()
  fun testChainedConstructorInvalidDuplicates() = doTest()
  fun testChangedReturnType() = doTest(false) // todo
  fun testCheckQualifierMapping() = doTest()
  fun testClassReference2() = doTest()
  fun testClassReference() = doTest()
  fun testCodeDuplicates2() = doTest()
  fun testCodeDuplicates3() = doTest()
  fun testCodeDuplicates4() = doTest()
  fun testCodeDuplicates5() = doTest()
  fun testCodeDuplicates() = doTest()
  fun testCodeDuplicatesVarargsShouldNotChangeReturnType() = doTest()
  fun testCodeDuplicatesWithComments() = doTest()
  fun testCodeDuplicatesWithContinue() = doTest()
  fun testCodeDuplicatesWithContinueNoReturn() = doTest()
  fun testCodeDuplicatesWithEmptyStatementsBlocksParentheses() = doTest()
  fun testCodeDuplicatesWithMultExitPoints() = doTest()
  fun testCodeDuplicatesWithOutputValue1() = doTest()
  fun testCodeDuplicatesWithOutputValue() = doTest()
  fun testCodeDuplicatesWithReturn2() = doTest()
  fun testCodeDuplicatesWithReturnInAnonymous() = doTest()
  fun testCodeDuplicatesWithReturn() = doTest()
  fun testCodeDuplicatesWithStaticInitializer() = doTest()
  fun testComplexTypeParams() = doTest()
  fun testConditionalExitCombinedWithNullabilityShouldPreserveVarsUsedInExitStatements() = doTest()
  fun testConditionalReturnInDuplicate() = doTest()
  fun testConditionalReturnVsAssignDuplicate() = doTest()
  fun testConditionalWithTwoParameters() = doTest()
  fun testConflictingAnonymous() = doTest(false) // todo
  fun testConstantConditionsAffectingControlFlow1() = doTest()
  fun testConstantConditionsAffectingControlFlow() = doTest()
  fun testContinueInside() = doTest()
  fun testCopyParamAnnotations() = doTest()
  fun testParameterUsedInReturnedExpression() = doTest()
  fun testParameterUsedInReturnCondition() = doTest()
  fun testMethodUsedInReturnCondition() = doTest()
  fun testMethodFromCycle() = doTest()

  fun test() = doTest()

  private fun doTest(checkHighlighting: Boolean = true) {
    val testName = getTestName(false)
    if (testName.isEmpty()) return

    myFixture.configureByFile("$BASE_PATH$testName.java")

    performAction()

    if (checkHighlighting) {
      myFixture.checkHighlighting()
    }
    myFixture.checkResultByFile(BASE_PATH + testName + "_after.java", true)
  }

  private fun performAction() {
    val elements: Array<PsiElement> = getSelectedElements()
    assertTrue("elements", elements.isNotEmpty())

    val processor = ExtractMethodWithResultObjectProcessor(project, editor, elements)
    val prepared = processor.prepare()
    assertTrue("prepare", prepared)

    ExtractMethodWithResultObjectHandler.extractMethodWithResultObjectImpl(processor)
  }

  private fun getSelectedElements(): Array<PsiElement> {
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
