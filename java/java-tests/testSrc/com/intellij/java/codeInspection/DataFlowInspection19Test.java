// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class DataFlowInspection19Test extends DataFlowInspectionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_19;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }

  public void testWhenPatterns() {
    doTest();
  }
  public void testSwitchNullability() {
    doTest();
  }
  public void testRecordPatterns() {
    doTest();
  }
  public void testRecordPatternNested() {
    doTest();
  }
  public void testRecordPatternAndWhen() {
    doTest();
  }
  public void testNestedRecordPatterns() {
    doTest();
  }
}