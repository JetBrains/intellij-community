// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.RedundantExplicitCloseInspection;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class RedundantExplicitCloseInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/redundantExplicitClose/";
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new RedundantExplicitCloseInspection();
  }

  public void testRedundantExplicitClose() { doTest(); }
}