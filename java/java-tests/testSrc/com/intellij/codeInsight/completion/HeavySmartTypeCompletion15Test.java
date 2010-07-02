package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;

@SuppressWarnings({"ALL"})
public class HeavySmartTypeCompletion15Test extends CompletionTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/smartType";

  public HeavySmartTypeCompletion15Test() {
    setType(CompletionType.SMART);
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
  

  public void testGetInstance() throws Throwable {
    configureByFile(BASE_PATH + "/foo/" + getTestName(false) + ".java", BASE_PATH);
    performAction();
    selectItem(myItems[0]);
    checkResultByFile(BASE_PATH + "/foo/" + getTestName(false) + "-out.java");
  }

  public void testProtectedAnonymousConstructor() throws Throwable {
    createClass("package pkg;" +
                                "public class Foo {" +
                                "  protected Foo(int a) {}" +
                                "}");
    createClass("package pkg;" +
                                "public class Bar<T> {" +
                                "  protected Bar(java.util.List<T> list) {}" +
                                "}");
    doTest();
  }

  public void testProtectedAnonymousConstructor2() throws Throwable {
    createClass("package pkg;" +
                                "public class Foo {" +
                                "  protected Foo(int a) {}" +
                                "}");
    createClass("package pkg;" +
                                "public class Bar<T> {" +
                                "  protected Bar(java.util.List<T> list) {}" +
                                "}");
    doTest();
  }

  public void testUnlockDocument() throws Throwable {
    createClass("package pkg; public class Bar {}");
    createClass("package pkg; public class Foo {" +
                              "  public static void foo(java.util.List<pkg.Bar> list) {}" +
                              "}");

    doTest();
  }

  private void doTest() throws Exception {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  public void testClassLiteralShouldInsertImport() throws Throwable {
    createClass("package bar; public class Intf {}");
    createClass("package foo; public class Bar extends bar.Intf {}");

    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    selectItem(myItems[0]);
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  private void performAction() {
    complete();
  }

  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  protected void tearDown() throws Exception {
    LookupManager.getInstance(myProject).hideActiveLookup();
    super.tearDown();
  }

  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }
}