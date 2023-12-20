// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.RedundantSuppressInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ExtractMethodRecommenderInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testExtractMethodRecommender() {
    ExtractMethodRecommenderInspection inspection = new ExtractMethodRecommenderInspection();
    inspection.minLength = 10;
    myFixture.enableInspections(inspection);
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
  
  public void testImplicitClass() {
    ExtractMethodRecommenderInspection inspection = new ExtractMethodRecommenderInspection();
    myFixture.enableInspections(inspection);
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  public void testRedundantSuppression() {
    ExtractMethodRecommenderInspection inspection = new ExtractMethodRecommenderInspection();
    inspection.minLength = 20;
    myFixture.enableInspections(inspection, new RedundantSuppressInspection());
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  @Override
  public String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/extractMethodRecommender";
  } 
}
