// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.inline.InlineLocalHandler;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestLoggerKt;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class InlineLocalTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.HIGHEST, () -> IdeaTestUtil.getMockJdk17()));
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testInference() {
    doTest();
  }

  @Test
  public void testQualifier() {
    doTest();
  }

  @Test
  public void testInnerInnerClass() {
    doTest();
  }

  @Test
  public void testIDEADEV950() {
    doTest();
  }

  @Test
  public void testNoRedundantCasts() {
    doTest();
  }

  @Test
  public void testIdeaDEV9404() {
    doTest();
  }

  @Test
  public void testIDEADEV12244() {
    doTest();
  }

  @Test
  public void testIDEADEV10376() {
    doTest();
  }

  @Test
  public void testIDEADEV13151() {
    UiInterceptors.register(new ChooserInterceptor(List.of("This reference only", "All 2 references and remove the variable"),
                                                   "All 2 references and remove the variable"));
    doTest();
  }

  @Test
  public void testArrayInitializer() {
    doTest();
  }

  @Test
  public void testNonWriteUnaryExpression() {
    doTest();
  }

  @Test
  public void testNewExpression() {
    doTest();
  }

  @Test
  public void testNewExpressionWithDiamond() {
    doTest();
  }

  @Test
  public void testNewExpressionWithPreservedDiamond() {
    doTest();
  }

  @Test
  public void testAugmentedAssignment() {
    doTest();
  }

  @Test
  public void testUsedInInnerClass() {       // IDEADEV-28786
    doTest();
  }

  @Test
  public void testUsedInInnerClass2() {       // IDEADEV-28786
    doTest();
  }

  @Test
  public void testUsedInInnerClass3() {       // IDEADEV-28786
    UiInterceptors.register(new ChooserInterceptor(List.of("This reference only", "All 2 references and remove the variable"),
                                                   "All 2 references and remove the variable"));
    doTest();
  }

  @Test
  public void testUsedInInnerClass4() {       // IDEADEV-28786
    UiInterceptors.register(new ChooserInterceptor(List.of("This reference only", "All 2 references and remove the variable"),
                                                   "All 2 references and remove the variable"));
    doTest();
  }

  @Test
  public void testAnotherDefinitionUsed() {
    doTest("Cannot perform refactoring.\nAnother variable 'bar' definition is used together with inlined one");
  }

  @Test
  public void testAnotherDefinitionUsed1() {
    doTest("Cannot perform refactoring.\nAnother variable 'bar' definition is used together with inlined one");
  }

  @Test
  public void testTypeArgumentsStatic() {
    doTest();
  }

  @Test
  public void testTypeArguments() {
    doTest();
  }

  @Test
  public void testWildcard() {
    doTest();
  }

  @Test
  public void testStaticImported() {
    doTest();
  }

  @Test
  public void testQualified() {
    doTest();
  }

  @Test
  public void testAssignmentToArrayElement() throws Exception {
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      UiInterceptors.register(new ChooserInterceptor(List.of("This reference only", "All 2 references and remove the variable"),
                                                     "All 2 references and remove the variable"));
      doTest("Cannot perform refactoring.\n" +
             "Variable 'arr' is accessed for writing");
    });
  }

  @Test
  public void testArrayMethodCallInitialized() {
    doTest();
  }

  @Test
  public void testArrayIndex() {
    doTest();
  }

  @Test
  public void testNonEqAssignment() {
    doTest("Cannot perform refactoring.\n" +
           "Cannot find a single definition to inline");
  }

  @Test
  public void testInlineFromTryCatch() {
    doTest("Unable to inline outside try/catch statement");
  }

  @Test
  public void testInlineFromTryCatchAvailable() {
    doTest();
  }

  @Test
  public void testConditionExpr() {
    doTest();
  }

  @Test
  public void testLambdaExpr() {
    doTest(LanguageLevel.JDK_1_8);
  }

  @Test
  public void testLambdaExprAsRefQualifier() {
    doTest(LanguageLevel.JDK_1_8);
  }

  @Test
  public void testMethodRefAsRefQualifier() {
    doTest(LanguageLevel.JDK_1_8);
  }

  @Test
  public void testLocalVarInsideLambdaBody() {
    UiInterceptors.register(new ChooserInterceptor(List.of("This reference only", "All 2 references and remove the variable"),
                                                   "All 2 references and remove the variable"));
    doTest(LanguageLevel.JDK_1_8);
  }

  @Test
  public void testLocalVarInsideLambdaBody1() {
    doTest(LanguageLevel.JDK_1_8);
  }

  @Test
  public void testLocalVarInsideLambdaBody2() { doTest(LanguageLevel.JDK_1_8); }

  @Test
  public void testLocalVarUsedInLambdaBody() { doTest(LanguageLevel.JDK_1_8); }

  @Test
  public void testCastAroundLambda() { doTest(LanguageLevel.JDK_1_8); }

  @Test
  public void testNoCastAroundLambda() { doTest(LanguageLevel.JDK_1_8); }

  @Test
  public void testNoCastWithVar() { doTest(LanguageLevel.JDK_10); }

  @Test
  public void testDiamondInAnonymousClass() { doTest(LanguageLevel.JDK_11); }

  @Test
  public void testAssignmentInAnonymousClass() { doTest(); }

  @Test
  public void testAssignmentInAnonymousClass2() { doTest(); }

  @Test
  public void testUncheckedCast() {
    doTest();
  }

  @Test
  public void testUncheckedCastNotNeeded() {
    doTest();
  }

  @Test
  public void testCastNotNeeded() {
    doTest();
  }

  @Test
  public void testResourceVariable() {
    doTest();
  }

  @Test
  public void testEnclosingThisExpression() {
    doTest();
  }

  @Test
  public void testParentStaticQualifier() {
    doTest();
  }

  @Test
  public void testCollapseArrayCreation() {
    doTest();
  }

  @Test
  public void testRenameLambdaParamsToAvoidConflicts() {
    doTest();
  }

  @Test
  public void testParenthesisAroundInlinedLambda() {
    doTest();
  }

  @Test
  public void testArrayAccessPriority() {
    doTest();
  }

  @Test
  public void testDecodeRefsBeforeCheckingOverRedundantCasts() {
    doTest();
  }

  @Test
  public void testDontOpenMultidimensionalArrays() {
    doTest();
  }

  @Test
  public void testInsertNarrowingCastToAvoidSemanticsChange() {
    doTest();
  }

  @Test
  public void testInsertCastToGenericTypeToProvideValidReturnType() {
    doTest();
  }

  @Test
  public void testDisableShortCircuit() {
    doTest();
  }

  @Test
  public void testOperationPrecedenceWhenInlineToStringConcatenation() {
    doTest();
  }

  @Test
  public void testParenthesisAroundCast() {
    doTest();
  }

  @Test
  public void testLocalVarInsideLambdaBodyWriteUsage() throws Exception {
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      doTest("Cannot perform refactoring.\n" +
             "Variable 'hello' is accessed for writing");
    });
  }

  @Test
  public void testReassignedVariableNoOption() {
    doTest();
  }

  @Test
  public void testInlineVariableIntoNestedLambda() {
    doTest();
  }

  @Test
  public void testAvoidTypeSpecificationWhenPossibleToAvoid() {
    doTest();
  }

  @Test
  public void testLocalInsideLambdaWithNestedLambda() { doTest(); }

  @Test
  public void testDefInMultiAssignmentStatement() { doTest(); }

  @Test
  public void testPrivateOverload() { doTest(); }

  @Test
  public void testAssignedVarsUpdatedBeforeRead() {
    UiInterceptors.register(new ChooserInterceptor(List.of("Highlight 3 conflicting writes", "Ignore writes and continue"), 
                                                   "Ignore writes and continue"));
    doTest();
  }

  @Test
  public void testAssignedVarUpdatedAfterRead() {
    doTest();
  }

  @Test
  public void testAssignmentAndReassignmentInLoop() {
    UiInterceptors.register(new ChooserInterceptor(List.of("Highlight 1 conflicting write", "Ignore writes and continue"),
                                                   "Ignore writes and continue"));
    doTest();
  }

  @Test
  public void testLoopReassignment() {
    UiInterceptors.register(new ChooserInterceptor(List.of("Highlight 1 conflicting write", "Ignore writes and continue"),
                                                   "Ignore writes and continue"));
    doTest();
  }

  @Test
  public void testOuterLoopReassignment() {
    UiInterceptors.register(new ChooserInterceptor(List.of("Highlight 1 conflicting write", "Ignore writes and continue"),
                                                   "Ignore writes and continue"));
    doTest();
  }

  @Test
  public void testUnusedReassignmentInLoop() {
    doTest();
  }
  
  @Test
  public void testCompilationError() {
    doTest();
  }

  @Test
  public void testCompilationErrorAtRef() {
    doTest();
  }

  @Test
  public void testCompilationErrorAssignment() {
    doTest("Cannot perform refactoring.\n" +
           "Code contains syntax errors. Cannot perform necessary analysis.");
  }

  @Test
  public void testEolComment() {
    doTest();
  }
  
  @Test
  public void testCompositeAssignment() { doTest(); }
  
  @Test
  public void testCompositeAssignmentCast() { doTest(); }
  
  @Test
  public void testLambdaInitialization() { doTest(); }
  
  @Test
  public void testSeparateInitialization() { doTest(); }
  @Test
  public void testSeparateInitialization2() { doTest(); }
  @Test
  public void testSeparateInitialization3() { doTest("Cannot perform refactoring.\nVariable aaa has no initializer"); }
  @Test
  public void testSeparateInitialization4() { doTest(); }

  private void doTest(String conflictMessage) {
    try {
      doTest();
      fail("Conflict was not detected");
    }
    catch (RuntimeException | AssertionError e) {
      assertEquals(conflictMessage, e.getMessage());
    }
  }

  @Test
  public void testVariableInsideResourceList() {
    doTest("Cannot perform refactoring.\n" +
           "Variable is used as resource reference");
  }

  @Test
  public void testLocalVariableInThisOnlyMode() {
    boolean initialSetting = JavaRefactoringSettings.getInstance().INLINE_LOCAL_THIS;
    try {
      JavaRefactoringSettings.getInstance().INLINE_LOCAL_THIS = true;
      doTest();
    }
    finally {
      JavaRefactoringSettings.getInstance().INLINE_LOCAL_THIS = initialSetting;
    }
  }

  @Test
  public void testInLambda() {
    doTest(LanguageLevel.JDK_1_8);
  }

  private void doTest() {
    doTest(LanguageLevel.JDK_1_7);
  }

  private void doTest(LanguageLevel languageLevel) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String fileName = prepareTest(languageLevel);
    myFixture.runInCommand(() -> {
      performInline(myFixture.getProject(), myFixture.getEditor());
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile(fileName + ".after");
    });
  }

  private String prepareTest(LanguageLevel languageLevel) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), languageLevel, myFixture.getTestRootDisposable());
    String name = myFixture.getTestName(false);
    String fileName = "/refactoring/inlineLocal/" + name + ".java";
    myFixture.configureByFile(fileName);
    return fileName;
  }

  private static PsiLocalVariable getTarget(Editor editor) {
    PsiElement element = TargetElementUtil
      .findTargetElement(editor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertInstanceOf(PsiLocalVariable.class, element);

    return (PsiLocalVariable)element;
  }

  public static void performInline(Project project, Editor editor) {
    PsiLocalVariable element = getTarget(editor);
    new InlineLocalHandler().inlineElement(project, editor, element);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
  }
}
