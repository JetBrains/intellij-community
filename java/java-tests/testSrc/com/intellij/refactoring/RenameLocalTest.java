package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.java.JavaRefactoringSupportProvider;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceField.InplaceIntroduceFieldPopup;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenameWrongRefHandler;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import sun.misc.Ref;

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

  public void testRenameWrongRef() throws Exception {
    doRenameWrongRef("i");
  }

  private void doRenameWrongRef(final String newName) throws Exception {
    final String name = getTestName(true);
    configureByFile(BASE_PATH + name + ".java");

    final TemplateManagerImpl templateManager = (TemplateManagerImpl)TemplateManager.getInstance(getProject());
    try {
      templateManager.setTemplateTesting(true);

      new RenameWrongRefHandler().invoke(getProject(), getEditor(), getFile(), null);

      final TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assert state != null;
      final TextRange range = state.getCurrentVariableRange();
      assert range != null;

      new WriteCommandAction.Simple(getProject()) {
        @Override
        protected void run() throws Throwable {
          getEditor().getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), newName);
        }
      }.execute().throwException();

      state.gotoEnd(false);
      checkResultByFile(BASE_PATH + name + "_after.java");
    }
    finally {
      templateManager.setTemplateTesting(false);
    }
  }
}
