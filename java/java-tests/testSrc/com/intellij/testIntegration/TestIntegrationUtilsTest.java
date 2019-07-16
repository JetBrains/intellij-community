// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    doTest("class Foo {void bar() {} private void baz() {}}", false, "bar():void");
    doTest("class Foo extends Super {void bar() {}} class Super {void qux() {}}", false, "bar():void");
    doTest("class Foo extends Super {void bar() {}} class Super {void qux() {}}", true, "qux():void", "bar():void");
    doTest("class Foo implements I {void bar() {}} interface I {void qux();}", true, "bar():void");
    doTest("class Foo implements I {void bar() {}} interface I {default void qux() {}}", true, "qux():void", "bar():void");
    doTest("interface Foo extends I {void bar();} interface I {void qux();}", true, "bar():void");
  }

  private void doTest(@Language("JAVA") String text, boolean inherited, String... expected) {
    PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(getProject()).createFileFromText("Test.java", JavaFileType.INSTANCE, text);
    PsiClass firstClass = file.getClasses()[0];
    List<MemberInfo> infos = TestIntegrationUtils.extractClassMethods(firstClass, inherited);
    List<String> actual = ContainerUtil.map(infos, MemberInfoBase::getDisplayName);
    assertEquals(Arrays.asList(expected), actual);
  }
}