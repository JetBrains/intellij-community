package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.TestLookupManager;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.inplace.ResolveSnapshotProvider;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.testFramework.LightCodeInsightTestCase;

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

  //reference itself won't be renamed
  private void doTestInplaceRename(String newName) throws Exception {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    PsiElement element = TargetElementUtilBase.findTargetElement(myEditor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertNotNull(element);
    final PsiMethod methodScope = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    assertNotNull(methodScope);

    ResolveSnapshotProvider resolveSnapshotProvider = VariableInplaceRenamer.INSTANCE.forLanguage(getFile().getLanguage());
    assertNotNull(resolveSnapshotProvider);
    final ResolveSnapshotProvider.ResolveSnapshot snapshot = resolveSnapshotProvider.createSnapshot(methodScope);
    assertNotNull(snapshot);

    final int offset = element.getTextOffset();
    VariableInplaceRenamer renamer = new VariableInplaceRenamer((PsiNameIdentifierOwner)element, getEditor());
    ((TemplateManagerImpl)TemplateManager.getInstance(getProject())).setTemplateTesting(true);
    try {
      renamer.performInplaceRename();
    }
    finally {
      renamer.finish();
      snapshot.apply(newName);

      TemplateManagerImpl.getTemplateState(myEditor).gotoEnd();
      renamer.performAutomaticRename(newName, PsiTreeUtil.getParentOfType(myFile.findElementAt(offset), PsiNameIdentifierOwner.class));
      ((TestLookupManager)LookupManager.getInstance(getProject())).clearLookup();
      ((TemplateManagerImpl)TemplateManager.getInstance(getProject())).setTemplateTesting(false);
    }
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }
}
