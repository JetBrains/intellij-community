// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class DataFlowInspection17Test extends DataFlowInspectionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }

  public void testParameterNullabilityFromSwitch() {
    doTest();
  }

  public void testDefaultLabelElementInSwitch() {
    doTest();
  }

  public void testSuspiciousLabelElements() {
    doTest();
  }

  public void testPredicateNot() { doTest(); }

  public void testEnumNullability() {
    doTest();
  }

  public void testBoxedTypeNullability() {
    doTest();
  }

  public void testPatternsNullability() {
    doTest();
  }

  public void testPatterns() {
    doTest();
  }

  public void testInstanceof() {
    doTest();
  }

  public void testNewStringWrongEquals() { doTest(); }
}