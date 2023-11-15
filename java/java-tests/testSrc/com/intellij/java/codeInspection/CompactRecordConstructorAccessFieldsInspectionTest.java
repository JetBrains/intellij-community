// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.CompactRecordConstructorAccessFieldsInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;


public class CompactRecordConstructorAccessFieldsInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNestedCalls() { doTest(); }

  private void doTest() {
    myFixture.enableInspections(new CompactRecordConstructorAccessFieldsInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_21;
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath()+"/inspection/compactRecordConstructorAccessFields";
  }
}