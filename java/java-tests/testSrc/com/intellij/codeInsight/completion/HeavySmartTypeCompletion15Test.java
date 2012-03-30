package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

@SuppressWarnings({"ALL"})
public class HeavySmartTypeCompletion15Test extends JavaCodeInsightFixtureTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/smartType";

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testGetInstance() throws Throwable {
    myFixture.configureFromExistingVirtualFile(
      myFixture.copyFileToProject(BASE_PATH + "/foo/" + getTestName(false) + ".java", "foo/" + getTestName(false) + ".java"));
    performAction();
    myFixture.type('\n');
    myFixture.checkResultByFile(BASE_PATH + "/foo/" + getTestName(false) + "-out.java");
  }

  public void testProtectedAnonymousConstructor() throws Throwable {
    myFixture.addClass("package pkg;" +
                       "public class Foo {" +
                       "  protected Foo(int a) {}" +
                       "}");
    myFixture.addClass("package pkg;" +
                       "public class Bar<T> {" +
                       "  protected Bar(java.util.List<T> list) {}" +
                       "}");
    doTest();
  }

  public void testProtectedAnonymousConstructor2() throws Throwable {
    myFixture.addClass("package pkg;" +
                       "public class Foo {" +
                       "  protected Foo(int a) {}" +
                       "}");
    myFixture.addClass("package pkg;" +
                       "public class Bar<T> {" +
                       "  protected Bar(java.util.List<T> list) {}" +
                       "}");
    doTest();
  }

  public void testUnlockDocument() throws Throwable {
    myFixture.addClass("package pkg; public class Bar {}");
    myFixture.addClass("package pkg; public class Foo {" +
                       "  public static void foo(java.util.List<pkg.Bar> list) {}" +
                       "}");

    doTest();
  }

  private void doTest() throws Exception {
    configure();
    checkResult();
  }

  private void checkResult() {
    myFixture.checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  private void configure() {
    myFixture.configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    performAction();
  }

  public void testClassLiteralShouldInsertImport() throws Throwable {
    myFixture.addClass("package bar; public class Intf {}");
    myFixture.addClass("package foo; public class Bar extends bar.Intf {}");

    configure();
    myFixture.type('\n');
    checkResult();
  }

  private void performAction() {
    myFixture.complete(CompletionType.SMART);
  }

}