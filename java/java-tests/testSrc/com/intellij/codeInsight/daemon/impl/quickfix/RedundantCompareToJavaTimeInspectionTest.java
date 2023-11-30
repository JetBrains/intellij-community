// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.RedundantCompareToJavaTimeInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;


public class RedundantCompareToJavaTimeInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  public void testCompareTo() {
    final LocalInspectionTool inspection = new RedundantCompareToJavaTimeInspection();
    myFixture.enableInspections(inspection);

    myFixture.configureByFile("beforeCompareTo.java");
    myFixture.launchAction(myFixture.findSingleIntention("Fix all 'Expression with 'java.time' 'compareTo()' call can be simplified' problems in file"));
    myFixture.checkResultByFile("afterCompareTo.java");
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() +
     "/codeInsight/daemonCodeAnalyzer/quickFix/redundantCompareToJavaTime";
  }
}