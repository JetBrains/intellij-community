// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.*;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.extractSuperclass.ExtractSuperClassProcessor;
import com.intellij.refactoring.extractSuperclass.ExtractSuperClassUtil;
import com.intellij.refactoring.memberPullUp.PullUpConflictsUtil;
import com.intellij.refactoring.memberPullUp.PullUpProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

/**
 * @author yole
 */
public class ExtractSuperClassTest extends LightMultiFileTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_5;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/extractSuperClass/";
  }

  public void testFinalFieldInitialization() {   // IDEADEV-19704
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("X", PsiClass.class),
           new RefactoringTestUtil.MemberDescriptor("x", PsiField.class));
  }

  public void testFieldInitializationWithCast() {
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("x", PsiField.class));
  }

   public void testMethodTypeParameter() {
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("m", PsiMethod.class));
  }

  public void testMultipleTypeParameters() {
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("m", PsiMethod.class));
  }


  public void testEmptyForeach() {
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("m", PsiMethod.class));
  }

  public void testConflictUsingPrivateMethod() {
    doTest("Test", "TestSubclass",
           new String[] {"Method <b><code>Test.foo()</code></b> is private and will not be accessible from method <b><code>x()</code></b>."},
           new RefactoringTestUtil.MemberDescriptor("x", PsiMethod.class));
  }

  public void testConflictMoveAbstractWithPrivateMethod() {
    doTest("Test", "TestSubclass",
           new String[] {"Method <b><code>x()</code></b> uses method <b><code>Test.xx()</code></b> which won't be accessible from the subclass."},
           new RefactoringTestUtil.MemberDescriptor("x", PsiMethod.class, true),
           new RefactoringTestUtil.MemberDescriptor("xx", PsiMethod.class));
  }

  public void testConflictAbstractPackageLocalMethod() {
    doTest("a.Test", "TestSubclass",
           new String[] {"Can't make method <b><code>x()</code></b> abstract as it won't be accessible from the subclass."},
           "b",
           new RefactoringTestUtil.MemberDescriptor("x", PsiMethod.class, true));
  }

  public void testConflictUsingPackageLocalMethod() {
    doTest("a.Test", "TestSubclass",
           new String[] {"method <b><code>Sup.foo()</code></b> won't be accessible"},
           "b",
           new RefactoringTestUtil.MemberDescriptor("x", PsiMethod.class));
  }

  public void testConflictUsingPackageLocalSuperClass() {
    doTest("a.Test", "TestSubclass",
           new String[] {"class <b><code>a.Sup</code></b> won't be accessible from package <b><code>b</code></b>"},
           "b",
           new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testNoConflictUsingProtectedMethodFromSuper() {
    doTest("Test", "TestSubclass",
           new RefactoringTestUtil.MemberDescriptor("x", PsiMethod.class));
  }

  public void testParameterNameEqualsFieldName() {    // IDEADEV-10629
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("a", PsiField.class), new RefactoringTestUtil.MemberDescriptor("b", PsiField.class));
  }

  public void testSameTypeParameterName() {
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("A", PsiClass.class), new RefactoringTestUtil.MemberDescriptor("B", PsiClass.class));
  }

  public void testExtendsLibraryClass() {
    doTest("Test", "TestSubclass");
  }

  public void testRequiredImportRemoved() {
    doTest("foo.impl.B", "BImpl", new RefactoringTestUtil.MemberDescriptor("getInstance", PsiMethod.class));
  }

  public void testSubstituteGenerics() {
    doTest("B", "AB");
  }

  public void testExtendsList() {
    doTest("Test", "TestSubclass", new RefactoringTestUtil.MemberDescriptor("List", PsiClass.class));
  }

  public void testImportsCorruption() {
    doTest("p1.A", "AA", new RefactoringTestUtil.MemberDescriptor("m1", PsiMethod.class));
  }

  public void testAnonymClass() {
    doTest(() -> {
      PsiClass psiClass = myFixture.findClass("Test");
      assertNotNull(psiClass);
      final PsiField[] fields = psiClass.getFields();
      assertEquals(1, fields.length);
      final PsiExpression initializer = fields[0].getInitializer();
      assertNotNull(initializer);
      assertInstanceOf(initializer, PsiNewExpression.class);
      final PsiAnonymousClass anonymousClass = ((PsiNewExpression)initializer).getAnonymousClass();
      assertNotNull(anonymousClass);
      final ArrayList<MemberInfo> infos = new ArrayList<>();
      MemberInfo.extractClassMembers(anonymousClass, infos, member -> true, false);
      for (MemberInfo info : infos) {
        info.setChecked(true);
      }
      WriteCommandAction.writeCommandAction(getProject()).run(() -> ExtractSuperClassUtil
        .extractSuperClass(getProject(), psiClass.getContainingFile().getContainingDirectory(), "TestSubclass", anonymousClass,
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
    doTest(() -> {
      PsiClass psiClass = myFixture.findClass(className);
      assertNotNull(psiClass);
      final MemberInfo[] members = RefactoringTestUtil.findMembers(psiClass, membersToFind);
      doTest(members, newClassName, targetPackageName, psiClass, conflicts);
    });
  }

  private void doTest(MemberInfo[] members,
                      @NonNls String newClassName,
                      String targetPackageName,
                      PsiClass psiClass,
                      String[] conflicts) {
    PsiDirectory targetDirectory;
    if (targetPackageName == null) {
      targetDirectory = psiClass.getContainingFile().getContainingDirectory();
    } else {
      final PsiPackage aPackage = myFixture.findPackage(targetPackageName);
      assertNotNull(aPackage);
      targetDirectory = aPackage.getDirectories()[0];
    }
    ExtractSuperClassProcessor processor = new ExtractSuperClassProcessor(getProject(),
                                                                          targetDirectory,
                                                                          newClassName,
                                                                          psiClass, members,
                                                                          false,
                                                                          new DocCommentPolicy<>(DocCommentPolicy.ASIS));
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
      assertEquals(expectedConflicts.size(), actualConflicts.size());
      for (String actualConflict : actualConflicts) {
        if (!expectedConflicts.contains(actualConflict)) {
          fail("Unexpected conflict: " + actualConflict);
        }
      }
    } else if (!conflictsMap.isEmpty()) {
      fail("Unexpected conflicts!!!");
    }
    processor.run();
  }
}
