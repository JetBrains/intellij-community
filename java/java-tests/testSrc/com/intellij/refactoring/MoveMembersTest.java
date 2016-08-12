/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.move.moveMembers.MockMoveMembersOptions;
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public class MoveMembersTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testJavadocRefs() throws Exception {
    doTest("Class1", "Class2", 0);
  }

  public void testWeirdDeclaration() throws Exception {
    doTest("A", "B", 0);
  }

  public void testInnerClass() throws Exception {
    doTest("A", "B", 0);
  }

  public void testScr11871() throws Exception {
    doTest("pack1.A", "pack1.B", 0);
  }

  public void testOuterClassTypeParameters() throws Exception {
    doTest("pack1.A", "pack2.B", 0);
  }

  public void testscr40064() throws Exception {
    doTest("Test", "Test1", 0);
  }

  public void testscr40947() throws Exception {
    doTest("A", "Test", 0, 1);
  }

  public void testIDEADEV11416() throws Exception {
    doTest("Y", "X", false, 0);
  }

  public void testDependantConstants() throws Exception {
    doTest("A", "B", 0, 1);
  }

  public void testTwoMethods() throws Exception {
    doTest("pack1.A", "pack1.C", 0, 1, 2);
  }

  public void testParameterizedRefOn() throws Exception {
    doTest("pack1.POne", "pack1.C", 1, 2);
  }

  public void testIDEADEV12448() throws Exception {
    doTest("B", "A", false, 0);
  }

  public void testFieldForwardRef() throws Exception {
    doTest("A", "Constants", 0);
  }

  public void testStaticImport() throws Exception {
    doTest("C", "B", 0);
  }

  public void testExplicitStaticImport() throws Exception {
    doTest("C", "B", 0);
  }

  public void testProtectedConstructor() throws Exception {
    doTest("pack1.A", "pack1.C", 0);
  }

  public void testUntouchedVisibility() throws Exception {
    doTest("pack1.A", "pack1.C", 0, 1);
  }

  public void testEscalateVisibility() throws Exception {
    doTest("pack1.A", "pack1.C", true, VisibilityUtil.ESCALATE_VISIBILITY, 0);
  }

  public void testOtherPackageImport() throws Exception {
    doTest("pack1.ClassWithStaticMethod", "pack2.OtherClass", 1);
  }

  public void testEnumConstant() throws Exception {
    doTest("B", "A", 0);
  }

  public void testEnumConstantFromCaseStatement() throws Exception {
    doTest("B", "A", 0);
  }

  public void testStringConstantFromCaseStatement() throws Exception {
    doTest("B", "A", 0);
  }

  public void testDependantFields() throws Exception {
    doTest("B", "A", 0);
  }

  public void testStaticImportAndOverridenMethods() throws Exception {
    doTest("bar.B", "bar.A", 0);
  }

  public void testWritableField() throws Exception {
    try {
      doTest("B", "A", 0);
      fail("conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Field <b><code>B.ONE</code></b> has write access but is moved to an interface", e.getMessage());
    }
  }
  
  public void testFinalFieldWithInitializer() throws Exception {
    try {
      doTest("B", "A", 0);
      fail("conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("final variable initializer won't be available after move.", e.getMessage());
    }
  }

  public void testExistingFieldInSuper() throws Exception {
    try {
      doTest("B", "A", 0, 1);
      fail("conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Field <b><code>truth</code></b> already exists in the target class.\n" +
                   "Method <b><code>important()</code></b> already exists in the target class.", e.getMessage());
    }
  }

  public void testInnerToInterface() throws Exception {
    doTest("A", "B", 0);
  }

  public void testStaticToInterface() throws Exception {
    final LanguageLevelProjectExtension levelProjectExtension = LanguageLevelProjectExtension.getInstance(getProject());
    final LanguageLevel level = levelProjectExtension.getLanguageLevel();
    try {
      levelProjectExtension.setLanguageLevel(LanguageLevel.JDK_1_8);
      doTest("A", "B", 0);
    }
    finally {
      levelProjectExtension.setLanguageLevel(level);
    }
  }
  
  public void testEscalateVisibility1() throws Exception {
    doTest("A", "B", true, VisibilityUtil.ESCALATE_VISIBILITY, 0);
  }

  public void testStringConstantInSwitchLabelExpression() throws Exception {
    doTest("A", "B", true, VisibilityUtil.ESCALATE_VISIBILITY, 0);
  }

  public void testMultipleWithDependencies() throws Exception {
    doTest("A", "B", true, VisibilityUtil.ESCALATE_VISIBILITY, 0, 1);
  }

  public void testMultipleWithDependencies1() throws Exception {
    doTest("A", "B", true, VisibilityUtil.ESCALATE_VISIBILITY, 0, 1);
  }

  public void testFromNestedToOuter() throws Exception {
    doTest("Outer.Inner", "Outer", true, VisibilityUtil.ESCALATE_VISIBILITY, 0);
  }

  public void testMixedStaticImportAndQualified() throws Exception {
    doTest("ImportingClass.Constants", "ImportingClass.ImportantConstants", 0);
  }

  public void testStaticProblemsShouldNotRaiseAConflict() throws Exception {
    doTest("A", "B", 0);
  }

  public void testFromNestedToOuterMethodRef() throws Exception {
    final LanguageLevelProjectExtension projectExtension = LanguageLevelProjectExtension.getInstance(getProject());
    final LanguageLevel oldLevel = projectExtension.getLanguageLevel();
    try {
      projectExtension.setLanguageLevel(LanguageLevel.HIGHEST);
      doTest("Outer.Inner", "Outer", true, VisibilityUtil.ESCALATE_VISIBILITY, 0);
    }
    finally {
      projectExtension.setLanguageLevel(oldLevel);
    }
  }

  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/moveMembers/";
  }

  private void doTest(final String sourceClassName, final String targetClassName, final int... memberIndices) throws Exception {
    doTest(sourceClassName, targetClassName, true, memberIndices);
  }

  private void doTest(final String sourceClassName,
                      final String targetClassName,
                      final boolean lowercaseFirstLetter,
                      final int... memberIndices) throws Exception {
    doTest(sourceClassName, targetClassName, lowercaseFirstLetter, null, memberIndices);
  }

  private void doTest(final String sourceClassName,
                      final String targetClassName,
                      final boolean lowercaseFirstLetter,
                      final String defaultVisibility,
                      final int... memberIndices)
    throws Exception {
    doTest((rootDir, rootAfter) -> this.performAction(sourceClassName, targetClassName, memberIndices, defaultVisibility), lowercaseFirstLetter);
  }

  private void performAction(String sourceClassName, String targetClassName, int[] memberIndices, final String visibility) throws Exception {
    PsiClass sourceClass = myJavaFacade.findClass(sourceClassName, ProjectScope.getProjectScope(myProject));
    assertNotNull("Class " + sourceClassName + " not found", sourceClass);
    PsiClass targetClass = myJavaFacade.findClass(targetClassName, ProjectScope.getProjectScope(myProject));
    assertNotNull("Class " + targetClassName + " not found", targetClass);

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
    new MoveMembersProcessor(myProject, null, options).run();
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
