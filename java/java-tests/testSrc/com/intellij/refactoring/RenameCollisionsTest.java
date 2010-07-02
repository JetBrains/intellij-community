package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.usageView.UsageInfo;
import org.junit.Assert;

import java.util.HashMap;

/**
 * @author sashache
 */
public class RenameCollisionsTest extends LightCodeInsightTestCase {
  private static final String BASE_PATH = "/refactoring/renameCollisions/";

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testRenameClassInnerToLocal() throws Exception {
    doTest("LocalClass");
  }

  public void testRenameClassLocalToAlien() throws Exception {
    doTest("String");
  }

  //Fails due to IDEADEV-25194.
  //public void testRenameClassLocalToAlienNoImports() throws Exception {
  //  doTest("String");
  //}

  public void testRenameClassLocalToInner() throws Exception {
    doTest("StaticInnerClass");
  }

  public void testRenameClassThisFqnToAlien() throws Exception {
    doTest("String");
  }

  public void testRenameClassThisToAlien() throws Exception {
    doTest("String");
  }

  public void testRenameMethodIndiInstancesInnerToOuter() throws Exception {
    doTest("method");
  }

  public void testRenameMethodIndiInstancesOuterToInner() throws Exception {
    doTest("siMethod");
  }

  public void testRenameMethodInnerInstanceToOuterInstance() throws Exception {
    doTest("method");
  }

  public void testRenameMethodInnerStaticToOuterStatic() throws Exception {
    doTest("staticMethod");
  }

  public void testRenameMethodOuterInstanceToInnerInstance() throws Exception {
    doTest("innerMethod");
  }

  public void testRenameMethodOuterStaticToInnerStatic() throws Exception {
    doTest("siStaticMethod");
  }

  public void testRenameMethodStaticToAlien() throws Exception {
    doTest("valueOf");
  }

  public void testRenameVarConstToAlien() throws Exception {
    doTest("CASE_INSENSITIVE_ORDER");
  }

  public void testRenameVarConstToAlien1() throws Exception {
    doTest("CASE_INSENSITIVE_ORDER");
  }

  public void testRenameVarConstToParam() throws Exception {
    doTest("param3");
  }

  public void testRenameVarFieldToLocal() throws Exception {
    doTest("localVar3");
  }

  public void testRenameVarInnerConstToOuterConst() throws Exception {
    doTest("STATIC_FIELD");
  }

  public void testRenameVarInnerFieldToOuterField() throws Exception {
    doTest("myField");
  }

  public void testRenameVarLocalToAlien() throws Exception {
    doTest("BOTTOM");
  }

  public void testRenameVarLocalToConst() throws Exception {
    doTest("INNER_STATIC_FIELD");
  }

  public void testRenameVarLocalToOuterField() throws Exception {
    doTest("myField");
  }

  public void testRenameVarOuterConstToInnerConst() throws Exception {
    doTest("SI_STATIC_FIELD");
  }

  public void testRenameVarOuterConstToLocal() throws Exception {
    doTest("localVar3");
  }

  public void testRenameVarOuterConstToParam() throws Exception {
    doTest("param2");
  }

  public void testRenameVarOuterFieldToLocal() throws Exception {
    doTest("localVar3");
  }

  public void testRenameVarOuterFieldToParam() throws Exception {
    doTest("param3");
  }

  public void testRenameVarParamToAlien() throws Exception {
    doTest("BOTTOM");
  }

  public void testRenameVarParamToField() throws Exception {
    doTest("myInnerField");
  }

  public void testRenameVarParamToOuterConst() throws Exception {
    doTest("STATIC_FIELD");
  }

  public void testRenameLocalVariableHidesFieldInAnonymous() throws Exception {
    try {
      doTest("y");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      Assert.assertEquals("There is already a field <b><code>y</code></b>. It will conflict with the renamed variable", e.getMessage());
      return;
    }
    fail("Conflicts were not found");
  }

  public void testRenameTypeParameterToExistingClassName() throws Exception {
    doTest("P");
  }

  public void testRenameInnerInSuperClass() throws Exception {
    doTest("C");
  }

  public void testRenameInnerInSuperClassStatic() throws Exception {
    doTest("C");
  }

  private void doTest(final String newName) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement element = TargetElementUtilBase
        .findTargetElement(myEditor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    new RenameProcessor(getProject(), element, newName, true, true).run();
    checkResultByFile(BASE_PATH + getTestName(false) + ".java.after");
  }

  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }

  public void testAllUsagesInCode() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement element = TargetElementUtilBase
        .findTargetElement(myEditor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    final UsageInfo[] usageInfos = RenameUtil.findUsages(element, "newName", true, true, new HashMap<PsiElement, String>());
    assertSize(1, usageInfos);
    for (UsageInfo usageInfo : usageInfos) {
      assertTrue(usageInfo instanceof MoveRenameUsageInfo);
      assertFalse(usageInfo.isNonCodeUsage);
    }
  }
}
