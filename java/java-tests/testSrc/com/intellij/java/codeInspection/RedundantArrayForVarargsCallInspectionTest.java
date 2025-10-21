// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.miscGenerics.RedundantArrayForVarargsCallInspection;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class RedundantArrayForVarargsCallInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/redundantArrayForVarargs/";
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new RedundantArrayForVarargsCallInspection();
  }

  public void testIDEADEV15215() { doTest(); }
  public void testIDEADEV25923() { doTest(); }
  public void testNestedArray() { doTest(); }
  public void testCheckEnumConstant() { doTest(); }
  public void testGeneric() { doTest(); }
  public void testRawArray() { doTest(); }
  public void testPolymorphicSignature() { doTest(); }
  public void testValidSubtype() { doTest(); }
}
