// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaDescriptorOfTestName;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author sashache
 */
@TestApplication
public class RenameCollisionsTest {
  private static final LightProjectDescriptor DESCRIPTOR = lightJavaTestDescriptor();
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, lightJavaDescriptorOfTestName(DESCRIPTOR), "/java/java-tests/testData");

  private static final String BASE_PATH = "/refactoring/renameCollisions/";

  @Test
  public void testRenameClassInnerToLocal() {
    doTest("LocalClass");
  }

  @Test
  public void testRenameClassLocalToAlien() {
    doTest("String");
  }

  @Test
  public void testRenameClassLocalToAlienNoImports() {
    doTest("String");
  }

  @Test
  public void testRenameClassLocalToInner() {
    doTest("StaticInnerClass");
  }

  @Test
  public void testRenameClassThisFqnToAlien() {
    doTest("String");
  }

  @Test
  public void testRenameClassThisToAlien() {
    doTest("String");
  }

  @Test
  public void testRenameMethodIndiInstancesInnerToOuter() {
    doTest("method");
  }

  @Test
  public void testRenameMethodIndiInstancesOuterToInner() {
    doTest("siMethod");
  }

  @Test
  public void testRenameMethodInnerInstanceToOuterInstance() {
    doTest("method");
  }

  @Test
  public void testRenameMethodInnerStaticToOuterStatic() {
    doTest("staticMethod");
  }

  @Test
  public void testRenameMethodOuterInstanceToInnerInstance() {
    doTest("innerMethod");
  }

  @Test
  public void testRenameMethodOuterStaticToInnerStatic() {
    doTest("siStaticMethod");
  }

  @Test
  public void testRenameMethodInnerStaticToOuterStaticMoreParameters() {
    doTest("staticMethod");
  }

  @Test
  public void testRenameMethodStaticToAlien() {
    doTest("valueOf");
  }

  @Test
  public void testRenameVarConstToAlien() {
    doTest("CASE_INSENSITIVE_ORDER");
  }

  @Test
  public void testRenameVarConstToAlien1() {
    doTest("CASE_INSENSITIVE_ORDER");
  }

  @Test
  public void testRenameVarConstToParam() {
    doTest("param3");
  }

  @Test
  public void testRenameVarFieldToLocal() {
    doTest("localVar3");
  }

  @Test
  public void testRenameVarInnerConstToOuterConst() {
    doTest("STATIC_FIELD");
  }

  @Test
  public void testRenameVarInnerFieldToOuterField() {
    doTest("myField");
  }

  @Test
  public void testRenameVarLocalToAlien() {
    doTest("separatorChar");
  }

  @Test
  public void testRenameVarLocalToConst() {
    doTest("INNER_STATIC_FIELD");
  }

  @Test
  public void testRenameVarLocalToOuterField() {
    doTest("myField");
  }

  @Test
  public void testRenameVarOuterConstToInnerConst() {
    doTest("SI_STATIC_FIELD");
  }

  @Test
  public void testRenameVarOuterConstToLocal() {
    doTest("localVar3");
  }

  @Test
  public void testRenameVarOuterConstToParam() {
    doTest("param2");
  }

  @Test
  public void testRenameVarOuterFieldToLocal() {
    doTest("localVar3");
  }

  @Test
  public void testRenameVarOuterFieldToParam() {
    doTest("param3");
  }

  @Test
  public void testRenameVarParamToAlien() {
    doTest("separatorChar");
  }

  @Test
  public void testRenameVarParamToField() {
    doTest("myInnerField");
  }

  @Test
  public void testRenameVarParamToOuterConst() {
    doTest("STATIC_FIELD");
  }
  @Test
  public void testRenameConflictNoQualification() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest("f"));
  }

  @Test
  public void testRenameLocalVariableHidesFieldInAnonymous() {
    doTestConflict("y", "An existing field <b><code>y</code></b> has the same name");
  }
  
  @Test
  public void testLocalVariableCollisionWithLambdaParameter() {
    doTestConflict("x", "An existing parameter <b><code>x</code></b> has the same name");
  }

  @Test
  public void testRenameMethodCollisionWithOtherSignature() {
    doTestConflict("foo2", "Different method <b><code>RenameTest.foo2(Long)</code></b> will be called after rename");
  }

  @Test
  public void testRenameMethodCollisionSameSignature() {
    doTestConflict("foo1", "Method with the same erasure is already defined in class <b><code>RenameTest</code></b>");
  }

  @Test
  public void testFieldHidesLocal() {
    doTestConflict("b", "Renamed field will hide local variable <b><code>b</code></b>");
  }

  @Test
  public void testRenameMethodNoCollisionWithOtherSignature() {
    doTest("foo2");
  }

  @Test
  public void testRenameMethodNoCollisionWithOtherSignatureMethodRef() {
    doTest("foo2");
  }

  @Test
  public void testRenameNoStaticOverridingInInterfaces() {
    doTest("foo");
  }

  @Test
  public void testRenameTypeParameterToExistingClassName() {
    doTest("P");
  }

  @Test
  public void testRenameInnerInSuperClass() {
    doTest("C");
  }

  @Test
  public void testRenameInnerInSuperClassStatic() {
    doTest("C");
  }

  @Test
  public void testRenameStaticMethodTypeParameter() {
    doTest("E");
  }
  
  @Test
  public void testRenameFieldInSuper() {
    doTest("gg");
  }

  @Test
  public void testRenameTypeParamToSuper() {
    doTest("T");
  }
  @Test
  public void testRenameSwitchToUnnamedJava21Preview() {
    doTest("_");
  }

  @Test
  public void testInnerClassNameCollisionWithSuperClassOfContainer() {
    doTest("handleAction");
  }

  private void doTestImpossibleToRename() {
    PsiElement target = getTarget();
    assertTrue(ReadAction.computeBlocking(() -> PsiElementRenameHandler.isVetoed(target)));
  }

  @Test
  public void testNotAvailableForValueOf() {
    doTestImpossibleToRename();
  }

  @Test
  public void testNotAvailableForValues() {
    doTestImpossibleToRename();
  }

  @Test
  public void testNotAvailableForArrayLength() {
    doTestConflict("val", "Cannot perform refactoring.\nThis element cannot be renamed");
  }
  
  private void doTestConflict(String newName, String conflictText) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiElement target = getTarget();
    try {
      myFixture.runInCommand(() -> rename(target, newName));
      fail("No conflicts found");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException | CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals(conflictText, e.getMessage());
    }
  }

  private void doTest(String newName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiElement target = getTarget();
    myFixture.runInCommand(() -> {
      rename(target, newName);
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + ".java.after");
    });
  }

  /** Renames {@code target}, searching in comments and in text occurrences; a subclass renames with another engine. */
  protected void rename(@NotNull PsiElement target, @NotNull String newName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    new RenameProcessor(myFixture.getProject(), target, newName, true, true).run();
  }

  private @NotNull PsiElement getTarget() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    PsiElement element = ReadAction.computeBlocking(() -> TargetElementUtil
      .findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED));
    assertNotNull(element);
    return element;
  }

  @Test
  public void testAllUsagesInCode() {
    PsiElement target = getTarget();
    final UsageInfo[] usageInfos = ReadAction.computeBlocking(() -> RenameUtil.findUsages(target, "newName", true, true, new HashMap<>()));
    assertEquals(1, usageInfos.length);
    for (UsageInfo usageInfo : usageInfos) {
      assertInstanceOf(MoveRenameUsageInfo.class, usageInfo);
      assertFalse(usageInfo.isNonCodeUsage);
    }
  }
}
