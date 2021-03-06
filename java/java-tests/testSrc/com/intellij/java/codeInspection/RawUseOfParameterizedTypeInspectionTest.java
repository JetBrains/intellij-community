// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.miscGenerics.RawUseOfParameterizedTypeInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RawUseOfParameterizedTypeInspectionTest extends LightJavaInspectionTestCase {

  public void testRawUseOfParameterizedType() {
    doTest();
  }

  public void testIgnoreWhenQuickFixNotAvailable() {
    final RawUseOfParameterizedTypeInspection inspection = (RawUseOfParameterizedTypeInspection)getInspection();
    inspection.ignoreWhenQuickFixNotAvailable = true;
    myFixture.enableInspections(inspection);
    myFixture.configureByFile("RawUseOfParameterizedType.java");
    final Document document = myFixture.getEditor().getDocument();
    final ExpectedHighlightingData data = new ExpectedHighlightingData(document, false, false, false);
    data.init();
    final List<HighlightInfo> infos = myFixture.doHighlighting(HighlightSeverity.WARNING);
    assertEmpty(infos);
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final RawUseOfParameterizedTypeInspection inspection = new RawUseOfParameterizedTypeInspection();
    inspection.ignoreObjectConstruction = false;
    inspection.ignoreUncompilable = true;
    inspection.ignoreParametersOfOverridingMethods = true;
    inspection.ignoreTypeCasts = true;
    inspection.ignoreWhenQuickFixNotAvailable = false;
    return inspection;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/rawUseOfParameterizedType/";
  }
}