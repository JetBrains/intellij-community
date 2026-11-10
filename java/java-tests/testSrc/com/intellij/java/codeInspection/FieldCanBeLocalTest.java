// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class FieldCanBeLocalTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/fieldCanBeLocal");
  }

  private void doTest() {
    doTest(new FieldCanBeLocalInspection());
  }

  private void doTest(InspectionProfileEntry inspection) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testFieldWrittenInAnonymousClassFieldInitializer() { doTest(); }
  @Test
  public void testFieldUsedInAnotherMethodAsQualifier() { doTest(); }
  @Test
  public void testFieldUsedForWritingInLambda() { doTest(); }
  @Test
  public void testFieldUsedInConstantInitialization() { doTest(); }
  @Test
  public void testLocalVar2InnerClass() { doTest(); }
  @Test
  public void testFieldReferencedFromAnotherObject() { doTest(); }
  @Test
  public void testDontSimplifyRuntimeConstants() { doTest(); }
  @Test
  public void testInnerClassConstructor() { doTest(); }
  @Test
  public void testAnonymousClassConstructor() { doTest(); }
  @Test
  public void testStateField() { doTest(); }
  @Test
  public void testConstructorThisRef() { doTest(); }

  @Test
  public void testConstructorThisRef2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), LanguageLevel.JDK_25, myFixture.getTestRootDisposable());
    doTest();
  }

  @Test
  public void testStaticQualifiedFieldAccessForWriting() { doTest(); }

  @Test
  public void testIgnoreAnnotated() {
    final FieldCanBeLocalInspection inspection = new FieldCanBeLocalInspection();
    inspection.EXCLUDE_ANNOS.add(Deprecated.class.getName());
    doTest(inspection);
  }

  @Test
  public void testInnerClassFieldInitializer() { doTest(); }
  @Test
  public void testFieldWithImmutableType() { doTest(); }

  @Test
  public void testLambda() {
    final FieldCanBeLocalInspection inspection = new FieldCanBeLocalInspection();
    inspection.IGNORE_FIELDS_USED_IN_MULTIPLE_METHODS = false;
    doTest(inspection);
  }

  @Test
  public void testTwoMethodsNotIgnoreMultipleMethods () {
    final FieldCanBeLocalInspection inspection = new FieldCanBeLocalInspection();
    inspection.IGNORE_FIELDS_USED_IN_MULTIPLE_METHODS = false;
    inspection.EXCLUDE_ANNOS.add(Deprecated.class.getName());
    doTest(inspection);
  }

  @Test
  public void testMockito() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package org.mockito;
                         public @interface Mock {}
                         """);
    myFixture.addClass("""
                         package org.mockito;
                         public class Mockito {
                           public static <T> T mock(Class<T> classToMock) {
                             return null;
                           }
                         }
                         """);
    doTest();
  }

  @Test
  public void testStaticFinal() { doTest(); }
  @Test
  public void testTwoMethods() { doTest(); }
  @Test
  public void testNotConstantInitializer() { doTest(); }
  @Test
  public void testLocalStateVar2InnerClass() { doTest(); }
  @Test
  public void testSimple() { doTest(); }
  @Test
  public void testStaticAccess() { doTest(); }
  @Test
  public void testConstructor() { doTest(); }
  @Test
  public void testReflection() { doTest(); }
  @Test
  public void testArrayAccessAssignment() { doTest(); }

  /// Regression test for IDEA-386737.
  @Test
  public void testSuppressAnnotationFixHasNoFixAll() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    FieldCanBeLocalInspection inspection = new FieldCanBeLocalInspection();
    myFixture.enableInspections(inspection);
    myFixture.configureByText("Test.java", """
      package my.annotation1;

      class Test {
      @MyAnn
        private int <caret>f;

        void foo() {
          f = 0;
          int k = f;
        }
      }

      @interface MyAnn {
      }
      """);

    IntentionAction suppressFix = myFixture.findSingleIntention(
      QuickFixBundle.message("fix.add.special.annotation.text", "my.annotation1.MyAnn"));
    IntentionAction fixAll = IntentionManager.getInstance().createFixAllIntention(new LocalInspectionToolWrapper(inspection), suppressFix);
    assertNull(fixAll);
  }
}
