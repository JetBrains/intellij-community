// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.MeaninglessRecordAnnotationInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class MeaninglessRecordAnnotationInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInspection/meaninglessRecordAnnotation";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new MeaninglessRecordAnnotationInspection());
  }

  public void testMeaninglessRecordAnnotation() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFiles(getTestName(false) + ".java");
    myFixture.checkHighlighting(true, false, false);
  }
}