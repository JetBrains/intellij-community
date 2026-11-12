// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.modifiers;

import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TestLoggerKt;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.regex.Pattern;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ChangeModifierIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "modifiers/change_modifier", IntentionPowerPackBundle.message("change.modifier.intention.name"));

  @Test
  public void testMyEnum() { ipp.get().assertIntentionNotAvailable(); }
  @Test
  public void testMyClass() { ipp.get().assertIntentionNotAvailable(); }
  @Test
  public void testMyInterface() { ipp.get().assertIntentionNotAvailable(); }
  @Test
  public void testMyInterfaceJava9() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_9, () -> ipp.get().assertIntentionNotAvailable("Make 'm()' private"));
  }
  @Test
  public void testMyInterfaceDefaultJava9() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_9, () -> ipp.get().doTest("Make 'm()' private"));
  }
  @Test
  public void testMyInterfacePrivateJava9() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_9, () -> ipp.get().doTest("Make 'm()' public"));
  }
  @Test
  public void testMyInterfacePrivateStaticJava9() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_9, () -> ipp.get().doTest("Make 'm()' public"));
  }
  @Test
  public void testEnumConstructor() { ipp.get().assertIntentionNotAvailable(); }
  @Test
  public void testLocalClass() { ipp.get().assertIntentionNotAvailable(); }
  @Test
  public void testMethod() { doTestWithChooser("private"); }
  @Test
  public void testMethod2() { doTestWithChooser("package-private"); }
  @Test
  public void testAnnotatedMember() { doTestWithChooser("private"); }
  @Test
  public void testClass() { doTestWithChooser("protected"); }
  @Test
  public void testInDefaultPackage() { ipp.get().doTest("Make 'Test' package-private"); }
  @Test
  public void testInheritance() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> ipp.get().doTest("Make 'foo()' public"));
  }

  @Test
  public void testTypeParameter() {
    doTestWithChooser("protected");
  }

  @Test
  public void testAccessConflict() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    DefaultLogger.disableStderrDumping(myFixture.getTestRootDisposable());
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      try {
        doTestWithChooser("protected");
        fail("Must have an exception");
      }
      catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
        assertEquals(
          "method <b><code>fooooooo()</code></b> will have incompatible access privileges with overriding method <b><code>Y.fooooooo()</code></b>",
          e.getMessage());
      }
    });
  }

  @Test
  public void testAccessConflictIgnore() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTestWithChooser("protected"));
  }

  @Test
  public void testRecordConstructor1() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture(); 
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.HIGHEST, () -> ipp.get().doTest("Make 'Foo()' protected"));
  }

  @Test
  public void testRecordConstructor2() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.HIGHEST, () -> doTestWithChooser("public"));
  }
  
  void doTestWithChooser(String wanted) {
    UiInterceptors
      .register(new ChooserInterceptor(Arrays.asList("public", "protected", "package-private", "private"), Pattern.quote(wanted)));
    ipp.get().doTest();
  }
}
