// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public void testStaticFinalFieldDifferentQualifiers() {
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
      assertEquals("Parameter initializer depends on <b><code>this<code></b> which is not accessible inside the parameter's method", e.getMessage());
    }
  }

  public void testInlineLambda() {
    doTest(false);
  }

  public void testInlineLambdaWithOuterRef() {
    doTest(false);
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

  public void testRefSameFinalFieldOtherObject() {
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
      assertEquals("Parameter initializer depends on class <b><code>User.Local</code></b> which is not accessible inside the parameter's method", e.getMessage());
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
      assertEquals("Inlining parameter with write usages is not supported", e.getMessage());
    }
  }

  public void testRefNewInnerFromMethod() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on local class <b><code>Local</code></b> which is not accessible inside the parameter's method", e.getMessage());
    }
  }

  public void testRefNewInnerInHierarchyAvailable() {
    doTest(false);
  }

  public void testRefNewTopLevel() {
    doTest(false);
  }

  public void testConflictingFieldName() {
    doTest(true);
  }

  public void testRefNewLocal() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on local class <b><code>Local</code></b> which is not accessible inside the parameter's method", e.getMessage());
    }
  }

  public void testRefArrayAccess() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Write access to parameter initializer cannot be inlined", e.getMessage());
    }
  }

  public void testRefCallerParameter() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on caller's parameter <b><code>objct</code></b>", e.getMessage());
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
      assertEquals("Parameter initializer depends on method <b><code>provideObject()</code></b> which is not accessible inside the parameter's method", e.getMessage());
    }
  }

  public void testRefNonStaticClass() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on non-static class <b><code>ExpData.DD</code></b> which is not accessible inside the parameter's method", e.getMessage());
    }
  }
  
  public void testRefNonStaticClassArray() {
    doTest(false);
  }
  
  public void testNoWarning() {
    doTest(false);
  }

  public void testRefThisFromStatic() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on <b><code>this<code></b> which is not accessible inside the parameter's method", e.getMessage());
    }
  }

  public void testVisibility() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on method <b><code>VisibilityPinline.provideObject()</code></b> which is not accessible inside the parameter's method", e.getMessage());
    }
  }

  public void testWriteAccess() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Write access to parameter initializer cannot be inlined", e.getMessage());
    }
  }

  public void testRefCallerParameterInCallChain() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on caller's parameter <b><code>b</code></b>", e.getMessage());
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
    }
  }
  
  public void testCantInlineRecursive2() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on caller's parameter <b><code>a</code></b>", e.getMessage());
    }
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
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method <b><code>doTest()</code></b> is already defined in class <b><code>Test</code></b>", e.getMessage());
    }
  }
  
  public void testInlineVararg() { doTest(false); }
  public void testVarargs() { doTest(false); }
  public void testArrayInitializer() { doTest(false); }

  private void doTest(boolean createLocal) {
    getProject().putUserData(InlineParameterExpressionProcessor.CREATE_LOCAL_FOR_TESTS, createLocal);

    @NonNls String fileName = "/refactoring/inlineParameter/" + getTestName(false) + ".java";
    configureByFile(fileName);
    performAction();
    checkResultByFile(null, fileName + ".after", true);
  }

  private void performAction() {
    final PsiElement element = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED |
                                                                                TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    new InlineParameterHandler().inlineElement(getProject(), getEditor(), element);
  }
}
