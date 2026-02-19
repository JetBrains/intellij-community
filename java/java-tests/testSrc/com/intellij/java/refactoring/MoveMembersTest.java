// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.move.moveMembers.MockMoveMembersOptions;
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.VisibilityUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public class MoveMembersTest extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/moveMembers/";
  }

  public void testJavadocRefs() {
    doTest("Class1", "Class2", 0);
  }

  public void testWeirdDeclaration() {
    doTest("A", "B", 0);
  }

  public void testInnerClass() {
    doTest("A", "B", 0);
  }

  public void testScr11871() {
    doTest("pack1.A", "pack1.B", 0);
  }

  public void testOuterClassTypeParameters() {
    doTest("pack1.A", "pack2.B", 0);
  }

  public void testscr40064() {
    doTest("Test", "Test1", 0);
  }

  public void testscr40947() {
    doTest("A", "Test", 0, 1);
  }

  public void testIDEADEV11416() {
    doTest("Y", "X", false, 0);
  }

  public void testDependantConstants() {
    doTest("A", "B", 0, 1);
  }

  public void testTwoMethods() {
    doTest("pack1.A", "pack1.C", 0, 1, 2);
  }

  public void testParameterizedRefOn() {
    doTest("pack1.POne", "pack1.C", 1, 2);
  }

  public void testIDEADEV12448() {
    doTest("B", "A", false, 0);
  }
  
  public void testClearFinalStatic() {
    doTest("B", "A", 0, 1);
  }

  public void testFieldForwardRef() {
    doTest("A", "Constants", 0);
  }

  public void testStaticImport() {
    doTest("C", "B", 0);
  }

  public void testExplicitStaticImport() {
    doTest("C", "B", 0);
  }

  public void testProtectedConstructor() {
    doTest("pack1.A", "pack1.C", 0);
  }

  public void testPackagePrivateStaticMember() {
    doTest("pack1.A", "pack2.C", true, PsiModifier.PACKAGE_LOCAL, 0);
  }

  public void testUntouchedVisibility() {
    doTest("pack1.A", "pack1.C", 0, 1);
  }

  public void testEscalateVisibility() {
    doTest("pack1.A", "pack1.C", true, VisibilityUtil.ESCALATE_VISIBILITY, 0);
  }

  public void testOtherPackageImport() {
    doTest("pack1.ClassWithStaticMethod", "pack2.OtherClass", 1);
  }

  public void testEnumConstant() {
    doTest("B", "A", 0);
  }

  public void testEnumConstantFromCaseStatement() {
    try {
      doTest("B", "A", 0);
      fail("Conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Enum type won't be applicable in the current context", e.getMessage());
    }
  }

  public void testStringConstantFromCaseStatement() {
    doTest("B", "A", 0);
  }

  public void testDependantFields() {
    doTest("B", "A", 0);
  }

  public void testStaticImportAndOverridenMethods() {
    doTest("bar.B", "bar.A", 0);
  }

  public void testStaticClassInitializer() {
    doTest("B", "A", 0);
  }

  public void testStaticClassInitializerToInterface() {
    try {
      doTest("B", "A", 0);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Static class initializers are not allowed in interfaces.", e.getMessage());
    }
  }

  public void testWritableField() {
    try {
      doTest("B", "A", 0);
      fail("conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Field <b><code>B.ONE</code></b> is written to, but an interface is only allowed to contain constants.", e.getMessage());
    }
  }
  
  public void testFinalFieldWithInitializer() {
    try {
      doTest("B", "A", 0);
      fail("conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("The initializer of final field <b><code>B.ONE</code></b> will be left behind.", e.getMessage());
    }
  }

  public void testEnumConstantToInterface() {
    try {
      doTest("B", "A", 0);
      fail("conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Enum constant <b><code>B.A</code></b> won't be compilable when moved to class <b><code>A</code></b>.", e.getMessage());
    }
  }

  public void testNonConstantToInterface() {
    try {
      doTest("B", "A", 0);
      fail("conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Non-constant field <b><code>B.i</code></b> will not be compilable when moved to an interface.", e.getMessage());
    }
  }

  public void testExistingFieldInSuper() {
    try {
      doTest("B", "A", 0, 1);
      fail("conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Field <b><code>truth</code></b> already exists in the target class.\n" +
                   "Method <b><code>important()</code></b> already exists in the target class.", e.getMessage());
    }
  }

  public void testInnerToInterface() {
    doTest("A", "B", 0);
  }

  public void testStaticToInterface() {
    final LanguageLevel level = IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_8);
    try {
      doTest("A", "B", 0);
    }
    finally {
      IdeaTestUtil.setProjectLanguageLevel(getProject(), level);
    }
  }
  
  public void testEscalateVisibility1() {
    doTest("A", "B", true, VisibilityUtil.ESCALATE_VISIBILITY, 0);
  }

  public void testEscalateVisibilityWhenMoveStaticMemberToStaticClass() {
    doTest("pack.A", "pack.A.B", true, VisibilityUtil.ESCALATE_VISIBILITY, 1, 2);
  }

  public void testStringConstantInSwitchLabelExpression() {
    doTest("A", "B", true, VisibilityUtil.ESCALATE_VISIBILITY, 0);
  }

  public void testMultipleWithDependencies() {
    doTest("A", "B", true, VisibilityUtil.ESCALATE_VISIBILITY, 0, 1);
  }

  public void testMultipleWithDependencies1() {
    doTest("A", "B", true, VisibilityUtil.ESCALATE_VISIBILITY, 0, 1);
  }

  public void testFromNestedToOuter() {
    doTest("Outer.Inner", "Outer", true, VisibilityUtil.ESCALATE_VISIBILITY, 0);
  }

  public void testMixedStaticImportAndQualified() {
    doTest("ImportingClass.Constants", "ImportingClass.ImportantConstants", 0);
  }

  public void testStaticProblemsShouldNotRaiseAConflict() {
    doTest("A", "B", 0);
  }

  public void testClearQualifierInsideInnerAnnotation() {
    doTest("A", "B", 0);
  }

  public void testFromNestedToOuterMethodRef() {
    final LanguageLevel oldLevel = IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.HIGHEST);
    try {
      doTest("Outer.Inner", "Outer", true, VisibilityUtil.ESCALATE_VISIBILITY, 0);
    }
    finally {
      IdeaTestUtil.setProjectLanguageLevel(getProject(), oldLevel);
    }
  }

  private void doTest(final String sourceClassName, final String targetClassName, final int... memberIndices) {
    doTest(sourceClassName, targetClassName, true, memberIndices);
  }

  private void doTest(final String sourceClassName,
                      final String targetClassName,
                      final boolean lowercaseFirstLetter,
                      final int... memberIndices) {
    doTest(sourceClassName, targetClassName, lowercaseFirstLetter, null, memberIndices);
  }

  private void doTest(final String sourceClassName,
                      final String targetClassName,
                      final boolean lowercaseFirstLetter,
                      final String defaultVisibility,
                      final int... memberIndices) {
    doTest(() -> this.performAction(sourceClassName, targetClassName, memberIndices, defaultVisibility), lowercaseFirstLetter);
  }

  private void performAction(String sourceClassName, String targetClassName, int[] memberIndices, final String visibility) {
    PsiClass sourceClass = myFixture.findClass(sourceClassName);
    PsiClass targetClass = myFixture.findClass(targetClassName);
    PsiElement[] children = sourceClass.getChildren();
    ArrayList<PsiMember> members = new ArrayList<>();
    for (PsiElement child : children) {
      if (child instanceof PsiMember) {
        members.add(((PsiMember) child));
      }
    }

    LinkedHashSet<PsiMember> memberSet = new LinkedHashSet<>();
    for (int index : memberIndices) {
      PsiMember member = members.get(index);
      assertTrue(member.hasModifierProperty(PsiModifier.STATIC));
      memberSet.add(member);
    }

    MockMoveMembersOptions options = new MockMoveMembersOptions(targetClass.getQualifiedName(), memberSet);
    options.setMemberVisibility(visibility);
    new MoveMembersProcessor(getProject(), null, options).run();
  }

}
