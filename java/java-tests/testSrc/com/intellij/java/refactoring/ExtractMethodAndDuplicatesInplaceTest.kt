// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.lookup.LookupFocusDegree
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.IdePopupManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
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
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaDescriptorOfTestName
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.fixtures.lightJavaTestDescriptor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.utils.coroutines.waitCoroutinesBlocking
import com.intellij.ui.ChooserInterceptor
import com.intellij.ui.UiInterceptors
import com.intellij.util.FileContentUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.annotations.NonNls
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class ExtractMethodAndDuplicatesInplaceTest {

  companion object {
    private val DESCRIPTOR = lightJavaTestDescriptor()
    private val projectFixture = lightJavaProjectFixture(DESCRIPTOR)

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

  private val myFixture by lightJavaCodeInsightFixture(projectFixture, lightJavaDescriptorOfTestName(DESCRIPTOR), "/java/java-tests/testData")

  private val BASE_PATH: @NonNls String = "/refactoring/extractMethodAndDuplicatesInplace"

  @Test
  fun testStatement(): Unit = doTest()

  @Test
  fun testReassignFinalFieldWriteOnly(): Unit = doTest()

  @Test
  fun testConflictedNamesFiltered(): Unit = doTest()

  @Test
  fun testVariableGetterSuggested(): Unit = doTest()

  @Test
  fun testElseIfWithOutputVariable(): Unit = doTest()

  @Test
  fun testExactDuplicates(): Unit = doTest()

  @Test
  fun testInvalidRename() {
    doTest {
      renameTemplate("invalid! name")
      nextTemplateVariable()
    }
    require(getActiveTemplate() != null)
  }

  @Test
  fun testConflictRename() {
    doTest {
      renameTemplate("conflict")
      nextTemplateVariable()
    }
    require(getActiveTemplate() != null)
  }

  @Test
  fun testValidRename() {
    doTest {
      renameTemplate("valid")
      nextTemplateVariable()
    }
  }

  @Test
  fun testGeneratedDefault(): Unit = doTest()

  @Test
  fun testRenamedExactDuplicate() {
    doTest {
      renameTemplate("renamed")
      nextTemplateVariable()
    }
  }

  @Test
  fun testRenamedParametrizedDuplicate() {
    doTest {
      renameTemplate("averageWithOffset")
      nextTemplateVariable()
    }
  }

  @Test
  fun testStaticMustBePlaced(): Unit = doTest()

  @Test
  fun testShortenClassReferences() {
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_11) {
      doTest()
    }
  }

  @Test
  fun testThreeDuplicates() {
    doTest {
      renameTemplate("sayHello")
      nextTemplateVariable()
    }
  }

  @Test
  fun testParameterGrouping(): Unit = doTest()

  @Test
  fun testConditionalExitPoint(): Unit = doTest()

  @Test
  fun testUnconditionalExitWithReturn(): Unit = doTest()

  @Test
  fun testRuntimeCatchMayChangeSemantic1() {
    assertThatThrownBy {
      doTest()
    }.isInstanceOf(RefactoringErrorHintException::class.java).hasMessageContaining(JavaRefactoringBundle.message("extract.method.error.exception"))
  }

  @Test
  fun testRuntimeCatchMayChangeSemantic2() {
    assertThatThrownBy {
      doTest()
    }.isInstanceOf(RefactoringErrorHintException::class.java).hasMessageContaining(JavaRefactoringBundle.message("extract.method.error.exception"))
  }

  @Test
  fun testRuntimeCatchWithLastAssignment(): Unit = doTest()

  @Test
  fun testSpecificCatch(): Unit = doTest()

  @Test
  fun testExpressionDuplicates(): Unit = doTest()

  @Test
  fun testArrayFoldingWithDuplicate(): Unit = doTest()

  @Test
  fun testFoldReturnExpression(): Unit = doTest()

  @Test
  fun testOverlappingRanges(): Unit = doTest()

  @Test
  fun testConditionalYield(): Unit = doTest()

  @Test
  fun testYieldWithDuplicate(): Unit = doTest()

  @Test
  fun testDisabledOnSwitchRules() {
    assertThatThrownBy {
      doTest()
    }.isInstanceOf(RefactoringErrorHintException::class.java).hasMessageContaining(RefactoringBundle.message("selected.block.should.represent.a.set.of.statements.or.an.expression"))
  }

  @Test
  fun testNormalizedOnSwitchRule(): Unit = doTest()

  @Test
  fun testExpressionStatementInSwitchExpression(): Unit = doTest()

  @Test
  fun testExpressionStatementInSwitchStatement(): Unit = doTest()

  @Test
  fun testTargetClassSurvivesReparse() {
    myFixture.configureByText("Reparse2.java", """
      class Test {
          void a() { helper(); }
          void helper() {}
      }
    """.trimIndent())
    myFixture.runInCommand {
      val file = myFixture.file
      val cls = PsiTreeUtil.findChildOfType(file, PsiClass::class.java)!!
      val elements = cls.findMethodsByName("a", false).first().body!!.statements.toList()
      val extractor = DuplicatesMethodExtractor.create(cls, elements, "extracted", false)

      FileContentUtil.reparseFiles(myFixture.project, listOf(file.virtualFile), true)

      val targetFile = extractor.targetFile
      assertNotNull(targetFile, "targetFile pointer must re-resolve after reparse")
      assertTrue(targetFile!!.isValid, "targetFile must be valid after reparse")
      assertTrue(extractor.targetClass.isValid, "targetClass pointer must re-resolve to a valid class after reparse")
    }
  }

  @Test
  fun testIDEA278872(): Unit = doTest()

  @Test
  fun testLocalAssignmentDuplicates(): Unit = doTest()

  @Test
  fun testWrongLocalAssignmentDuplicates(): Unit = doTest()

  @Test
  fun testDuplicateWithLocalMethodReference(): Unit = doTest()

  @Test
  fun testDuplicateWithAnonymousMethodReference(): Unit = doTest()

  @Test
  fun testDuplicateWithAnonymousFieldReference(): Unit = doTest()

  @Test
  fun testDuplicateWithLocalReferenceInLambda(): Unit = doTest()

  @Test
  fun testAvoidChangeSignatureForLocalRefsInPattern(): Unit = doTest()

  @Test
  fun testAvoidChangeSignatureForLocalRefsInCandidate(): Unit = doTest()

  @Test
  fun testDiamondTypesConsideredAsEqual(): Unit = doTest()

  @Test
  fun testDuplicatedExpressionAndChangeSignature(): Unit = doTest()

  @Test
  fun testChangedVariableDeclaredOnce(): Unit = doTest()

  @Test
  fun testDuplicatedWithDeclinedChangeSignature() {
    DuplicatesMethodExtractor.changeSignatureDefault = false
    doTest()
  }

  @Test
  fun testDuplicatedButDeclined() {
    DuplicatesMethodExtractor.replaceDuplicatesDefault = false
    doTest()
  }

  @Test
  fun testTemplateRenamesInsertedCallOnly() {
    doTest {
      renameTemplate("renamed")
      nextTemplateVariable()
    }
  }

  @Test
  fun testSignatureChangeIsNotAvoided(): Unit = doTest()

  @Test
  fun testSignatureChangeIsAvoided1(): Unit = doTest()

  @Test
  fun testSignatureChangeIsAvoided2(): Unit = doTest()

  @Test
  fun testSignatureChangeIsAvoided3(): Unit = doTest()

  @Test
  fun testLiteralDuplicates(): Unit = doTest()

  @Test
  fun testSubExpressionWithCall(): Unit = doTest()

  @Test
  fun testMakeStaticWithThis() {
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    doTest()
  }

  @Test
  fun testMakeStaticWithQualifiedThis() {
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    doTest()
  }

  @Test
  fun testMakeStaticWithStaticMembers() {
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    doTest()
  }

  @Test
  fun testMakeStaticWithLocalMethod() {
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    doTest()
  }

  @Test
  fun testFoldedParametersInExactDuplicates() {
    DuplicatesMethodExtractor.changeSignatureDefault = false
    doTest()
  }

  @Test
  fun testIntroduceSimpleObject() {
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_1_8) {
      doTest()
    }
  }

  @Test
  fun testIntroduceSimpleRecord(): Unit = doTest()

  @Test
  fun testIntroduceNullableObject(): Unit = doTest()

  @Test
  fun testIntroduceObjectWithAssignments(): Unit = doTest()

  @Test
  fun testIntroduceObjectWithTypeParameters(): Unit = doTest()

  @Test
  fun testIntroduceObjectInsideNestedClass() {
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        IdeEventQueue.getInstance().popupManager.closeAllPopups()
        IdePopupManager().closeAllPopups()
      }
    }
    doTest()
  }

  @Test
  fun testMakeStaticInsideInner() {
    shouldSelectTargetClass("Anonymous in r in X")
    doTest()
  }

  @Test
  fun testMakeStaticInsideInnerFail() {
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_15) {
      shouldSelectTargetClass("Anonymous in r in X")
      doTest()
    }
  }

  @Test
  fun testMakeStaticFailsWithClassUsage() {
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    doTest()
  }

  @Test
  fun testMakeStaticWithClassUsage() {
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    doTest()
  }

  @Test
  fun testMakeStaticPassLocalParameters() {
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    shouldSelectTargetClass("Anonymous.*")
    doTest()
  }

  @Test
  fun testNotStaticByDefault() {
    shouldSelectTargetClass("Anonymous.*")
    doTest()
  }

  @Test
  fun testChangeTargetClassAndMakeStatic() {
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    shouldSelectTargetClass("Test.*")
    doTest()
  }

  @Test
  fun testIntroduceObjectConflictInsideNestedClass() {
    doTest {
      renameTemplate("Result")
      nextTemplateVariable()
      nextTemplateVariable()
      nextTemplateVariable()
    }
    require(getActiveTemplate() != null)
  }

  @Test
  fun testIntroduceObjectWithRename() {
    doTest {
      renameTemplate("MyResult")
      nextTemplateVariable()
      renameTemplate("myVariable")
      nextTemplateVariable()
      nextTemplateVariable()
    }
  }

  @Test
  fun testIntroduceObjectWithWrongClassname1() {
    doTest {
      renameTemplate("Wrong !")
      nextTemplateVariable()
      nextTemplateVariable()
      nextTemplateVariable()
    }
    require(getActiveTemplate() != null)
  }

  @Test
  fun testIntroduceObjectWithWrongClassname2() {
    doTest {
      renameTemplate("Conflict")
      nextTemplateVariable()
      nextTemplateVariable()
      nextTemplateVariable()
    }
    require(getActiveTemplate() != null)
  }

  @Test
  fun testIntroduceObjectWithWrongVariableName1() {
    doTest {
      nextTemplateVariable()
      renameTemplate("wrong !")
      nextTemplateVariable()
      nextTemplateVariable()
    }
    require(getActiveTemplate() != null)
  }

  @Test
  fun testIntroduceObjectWithWrongVariableName2() {
    doTest {
      nextTemplateVariable()
      renameTemplate("conflict")
      nextTemplateVariable()
      nextTemplateVariable()
    }
    require(getActiveTemplate() != null)
  }

  @Test
  fun testIntroduceObjectFailedWithLoop() {
    assertThatThrownBy {
      doTest()
    }.isInstanceOf(RefactoringErrorHintException::class.java)
  }

  @Test
  fun testIntroduceObjectFailedWithLoop2() {
    assertThatThrownBy {
      doTest()
    }.isInstanceOf(RefactoringErrorHintException::class.java)
  }

  @Test
  fun testIntroduceObjectFailedWithAssignment1() {
    assertThatThrownBy {
      doTest()
    }.isInstanceOf(RefactoringErrorHintException::class.java)
  }

  @Test
  fun testIntroduceObjectFailedWithAssignment2() {
    assertThatThrownBy {
      doTest()
    }.isInstanceOf(RefactoringErrorHintException::class.java)
  }

  @Test
  fun testExtractStaticDuplicateFromNonStaticContext() {
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD = false
    doTest()
  }

  @Test
  fun testExtractDefaultToInterfaceJava8() {
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD = false
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_1_8) {
      doTest()
    }
  }

  @Test
  fun testExtractStaticToInterfaceJava8() {
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD = true
    shouldSelectTargetClass("Test")
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_1_8) {
      doTest()
    }
  }

  @Test
  fun testExtractPrivateToInterface() {
    shouldSelectTargetClass("Test")
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD = false
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_11) {
      doTest()
    }
  }

  @Test
  fun testExtractToInterfaceNotSuggested() {
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_1_7) {
        doTest()
    }
  }

  @Test
  fun testPassTypeParametersInStaticMethods() {
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    doTest()
  }

  @Test
  fun testExtractVirtualExpressionFromPolyadic(): Unit = doTest()

  @Test
  fun testExtractVirtualExpressionFromSubstring(): Unit = doTest()

  @Test
  fun testExtractExpressionFromClassContext() {
    doTest{
      nextTemplateVariable()
    }
  }

  @Test
  fun testExtractMethodFromClassContext() {
    doTest{
      nextTemplateVariable()
    }
  }

  @Test
  fun testFoldParametersInDuplicates(): Unit = doTest()

  @Test
  fun testTypeParametersInNonStaticTarget() {
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD = true
    shouldSelectTargetClass("Inner in Test")
    doTest()
  }

  @Test
  fun testNonStaticExtractFromStaticInner() {
    shouldSelectTargetClass("Inner in Test")
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD = false
    doTest()
  }

  @Test
  fun testPassThisAsParameter() {
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD_AND_PASS_FIELDS = true
    doTest()
  }

  @Test
  fun testCheckNameExtractedFromLambda() {
    doTest {
      nextTemplateVariable()
    }
  }

  @Test
  fun testChangeSignatureIsIgnored() {
    DuplicatesMethodExtractor.changeSignatureDefault = true
    doTest()
  }

  @Test
  fun testKeepVarKeyword(): Unit = doTest()

  @Test
  fun testDeclareVarType() {
    JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_VAR_TYPE = true
    doTest()
  }

  @Test
  fun testUseVariableName(): Unit = doTest()

  @Test
  fun testNameFromComment1(): Unit = doTest()

  @Test
  fun testNameFromComment2(): Unit = doTest()

  @Test
  fun testCreatePrefixForNewExpression(): Unit = doTest()

  @Test
  fun testRefactoringListener() {
    templateTest {
      configureByDefaultFile()
      var startReceived = false
      var doneReceived = false
      val connection = myFixture.project.messageBus.connect()
      Disposer.register(myFixture.testRootDisposable, connection)
      connection.subscribe(RefactoringEventListener.REFACTORING_EVENT_TOPIC, object : RefactoringEventListener {
        override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {
          startReceived = true
        }
        override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
          doneReceived = true
        }
      })
      startRefactoring(myFixture.editor)
      require(startReceived)
      nextTemplateVariable()
      require(doneReceived)
    }
  }

  @Test
  fun testNameLookupNotPreselected() {
    templateTest {
      configureByDefaultFile()
      startRefactoring(myFixture.editor)
      val lookup = LookupManager.getActiveLookup(myFixture.editor) as? LookupImpl
      require(lookup != null) {
        "Method-name suggestion lookup must be visible after Extract Method starts"
      }
      require(lookup.items.isNotEmpty()) {
        "Sanity check: the scenario should produce at least one suggested method name"
      }
      assertEquals(
        LookupFocusDegree.UNFOCUSED,
        lookup.lookupFocusDegree,
        "Extract Method's name-suggestion lookup must not pre-select the first suggestion. " +
        "Otherwise typing a custom name and pressing Enter replaces it with the highlighted " +
        "suggestion."
      )
    }
  }

  private fun doTest(runnable: (TemplateState) -> Unit = { finishTemplate() }) {
    templateTest {
      configureByDefaultFile()
      val template = startRefactoring(myFixture.editor)
      runnable.invoke(template)
      if (getActiveTemplate() == null) {
        myFixture.checkResultByFile("$BASE_PATH/${myFixture.getTestName(false)}_after.java")
      }
    }
  }

  private fun configureByDefaultFile() {
    myFixture.configureByFile("$BASE_PATH/${myFixture.getTestName(false)}.java")
  }

  private fun finishTemplate() {
    do {
      val isVariableSwitched = nextTemplateVariable()
    } while (isVariableSwitched)
  }

  @BeforeEach
  fun saveSettings() {
    val settings = JavaRefactoringSettings.getInstance()
    val defaultStatic = settings.EXTRACT_STATIC_METHOD
    val defaultPassFields = settings.EXTRACT_STATIC_METHOD_AND_PASS_FIELDS
    val defaultDeclareVar = settings.INTRODUCE_LOCAL_CREATE_VAR_TYPE
    val defaultChangeSignature = DuplicatesMethodExtractor.changeSignatureDefault
    val defaultReplaceDuplicates = DuplicatesMethodExtractor.replaceDuplicatesDefault
    Disposer.register(myFixture.testRootDisposable) {
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
    ApplicationManager.getApplication().replaceService(HintManager::class.java, manager, myFixture.testRootDisposable)
    block.invoke()
    if (message != null) throw RefactoringErrorHintException(message)
  }

  private fun startRefactoring(editor: Editor): TemplateState {
    val selection = with(editor.selectionModel) { TextRange(selectionStart, selectionEnd) }
    throwHintError {
      MethodExtractor().doExtract(myFixture.file, selection)
      UIUtil.dispatchAllInvocationEvents()
      waitCoroutinesBlocking(ExtractMethodService.getInstance(myFixture.project).scope)
    }
    val templateState = getActiveTemplate()
    require(templateState != null) { "Failed to start refactoring" }
    return templateState
  }

  private fun getActiveTemplate() = TemplateManagerImpl.getTemplateState(myFixture.editor)

  private fun nextTemplateVariable(): Boolean {
    return nextTemplateVariable(getActiveTemplate()).also {
      waitCoroutinesBlocking(ExtractMethodService.getInstance(myFixture.project).scope)
    }
  }

  private fun renameTemplate(name: String) = renameTemplate(getActiveTemplate(), name)

  private fun templateTest(test: () -> Unit) {
    myFixture.runInCommand {
      val disposable = Disposer.newDisposable()
      try {
        TemplateManagerImpl.setTemplateTesting(disposable)
        test()
      }
      finally {
        Disposer.dispose(disposable)
      }
    }
  }

  @AfterEach
  fun disposeActiveTemplate(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val editor = myFixture.editor ?: return@withContext
      val template = TemplateManagerImpl.getTemplateState(editor)
      if (template != null) Disposer.dispose(template)
    }
  }

  private fun shouldSelectTargetClass(targetClassName: String) {
    UiInterceptors.register(ChooserInterceptor(null, targetClassName))
  }
}