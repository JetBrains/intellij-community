// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.colors;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ui.ColorIcon;

/**
 * @author Bas Leijdekkers
 */
@PerformanceUnitTest
public final class JavaColorProviderPerformanceTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package java.awt;" +
                       "public class Color {" +
                       "public Color(int rgb) {}" +
                       "public Color(int r, int g, int b) {}" +
                       "public Color(int rgba, boolean hasalpha) {}" +
                       "public Color(float r, float g, float b) {}" +
                       "public Color(float r, float g, float b, float a) {}" +
                       "}");
  }

  @Override
  public String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/colors/";
  }

  public void testLargeArrayLiteral() {
    Benchmark.newBenchmark("Large array initializer performance", () -> {
      // needs to be inside the benchmark otherwise gutters are cached from the warmup phase. 
      myFixture.configureByFile(getTestName(false) + ".java");
      
      long count = myFixture.findAllGutters().stream()
        .filter(g -> g.getIcon() instanceof ColorIcon).count();

      assertEquals(1, count);
    }).start();
  }
}
