// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.testIntegration;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.testIntegration.TestIntegrationUtils;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class TestIntegrationUtilsTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);

  @Test
  public void testExtractClassMethods() {
    doTest("class Foo {void bar() {} private void baz() {}}", false, "bar(): void");
    doTest("class Foo extends Super {void bar() {}} class Super {void qux() {}}", false, "bar(): void");
    doTest("class Foo extends Super {void bar() {}} class Super {void qux() {}}", true, "bar(): void", "qux(): void");
    doTest("class Foo implements I {void bar() {}} interface I {void qux();}", true, "bar(): void", "qux(): void");
    doTest("class Foo implements I {void bar() {}} interface I {default void qux() {}}", true, "bar(): void", "qux(): void");
    doTest("interface Foo extends I {void bar();} interface I {void qux();}", true, "bar(): void", "qux(): void");
    doTest("interface Foo extends I {void bar();} interface I extends B {} interface B {void qux();}", true, "bar(): void", "qux(): void");
    doTest("class Foo extends I {@Override void bar();} interface I {void bar();default void foo(){}}", true, "bar(): void", "foo(): void");
  }

  private void doTest(@Language("JAVA") String text, boolean inherited, String... expected) {
    List<String> actual = ReadAction.computeBlocking(() -> {
      PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(projectFixture.get().getProject())
        .createFileFromText("Test.java", JavaFileType.INSTANCE, text);
      PsiClass firstClass = file.getClasses()[0];
      List<MemberInfo> infos = TestIntegrationUtils.extractClassMethods(firstClass, inherited);
      return ContainerUtil.map(infos, MemberInfoBase::getDisplayName);
    });
    assertEquals(Arrays.asList(expected), actual);
  }
}