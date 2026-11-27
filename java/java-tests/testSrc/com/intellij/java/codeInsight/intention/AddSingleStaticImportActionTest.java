// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class AddSingleStaticImportActionTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    javaModuleCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/addSingleStaticImport");

  private String testName;

  @BeforeEach
  void setUpTestName(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), false);
  }

  @Test
  public void testInaccessible() {
    codeInsightFixture.get().addClass("package foo; class Foo {public static void foo(){}}");
    doTest("Add static import for 'impl.FooImpl.foo'");
  }

  @Test
  public void testInaccessibleSuper() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; class Foo {public static void foo(){}}");
    myFixture.addClass("package foo; public class Bar extends Foo {}");
    doTest("Add static import for 'foo.Bar.foo'");
  }

  @Test
  public void testInsideParameterizedReference() {
    codeInsightFixture.get().addClass("package foo; " +
                       "public class Class1 {" +
                       "  public static class Inner1 {}\n" +
                       "  public static class Inner2<T> {}" +
                       "}");
    doTest("Add import for 'foo.Class1.Inner2'");
  }

  @Test
  public void testDisabledInsideParameterizedQualifierReference() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testName + ".java");
    assertNull(myFixture.getAvailableIntention("Add import for 'foo.Outer.Inner'"));
  }

  @Test
  public void testDisabledInsideParameterizedQualifierReferenceDeep() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testName + ".java");
    assertNull(myFixture.getAvailableIntention("Add import for 'foo.Outer.Inner.Inner1'"));
  }

  @Test
  public void testWrongCandidateAfterImport() {
    codeInsightFixture.get().addClass("package foo; class Empty {}"); //to ensure package is in the project
    doTest("Add static import for 'foo.Test.X.test'");
  }

  @Test
  public void testAllowStaticImportWhenAlreadyImported() {
    codeInsightFixture.get().addClass("""
                         package foo; public class Clazz {
                               public  enum Foo{
                                 Const_1, Const_2
                             }
                         }""");
    doTest("Add import for 'foo.Clazz.Foo'");
  }

  @Test
  public void testInsideParameterizedReferenceInsideParameterizedReference() {
    codeInsightFixture.get().addClass("package foo; " +
                       "public class Class1 {" +
                       "  public static class Inner1 {}\n" +
                       "  public static class Inner2<T> {}" +
                       "}");
    doTest("Add import for 'foo.Class1.Inner1'");
  }

  @Test
  public void testPredefinedAlwaysUseOnDemandImport() {
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(codeInsightFixture.get().getProject());
    settings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.addEntry(new PackageEntry(true, "java.lang.Math", true));
    doTest("Add static import for 'java.lang.Math.abs'");
  }

  @Test
  public void testSingleStaticReferencesUntilCollapsedToDiamond() {
    doTest("Add static import for 'java.lang.Math.max'");
  }

 @Test
 public void testDisabledInsideParameterizedReference() {
   JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; " +
                       "public class Class1 {" +
                       "  public static <T> T foo(){return null;}\n" +
                       "}");
    myFixture.configureByFile(testName + ".java");

    final IntentionAction intentionAction = myFixture.getAvailableIntention("Add static import for 'foo.Class1.foo'");
    assertNull(intentionAction);
  }

  @Test
  public void testSkipSameNamedNonStaticReferences() {
    codeInsightFixture.get().addClass("package foo;" +
                       "public class Clazz {" +
                       "   public void print(String s) {}" +
                       "   public static void print() {}" +
                       "   public static void print(int i) {}" +
                       "}");
    doTest("Add static import for 'foo.Clazz.print'");
  }

  @Test
  public void testAllowSingleStaticImportWhenOnDemandImportOverloadedMethod() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; class Foo {public static void foo(int i){}}");
    myFixture.addClass("package foo; class Bar {public static void foo(String s){}}");
    doTest("Add static import for 'foo.Bar.foo'");
  }

  @Test
  public void testInvalidInput() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class X {
        Character.
                  Sub<caret>set
      }""");
    myFixture.getAvailableIntentions();
  }

  @Test
  public void testSingleImportWhenConflictingWithOnDemand() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; class Foo {public static void foo(int i){}}");
    myFixture.addClass("package foo; class Bar {public static void foo(String s){}}");

    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    settings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 1;
    doTest("Add static import for 'foo.Foo.foo'");
  }

  @Test
  public void testConflictingNamesInScope() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; public class Assert {public static void assertTrue(boolean b) {}}");
    myFixture.configureByFile(testName + ".java");
    IntentionAction intention = myFixture.getAvailableIntention("Add static import for 'foo.Assert.assertTrue'");
    assertNull(intention);
  }

  @Test
  public void testNonStaticInnerClassImport() {
    codeInsightFixture.get().addClass("package foo; public class Foo {public class Bar {}}");
    doTest("Add import for 'foo.Foo.Bar'");
  }

  @Test
  public void testProhibitWhenMethodWithIdenticalSignatureAlreadyImportedFromAnotherClass() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; class Foo {public static void foo(int i){}}");
    myFixture.addClass("package foo; class Bar {public static void foo(int i){}}");
    myFixture.configureByFile(testName + ".java");

    IntentionAction intention = myFixture.getAvailableIntention("Add static import for 'foo.Bar.foo'");
    assertNull(intention);
  }

  @Test
  public void testInaccessibleClassReferencedInsideJavadocLink() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; class Foo {static class Baz {}}");
    myFixture.configureByText("a.java",
                              """
                                /**
                                 * {@link foo.Foo.Baz<caret>}
                                 */
                                 class InaccessibleClassReferencedInsideJavadocLink { }""");
    IntentionAction intention = myFixture.getAvailableIntention("Add import for 'foo.Foo.Baz'");
    assertNull(intention);
  }

  @Test
  public void testComment() {
    doTest("Add static import for 'java.util.Arrays.asList'");
  }

  @Test
  public void testLineComment() {
    doTest("Add static import for 'java.lang.System.currentTimeMillis'");
  }

  private void doTest(String intentionName) {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testName + ".java");
    IntentionAction intention = myFixture.findSingleIntention(intentionName);
    assertNotNull(intention);
    myFixture.launchAction(intention);
    EdtTestUtil.runInEdtAndWait(() -> NonBlockingReadActionImpl.waitForAsyncTaskCompletion());
    myFixture.checkResultByFile(testName + "_after.java");
  }

}
