// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

public class AddSingleStaticImportActionTest extends JavaCodeInsightFixtureTestCase {

  public void testInaccessible() {
    myFixture.addClass("package foo; class Foo {public static void foo(){}}");
    doTest("Add static import for 'impl.FooImpl.foo'");
  }

  public void testInaccessibleSuper() {
    myFixture.addClass("package foo; class Foo {public static void foo(){}}");
    myFixture.addClass("package foo; public class Bar extends Foo {}");
    doTest("Add static import for 'foo.Bar.foo'");
  }

  public void testInsideParameterizedReference() {
    myFixture.addClass("package foo; " +
                       "public class Class1 {" +
                       "  public static class Inner1 {}\n" +
                       "  public static class Inner2<T> {}" +
                       "}");
    doTest("Add import for 'foo.Class1.Inner2'");
  }

  public void testWrongCandidateAfterImport() {
    myFixture.addClass("package foo; class Empty {}"); //to ensure package is in the project
    doTest("Add static import for 'foo.Test.X.test'");
  }

  public void testAllowStaticImportWhenAlreadyImported() {
    myFixture.addClass("package foo; " +
                       "public class Clazz {\n" +
                       "      public  enum Foo{\n" +
                       "        Const_1, Const_2\n" +
                       "    }\n" +
                       "}");
    doTest("Add import for 'foo.Clazz.Foo'");
  }

  public void testInsideParameterizedReferenceInsideParameterizedReference() {
    myFixture.addClass("package foo; " +
                       "public class Class1 {" +
                       "  public static class Inner1 {}\n" +
                       "  public static class Inner2<T> {}" +
                       "}");
    doTest("Add import for 'foo.Class1.Inner1'");
  }

  public void testPredefinedAlwaysUseOnDemandImport() {
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.addEntry(new PackageEntry(true, "java.lang.Math", true));
    doTest("Add static import for 'java.lang.Math.abs'");
  }

  public void testSingleStaticReferencesUntilCollapsedToDiamond() {
    doTest("Add static import for 'java.lang.Math.max'");
  }

 public void testDisabledInsideParameterizedReference() {
    myFixture.addClass("package foo; " +
                       "public class Class1 {" +
                       "  public static <T> T foo(){return null;}\n" +
                       "}");
    myFixture.configureByFile(getTestName(false) + ".java");

    final IntentionAction intentionAction = myFixture.getAvailableIntention("Add static import for 'foo.Class1.foo'");
    assertNull(intentionAction);
  }

  public void testSkipSameNamedNonStaticReferences() {
    myFixture.addClass("package foo;" +
                       "public class Clazz {" +
                       "   public void print(String s) {}" +
                       "   public static void print() {}" +
                       "   public static void print(int i) {}" +
                       "}");
    doTest("Add static import for 'foo.Clazz.print'");
  }

  public void testAllowSingleStaticImportWhenOnDemandImportOverloadedMethod() {
    myFixture.addClass("package foo; class Foo {public static void foo(int i){}}");
    myFixture.addClass("package foo; class Bar {public static void foo(String s){}}");
    doTest("Add static import for 'foo.Bar.foo'");
  }

  public void testInvalidInput() {
    myFixture.configureByText(JavaFileType.INSTANCE, "class X {\n  Character.\n" +
                                                     "            Sub<caret>set\n}");
    myFixture.getAvailableIntentions();
  }

  public void testSingleImportWhenConflictingWithOnDemand() {
    myFixture.addClass("package foo; class Foo {public static void foo(int i){}}");
    myFixture.addClass("package foo; class Bar {public static void foo(String s){}}");

    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 1;
    doTest("Add static import for 'foo.Foo.foo'");
  }

  public void testConflictingNamesInScope() {
    myFixture.addClass("package foo; public class Assert {public static void assertTrue(boolean b) {}}");
    myFixture.configureByFile(getTestName(false) + ".java");
    IntentionAction intention = myFixture.getAvailableIntention("Add static import for 'foo.Assert.assertTrue'");
    assertNull(intention);
  }

  public void testNonStaticInnerClassImport() {
    myFixture.addClass("package foo; public class Foo {public class Bar {}}");
    doTest("Add import for 'foo.Foo.Bar'");
  }

  public void testProhibitWhenMethodWithIdenticalSignatureAlreadyImportedFromAnotherClass() {
    myFixture.addClass("package foo; class Foo {public static void foo(int i){}}");
    myFixture.addClass("package foo; class Bar {public static void foo(int i){}}");
    myFixture.configureByFile(getTestName(false) + ".java");

    IntentionAction intention = myFixture.getAvailableIntention("Add static import for 'foo.Bar.foo'");
    assertNull(intention);
  }

  public void testInaccessibleClassReferencedInsideJavadocLink() {
    myFixture.addClass("package foo; class Foo {static class Baz {}}");
    myFixture.configureByText("a.java",
                              "/**\n" +
                       " * {@link foo.Foo.Baz<caret>}\n" +
                       " */\n" +
                       " class InaccessibleClassReferencedInsideJavadocLink { }");
    IntentionAction intention = myFixture.getAvailableIntention("Add import for 'foo.Foo.Baz'");
    assertNull(intention);
  }

  public void testComment() {
    doTest("Add static import for 'java.util.Arrays.asList'");
  }

  public void testLineComment() {
    doTest("Add static import for 'java.lang.System.currentTimeMillis'");
  }

  private void doTest(String intentionName) {
    myFixture.configureByFile(getTestName(false) + ".java");
    IntentionAction intention = myFixture.findSingleIntention(intentionName);
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/addSingleStaticImport";
  }
}
