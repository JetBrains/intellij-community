// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.CapturingCleanerInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class CapturingCleaner18InspectionTest extends LightJavaCodeInsightFixtureTestCase {

  public void testCapturingCleaner() {doTest();}

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.enableInspections(new CapturingCleanerInspection());
  }

  private void doTest() {
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_18;
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/cleanerCapturingThis/18";
  }
}