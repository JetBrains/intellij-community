// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixtureByTestName;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class MissortedModifiersInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixtureByTestName(projectFixture, testName -> getInspection(testName));

  @Test
  public void testMissortedModifiers() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testIgnoreAnnotations() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testTypeUseWithTypeUseGenerated() {
    doTestQuickFix();
  }
  @Test
  public void testTypeUseWithTypeUseGeneratedInline() {
    doTestQuickFix();
  }

  @Test
  public void testTypeUseWithTypeUseNotGenerated() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testTypeUseWithTypeHighlightingUseNotGenerated() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testTypeUseWithTypeHighlightingUseGenerated() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testSimpleComment() {
    doTestQuickFix();
  }

  @Test
  public void testAnotherComment() {
    doTestQuickFix();
  }

  @Test
  public void testKeepAnnotationOrder() {
    doTestQuickFix();
  }

  @Test
  public void testMissortedInModuleRequires(){
    doTestQuickFix();
  }

  @Test
  public void testSealedClass() {
    doTestQuickFix();
  }

  public void doTestQuickFix() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("missorted.modifiers.sort.quickfix"));
  }

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    JavaCodeStyleSettings instance = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE = false;
    if (inspectionFixture.get().getTestName(false).contains("TypeUseWithType")) {
      instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE = true;
    }
  }

  @Nullable
  private static InspectionProfileEntry getInspection(String testName) {
    MissortedModifiersInspection inspection = new MissortedModifiersInspection();
    if (testName.contains("IgnoreAnnotations")) {
      inspection.m_requireAnnotationsFirst = false;
    }
    if (testName.contains("UseGenerated")) {
      inspection.typeUseWithType = true;
    }
    return inspection;
  }
}
