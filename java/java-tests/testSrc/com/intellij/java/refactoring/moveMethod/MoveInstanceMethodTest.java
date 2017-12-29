/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.refactoring.moveMethod;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.java.refactoring.LightRefactoringTestCase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodHandler;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodProcessor;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class MoveInstanceMethodTest extends LightRefactoringTestCase {

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

  public void testQualifyFieldAccess() {
    doTest(false, 0);
  }

  public void testStripFieldQualifier() {
    doTest(false, 0);
  }

  public void testUsageInAnonymousClass() {
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
    try {
      BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(true);
      doTest(true, 0);
    }
    finally {
      BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(false);
    }
  }

  private void doTest(boolean isTargetParameter, final int targetIndex) {
    doTest(isTargetParameter, targetIndex, null);
  }

  private void doTest(boolean isTargetParameter, final int targetIndex, final String newVisibility) {
    final String filePath = "/refactoring/moveInstanceMethod/" + getTestName(false) + ".java";
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    PsiMethod method = (PsiMethod) targetElement;
    final PsiVariable targetVariable = isTargetParameter ? method.getParameterList().getParameters()[targetIndex] :
                                       method.getContainingClass().getFields()[targetIndex];
    new MoveInstanceMethodProcessor(getProject(),
                                    method, targetVariable, newVisibility, MoveInstanceMethodHandler.suggestParameterNames (method, targetVariable)).run();
    checkResultByFile(filePath + ".after");

  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

}
