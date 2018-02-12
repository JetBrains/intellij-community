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
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.HashMap;

/**
 * @author sashache
 */
public class RenameCollisionsTest extends LightRefactoringTestCase {
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

  //Fails due to IDEADEV-25194.
  //public void testRenameClassLocalToAlienNoImports() throws Exception {
  //  doTest("String");
  //}

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

  public void testRenameLocalVariableHidesFieldInAnonymous() {
    try {
      doTest("y");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      Assert.assertEquals("There is already a field <b><code>y</code></b>. It will conflict with the renamed variable", e.getMessage());
      return;
    }
    fail("Conflicts were not found");
  }

  public void testRenameMethodCollisionWithOtherSignature() {
    try {
      doTest("foo2");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      Assert.assertEquals("Method call would be linked to \"method <b><code>RenameTest.foo2(Long)</code></b>\" after rename", e.getMessage());
      return;
    }
    fail("Conflicts were not found");
  }

  public void testRenameMethodCollisionSameSignature() {
    try {
      doTest("foo1");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      Assert.assertEquals("Method with same erasure is already defined in the class <b><code>RenameTest</code></b>", e.getMessage());
      return;
    }
    fail("Conflicts were not found");
  }

  public void testFieldHidesLocal() {
    try {
      doTest("b");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      Assert.assertEquals("Renamed field will hide local variable <b><code>b</code></b>", e.getMessage());
      return;
    }
    fail("Conflicts were not found");
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

  public void testInnerClassNameCollisionWithSuperClassOfContainer() {
    doTest("handleAction");
  }

  private void doTestImpossibleToRename() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement element = TargetElementUtil
      .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    assertTrue(PsiElementRenameHandler.isVetoed(element));
  }

  public void testNotAvailableForValueOf() {
    doTestImpossibleToRename();
  }

  public void testNotAvailableForValues() {
    doTestImpossibleToRename();
  }

  public void testNotAvailableForArrayLength() {
    try {
      doTest("val");
      fail("Should be impossible to rename");
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Cannot perform refactoring.\n" +
                   "This element cannot be renamed", e.getMessage());
    }
  }

  private void doTest(final String newName) {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement element = TargetElementUtil
        .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    new RenameProcessor(getProject(), element, newName, true, true).run();
    checkResultByFile(BASE_PATH + getTestName(false) + ".java.after");
  }

  public void testAllUsagesInCode() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement element = TargetElementUtil
        .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    final UsageInfo[] usageInfos = RenameUtil.findUsages(element, "newName", true, true, new HashMap<>());
    assertSize(1, usageInfos);
    for (UsageInfo usageInfo : usageInfos) {
      assertTrue(usageInfo instanceof MoveRenameUsageInfo);
      assertFalse(usageInfo.isNonCodeUsage);
    }
  }
}
