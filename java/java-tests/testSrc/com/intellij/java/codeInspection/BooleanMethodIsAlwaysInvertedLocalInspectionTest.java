// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInspection.booleanIsAlwaysInverted.BooleanMethodIsAlwaysInvertedInspection;
import com.intellij.testFramework.junit5.TestApplication;

@TestApplication
public class BooleanMethodIsAlwaysInvertedLocalInspectionTest extends BooleanMethodIsAlwaysInvertedInspectionTest {

  @Override
  protected void doTest(boolean checkRange) {
    javaInspection.get().doTest("invertedBoolean/" + javaInspection.get().getTestName(true), new BooleanMethodIsAlwaysInvertedInspection().getSharedLocalInspectionTool());
  }
}
