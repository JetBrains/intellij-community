// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.colors;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ui.ColorIcon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Bas Leijdekkers
 */
@PerformanceUnitTest
@StressTestApplication
public final class JavaColorProviderPerformanceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/colors/");
    myFixture.addClass("package java.awt;" +
                       "public class Color {" +
                       "public Color(int rgb) {}" +
                       "public Color(int r, int g, int b) {}" +
                       "public Color(int rgba, boolean hasalpha) {}" +
                       "public Color(float r, float g, float b) {}" +
                       "public Color(float r, float g, float b, float a) {}" +
                       "}");
  }

  @Test
  public void testLargeArrayLiteral() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Benchmark.newBenchmark("Large array initializer performance", () -> {
      // needs to be inside the benchmark otherwise gutters are cached from the warmup phase. 
      myFixture.configureByFile(myFixture.getTestName(false) + ".java");
      
      long count = myFixture.findAllGutters().stream()
        .filter(g -> g.getIcon() instanceof ColorIcon).count();

      assertEquals(1, count);
    }).start();
  }
}
