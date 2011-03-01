package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.lang.java.JavaRefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;

/**
 * @author ven
 */
public class RenameLocalTest extends LightCodeInsightTestCase {
  private static final String BASE_PATH = "/refactoring/renameLocal/";

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testIDEADEV3320() throws Exception {
    doTest("f");
  }

  public void testIDEADEV13849() throws Exception {
    doTest("aaaaa");
  }

  public void testConflictWithOuterClassField() throws Exception {  // IDEADEV-24564
    doTest("f");
  }

  public void testConflictWithJavadocTag() throws Exception {
    doTest("i");
  }

  public void testRenameLocalIncomplete() throws Exception {
    doTest("_i");
  }

  public void testRenameParamIncomplete() throws Exception {
    doTest("_i");
  }

  private void doTest(final String newName) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement element = TargetElementUtilBase
      .findTargetElement(myEditor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    new RenameProcessor(getProject(), element, newName, true, true).run();
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testRenameInPlaceQualifyFieldReference() throws Exception {
    doTestInplaceRename("myI");
  }

  public void testRenameInPlaceParamInOverriderAutomaticRenamer() throws Exception {
    doTestInplaceRename("pp");
  }

  public void testRenameInPlaceParamInOverriderAutomaticRenamerConflict() throws Exception {
    doTestInplaceRename("pp");
  }

  public void testRenameResource() throws Exception {
    doTest("r1");
  }

  public void testRenameResourceInPlace() throws Exception {
    doTestInplaceRename("r1");
  }

  private void doTestInplaceRename(final String newName) throws Exception {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");

    final PsiElement element = TargetElementUtilBase.findTargetElement(myEditor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertNotNull(element);
    assertTrue("In-place rename not allowed for " + element,
               JavaRefactoringSupportProvider.mayRenameInplace(element, null));

    CodeInsightTestUtil.doInlineRename(new VariableInplaceRenameHandler(), newName, getEditor(), element);

    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }
}
