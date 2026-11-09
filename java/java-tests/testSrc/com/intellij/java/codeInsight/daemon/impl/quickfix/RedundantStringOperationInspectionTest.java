// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import com.siyeh.ig.redundancy.RedundantStringOperationInspection;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class RedundantStringOperationInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  @BeforeEach
  void setUpTestDataPath() {
    inspectionFixture.get().getFixture().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/redundantStringOperation/");
  }

  @Nullable
  private static InspectionProfileEntry getInspection() {
    RedundantStringOperationInspection inspection = new RedundantStringOperationInspection();
    inspection.ignoreSingleArgSubstring = false;
    return inspection;
  }

  @Test
  public void testShouldReplaceStripByIsBlank() {inspectionFixture.get().doTest();}
  @Test
  public void testEmptyStringArgument() {inspectionFixture.get().doTest();}
  @Test
  public void testStringLengthArgument() {inspectionFixture.get().doTest();}
  @Test
  public void testZeroArgument() {inspectionFixture.get().doTest();}
  @Test
  public void testBAOStoString() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, inspectionFixture.get()::doTest);
  }
  @Test
  public void testNewStringNewChar() {inspectionFixture.get().doTest();}
  @Test
  public void testStringValueOfNewChar() {inspectionFixture.get().doTest();}
  @Test
  public void testRedundantStringOperation() {inspectionFixture.get().doTest();}

  @Test
  public void testRedundantStrTemplateProcessorFix() {
    doQuickFixTest();
  }

  protected void doTest21() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, inspectionFixture.get()::doTest);
  }

  protected void doQuickFixTest() {
    doTest21();
    inspectionFixture.get().checkQuickFixAll();
  }
}