// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.ForEachWithRecordPatternCanBeUsedInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class ForEachWithRecordPatternCanBeUsedInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testSimple() { doTest(); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.enableInspections(new ForEachWithRecordPatternCanBeUsedInspection());
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_20;
  }


  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/forEachWithRecordPatternCanBeUsed";
  }

  private void doTest() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }
}
