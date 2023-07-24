// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class LightStringTemplatesHighlightingTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlightingStringTemplates";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  public void testStringTemplates() { doTest(); }

  private void doTest() {
    myFixture.addClass("""
      package java.lang;
      public interface StringTemplate {
        Processor<String, RuntimeException> STR = null;
        Processor<StringTemplate, RuntimeException> RAW = st -> st;
        
        @PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
        @FunctionalInterface
        public interface Processor<R, E extends Throwable> {
          R process(StringTemplate stringTemplate) throws E;
        }
      }""");
    myFixture.addClass("""
      package java.util;
      public final class FormatProcessor implements Processor<String, RuntimeException> {
        private FormatProcessor(Locale locale) {}
        public static final FormatProcessor FMT = new FormatProcessor(Locale.ROOT);
      }""");
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}