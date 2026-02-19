// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.CapturingCleanerInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class CapturingCleanerInspectionTest extends LightJavaCodeInsightFixtureTestCase {

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
    return JAVA_9;
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/cleanerCapturingThis/before18";
  }
}