// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.inline.InlineLocalHandler;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class InlineLocalTest extends LightJavaCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testInference() {
    doTest(false);
  }

  public void testQualifier() {
    doTest(false);
  }

  public void testInnerInnerClass() {
    doTest(true);
  }

  public void testIDEADEV950() {
    doTest(false);
  }

  public void testNoRedundantCasts() {
    doTest(false);
  }

  public void testIdeaDEV9404() {
    doTest(false);
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk17(); // there is JPanel inside
  }

  public void testIDEADEV12244() {
    doTest(false);
  }

  public void testIDEADEV10376() {
    doTest(true);
  }

  public void testIDEADEV13151() {
    doTest(true);
  }

  public void testArrayInitializer() {
    doTest(false);
  }

  public void testNonWriteUnaryExpression() {
    doTest(true);
  }

  public void testNewExpression() {
    doTest(false);
  }

  public void testNewExpressionWithDiamond() {
    doTest(false);
  }

  public void testNewExpressionWithPreservedDiamond() {
    doTest(false);
  }

  public void testAugmentedAssignment() {
    String exception = null;
    try {
      doTest(false);
    }
    catch (RuntimeException ex) {
      exception = ex.getMessage();
    }
    String error = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("variable.is.accessed.for.writing", "text"));
    assertEquals(error, exception);
  }

  public void testUsedInInnerClass() {       // IDEADEV-28786
    doTest(true);
  }

  public void testUsedInInnerClass2() {       // IDEADEV-28786
    doTest(true);
  }

  public void testUsedInInnerClass3() {       // IDEADEV-28786
    doTest(true);
  }

  public void testUsedInInnerClass4() {       // IDEADEV-28786
    doTest(true);
  }

  public void testAnotherDefinitionUsed() {
    doTest(true, "Cannot perform refactoring.\nAnother variable 'bar' definition is used together with inlined one");
  }

  public void testAnotherDefinitionUsed1() {
    doTest(false, "Cannot perform refactoring.\nAnother variable 'bar' definition is used together with inlined one");
  }

  public void testTypeArgumentsStatic() {
    doTest(true);
  }

  public void testTypeArguments() {
    doTest(true);
  }

  public void testWildcard() {
    doTest(true);
  }

  public void testStaticImported() {
    doTest(true);
  }

  public void testQualified() {
    doTest(true);
  }

  public void testAssignmentToArrayElement() {
    doTest(true, "Cannot perform refactoring.\n" +
                 "Variable 'arr' is accessed for writing");
  }

  public void testArrayMethodCallInitialized() {
    doTest(false);
  }

  public void testArrayIndex() {
    doTest(true);
  }

  public void testNonEqAssignment() {
    doTest(false, "Cannot perform refactoring.\n" +
                  "Variable 'x' is accessed for writing");
  }

  public void testInlineFromTryCatch() {
    doTest(true, "Unable to inline outside try/catch statement");
  }

  public void testInlineFromTryCatchAvailable() {
    doTest(true);
  }

  public void testConditionExpr() {
    doTest(true);
  }

  public void testLambdaExpr() {
    doTest(true, LanguageLevel.JDK_1_8);
  }

  public void testLambdaExprAsRefQualifier() {
    doTest(true, LanguageLevel.JDK_1_8);
  }

  public void testMethodRefAsRefQualifier() {
    doTest(true, LanguageLevel.JDK_1_8);
  }

  public void testLocalVarInsideLambdaBody() {
    doTest(true, LanguageLevel.JDK_1_8);
  }

  public void testLocalVarInsideLambdaBody1() {
    doTest(true, LanguageLevel.JDK_1_8);
  }

  public void testLocalVarInsideLambdaBody2() { doTest(true, LanguageLevel.JDK_1_8); }
  public void testLocalVarUsedInLambdaBody() { doTest(true, LanguageLevel.JDK_1_8); }
  public void testCastAroundLambda() { doTest(true, LanguageLevel.JDK_1_8); }
  public void testNoCastAroundLambda() { doTest(true, LanguageLevel.JDK_1_8); }
  public void testNoCastWithVar() { doTest(true, LanguageLevel.JDK_10); }
  public void testDiamondInAnonymousClass() { doTest(true, LanguageLevel.JDK_11); }
  public void testAssignmentInAnonymousClass() { doTest(true); }
  public void testAssignmentInAnonymousClass2() { doTest(true); }

  public void testUncheckedCast() {
    doTest(true);
  }

  public void testUncheckedCastNotNeeded() {
    doTest(true);
  }

  public void testCastNotNeeded() {
    doTest(true);
  }

  public void testResourceVariable() {
    doTest(false);
  }

  public void testEnclosingThisExpression() {
    doTest(true);
  }

  public void testParentStaticQualifier() {
    doTest(true);
  }

  public void testCollapseArrayCreation() {
    doTest(true);
  }

  public void testRenameLambdaParamsToAvoidConflicts() {
    doTest(true);
  }

  public void testParenthesisAroundInlinedLambda() {
    doTest(true);
  }

  public void testArrayAccessPriority() {
    doTest(true);
  }

  public void testDecodeRefsBeforeCheckingOverRedundantCasts() {
    doTest(true);
  }

  public void testDontOpenMultidimensionalArrays() {
    doTest(false);
  }

  public void testInsertNarrowingCastToAvoidSemanticsChange() {
    doTest(false);
  }

  public void testInsertCastToGenericTypeToProvideValidReturnType() {
    doTest(false);
  }

  public void testDisableShortCircuit() {
    doTest(false);
  }

  public void testOperationPrecedenceWhenInlineToStringConcatenation() {
    doTest(false);
  }

  public void testParenthesisAroundCast() {
    doTest(false);
  }

  public void testLocalVarInsideLambdaBodyWriteUsage() {
    doTest(true, "Cannot perform refactoring.\n" +
                 "Variable 'hello' is accessed for writing");
  }

  public void testInlineVariableIntoNestedLambda() {
    doTest(false);
  }

  public void testAvoidTypeSpecificationWhenPossibleToAvoid() {
    doTest(false);
  }

  public void testLocalInsideLambdaWithNestedLambda() { doTest(true); }
  public void testDefInMultiAssignmentStatement() { doTest(true); }
  public void testPrivateOverload() { doTest(true); }

  public void testAssignedVarsUpdatedBeforeRead() {
    doTestConflict("Variable 'p2' is changed before last access to variable 'f'.",
                   "Variable 'p1' is changed before last access to variable 'f'.",
                   "Variable 'p2' is changed before last access to variable 'f'.");
  }

  public void testAssignedVarUpdatedAfterRead() {
    doTest(false);
  }

  public void testAssignmentAndReassignmentInLoop() {
    doTestConflict("Variable 'replacement' is changed before last access to variable 'original'.");
  }

  public void testLoopReassignment() {
    doTestConflict("Variable 'replacement' is changed before last access to variable 'original'.");
  }

  public void testOuterLoopReassignment() {
    doTestConflict("Variable 'replacement' is changed before last access to variable 'original'.");
  }

  public void testUnusedReassignmentInLoop() {
    doTest(false);
  }

  public void testEolComment() {
    doTest(false);
  }
  
  private void doTest(final boolean inlineDef, String conflictMessage) {
    try {
      doTest(inlineDef);
      fail("Conflict was not detected");
    }
    catch (RuntimeException e) {
      assertEquals(conflictMessage, e.getMessage());
    }
  }

  public void testVariableInsideResourceList() {
    doTest(false, "Cannot perform refactoring.\n" +
                  "Variable is used as resource reference");
  }

  private void doTest(final boolean inlineDef) {
    doTest(inlineDef, LanguageLevel.JDK_1_7);
  }

  private void doTest(final boolean inlineDef, LanguageLevel languageLevel) {
    String fileName = prepareTest(languageLevel);
    if (!inlineDef) {
      performInline(getProject(), getEditor());
    }
    else {
      performDefInline(getProject(), getEditor());
    }
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResultByFile(fileName + ".after");
  }

  private void doTestConflict(@NotNull final String conflict, final String @NotNull ... rest) {
    List<String> expected = new ArrayList<>(Arrays.asList(rest));
    expected.add(conflict);

    try {
      doTest(false);
      fail("Conflict weren't found");
    } catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      Collection<String> actual = e.getMessages();
      assertSameElements(expected, actual);
    }
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
    InlineLocalHandler.inlineVariable(project, editor, element, null);
  }

  public static void performDefInline(Project project, Editor editor) {
    PsiReference reference = TargetElementUtil.findReference(editor);
    assertTrue(reference instanceof PsiReferenceExpression);
    final PsiElement local = reference.resolve();
    assertTrue(local instanceof PsiLocalVariable);

    InlineLocalHandler.inlineVariable(project, editor, (PsiLocalVariable)local, (PsiReferenceExpression)reference);
  }
}
