/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
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

  public void testRenameMethodCollisionWithOtherSignature() throws Exception {
    try {
      doTest("foo2");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      Assert.assertEquals("Method call would be linked to \"method <b><code>RenameTest.foo2(Long)</code></b>\" after rename", e.getMessage());
      return;
    }
    fail("Conflicts were not found");
  }

  public void testRenameMethodCollisionSameSignature() throws Exception {
    try {
      doTest("foo1");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      Assert.assertEquals("Method with same erasure is already defined in the class <b><code>RenameTest</code></b>", e.getMessage());
      return;
    }
    fail("Conflicts were not found");
  }

  public void testFieldHidesLocal() throws Exception {
    try {
      doTest("b");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      Assert.assertEquals("Renamed field will hide local variable <b><code>b</code></b>", e.getMessage());
      return;
    }
    fail("Conflicts were not found");
  }

  public void testRenameMethodNoCollisionWithOtherSignature() throws Exception {
    doTest("foo2");
  }

  public void testRenameMethodNoCollisionWithOtherSignatureMethodRef() throws Exception {
    doTest("foo2");
  }

  public void testRenameNoStaticOverridingInInterfaces() throws Exception {
    doTest("foo");
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

  public void testRenameStaticMethodTypeParameter() throws Exception {
    doTest("E");
  }
  
  public void testRenameFieldInSuper() throws Exception {
    doTest("gg");
  }

  public void testRenameTypeParamToSuper() throws Exception {
    doTest("T");
  }

  private void doTestImpossibleToRename() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement element = TargetElementUtil
      .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    assertTrue(PsiElementRenameHandler.isVetoed(element));
  }

  public void testNotAvailableForValueOf() throws Exception {
    doTestImpossibleToRename();
  }

  public void testNotAvailableForValues() throws Exception {
    doTestImpossibleToRename();
  }

  public void testNotAvailableForArrayLength() throws Exception {
    try {
      doTest("val");
      fail("Should be impossible to rename");
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Cannot perform refactoring.\n" +
                   "This element cannot be renamed", e.getMessage());
    }
  }

  private void doTest(final String newName) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement element = TargetElementUtil
        .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    new RenameProcessor(getProject(), element, newName, true, true).run();
    checkResultByFile(BASE_PATH + getTestName(false) + ".java.after");
  }

  public void testAllUsagesInCode() throws Exception {
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
