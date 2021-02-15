// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.extractInterface.ExtractInterfaceProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class ExtractSuperInterfaceTest extends ExtractSuperClassTest {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15;
  }

  public void testFromRecord() {
    doExtractInterfaceTest("p.A", "IA", new RefactoringTestUtil.MemberDescriptor("m", PsiMethod.class, true));
  }

  //additional abstract methods in newly jdk
  @Override
  public void testExtendsLibraryClass() { }
  @Override
  public void testNoConflictUsingProtectedMethodFromSuper() { }

  private void doExtractInterfaceTest(String className, String newClassName, RefactoringTestUtil.MemberDescriptor... membersToFind) {
    doTest(() -> {
      PsiClass psiClass = myFixture.findClass(className);
      assertNotNull(psiClass);
      final MemberInfo[] members = RefactoringTestUtil.findMembers(psiClass, membersToFind);
      doTest(members, null, psiClass, null,
             targetDirectory -> new ExtractInterfaceProcessor(getProject(), false, targetDirectory, newClassName, psiClass, members, new DocCommentPolicy<>(DocCommentPolicy.ASIS)));
    });
  }

}
