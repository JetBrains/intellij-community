// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.moveMethod;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodHandler;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodProcessor;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;

public class MoveInstanceMethodTest extends LightJavaCodeInsightTestCase {

  public void testSimple() { doTest(true, 0); }

  public void testSimpleWithTargetField() { doTest(false, 1); }

  public void testInterface() { doTest(true, 0); }

  public void testWithInner() { doTest(true, 0); }

  public void testJavadoc() { doTest(true, 0); }

  public void testRecursive() { doTest(true, 0); }

  public void testRecursive1() { doTest(true, 0); }

  public void testQualifiedThis() { doTest(true, 0); }

  public void testQualifyThisHierarchy() {doTest(true, 0);}

  public void testQualifyField() {doTest(false, 0);}

  public void testAnonymousHierarchy() {doTest(true, 0);}

  public void testTwoParams() { doTest(true, 0); }

  public void testNoThisParam() { doTest(false, 0); }

  public void testNoGenerics() { doTest(false, 0); }

  public void testQualifierToArg1() { doTest(true, 0); }

  public void testQualifierToArg2() { doTest(true, 0); }

  public void testQualifierToArg3() { doTest(true, 0); }

  public void testIDEADEV11257() { doTest(true, 0); }

  public void testThisInAnonymous() { doTest(true, 0); }

  public void testOverloadingMethods() { doTest(true, 0); }
  public void testOverloadingMethods1() { doTest(true, 0); }
  public void testMoveAbstractMethod() { doTest(true, 0); }
  public void testNoIncorrectParameterAdded() { doTest(true, 0); }
  public void testPolyadicExpr() { doTest(true, 0); }

  public void testIOOBE_MovingInvalidCode() { doTest(true, 0); }

  public void testEscalateVisibility() {
    doTest(true, 0, VisibilityUtil.ESCALATE_VISIBILITY);
  }

  public void testSameNames() {
    doTest(true, 0);
  }

  public void testCorrectThisRefs() {
    doTest(true, 0);
  }

  public void testSameNamesRecursion() {
    doTest(true, 0);
  }

  public void testDefaultInClass() {
    doTest(true, 0);
  }

  public void testQualifyFieldAccess() {
    doTest(false, 0);
  }

  public void testStripFieldQualifier() {
    doTest(false, 0);
  }

  public void testUsageInAnonymousClass() {
    doTest(true, 0);
  }

  public void testInterfaceMethodIntoClass() {
    doTest(true, 0);
  }

  public void testInterfaceMethodIntoClass2() {
    doTest(true, 0);
  }

  public void testConflictingLocalVariableAndTargetClassField() {
    doTest(true, 0);
  }

  public void testMethodReference() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest(true, 0));
  }

  public void testThisMethodReferenceWithTargetField() {
    doTest(false, 0);
  }

  public void testMethodReferenceWithThisTarget() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest(true, 0));
  }

  public void testMethodReferenceToExpandToLambda() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest(true, 1));
  }

  public void testForeignMethodReferenceWithTargetField() {
    doTest(false, 0);
  }

  public void testParameterMethodReference() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(()->doTest(true, 0));
  }

  private void doTest(boolean isTargetParameter, int targetIndex) {
    doTest(isTargetParameter, targetIndex, null);
  }

  private void doTest(boolean isTargetParameter, int targetIndex, String newVisibility) {
    final String filePath = "/refactoring/moveInstanceMethod/" + getTestName(false) + ".java";
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    PsiMethod method = (PsiMethod) targetElement;
    final PsiVariable targetVariable = isTargetParameter ? method.getParameterList().getParameters()[targetIndex] :
                                       method.getContainingClass().getFields()[targetIndex];
    new MoveInstanceMethodProcessor(getProject(), method, targetVariable, newVisibility, 
                                    MoveInstanceMethodHandler.suggestParameterNames (method, targetVariable)).run();
    checkResultByFile(filePath + ".after");
  }

  @Override
  protected @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
