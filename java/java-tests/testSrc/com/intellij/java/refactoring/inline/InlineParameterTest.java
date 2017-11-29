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
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.java.refactoring.LightRefactoringTestCase;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.inline.InlineParameterExpressionProcessor;
import com.intellij.refactoring.inline.InlineParameterHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */

public class InlineParameterTest extends LightRefactoringTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testSameValue() {
    doTest(true);
  }

  public void testNullValue() {
    doTest(true);
  }

  public void testConstructorCall() {
    doTest(true);
  }

  public void testStaticFinalField() {
    doTest(true);
  }

  public void testRefIdentical() {
     doTest(true);
   }

  public void testRefIdenticalNoLocal() {
     doTest(false);
   }

  public void testRefLocalConstantInitializer() {
     doTest(false);
  }

  public void testRefLocalWithLocal() {
     doTest(false);
  }

  public void testRefMethod() {
     doTest(true);
  }

  public void testRefMethodOnLocal() {
     doTest(false);
  }

  public void testRefFinalLocal() {
     doTest(true);
  }

  public void testRefStaticField() {
     doTest(true);
  }

  public void testRefFinalLocalInitializedWithMethod() {
    doTest(false);
  }

  public void testRefSelfField() {
    doTest(false);
  }

  public void testRefStaticMethod() {
    doTest(true);
  }

  public void testRefOuterThis() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on this which is not available inside the method and cannot be inlined", e.getMessage());
    }
  }

  public void testRefThis() {
    doTest(false);
  }

  public void testRefQualifiedThis() {
    doTest(false);
  }

  public void testRefSameNonFinalField() {
    doTest(false);
  }

  public void testRefSameNonFinalFieldOtherObject() {
    doTestCannotFindInitializer();
  }

  public void testRef2ConstantsWithTheSameValue() {
    doTest(false);
  }

  public void testRefConstantAndField() {
    doTestCannotFindInitializer();
  }

  public void testRefNewInner() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on class <b><code>User.Local</code></b> which is not available inside method and cannot be inlined", e.getMessage());
    }
  }

  public void testRightSideAssignment() {
    doTest(false);
  }

  public void testRefNewInnerForMethod() {
    doTest(false);
  }

  public void testRefNewInnerAvailable() {
    doTest(false);
  }

  public void testLocalVarDeclarationInConstructor() {
    doTest(true);
  }

  public void testFromClassInitializer() {
    doTest(false);
  }

  public void testPropagatedParams() {
    doTest(false);
  }

  public void testParameterWithWriteAccess() {
    try {
      doTest(false);
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Inline parameter which has write usages is not supported", e.getMessage());
    }
  }

  public void testRefNewInnerFromMethod() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on class <b><code>Local</code></b> which is not available inside method and cannot be inlined", e.getMessage());
    }
  }

  public void testRefNewInnerInHierarchyAvailable() {
    doTest(false);
  }

  public void testRefNewTopLevel() {
    doTest(false);
  }

  public void testRefNewLocal() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on class <b><code>Local</code></b> which is not available inside method and cannot be inlined", e.getMessage());
    }
  }

  public void testRefArrayAccess() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on value which is not available inside method and cannot be inlined", e.getMessage());
    }
  }

  public void testRefCallerParameter() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on callers parameter", e.getMessage());
    }
  }


  public void testHandleExceptions() {
    doTest(false);
  }

  private void doTestCannotFindInitializer() {
    try {
      doTest(false);
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Cannot find constant initializer for parameter", e.getMessage());
    }
  }

  public void testRefNonStatic() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on method <b><code>provideObject()</code></b> which is not available inside the static method", e.getMessage());
    }
  }

  public void testRefNonStaticClass() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on non static class which is not available inside static method", e.getMessage());
    }
  }

  public void testRefThisFromStatic() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on this which is not available inside the static method", e.getMessage());
    }
  }

  public void testVisibility() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on value which is not available inside method", e.getMessage());
    }
  }

  public void testWriteAccess() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on value which is not available inside method and cannot be inlined", e.getMessage());
    }
  }

  public void testRefCallerParameterInCallChain() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on callers parameter", e.getMessage());
    }
  }

  public void testInlineLocalParamDef() {
    doTest(false);
  }

  public void testInlineRecursive() {
    doTest(false);
  }

  public void testCantInlineRecursive() {
    try {
      doTest(false);
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Cannot find constant initializer for parameter", e.getMessage());
      return;
    }
    fail("Initializer shoul not be found");
  }

  public void testParameterDefWithWriteAccess() {
    try {
      doTest(false);
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Method has no usages", e.getMessage());
    }
  }

  public void testSameSignatureExistConflict() {
    try {
      doTest(false);
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method doTest() is already defined in the class <b><code>Test</code></b>", e.getMessage());
    }
  }

  private void doTest(final boolean createLocal) {
    getProject().putUserData(InlineParameterExpressionProcessor.CREATE_LOCAL_FOR_TESTS, createLocal);

    String name = getTestName(false);
    @NonNls String fileName = "/refactoring/inlineParameter/" + name + ".java";
    configureByFile(fileName);
    performAction();
    checkResultByFile(null, fileName + ".after", true);
  }

  private static void performAction() {
    final PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil
                                                                               .REFERENCED_ELEMENT_ACCEPTED |
                                                                             TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    new InlineParameterHandler().inlineElement(getProject(), myEditor, element);
  }
}
