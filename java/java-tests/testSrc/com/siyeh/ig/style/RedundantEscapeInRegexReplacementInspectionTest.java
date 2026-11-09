// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class RedundantEscapeInRegexReplacementInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new RedundantEscapeInRegexReplacementInspection(), getEnvironmentClasses());

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