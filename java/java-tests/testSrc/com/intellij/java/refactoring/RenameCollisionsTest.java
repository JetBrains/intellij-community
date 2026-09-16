// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

/**
 * @author sashache
 */
public class RenameCollisionsTest extends LightJavaCodeInsightTestCase {
  private static final String BASE_PATH = "/refactoring/renameCollisions/";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testRenameClassInnerToLocal() {
    doTest("LocalClass");
  }

  public void testRenameClassLocalToAlien() {
    doTest("String");
  }

  public void testRenameClassLocalToAlienNoImports() {
    doTest("String");
  }

  public void testRenameClassLocalToInner() {
    doTest("StaticInnerClass");
  }

  public void testRenameClassThisFqnToAlien() {
    doTest("String");
  }

  public void testRenameClassThisToAlien() {
    doTest("String");
  }

  public void testRenameMethodIndiInstancesInnerToOuter() {
    doTest("method");
  }

  public void testRenameMethodIndiInstancesOuterToInner() {
    doTest("siMethod");
  }

  public void testRenameMethodInnerInstanceToOuterInstance() {
    doTest("method");
  }

  public void testRenameMethodInnerStaticToOuterStatic() {
    doTest("staticMethod");
  }

  public void testRenameMethodOuterInstanceToInnerInstance() {
    doTest("innerMethod");
  }

  public void testRenameMethodOuterStaticToInnerStatic() {
    doTest("siStaticMethod");
  }

  public void testRenameMethodInnerStaticToOuterStaticMoreParameters() {
    doTest("staticMethod");
  }

  public void testRenameMethodStaticToAlien() {
    doTest("valueOf");
  }

  public void testRenameVarConstToAlien() {
    doTest("CASE_INSENSITIVE_ORDER");
  }

  public void testRenameVarConstToAlien1() {
    doTest("CASE_INSENSITIVE_ORDER");
  }

  public void testRenameVarConstToParam() {
    doTest("param3");
  }

  public void testRenameVarFieldToLocal() {
    doTest("localVar3");
  }

  public void testRenameVarInnerConstToOuterConst() {
    doTest("STATIC_FIELD");
  }

  public void testRenameVarInnerFieldToOuterField() {
    doTest("myField");
  }

  public void testRenameVarLocalToAlien() {
    doTest("separatorChar");
  }

  public void testRenameVarLocalToConst() {
    doTest("INNER_STATIC_FIELD");
  }

  public void testRenameVarLocalToOuterField() {
    doTest("myField");
  }

  public void testRenameVarOuterConstToInnerConst() {
    doTest("SI_STATIC_FIELD");
  }

  public void testRenameVarOuterConstToLocal() {
    doTest("localVar3");
  }

  public void testRenameVarOuterConstToParam() {
    doTest("param2");
  }

  public void testRenameVarOuterFieldToLocal() {
    doTest("localVar3");
  }

  public void testRenameVarOuterFieldToParam() {
    doTest("param3");
  }

  public void testRenameVarParamToAlien() {
    doTest("separatorChar");
  }

  public void testRenameVarParamToField() {
    doTest("myInnerField");
  }

  public void testRenameVarParamToOuterConst() {
    doTest("STATIC_FIELD");
  }
  public void testRenameConflictNoQualification() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest("f"));
  }

  public void testRenameLocalVariableHidesFieldInAnonymous() {
    doTestConflict("y", "An existing field <b><code>y</code></b> has the same name");
  }
  
  public void testLocalVariableCollisionWithLambdaParameter() {
    doTestConflict("x", "An existing parameter <b><code>x</code></b> has the same name");
  }

  public void testRenameMethodCollisionWithOtherSignature() {
    doTestConflict("foo2", "Different method <b><code>RenameTest.foo2(Long)</code></b> will be called after rename");
  }

  public void testRenameMethodCollisionSameSignature() {
    doTestConflict("foo1", "Method with the same erasure is already defined in class <b><code>RenameTest</code></b>");
  }

  public void testFieldHidesLocal() {
    doTestConflict("b", "Renamed field will hide local variable <b><code>b</code></b>");
  }

  public void testRenameMethodNoCollisionWithOtherSignature() {
    doTest("foo2");
  }

  public void testRenameMethodNoCollisionWithOtherSignatureMethodRef() {
    doTest("foo2");
  }

  public void testRenameNoStaticOverridingInInterfaces() {
    doTest("foo");
  }

  public void testRenameTypeParameterToExistingClassName() {
    doTest("P");
  }

  public void testRenameInnerInSuperClass() {
    doTest("C");
  }

  public void testRenameInnerInSuperClassStatic() {
    doTest("C");
  }

  public void testRenameStaticMethodTypeParameter() {
    doTest("E");
  }
  
  public void testRenameFieldInSuper() {
    doTest("gg");
  }

  public void testRenameTypeParamToSuper() {
    doTest("T");
  }
  public void testRenameSwitchToUnnamedJava21Preview() {
    doTest("_");
  }

  public void testInnerClassNameCollisionWithSuperClassOfContainer() {
    doTest("handleAction");
  }

  private void doTestImpossibleToRename() {
    assertTrue(PsiElementRenameHandler.isVetoed(getTarget()));
  }

  public void testNotAvailableForValueOf() {
    doTestImpossibleToRename();
  }

  public void testNotAvailableForValues() {
    doTestImpossibleToRename();
  }

  public void testNotAvailableForArrayLength() {
    doTestConflict("val", "Cannot perform refactoring.\nThis element cannot be renamed");
  }
  
  private void doTestConflict(String newName, String conflictText) {
    try {
      new RenameProcessor(getProject(), getTarget(), newName, true, true).run();
      fail("No conflicts found");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException | CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals(conflictText, e.getMessage());
    }
  }

  private void doTest(String newName) {
    new RenameProcessor(getProject(), getTarget(), newName, true, true).run();
    checkResultByFile(BASE_PATH + getTestName(false) + ".java.after");
  }

  private @NotNull PsiElement getTarget() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement element = TargetElementUtil
      .findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    return element;
  }

  public void testAllUsagesInCode() {
    final UsageInfo[] usageInfos = RenameUtil.findUsages(getTarget(), "newName", true, true, new HashMap<>());
    assertSize(1, usageInfos);
    for (UsageInfo usageInfo : usageInfos) {
      assertTrue(usageInfo instanceof MoveRenameUsageInfo);
      assertFalse(usageInfo.isNonCodeUsage);
    }
  }
}
