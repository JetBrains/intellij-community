// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.pom.java.LanguageLevel
import com.intellij.refactoring.JavaRefactoringSettings
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.extractMethod.newImpl.MethodExtractor
import com.intellij.refactoring.extractMethod.newImpl.inplace.DuplicatesMethodExtractor
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightJavaCodeInsightTestCase
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NonNls

class ExtractMethodAndDuplicatesInplaceTest: LightJavaCodeInsightTestCase() {

  private val BASE_PATH: @NonNls String = "/refactoring/extractMethodAndDuplicatesInplace"

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
    doTest {
      renameTemplate("invalid! name")
      nextTemplateVariable()
    }
    require(getActiveTemplate() != null)
  }

  fun testConflictRename(){
    doTest {
      renameTemplate("conflict")
      nextTemplateVariable()
    }
    require(getActiveTemplate() != null)
  }

  fun testValidRename(){
    doTest {
      renameTemplate("valid")
      nextTemplateVariable()
    }
  }

  fun testGeneratedDefault(){
    doTest()
  }

  fun testRenamedExactDuplicate(){
    doTest {
      renameTemplate("renamed")
      nextTemplateVariable()
    }
  }

  fun testRenamedParametrizedDuplicate(){
    doTest {
      renameTemplate("averageWithOffset")
      nextTemplateVariable()
    }
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
    doTest {
      renameTemplate("sayHello")
      nextTemplateVariable()
    }
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

  fun testDuplicatedWithDeclinedChangeSignature(){
    runAndRevertSettings {
      DuplicatesMethodExtractor.changeSignatureDefault = false
      doTest()
    }
  }

  fun testDuplicatedButDeclined(){
    runAndRevertSettings {
      DuplicatesMethodExtractor.replaceDuplicatesDefault = false
      doTest()
    }
  }

  fun testTemplateRenamesInsertedCallOnly(){
    doTest {
      renameTemplate("renamed")
      nextTemplateVariable()
    }
  }

  fun testSignatureChangeIsNotAvoided() {
    doTest()
  }

  fun testSignatureChangeIsAvoided1(){
    doTest()
  }

  fun testSignatureChangeIsAvoided2(){
    doTest()
  }

  fun testSignatureChangeIsAvoided3(){
    doTest()
  }

  fun testLiteralDuplicates(){
    doTest()
  }

  fun testMakeStaticWithThis(){
    runAndRevertSettings {
      JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
      doTest()
    }
  }

  fun testMakeStaticWithQualifiedThis(){
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    doTest()
  }

  fun testMakeStaticWithStaticMembers(){
    runAndRevertSettings {
      JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
      doTest()
    }
  }

  fun testMakeStaticWithLocalMethod(){
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    doTest()
  }

  fun testFoldedParametersInExactDuplicates(){
    runAndRevertSettings {
      DuplicatesMethodExtractor.changeSignatureDefault = false
      doTest()
    }
  }

  fun testIntroduceSimpleObject(){
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_1_8) {
      doTest()
    }
  }

  fun testIntroduceSimpleRecord(){
      doTest()
  }

  fun testIntroduceNullableObject(){
    doTest()
  }

  fun testIntroduceObjectWithAssignments(){
    doTest()
  }

  fun testIntroduceObjectWithTypeParameters(){
    doTest()
  }

  fun testIntroduceObjectWithRename(){
    doTest {
      renameTemplate("MyResult")
      nextTemplateVariable()
      renameTemplate("myVariable")
      nextTemplateVariable()
      nextTemplateVariable()
    }
  }

  fun testIntroduceObjectWithWrongClassname1(){
    doTest {
      renameTemplate("Wrong !")
      nextTemplateVariable()
      nextTemplateVariable()
      nextTemplateVariable()
    }
    require(getActiveTemplate() != null)
  }

  fun testIntroduceObjectWithWrongClassname2(){
    doTest {
      renameTemplate("Conflict")
      nextTemplateVariable()
      nextTemplateVariable()
      nextTemplateVariable()
    }
    require(getActiveTemplate() != null)
  }

  fun testIntroduceObjectWithWrongVariableName1(){
    doTest {
      nextTemplateVariable()
      renameTemplate("wrong !")
      nextTemplateVariable()
      nextTemplateVariable()
    }
    require(getActiveTemplate() != null)
  }

  fun testIntroduceObjectWithWrongVariableName2(){
    doTest {
      nextTemplateVariable()
      renameTemplate("conflict")
      nextTemplateVariable()
      nextTemplateVariable()
    }
    require(getActiveTemplate() != null)
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
      startRefactoring(editor)
      require(startReceived)
      nextTemplateVariable()
      require(doneReceived)
    }
  }

  private fun doTest(runnable: (TemplateState) -> Unit = { finishTemplate() }){
    templateTest {
      configureByFile("$BASE_PATH/${getTestName(false)}.java")
      val template = startRefactoring(editor)
      runnable.invoke(template)
      if (getActiveTemplate() == null) {
        checkResultByFile("$BASE_PATH/${getTestName(false)}_after.java")
      }
    }
  }

  private fun finishTemplate(){
    do {
      val isVariableSwitched = nextTemplateVariable()
    } while (isVariableSwitched)
  }

  private inline fun runAndRevertSettings(action: () -> Unit) {
    val settings = JavaRefactoringSettings.getInstance()
    val defaultStatic = settings.EXTRACT_STATIC_METHOD
    val defaultPassFields = settings.EXTRACT_STATIC_METHOD_AND_PASS_FIELDS
    val defaultChangeSignature = DuplicatesMethodExtractor.changeSignatureDefault
    val defaultReplaceDuplicates = DuplicatesMethodExtractor.replaceDuplicatesDefault
    try {
      action.invoke()
    }
    finally {
      settings.EXTRACT_STATIC_METHOD = defaultStatic
      settings.EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = defaultPassFields
      DuplicatesMethodExtractor.changeSignatureDefault = defaultChangeSignature
      DuplicatesMethodExtractor.replaceDuplicatesDefault = defaultReplaceDuplicates
    }
  }

  private fun startRefactoring(editor: Editor): TemplateState {
    val selection = with(editor.selectionModel) { TextRange(selectionStart, selectionEnd) }
    MethodExtractor().doExtract(file, selection)
    UIUtil.dispatchAllInvocationEvents()
    val templateState = getActiveTemplate()
    require(templateState != null) { "Failed to start refactoring" }
    return templateState
  }

  private fun getActiveTemplate() = TemplateManagerImpl.getTemplateState(editor)

  private fun nextTemplateVariable(): Boolean {
    val templateState = getActiveTemplate() ?: return false
    val previousRange = templateState.currentVariableRange
    LookupManager.getActiveLookup(templateState.editor)?.hideLookup(true)
    val dataContext = DataManager.getInstance().getDataContext(editor.component)
    val event = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, Presentation(), dataContext)
    ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_NEXT_TEMPLATE_VARIABLE).actionPerformed(event)
    UIUtil.dispatchAllInvocationEvents()
    return templateState.isFinished || templateState.currentVariableRange != previousRange
  }

  private fun renameTemplate(name: String) {
    val templateState = getActiveTemplate() ?: return
    WriteCommandAction.runWriteCommandAction(project) {
      val range = templateState.currentVariableRange!!
      editor.document.replaceString(range.startOffset, range.endOffset, name)
      templateState.update()
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
    try {
      val template = getActiveTemplate()
      if (template != null) Disposer.dispose(template)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }
}