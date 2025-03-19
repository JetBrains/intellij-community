// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.IdePopupManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.pom.java.LanguageLevel
import com.intellij.refactoring.JavaRefactoringSettings
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodService
import com.intellij.refactoring.extractMethod.newImpl.MethodExtractor
import com.intellij.refactoring.extractMethod.newImpl.inplace.DuplicatesMethodExtractor
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightJavaCodeInsightTestCase
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.utils.coroutines.waitCoroutinesBlocking
import com.intellij.ui.ChooserInterceptor
import com.intellij.ui.UiInterceptors
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NonNls

class ExtractMethodAndDuplicatesInplaceTest: LightJavaCodeInsightTestCase() {

  companion object {
    fun nextTemplateVariable(templateState: TemplateState?): Boolean {
      if (templateState == null) return false
      val previousRange = templateState.currentVariableRange
      LookupManager.getActiveLookup(templateState.editor)?.hideLookup(true)
      val dataContext = DataManager.getInstance().getDataContext(templateState.editor.component)
      val event = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, Presentation(), dataContext)
      ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_NEXT_TEMPLATE_VARIABLE).actionPerformed(event)
      UIUtil.dispatchAllInvocationEvents()
      return templateState.isFinished || templateState.currentVariableRange != previousRange
    }

    fun renameTemplate(templateState: TemplateState?, name: String) {
      if (templateState == null) return
      WriteCommandAction.runWriteCommandAction(templateState.project) {
        ExtractMethodHelper.renameTemplate(templateState, name)
      }
    }
  }

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

  fun testUnconditionalExitWithReturn(){
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
    DuplicatesMethodExtractor.changeSignatureDefault = false
    doTest()
  }

  fun testDuplicatedButDeclined(){
    DuplicatesMethodExtractor.replaceDuplicatesDefault = false
    doTest()
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
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    doTest()
  }

  fun testMakeStaticWithQualifiedThis(){
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    doTest()
  }

  fun testMakeStaticWithStaticMembers(){
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    doTest()
  }

  fun testMakeStaticWithLocalMethod(){
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    doTest()
  }

  fun testFoldedParametersInExactDuplicates(){
    DuplicatesMethodExtractor.changeSignatureDefault = false
    doTest()
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

  fun testIntroduceObjectInsideNestedClass(){
    IdeEventQueue.getInstance().popupManager.closeAllPopups()
    IdePopupManager().closeAllPopups()
    doTest()
  }

  fun testMakeStaticInsideInner(){
    shouldSelectTargetClass("Anonymous in r in X")
    doTest()
  }

  fun testMakeStaticInsideInnerFail(){
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_15) {
      shouldSelectTargetClass("Anonymous in r in X")
      doTest()
    }
  }

  fun testMakeStaticFailsWithClassUsage(){
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    doTest()
  }

  fun testMakeStaticWithClassUsage(){
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    doTest()
  }

  fun testMakeStaticPassLocalParameters(){
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    shouldSelectTargetClass("Anonymous.*")
    doTest()
  }

  fun testNotStaticByDefault(){
    shouldSelectTargetClass("Anonymous.*")
    doTest()
  }

  fun testChangeTargetClassAndMakeStatic(){
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    shouldSelectTargetClass("Test.*")
    doTest()
  }

  fun testIntroduceObjectConflictInsideNestedClass(){
    doTest {
      renameTemplate("Result")
      nextTemplateVariable()
      nextTemplateVariable()
      nextTemplateVariable()
    }
    require(getActiveTemplate() != null)
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

  fun testIntroduceObjectFailedWithAssignment1(){
    assertThrows(RefactoringErrorHintException::class.java) {
      doTest()
    }
  }

  fun testIntroduceObjectFailedWithAssignment2(){
    assertThrows(RefactoringErrorHintException::class.java) {
      doTest()
    }
  }

  fun testExtractStaticDuplicateFromNonStaticContext(){
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD = false
    doTest()
  }

  fun testExtractDefaultToInterfaceJava8(){
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD = false
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_1_8) {
      doTest()
    }
  }

  fun testExtractStaticToInterfaceJava8(){
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD = true
    shouldSelectTargetClass("Test")
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_1_8) {
      doTest()
    }
  }

  fun testExtractPrivateToInterface(){
    shouldSelectTargetClass("Test")
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD = false
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_11) {
      doTest()
    }
  }

  fun testExtractToInterfaceNotSuggested(){
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_1_7) {
        doTest()
    }
  }

  fun testPassTypeParametersInStaticMethods(){
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    doTest()
  }

  fun testExtractVirtualExpressionFromPolyadic(){
    doTest()
  }

  fun testExtractVirtualExpressionFromSubstring(){
    doTest()
  }

  fun testExtractExpressionFromClassContext(){
    doTest{
      nextTemplateVariable()
    }
  }

  fun testExtractMethodFromClassContext(){
    doTest{
      nextTemplateVariable()
    }
  }

  fun testFoldParametersInDuplicates(){
    doTest()
  }

  fun testTypeParametersInNonStaticTarget(){
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD = true
    shouldSelectTargetClass("Inner in Test")
    doTest()
  }

  fun testNonStaticExtractFromStaticInner(){
    shouldSelectTargetClass("Inner in Test")
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD = false
    doTest()
  }

  fun testPassThisAsParameter(){
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    doTest()
  }

  fun testCheckNameExtractedFromLambda(){
    doTest {
      nextTemplateVariable()
    }
  }

  fun testChangeSignatureIsIgnored(){
    DuplicatesMethodExtractor.changeSignatureDefault = true
    doTest()
  }

  fun testKeepVarKeyword(){
    doTest()
  }

  fun testDeclareVarType(){
    JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_VAR_TYPE = true
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

  override fun setUp() {
    super.setUp()
    val settings = JavaRefactoringSettings.getInstance()
    val defaultStatic = settings.EXTRACT_STATIC_METHOD
    val defaultPassFields = settings.EXTRACT_STATIC_METHOD_AND_PASS_FIELDS
    val defaultDeclareVar = settings.INTRODUCE_LOCAL_CREATE_VAR_TYPE
    val defaultChangeSignature = DuplicatesMethodExtractor.changeSignatureDefault
    val defaultReplaceDuplicates = DuplicatesMethodExtractor.replaceDuplicatesDefault
    Disposer.register(testRootDisposable) {
      settings.EXTRACT_STATIC_METHOD = defaultStatic
      settings.EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = defaultPassFields
      settings.INTRODUCE_LOCAL_CREATE_VAR_TYPE = defaultDeclareVar
      DuplicatesMethodExtractor.changeSignatureDefault = defaultChangeSignature
      DuplicatesMethodExtractor.replaceDuplicatesDefault = defaultReplaceDuplicates
    }
  }

  @Throws(RefactoringErrorHintException::class)
  fun throwHintError(block: () -> Unit) {
    var message: String? = null
    val manager = object : HintManagerImpl() {
      override fun showErrorHint(editor: Editor, text: String) {
        super.showInformationHint(editor, text)
        message = text
      }
    }
    ApplicationManager.getApplication().replaceService(HintManager::class.java, manager, testRootDisposable)
    block.invoke()
    if (message != null) throw throw RefactoringErrorHintException(message)
  }

  private fun startRefactoring(editor: Editor): TemplateState {
    val selection = with(editor.selectionModel) { TextRange(selectionStart, selectionEnd) }
    throwHintError {
      MethodExtractor().doExtract(file, selection)
      UIUtil.dispatchAllInvocationEvents()
      waitCoroutinesBlocking(ExtractMethodService.getInstance(project).scope)
    }
    val templateState = getActiveTemplate()
    require(templateState != null) { "Failed to start refactoring" }
    return templateState
  }

  private fun getActiveTemplate() = TemplateManagerImpl.getTemplateState(editor)

  private fun nextTemplateVariable(): Boolean {
    return nextTemplateVariable(getActiveTemplate()).also {
      waitCoroutinesBlocking(ExtractMethodService.getInstance(project).scope)
    }
  }

  private fun renameTemplate(name: String)  = renameTemplate(getActiveTemplate(), name)

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

  private fun shouldSelectTargetClass(targetClassName: String) {
    UiInterceptors.register(ChooserInterceptor(null, targetClassName))
  }
}