// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.inline.InlineParameterExpressionProcessor;
import com.intellij.refactoring.inline.InlineParameterHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class InlineParameterTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testSameValue() {
    doTest(true);
  }

  @Test
  public void testNullValue() {
    doTest(true);
  }

  @Test
  public void testConstructorCall() {
    doTest(true);
  }

  @Test
  public void testStaticFinalField() {
    doTest(true);
  }

  @Test
  public void testStaticFinalFieldDifferentQualifiers() {
    doTest(true);
  }

  @Test
  public void testRefIdentical() {
     doTest(true);
   }

  @Test
  public void testRefIdenticalNoLocal() {
     doTest(false);
   }

  @Test
  public void testRefLocalConstantInitializer() {
     doTest(false);
  }

  @Test
  public void testRefLocalWithLocal() {
     doTest(false);
  }

  @Test
  public void testRefMethod() {
     doTest(true);
  }

  @Test
  public void testRefMethodOnLocal() {
     doTest(false);
  }

  @Test
  public void testRefFinalLocal() {
     doTest(true);
  }

  @Test
  public void testRefStaticField() {
     doTest(true);
  }

  @Test
  public void testRefFinalLocalInitializedWithMethod() {
    doTest(false);
  }

  @Test
  public void testRefSelfField() {
    doTest(false);
  }

  @Test
  public void testRefStaticMethod() {
    doTest(true);
  }

  @Test
  public void testRefOuterThis() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on <b><code>this</code></b> which is not accessible inside the parameter's method", e.getMessage());
    }
  }

  @Test
  public void testInlineLambda() {
    doTest(false);
  }

  @Test
  public void testInlineLambdaWithOuterRef() {
    doTest(false);
  }

  @Test
  public void testRefThis() {
    doTest(false);
  }

  @Test
  public void testRefQualifiedThis() {
    doTest(false);
  }

  @Test
  public void testRefSameNonFinalField() {
    doTest(false);
  }

  @Test
  public void testRefSameNonFinalFieldOtherObject() {
    doTestCannotFindInitializer();
  }

  @Test
  public void testRefSameFinalFieldOtherObject() {
    doTestCannotFindInitializer();
  }

  @Test
  public void testRef2ConstantsWithTheSameValue() {
    doTest(false);
  }

  @Test
  public void testRefConstantAndField() {
    doTestCannotFindInitializer();
  }

  @Test
  public void testRefNewInner() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on class <b><code>User.Local</code></b> which is not accessible inside the parameter's method", e.getMessage());
    }
  }

  @Test
  public void testRightSideAssignment() { doTest(false); }
  @Test
  public void testRefNewInnerForMethod() { doTest(false); }
  @Test
  public void testRefNewInnerAvailable() { doTest(false); }
  @Test
  public void testLocalVarDeclarationInConstructor() {
  LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get(); IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, () ->  doTest(true)); }
  @Test
  public void testFlexibleConstructorBody() { doTest(true); }
  @Test
  public void testFromClassInitializer() { doTest(false); }
  @Test
  public void testPropagatedParams() { doTest(false); }

  @Test
  public void testParameterWithWriteAccess() {
    try {
      doTest(false);
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Inlining parameter with write usages is not supported", e.getMessage());
    }
  }

  @Test
  public void testRefNewInnerFromMethod() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on local class <b><code>Local</code></b> which is not accessible inside the parameter's method", e.getMessage());
    }
  }

  @Test
  public void testRefNewInnerInHierarchyAvailable() {
    doTest(false);
  }

  @Test
  public void testRefNewTopLevel() {
    doTest(false);
  }

  @Test
  public void testConflictingFieldName() {
    doTest(true);
  }

  @Test
  public void testRefNewLocal() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on local class <b><code>Local</code></b> which is not accessible inside the parameter's method", e.getMessage());
    }
  }

  @Test
  public void testRefArrayAccess() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Write access to parameter initializer cannot be inlined", e.getMessage());
    }
  }

  @Test
  public void testRefCallerParameter() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on caller's parameter <b><code>objct</code></b>", e.getMessage());
    }
  }


  @Test
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

  @Test
  public void testRefNonStatic() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on method <b><code>provideObject()</code></b> which is not accessible inside the parameter's method", e.getMessage());
    }
  }

  @Test
  public void testRefNonStaticClass() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on non-static class <b><code>ExpData.DD</code></b> which is not accessible inside the parameter's method", e.getMessage());
    }
  }
  
  @Test
  public void testRefNonStaticClassArray() {
    doTest(false);
  }
  
  @Test
  public void testNoWarning() {
    doTest(false);
  }

  @Test
  public void testRefThisFromStatic() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on <b><code>this</code></b> which is not accessible inside the parameter's method", e.getMessage());
    }
  }

  @Test
  public void testVisibility() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on method <b><code>VisibilityPinline.provideObject()</code></b> which is not accessible inside the parameter's method", e.getMessage());
    }
  }

  @Test
  public void testWriteAccess() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Write access to parameter initializer cannot be inlined", e.getMessage());
    }
  }

  @Test
  public void testRefCallerParameterInCallChain() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on caller's parameter <b><code>b</code></b>", e.getMessage());
    }
  }

  @Test
  public void testInlineLocalParamDef() {
    doTest(false);
  }

  @Test
  public void testInlineRecursive() {
    doTest(false);
  }

  @Test
  public void testCantInlineRecursive() {
    try {
      doTest(false);
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Cannot find constant initializer for parameter", e.getMessage());
    }
  }
  
  @Test
  public void testCantInlineRecursive2() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on caller's parameter <b><code>a</code></b>", e.getMessage());
    }
  }

  @Test
  public void testParameterDefWithWriteAccess() {
    try {
      doTest(false);
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Method has no usages", e.getMessage());
    }
  }

  @Test
  public void testSameSignatureExistConflict() {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method <b><code>doTest()</code></b> is already defined in class <b><code>Test</code></b>", e.getMessage());
    }
  }
  
  @Test
  public void testInlineVararg() { doTest(false); }
  @Test
  public void testVarargs() { doTest(false); }
  @Test
  public void testArrayInitializer() { doTest(false); }

  private void doTest(boolean createLocal) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.getProject().putUserData(InlineParameterExpressionProcessor.CREATE_LOCAL_FOR_TESTS, createLocal);

    @NonNls String fileName = "/refactoring/inlineParameter/" + myFixture.getTestName(false) + ".java";
    myFixture.configureByFile(fileName);
    myFixture.runInCommand(() -> {
      performAction();
      myFixture.checkResultByFile(fileName + ".after", true);
    });
  }

  private void performAction() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED |
                                                                                TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    new InlineParameterHandler().inlineElement(myFixture.getProject(), myFixture.getEditor(), element);
  }
}
