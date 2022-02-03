// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.pom.java.LanguageLevel
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.extractMethod.newImpl.MethodExtractor
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightJavaCodeInsightTestCase
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NonNls

class ExtractMethodAndDuplicatesInplaceTest: LightJavaCodeInsightTestCase() {

  private val BASE_PATH: @NonNls String = "/refactoring/extractMethodAndDuplicatesInplace"

  override fun setUp() {
    super.setUp()
    val featureRegistry = Registry.get("java.refactoring.extractMethod.newDuplicatesExtractor")
    val previousValue = featureRegistry.asBoolean()
    Disposer.register(testRootDisposable) { featureRegistry.setValue(previousValue) }
    featureRegistry.setValue(true)
  }

  fun testStatement(){
    doTest()
  }

  fun testConflictedNamesFiltered(){
    doTest()
  }

  fun testVariableGetterSuggested(){
    doTest()
  }

  fun testExactDuplicates(){
    doTest()
  }

  fun testInvalidRename(){
    doTest(changedName = "invalid! name", checkResults = false)
    require(getActiveTemplate() != null)
  }

  fun testConflictRename(){
    doTest(changedName = "conflict", checkResults = false)
    require(getActiveTemplate() != null)
  }

  fun testValidRename(){
    doTest(changedName = "valid")
    require(getActiveTemplate() == null)
  }

  fun testGeneratedDefault(){
    doTest()
  }

  fun testRenamedExactDuplicate(){
    doTest(changedName = "renamed")
  }

  fun testRenamedParametrizedDuplicate(){
    doTest(changedName = "average")
  }

  fun testStaticMustBePlaced(){
    doTest()
  }

  fun testShortenClassReferences(){
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_11) {
      doTest()
    }
  }

  fun testThreeDuplicates(){
    doTest(changedName = "sayHello")
  }

  fun testParameterGrouping(){
    doTest()
  }

  fun testConditionalExitPoint(){
    doTest()
  }

  fun testRuntimeCatchMayChangeSemantic1(){
    assertThrows(RefactoringErrorHintException::class.java, JavaRefactoringBundle.message("extract.method.error.many.exits")) {
      doTest()
    }
  }

  fun testRuntimeCatchMayChangeSemantic2(){
    assertThrows(RefactoringErrorHintException::class.java, JavaRefactoringBundle.message("extract.method.error.many.exits")) {
      doTest()
    }
  }

  fun testRuntimeCatchWithLastAssignment(){
    doTest()
  }

  fun testSpecificCatch(){
    doTest()
  }

  fun testExpressionDuplicates(){
    doTest()
  }

  fun testArrayFoldingWithDuplicate(){
    doTest()
  }

  fun testFoldReturnExpression(){
    doTest()
  }

  fun testOverlappingRanges(){
    doTest()
  }

  fun testConditionalYield(){
    doTest()
  }

  fun testYieldWithDuplicate(){
    doTest()
  }

  fun testDisabledOnSwitchRules(){
    assertThrows(RefactoringErrorHintException::class.java, RefactoringBundle.message("selected.block.should.represent.a.set.of.statements.or.an.expression")) {
      doTest()
    }
  }

  fun testNormalizedOnSwitchRule(){
    doTest()
  }

  fun testExpressionStatementInSwitchExpression(){
    doTest()
  }

  fun testExpressionStatementInSwitchStatement(){
    doTest()
  }

  fun testIDEA278872(){
    doTest()
  }

  fun testLocalAssignmentDuplicates(){
    doTest()
  }

  fun testWrongLocalAssignmentDuplicates(){
    doTest()
  }

  fun testDuplicateWithLocalMethodReference(){
    doTest()
  }

  fun testDuplicateWithAnonymousMethodReference(){
    doTest()
  }

  fun testDuplicateWithAnonymousFieldReference(){
    doTest()
  }

  fun testDuplicateWithLocalReferenceInLambda(){
    doTest()
  }

  fun testAvoidChangeSignatureForLocalRefsInPattern(){
    doTest()
  }

  fun testAvoidChangeSignatureForLocalRefsInCandidate(){
    doTest()
  }

  fun testDiamondTypesConsideredAsEqual(){
    doTest()
  }

  fun testDuplicatedExpressionAndChangeSignature(){
    doTest()
  }

  fun testChangedVariableDeclaredOnce(){
    doTest()
  }

  fun testRefactoringListener(){
    templateTest {
      configureByFile("$BASE_PATH/${getTestName(false)}.java")
      var startReceived = false
      var doneReceived = false
      val connection = project.messageBus.connect()
      Disposer.register(testRootDisposable, connection)
      connection.subscribe(RefactoringEventListener.REFACTORING_EVENT_TOPIC, object : RefactoringEventListener {
        override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {
          startReceived = true
        }
        override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
          doneReceived = true
        }
        override fun conflictsDetected(refactoringId: String, conflictsData: RefactoringEventData) = Unit
        override fun undoRefactoring(refactoringId: String) = Unit
      })
      val template = startRefactoring(editor)
      require(startReceived)
      finishTemplate(template)
      require(doneReceived)
    }
  }

  private fun doTest(checkResults: Boolean = true, changedName: String? = null){
    templateTest {
      configureByFile("$BASE_PATH/${getTestName(false)}.java")
      val template = startRefactoring(editor)
      if (changedName != null) {
        renameTemplate(template, changedName)
      }
      finishTemplate(template)
      if (checkResults) {
        checkResultByFile("$BASE_PATH/${getTestName(false)}_after.java")
      }
    }
  }

  private fun startRefactoring(editor: Editor): TemplateState {
    val selection = with(editor.selectionModel) { TextRange(selectionStart, selectionEnd) }
    MethodExtractor().doExtract(file, selection)
    val templateState = getActiveTemplate()
    require(templateState != null) { "Failed to start refactoring" }
    return templateState
  }

  private fun getActiveTemplate() = TemplateManagerImpl.getTemplateState(editor)

  private fun finishTemplate(templateState: TemplateState){
    try {
      templateState.gotoEnd(false)
      UIUtil.dispatchAllInvocationEvents()
    } catch (ignore: RefactoringErrorHintException) {
    }
  }

  private fun renameTemplate(templateState: TemplateState, name: String) {
    WriteCommandAction.runWriteCommandAction(project) {
      val range = templateState.currentVariableRange!!
      editor.document.replaceString(range.startOffset, range.endOffset, name)
    }
  }

  private inline fun templateTest(test: () -> Unit) {
    val disposable = Disposer.newDisposable()
    try {
      TemplateManagerImpl.setTemplateTesting(disposable)
      test()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  override fun tearDown() {
    val template = getActiveTemplate()
    if (template != null) Disposer.dispose(template)
    super.tearDown()
  }

}