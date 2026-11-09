// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.TrailingWhitespacesInTextBlockInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class TrailingWhitespacesInTextBlockInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new TrailingWhitespacesInTextBlockInspection());

  @BeforeEach
  void setUpTestDataPath() {
    inspectionFixture.get().getFixture().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/trailingWhitespacesInTextBlock");
  }

  @Test
  public void testBadTextBlock() { inspectionFixture.get().doTest(); }
  @Test
  public void testBlankLine() { doTestAll(); }
  @Test
  public void testEmptyLine() { doTestEscape(); }
  @Test
  public void testEmptyLastLine() { doTestAll(); }
  @Test
  public void testOneLineBlock() { doTestAll(); }
  @Test
  public void testReplaceAtTheEnd() { doTestEscape(); }
  @Test
  public void testStringTemplate1Java21Preview() { doTestEscape(); }
  @Test
  public void testStringTemplate2Java21Preview() { doTestEscape(); }
  @Test
  public void testStringTemplate3Java21Preview() { doTestEscape(); }
  @Test
  public void testStringTemplate4Java21Preview() { doTestEscape(); }
  @Test
  public void testStringTemplate5() { inspectionFixture.get().doTest(); }
  @Test
  public void testStringTemplate6() { doTestAll(); }
  @Test
  public void testStringTemplateOneLine() { doTestAll(); }
  @Test
  public void testTrailingSpaces() { doTestAll(); }
  @Test
  public void testTrailingTabs() { doTestEscape(); }
  @Test
  public void testWithBlankLines() { doTestAll(); }
  @Test
  public void testWithEndQuotes() { doTestAll(); }
  @Test
  public void testWithoutTrailingSpaces() { inspectionFixture.get().doTest(); }
  @Test
  public void testWithUnicodeEscapes() { doTestEscape(); }

  private void doTestEscape() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix("Escape trailing whitespace characters");
  }

  private void doTestAll() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }
}
