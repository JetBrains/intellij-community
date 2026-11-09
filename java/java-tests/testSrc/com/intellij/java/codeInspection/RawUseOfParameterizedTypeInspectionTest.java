// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.miscGenerics.RawUseOfParameterizedTypeInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class RawUseOfParameterizedTypeInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  @BeforeEach
  void setUpTestDataPath() {
    inspectionFixture.get().getFixture().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/rawUseOfParameterizedType/");
  }

  @Test
  public void testRawUseOfParameterizedType() {
    inspectionFixture.get().doTest();
  }
  
  @Test
  public void testInvalidType() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testIgnoreWhenQuickFixNotAvailable() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final RawUseOfParameterizedTypeInspection inspection = (RawUseOfParameterizedTypeInspection)getInspection();
    inspection.ignoreWhenQuickFixNotAvailable = true;
    myFixture.enableInspections(inspection);
    myFixture.configureByFile("RawUseOfParameterizedType.java");
    final Document document = myFixture.getEditor().getDocument();
    final ExpectedHighlightingData data = new ExpectedHighlightingData(document, false, false, false);
    data.init();
    final List<HighlightInfo> infos = myFixture.doHighlighting(HighlightSeverity.WARNING);
    assertThat(infos).isNullOrEmpty();
  }

  @Nullable
  private static InspectionProfileEntry getInspection() {
    final RawUseOfParameterizedTypeInspection inspection = new RawUseOfParameterizedTypeInspection();
    inspection.ignoreObjectConstruction = false;
    inspection.ignoreUncompilable = true;
    inspection.ignoreParametersOfOverridingMethods = true;
    inspection.ignoreTypeCasts = true;
    inspection.ignoreWhenQuickFixNotAvailable = false;
    return inspection;
  }
}