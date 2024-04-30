// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class DataFlowInspectionPrimitivesInPatternsTest extends DataFlowInspectionTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new ProjectDescriptor(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel());
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

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }
}