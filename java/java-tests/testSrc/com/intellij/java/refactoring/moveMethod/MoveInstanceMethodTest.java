// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.moveMethod;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodHandler;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodProcessor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.VisibilityUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@TestApplication
public class MoveInstanceMethodTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testSimple() { doTest(true, 0); }

  @Test
  public void testSimpleWithTargetField() { doTest(false, 1); }

  @Test
  public void testInterface() { doTest(true, 0); }

  @Test
  public void testWithInner() { doTest(true, 0); }

  @Test
  public void testJavadoc() { doTest(true, 0); }

  @Test
  public void testRecursive() { doTest(true, 0); }

  @Test
  public void testRecursive1() { doTest(true, 0); }

  @Test
  public void testQualifiedThis() { doTest(true, 0); }

  @Test
  public void testQualifyThisHierarchy() {doTest(true, 0);}

  @Test
  public void testQualifyField() {doTest(false, 0);}

  @Test
  public void testAnonymousHierarchy() {doTest(true, 0);}

  @Test
  public void testTwoParams() { doTest(true, 0); }

  @Test
  public void testNoThisParam() { doTest(false, 0); }

  @Test
  public void testNoGenerics() { doTest(false, 0); }

  @Test
  public void testQualifierToArg1() { doTest(true, 0); }

  @Test
  public void testQualifierToArg2() { doTest(true, 0); }

  @Test
  public void testQualifierToArg3() { doTest(true, 0); }

  @Test
  public void testIDEADEV11257() { doTest(true, 0); }

  @Test
  public void testThisInAnonymous() { doTest(true, 0); }

  @Test
  public void testOverloadingMethods() { doTest(true, 0); }
  @Test
  public void testOverloadingMethods1() { doTest(true, 0); }
  @Test
  public void testMoveAbstractMethod() { doTest(true, 0); }
  @Test
  public void testNoIncorrectParameterAdded() { doTest(true, 0); }
  @Test
  public void testPolyadicExpr() { doTest(true, 0); }

  @Test
  public void testIOOBE_MovingInvalidCode() { doTest(true, 0); }

  @Test
  public void testEscalateVisibility() {
    doTest(true, 0, VisibilityUtil.ESCALATE_VISIBILITY);
  }

  @Test
  public void testSameNames() {
    doTest(true, 0);
  }

  @Test
  public void testCorrectThisRefs() {
    doTest(true, 0);
  }

  @Test
  public void testSameNamesRecursion() {
    doTest(true, 0);
  }

  @Test
  public void testDefaultInClass() {
    doTest(true, 0);
  }

  @Test
  public void testQualifyFieldAccess() {
    doTest(false, 0);
  }

  @Test
  public void testStripFieldQualifier() {
    doTest(false, 0);
  }

  @Test
  public void testUsageInAnonymousClass() {
    doTest(true, 0);
  }

  @Test
  public void testUsageInInnerClass() {
    doTest(false, 0);
  }

  @Test
  public void testInterfaceMethodIntoClass() {
    doTest(true, 0);
  }

  @Test
  public void testInterfaceMethodIntoClass2() {
    doTest(true, 0);
  }

  @Test
  public void testConflictingLocalVariableAndTargetClassField() {
    doTest(true, 0);
  }

  @Test
  public void testMethodReference() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest(true, 0));
  }

  @Test
  public void testThisMethodReferenceWithTargetField() {
    doTest(false, 0);
  }

  @Test
  public void testMethodReferenceWithThisTarget() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest(true, 0));
  }

  @Test
  public void testMethodReferenceToExpandToLambda() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest(true, 1));
  }

  @Test
  public void testForeignMethodReferenceWithTargetField() {
    doTest(false, 0);
  }

  @Test
  public void testParameterMethodReference() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(()->doTest(true, 0));
  }

  private void doTest(boolean isTargetParameter, int targetIndex) {
    doTest(isTargetParameter, targetIndex, null);
  }

  private void doTest(boolean isTargetParameter, int targetIndex, String newVisibility) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String filePath = "/refactoring/moveInstanceMethod/" + myFixture.getTestName(false) + ".java";
    myFixture.configureByFile(filePath);
    myFixture.runInCommand(() -> {
      final PsiElement targetElement = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
      assertInstanceOf(PsiMethod.class, targetElement, "<caret> is not on method name");
      PsiMethod method = (PsiMethod) targetElement;
      final PsiVariable targetVariable = isTargetParameter ? method.getParameterList().getParameters()[targetIndex] :
                                         method.getContainingClass().getFields()[targetIndex];
      new MoveInstanceMethodProcessor(myFixture.getProject(), method, targetVariable, newVisibility, 
                                      MoveInstanceMethodHandler.suggestParameterNames (method, targetVariable)).run();
      myFixture.checkResultByFile(filePath + ".after");
    });
  }
}
