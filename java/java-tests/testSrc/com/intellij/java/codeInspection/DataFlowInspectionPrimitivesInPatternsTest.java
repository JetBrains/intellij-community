// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class DataFlowInspectionPrimitivesInPatternsTest extends DataFlowInspectionTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_23;
  }

  public void testInstanceofFromBoxedObjectToPrimitive() {
    doTest();
  }

  public void testInstanceofFromObjectToPrimitive() {
    doTest();
  }

  public void testInstanceofFromPrimitiveToObject() {
    doTest();
  }

  public void testInstanceofFromPrimitiveToPrimitive() {
    doTest();
  }

  public void testSwitchWithPrimitive() {
    doTest();
  }

  public void testSwitchFloatRepresentation() {
    doTest();
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }
}