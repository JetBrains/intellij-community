// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryStringEscapeInspectionTest extends LightJavaInspectionTestCase {

  public void testEndOfTextBlockQuote() { doQuickFixTest(); }
  public void testNewlinesAndQuotes() { doQuickFixTest(); }
  public void testDoubleQuoteInChar() { doQuickFixTest(); }
  public void testSingleQuoteInString() { doQuickFixTest(); }
  public void testMultipleProblemsInSingleString() { doQuickFixTest(); }
  public void testEscapedNewLine() { doQuickFixTest(); }
  public void testStringTemplate1() { doQuickFixTest(); }
  public void testStringTemplate2() { doQuickFixTest(); }
  public void testNestedTextBlock() { doQuickFixTest(); }

  public void testEscapedNewLineNotUnnecessary() { doTest(); }
  public void testBrokenCode() { doTest(); }
  
  public void testInInjection() { doTest(); }

  protected void doQuickFixTest() {
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
    doTest();
    checkQuickFixAll();
  }

  @Override
  protected Class<? extends InspectionProfileEntry> getInspectionClass() {
    return UnnecessaryStringEscapeInspection.class;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final UnnecessaryStringEscapeInspection inspection = new UnnecessaryStringEscapeInspection();
    inspection.reportChars = true;
    return inspection;
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21_ANNOTATED;
  }
}