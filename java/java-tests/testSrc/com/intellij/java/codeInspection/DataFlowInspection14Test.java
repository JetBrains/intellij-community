// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class DataFlowInspection14Test extends DataFlowInspectionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_14;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }

  public void testInstanceOfPattern() { doTest(); }
  public void testSwitchStatements() { doTest(); }
  public void testSwitchExpressions() { doTest(); }
  public void testSwitchExpressionsNullability() { doTest(); }
  public void testConstantDescAsWrapperSupertype() {
    myFixture.addClass("package java.lang.constant; public interface ConstantDesc {}");
    doTest();
  }
}