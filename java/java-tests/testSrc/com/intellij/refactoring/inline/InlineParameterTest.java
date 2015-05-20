package com.intellij.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightRefactoringTestCase;
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

  public void testSameValue() throws Exception {
    doTest(true);
  }

  public void testNullValue() throws Exception {
    doTest(true);
  }

  public void testConstructorCall() throws Exception {
    doTest(true);
  }

  public void testStaticFinalField() throws Exception {
    doTest(true);
  }

  public void testRefIdentical() throws Exception {
     doTest(true);
   }

  public void testRefIdenticalNoLocal() throws Exception {
     doTest(false);
   }

  public void testRefLocalConstantInitializer() throws Exception {
     doTest(false);
  }

  public void testRefLocalWithLocal() throws Exception {
     doTest(false);
  }

  public void testRefMethod() throws Exception {
     doTest(true);
  }

  public void testRefMethodOnLocal() throws Exception {
     doTest(false);
  }

  public void testRefFinalLocal() throws Exception {
     doTest(true);
  }

  public void testRefStaticField() throws Exception {
     doTest(true);
  }

  public void testRefFinalLocalInitializedWithMethod() throws Exception {
    doTest(false);
  }

  public void testRefSelfField() throws Exception {
    doTest(false);
  }

  public void testRefStaticMethod() throws Exception {
    doTest(true);
  }

  public void testRefOuterThis() throws Exception {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on this which is not available inside the method and cannot be inlined", e.getMessage());
    }
  }

  public void testRefThis() throws Exception {
    doTest(false);
  }

  public void testRefQualifiedThis() throws Exception {
    doTest(false);
  }

  public void testRefSameNonFinalField() throws Exception {
    doTest(false);
  }

  public void testRefSameNonFinalFieldOtherObject() throws Exception {
    doTestCannotFindInitializer();
  }

  public void testRef2ConstantsWithTheSameValue() throws Exception {
    doTest(false);
  }

  public void testRefConstantAndField() throws Exception {
    doTestCannotFindInitializer();
  }

  public void testRefNewInner() throws Exception {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on class <b><code>User.Local</code></b> which is not available inside method and cannot be inlined", e.getMessage());
    }
  }

  public void testRightSideAssignment() throws Exception {
    doTest(false);
  }

  public void testRefNewInnerForMethod() throws Exception {
    doTest(false);
  }

  public void testRefNewInnerAvailable() throws Exception {
    doTest(false);
  }

  public void testLocalVarDeclarationInConstructor() throws Exception {
    doTest(true);
  }

  public void testFromClassInitializer() throws Exception {
    doTest(false);
  }

  public void testPropagatedParams() throws Exception {
    doTest(false);
  }

  public void testParameterWithWriteAccess() throws Exception {
    try {
      doTest(false);
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Inline parameter which has write usages is not supported", e.getMessage());
    }
  }

  public void testRefNewInnerFromMethod() throws Exception {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on class <b><code>Local</code></b> which is not available inside method and cannot be inlined", e.getMessage());
    }
  }

  public void testRefNewInnerInHierarchyAvailable() throws Exception {
    doTest(false);
  }

  public void testRefNewTopLevel() throws Exception {
    doTest(false);
  }

  public void testRefNewLocal() throws Exception {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on class <b><code>Local</code></b> which is not available inside method and cannot be inlined", e.getMessage());
    }
  }

  public void testRefArrayAccess() throws Exception {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on value which is not available inside method and cannot be inlined", e.getMessage());
    }
  }

  public void testRefCallerParameter() throws Exception {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on callers parameter", e.getMessage());
    }
  }


  public void testHandleExceptions() throws Exception {
    doTest(false);
  }

  private void doTestCannotFindInitializer() throws Exception {
    try {
      doTest(false);
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Cannot find constant initializer for parameter", e.getMessage());
    }
  }

  public void testRefNonStatic() throws Exception {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on method <b><code>provideObject()</code></b> which is not available inside the static method", e.getMessage());
    }
  }

  public void testRefNonStaticClass() throws Exception {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on non static class which is not available inside static method", e.getMessage());
    }
  }

  public void testRefThisFromStatic() throws Exception {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on this which is not available inside the static method", e.getMessage());
    }
  }

  public void testVisibility() throws Exception {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on value which is not available inside method", e.getMessage());
    }
  }

  public void testWriteAccess() throws Exception {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on value which is not available inside method and cannot be inlined", e.getMessage());
    }
  }

  public void testRefCallerParameterInCallChain() throws Exception {
    try {
      doTest(false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter initializer depends on callers parameter", e.getMessage());
    }
  }

  public void testInlineLocalParamDef() throws Exception {
    doTest(false);
  }

  public void testInlineRecursive() throws Exception {
    doTest(false);
  }

  public void testCantInlineRecursive() throws Exception {
    try {
      doTest(false);
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Cannot find constant initializer for parameter", e.getMessage());
      return;
    }
    fail("Initializer shoul not be found");
  }

  public void testParameterDefWithWriteAccess()  throws Exception {
    try {
      doTest(false);
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Method has no usages", e.getMessage());
    }
  }

  private void doTest(final boolean createLocal) throws Exception {
    getProject().putUserData(InlineParameterExpressionProcessor.CREATE_LOCAL_FOR_TESTS,createLocal);

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
