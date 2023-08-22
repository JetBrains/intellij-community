// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.BulkFileAttributesReadInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class BulkFileAttributesReadInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testSimple() { doTest(); }
  private void doTest() {
    myFixture.enableInspections(new BulkFileAttributesReadInspection());
    myFixture.testHighlighting(true, true, true, getTestName(false) + ".java");
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_20;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/bulkFileAttributesRead";
  }
}