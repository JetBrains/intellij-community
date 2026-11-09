// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class UnnecessaryStringEscapeInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21_ANNOTATED);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection(), ArrayUtilRt.EMPTY_STRING_ARRAY, null, UnnecessaryStringEscapeInspection.class);

  @Test
  public void testEndOfTextBlockQuote() { doQuickFixTest(); }
  @Test
  public void testNewlinesAndQuotes() { doQuickFixTest(); }
  @Test
  public void testDoubleQuoteInChar() { doQuickFixTest(); }
  @Test
  public void testSingleQuoteInString() { doQuickFixTest(); }
  @Test
  public void testMultipleProblemsInSingleString() { doQuickFixTest(); }
  @Test
  public void testEscapedNewLine() { doQuickFixTest(); }
  @Test
  public void testStringTemplate1() { doQuickFixTest(); }
  @Test
  public void testStringTemplate2() { doQuickFixTest(); }
  @Test
  public void testNestedTextBlock() { doQuickFixTest(); }

  @Test
  public void testEscapedNewLineNotUnnecessary() { inspectionFixture.get().doTest(); }
  @Test
  public void testBrokenCode() { inspectionFixture.get().doTest(); }
  
  @Test
  public void testInInjection() { inspectionFixture.get().doTest(); }

  protected void doQuickFixTest() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.addClass("""
      package java.lang;
      public interface StringTemplate {
        Processor<String, RuntimeException> STR = null;
        
        @PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
        @FunctionalInterface
        interface Processor<R, E extends Throwable> {
          R process(StringTemplate stringTemplate) throws E;
        }
      }""");
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }

  @Nullable
  private static InspectionProfileEntry getInspection() {
    final UnnecessaryStringEscapeInspection inspection = new UnnecessaryStringEscapeInspection();
    inspection.reportChars = true;
    return inspection;
  }
}