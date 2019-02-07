// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import groovy.transform.CompileStatic

@CompileStatic
class HeavySmartTypeCompletion15Test extends JavaCodeInsightFixtureTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/smartType"

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath()
  }

  void testGetInstance() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      myFixture.copyFileToProject(BASE_PATH + "/foo/" + getTestName(false) + ".java", "foo/" + getTestName(false) + ".java"))
    myFixture.complete(CompletionType.SMART)
    myFixture.type('\n')
    myFixture.checkResultByFile(BASE_PATH + "/foo/" + getTestName(false) + "-out.java")
  }

  void testProtectedAnonymousConstructor() throws Throwable {
    myFixture.addClass("package pkg;" +
                       "public class Foo {" +
                       "  protected Foo(int a) {}" +
                       "}")
    myFixture.addClass("package pkg;" +
                       "public class Bar<T> {" +
                       "  protected Bar(java.util.List<T> list) {}" +
                       "}")
    doTest()
  }

  void testProtectedAnonymousConstructor2() throws Throwable {
    myFixture.addClass("package pkg;" +
                       "public class Foo {" +
                       "  protected Foo(int a) {}" +
                       "}")
    myFixture.addClass("package pkg;" +
                       "public class Bar<T> {" +
                       "  protected Bar(java.util.List<T> list) {}" +
                       "}")
    doTest()
  }

  void testUnlockDocument() throws Throwable {
    myFixture.addClass("package pkg; public class Bar {}")
    myFixture.addClass("package pkg; public class Foo {" +
                       "  public static void foo(java.util.List<pkg.Bar> list) {}" +
                       "}")

    doTest()
  }

  private void doTest() throws Exception {
    configure()
    checkResult()
  }

  private void checkResult() {
    myFixture.checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java")
  }

  private void configure() {
    myFixture.configureByFile(BASE_PATH + "/" + getTestName(false) + ".java")
    myFixture.complete(CompletionType.SMART)
  }

  void testClassLiteralShouldInsertImport() throws Throwable {
    myFixture.addClass("package bar; public class Intf {}")
    myFixture.addClass("package foo; public class Bar extends bar.Intf {}")

    configure()
    myFixture.type('\n')
    checkResult()
  }

  void testInaccessibleClassAfterNew() {
    Module moduleA = PsiTestUtil.addModule(project, StdModuleTypes.JAVA, 'A', myFixture.tempDirFixture.findOrCreateDir("a"))
    Module moduleB = PsiTestUtil.addModule(project, StdModuleTypes.JAVA, 'B', myFixture.tempDirFixture.findOrCreateDir("b"))

    ModuleRootModificationUtil.addDependency(myModule, moduleB)
    ModuleRootModificationUtil.addDependency(moduleB, moduleA)

    myFixture.addFileToProject('a/foo/Foo.java', 'package foo; public interface Foo {}')
    myFixture.addFileToProject('b/bar/Bar.java', 'package bar; public class Bar { public static void accept(foo.Foo i) {}  }')

    configure()
    myFixture.type('\n')
    checkResult()
  }
}