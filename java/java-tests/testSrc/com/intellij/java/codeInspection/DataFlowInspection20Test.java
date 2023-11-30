// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class DataFlowInspection20Test extends DataFlowInspectionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_20;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }

  public void testSealedClassCast() { doTest(); }
  public void testCastToSealedInterface() { doTest(); }

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
  public void testSuspiciousLabelElementsJava20() {
    doTest();
  }
  public void testForEachPattern() {
    myFixture.addClass("""
                         package org.jetbrains.annotations;
                         public @interface Range {
                           long from();
                           long to();
                         }""");
    doTest();
  }
}