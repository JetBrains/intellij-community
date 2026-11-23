// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.extractInterface.ExtractInterfaceProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ExtractSuperInterfaceTest extends ExtractSuperClassTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);

  @Override
  protected TestFixture<LightJavaProject> getProjectFixture() {
    return projectFixture;
  }

  @Test
  public void testFromRecord() {
    doExtractInterfaceTest("p.A", "IA", new RefactoringTestUtil.MemberDescriptor("m", PsiMethod.class, true));
  }

  //additional abstract methods in newly jdk
  @Test
  @Override
  public void testExtendsLibraryClass() { }
  @Test
  @Override
  public void testNoConflictUsingProtectedMethodFromSuper() { }

  private void doExtractInterfaceTest(String className, String newClassName, RefactoringTestUtil.MemberDescriptor... membersToFind) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      PsiClass psiClass = myFixture.findClass(className);
      assertNotNull(psiClass);
      final MemberInfo[] members = RefactoringTestUtil.findMembers(psiClass, membersToFind);
      doTest(members, null, psiClass, null,
             targetDirectory -> new ExtractInterfaceProcessor(myFixture.getProject(), false, targetDirectory, newClassName, psiClass, members, new DocCommentPolicy(DocCommentPolicy.ASIS)));
    });
  }

}
