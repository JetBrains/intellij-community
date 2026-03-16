// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;

import java.util.Arrays;
import java.util.List;

public class TestIntegrationUtilsTest extends LightPlatformTestCase {

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
    PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(getProject()).createFileFromText("Test.java", JavaFileType.INSTANCE, text);
    PsiClass firstClass = file.getClasses()[0];
    List<MemberInfo> infos = TestIntegrationUtils.extractClassMethods(firstClass, inherited);
    List<String> actual = ContainerUtil.map(infos, MemberInfoBase::getDisplayName);
    assertEquals(Arrays.asList(expected), actual);
  }
}