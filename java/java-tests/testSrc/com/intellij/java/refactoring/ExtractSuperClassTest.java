// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.extractSuperclass.ExtractSuperBaseProcessor;
import com.intellij.refactoring.extractSuperclass.ExtractSuperClassProcessor;
import com.intellij.refactoring.extractSuperclass.ExtractSuperClassUtil;
import com.intellij.refactoring.memberPullUp.PullUpConflictsUtil;
import com.intellij.refactoring.memberPullUp.PullUpProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.function.Function;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class ExtractSuperClassTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_5);
  protected final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(getProjectFixture(), JavaTestUtil.getJavaTestDataPath() + "/refactoring/extractSuperClass/");

  /**
   * Returns the project of the tests. A subclass runs the tests of this class again in its own project.
   * The field initializer of {@link #multiFile} calls it, so it returns a static field.
   */
  protected TestFixture<LightJavaProject> getProjectFixture() {
    return projectFixture;
  }

  @Test
  public void testFinalFieldInitialization() {   // IDEADEV-19704
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("X", PsiClass.class),
           new RefactoringTestUtil.MemberDescriptor("x", PsiField.class));
  }

  @Test
  public void testFieldInitializationWithCast() {
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("x", PsiField.class));
  }

   @Test
   public void testMethodTypeParameter() {
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("m", PsiMethod.class));
  }

  @Test
  public void testMultipleTypeParameters() {
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("m", PsiMethod.class));
  }

  @Test
  public void testEmptyForeach() {
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("m", PsiMethod.class));
  }

  @Test
  public void testConflictUsingPrivateMethod() {
    doTest("Test", "TestSubclass",
           new String[] {"Method <b><code>Test.foo()</code></b> is private and will not be accessible from method <b><code>x()</code></b>."},
           new RefactoringTestUtil.MemberDescriptor("x", PsiMethod.class));
  }

  @Test
  public void testConflictMoveAbstractWithPrivateMethod() {
    doTest("Test", "TestSubclass",
           new String[] {"Method <b><code>x()</code></b> uses method <b><code>Test.xx()</code></b> which won't be accessible from the subclass."},
           new RefactoringTestUtil.MemberDescriptor("x", PsiMethod.class, true),
           new RefactoringTestUtil.MemberDescriptor("xx", PsiMethod.class));
  }

  @Test
  public void testConflictAbstractPackageLocalMethod() {
    doTest("a.Test", "TestSubclass",
           new String[] {"Can't make method <b><code>x()</code></b> abstract as it won't be accessible from the subclass."},
           "b",
           new RefactoringTestUtil.MemberDescriptor("x", PsiMethod.class, true));
  }

  @Test
  public void testConflictUsingPackageLocalMethod() {
    doTest("a.Test", "TestSubclass",
           new String[] {"method <b><code>Sup.foo()</code></b> won't be accessible"},
           "b",
           new RefactoringTestUtil.MemberDescriptor("x", PsiMethod.class));
  }

  @Test
  public void testConflictUsingPackageLocalSuperClass() {
    doTest("a.Test", "TestSubclass",
           new String[] {"class <b><code>a.Sup</code></b> won't be accessible from package <b><code>b</code></b>"},
           "b",
           new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  @Test
  public void testNoConflictUsingProtectedMethodFromSuper() {
    doTest("Test", "TestSubclass",
           new RefactoringTestUtil.MemberDescriptor("x", PsiMethod.class));
  }

  @Test
  public void testParameterNameEqualsFieldName() {    // IDEADEV-10629
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("a", PsiField.class), new RefactoringTestUtil.MemberDescriptor("b", PsiField.class));
  }

  @Test
  public void testDependentFields() {
    doTest("Test", "TestSubclass",
           new RefactoringTestUtil.MemberDescriptor("b", PsiField.class),
           new RefactoringTestUtil.MemberDescriptor("a", PsiField.class),
           new RefactoringTestUtil.MemberDescriptor("c", PsiField.class)
           );
  }
  
  @Test
  public void testAccessibleClasses() {
    doTest("a.Test", "TestSubclass", null, "a",
           new RefactoringTestUtil.MemberDescriptor("a", PsiField.class));
  }

  @Test
  public void testAbstractInSuper() {
    doTest("Test", "TestSubclass",
           new RefactoringTestUtil.MemberDescriptor("y", PsiMethod.class, true));
  }

  @Test
  public void testSameTypeParameterName() {
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("A", PsiClass.class), new RefactoringTestUtil.MemberDescriptor("B", PsiClass.class));
  }

  @Test
  public void testExtendsLibraryClass() {
    doTest("Test", "TestSubclass");
  }

  @Test
  public void testRequiredImportRemoved() {
    doTest("foo.impl.B", "BImpl", new RefactoringTestUtil.MemberDescriptor("getInstance", PsiMethod.class));
  }

  @Test
  public void testSubstituteGenerics() {
    doTest("B", "AB");
  }

  @Test
  public void testExtendsList() {
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("List", PsiClass.class));
  }

  @Test
  public void testImportsCorruption() {
    doTest("p1.A", "AA", new RefactoringTestUtil.MemberDescriptor("m1", PsiMethod.class));
  }

  @Test
  public void testAnonymClass() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      PsiClass psiClass = myFixture.findClass("Test");
      assertNotNull(psiClass);
      final PsiField[] fields = psiClass.getFields();
      assertEquals(1, fields.length);
      final PsiExpression initializer = fields[0].getInitializer();
      assertNotNull(initializer);
      assertInstanceOf(PsiNewExpression.class, initializer);
      final PsiAnonymousClass anonymousClass = ((PsiNewExpression)initializer).getAnonymousClass();
      assertNotNull(anonymousClass);
      final ArrayList<MemberInfo> infos = new ArrayList<>();
      MemberInfo.extractClassMembers(anonymousClass, infos, member -> true, false);
      for (MemberInfo info : infos) {
        info.setChecked(true);
      }
      WriteCommandAction.writeCommandAction(myFixture.getProject()).run(() -> ExtractSuperClassUtil
        .extractSuperClass(myFixture.getProject(), psiClass.getContainingFile().getContainingDirectory(), "TestSubclass", anonymousClass,
                           infos.toArray(new MemberInfo[0]), new DocCommentPolicy(DocCommentPolicy.ASIS)));
    });
  }

  private void doTest(@NonNls final String className, @NonNls final String newClassName,
                      RefactoringTestUtil.MemberDescriptor... membersToFind) {
    doTest(className, newClassName, null, membersToFind);
  }

  private void doTest(@NonNls final String className, @NonNls final String newClassName,
                      String[] conflicts,
                      RefactoringTestUtil.MemberDescriptor... membersToFind) {
    doTest(className, newClassName, conflicts, null, membersToFind);
  }

  private void doTest(@NonNls final String className,
                      @NonNls final String newClassName,
                      String[] conflicts,
                      String targetPackageName,
                      RefactoringTestUtil.MemberDescriptor... membersToFind) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      PsiClass psiClass = myFixture.findClass(className);
      assertNotNull(psiClass);
      final MemberInfo[] members = RefactoringTestUtil.findMembers(psiClass, membersToFind);
      doTest(members, targetPackageName, psiClass, conflicts, 
             targetDirectory -> new ExtractSuperClassProcessor(myFixture.getProject(), targetDirectory, newClassName, psiClass, members, false, new DocCommentPolicy(DocCommentPolicy.ASIS)));
    });
  }

  protected void doTest(MemberInfo[] members,
                        String targetPackageName,
                        PsiClass psiClass,
                        String[] conflicts,
                        Function<PsiDirectory, ExtractSuperBaseProcessor> createProcessor) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    PsiDirectory targetDirectory;
    if (targetPackageName == null) {
      targetDirectory = psiClass.getContainingFile().getContainingDirectory();
    } else {
      final PsiPackage aPackage = myFixture.findPackage(targetPackageName);
      assertNotNull(aPackage);
      targetDirectory = aPackage.getDirectories()[0];
    }
    ExtractSuperBaseProcessor processor = createProcessor.apply(targetDirectory);
    final PsiPackage targetPackage;
    if (targetDirectory != null) {
      targetPackage = JavaDirectoryService.getInstance().getPackage(targetDirectory);
    }
    else {
      targetPackage = null;
    }
    final PsiClass superClass = psiClass.getExtendsListTypes().length > 0 ? psiClass.getSuperClass() : null;
    final MultiMap<PsiElement, String> conflictsMap =
      PullUpConflictsUtil.checkConflicts(members, psiClass, superClass, targetPackage, targetDirectory,
                                         psiMethod -> PullUpProcessor.checkedInterfacesContain(Arrays.asList(members), psiMethod), false);
    if (conflicts != null) {
      if (conflictsMap.isEmpty()) {
        fail("Conflicts were not detected");
      }
      final HashSet<String> expectedConflicts = new HashSet<>(Arrays.asList(conflicts));
      final HashSet<String> actualConflicts = new HashSet<>(conflictsMap.values());
      assertEquals(expectedConflicts.size(), actualConflicts.size(), actualConflicts.toString());
      for (String actualConflict : actualConflicts) {
        if (!expectedConflicts.contains(actualConflict)) {
          fail("Unexpected conflict: " + actualConflict);
        }
      }
    } else if (!conflictsMap.isEmpty()) {
      fail("Unexpected conflicts!!!" + conflictsMap.toString());
    }
    processor.run();
  }
}
