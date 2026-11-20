// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@AnalysisHostTest
public class RedundantEscapeInRegexReplacementInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new RedundantEscapeInRegexReplacementInspection(), getEnvironmentClasses());

  @Test
  public void testRedundantEscapeInRegexReplacement() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      """
      package java.util.regex;
      public final class Pattern {
          public static Pattern compile(String regex) {
            return null;
          }
          public Matcher matcher(String s) {
            return null;
          }
      }
      """,
      """
      package java.util.regex;
      public final class Matcher {
          public String replaceAll(String r) {
            return null;
          }
          public String replaceFirst(String r) {
            return null;
          }
          public Matcher appendReplacement(StringBuilder sb, String r) {
            return this;
          }
          public Matcher appendReplacement(StringBuffer sb, String r) {
            return this;
          }
      }
      """
    };
  }
}