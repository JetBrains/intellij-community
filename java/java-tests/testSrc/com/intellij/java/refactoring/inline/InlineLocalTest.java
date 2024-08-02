// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.inline.InlineLocalHandler;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class InlineLocalTest extends LightJavaCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testInference() {
    doTest();
  }

  public void testQualifier() {
    doTest();
  }

  public void testInnerInnerClass() {
    doTest();
  }

  public void testIDEADEV950() {
    doTest();
  }

  public void testNoRedundantCasts() {
    doTest();
  }

  public void testIdeaDEV9404() {
    doTest();
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk17(); // there is JPanel inside
  }

  public void testIDEADEV12244() {
    doTest();
  }

  public void testIDEADEV10376() {
    doTest();
  }

  public void testIDEADEV13151() {
    UiInterceptors.register(new ChooserInterceptor(List.of("This reference only", "All 2 references and remove the variable"),
                                                   "All 2 references and remove the variable"));
    doTest();
  }

  public void testArrayInitializer() {
    doTest();
  }

  public void testNonWriteUnaryExpression() {
    doTest();
  }

  public void testNewExpression() {
    doTest();
  }

  public void testNewExpressionWithDiamond() {
    doTest();
  }

  public void testNewExpressionWithPreservedDiamond() {
    doTest();
  }

  public void testAugmentedAssignment() {
    doTest();
  }

  public void testUsedInInnerClass() {       // IDEADEV-28786
    doTest();
  }

  public void testUsedInInnerClass2() {       // IDEADEV-28786
    doTest();
  }

  public void testUsedInInnerClass3() {       // IDEADEV-28786
    UiInterceptors.register(new ChooserInterceptor(List.of("This reference only", "All 2 references and remove the variable"),
                                                   "All 2 references and remove the variable"));
    doTest();
  }

  public void testUsedInInnerClass4() {       // IDEADEV-28786
    UiInterceptors.register(new ChooserInterceptor(List.of("This reference only", "All 2 references and remove the variable"),
                                                   "All 2 references and remove the variable"));
    doTest();
  }

  public void testAnotherDefinitionUsed() {
    doTest("Cannot perform refactoring.\nAnother variable 'bar' definition is used together with inlined one");
  }

  public void testAnotherDefinitionUsed1() {
    doTest("Cannot perform refactoring.\nAnother variable 'bar' definition is used together with inlined one");
  }

  public void testTypeArgumentsStatic() {
    doTest();
  }

  public void testTypeArguments() {
    doTest();
  }

  public void testWildcard() {
    doTest();
  }

  public void testStaticImported() {
    doTest();
  }

  public void testQualified() {
    doTest();
  }

  public void testAssignmentToArrayElement() {
    UiInterceptors.register(new ChooserInterceptor(List.of("This reference only", "All 2 references and remove the variable"),
                                                   "All 2 references and remove the variable"));
    doTest("Cannot perform refactoring.\n" +
           "Variable 'arr' is accessed for writing");
  }

  public void testArrayMethodCallInitialized() {
    doTest();
  }

  public void testArrayIndex() {
    doTest();
  }

  public void testNonEqAssignment() {
    doTest("Cannot perform refactoring.\n" +
           "Cannot find a single definition to inline");
  }

  public void testInlineFromTryCatch() {
    doTest("Unable to inline outside try/catch statement");
  }

  public void testInlineFromTryCatchAvailable() {
    doTest();
  }

  public void testConditionExpr() {
    doTest();
  }

  public void testLambdaExpr() {
    doTest(LanguageLevel.JDK_1_8);
  }

  public void testLambdaExprAsRefQualifier() {
    doTest(LanguageLevel.JDK_1_8);
  }

  public void testMethodRefAsRefQualifier() {
    doTest(LanguageLevel.JDK_1_8);
  }

  public void testLocalVarInsideLambdaBody() {
    UiInterceptors.register(new ChooserInterceptor(List.of("This reference only", "All 2 references and remove the variable"),
                                                   "All 2 references and remove the variable"));
    doTest(LanguageLevel.JDK_1_8);
  }

  public void testLocalVarInsideLambdaBody1() {
    doTest(LanguageLevel.JDK_1_8);
  }

  public void testLocalVarInsideLambdaBody2() { doTest(LanguageLevel.JDK_1_8); }

  public void testLocalVarUsedInLambdaBody() { doTest(LanguageLevel.JDK_1_8); }

  public void testCastAroundLambda() { doTest(LanguageLevel.JDK_1_8); }

  public void testNoCastAroundLambda() { doTest(LanguageLevel.JDK_1_8); }

  public void testNoCastWithVar() { doTest(LanguageLevel.JDK_10); }

  public void testDiamondInAnonymousClass() { doTest(LanguageLevel.JDK_11); }

  public void testAssignmentInAnonymousClass() { doTest(); }

  public void testAssignmentInAnonymousClass2() { doTest(); }

  public void testUncheckedCast() {
    doTest();
  }

  public void testUncheckedCastNotNeeded() {
    doTest();
  }

  public void testCastNotNeeded() {
    doTest();
  }

  public void testResourceVariable() {
    doTest();
  }

  public void testEnclosingThisExpression() {
    doTest();
  }

  public void testParentStaticQualifier() {
    doTest();
  }

  public void testCollapseArrayCreation() {
    doTest();
  }

  public void testRenameLambdaParamsToAvoidConflicts() {
    doTest();
  }

  public void testParenthesisAroundInlinedLambda() {
    doTest();
  }

  public void testArrayAccessPriority() {
    doTest();
  }

  public void testDecodeRefsBeforeCheckingOverRedundantCasts() {
    doTest();
  }

  public void testDontOpenMultidimensionalArrays() {
    doTest();
  }

  public void testInsertNarrowingCastToAvoidSemanticsChange() {
    doTest();
  }

  public void testInsertCastToGenericTypeToProvideValidReturnType() {
    doTest();
  }

  public void testDisableShortCircuit() {
    doTest();
  }

  public void testOperationPrecedenceWhenInlineToStringConcatenation() {
    doTest();
  }

  public void testParenthesisAroundCast() {
    doTest();
  }

  public void testLocalVarInsideLambdaBodyWriteUsage() {
    doTest("Cannot perform refactoring.\n" +
           "Variable 'hello' is accessed for writing");
  }

  public void testReassignedVariableNoOption() {
    doTest();
  }

  public void testInlineVariableIntoNestedLambda() {
    doTest();
  }

  public void testAvoidTypeSpecificationWhenPossibleToAvoid() {
    doTest();
  }

  public void testLocalInsideLambdaWithNestedLambda() { doTest(); }

  public void testDefInMultiAssignmentStatement() { doTest(); }

  public void testPrivateOverload() { doTest(); }

  public void testAssignedVarsUpdatedBeforeRead() {
    UiInterceptors.register(new ChooserInterceptor(List.of("Highlight 3 conflicting writes", "Ignore writes and continue"), 
                                                   "Ignore writes and continue"));
    doTest();
  }

  public void testAssignedVarUpdatedAfterRead() {
    doTest();
  }

  public void testAssignmentAndReassignmentInLoop() {
    UiInterceptors.register(new ChooserInterceptor(List.of("Highlight 1 conflicting write", "Ignore writes and continue"),
                                                   "Ignore writes and continue"));
    doTest();
  }

  public void testLoopReassignment() {
    UiInterceptors.register(new ChooserInterceptor(List.of("Highlight 1 conflicting write", "Ignore writes and continue"),
                                                   "Ignore writes and continue"));
    doTest();
  }

  public void testOuterLoopReassignment() {
    UiInterceptors.register(new ChooserInterceptor(List.of("Highlight 1 conflicting write", "Ignore writes and continue"),
                                                   "Ignore writes and continue"));
    doTest();
  }

  public void testUnusedReassignmentInLoop() {
    doTest();
  }
  
  public void testCompilationError() {
    doTest();
  }

  public void testCompilationErrorAtRef() {
    doTest();
  }

  public void testCompilationErrorAssignment() {
    doTest("Cannot perform refactoring.\n" +
           "Code contains syntax errors. Cannot perform necessary analysis.");
  }

  public void testEolComment() {
    doTest();
  }
  
  public void testCompositeAssignment() { doTest(); }
  
  public void testCompositeAssignmentCast() { doTest(); }
  
  public void testLambdaInitialization() { doTest(); }

  private void doTest(String conflictMessage) {
    try {
      doTest();
      fail("Conflict was not detected");
    }
    catch (RuntimeException | AssertionError e) {
      assertEquals(conflictMessage, e.getMessage());
    }
  }

  public void testVariableInsideResourceList() {
    doTest("Cannot perform refactoring.\n" +
           "Variable is used as resource reference");
  }

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
  
  public void testInLambda() {
    doTest(LanguageLevel.JDK_1_8);
  }

  private void doTest() {
    doTest(LanguageLevel.JDK_1_7);
  }

  private void doTest(LanguageLevel languageLevel) {
    String fileName = prepareTest(languageLevel);
    performInline(getProject(), getEditor());
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResultByFile(fileName + ".after");
  }

  private String prepareTest(LanguageLevel languageLevel) {
    setLanguageLevel(languageLevel);
    String name = getTestName(false);
    String fileName = "/refactoring/inlineLocal/" + name + ".java";
    configureByFile(fileName);
    return fileName;
  }

  private static PsiLocalVariable getTarget(Editor editor) {
    PsiElement element = TargetElementUtil
      .findTargetElement(editor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertTrue(element instanceof PsiLocalVariable);

    return (PsiLocalVariable)element;
  }

  public static void performInline(Project project, Editor editor) {
    PsiLocalVariable element = getTarget(editor);
    new InlineLocalHandler().inlineElement(project, editor, element);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
  }
}
