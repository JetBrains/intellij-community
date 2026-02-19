// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.dataflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class TooBroadScopeInspectionTest extends LightJavaInspectionTestCase {

  public void testTooBroadScope() {
    doTest();
    checkQuickFixAll();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new TooBroadScopeInspection();
  }

  @Override
  protected String getBasePath() {
    return "/java/java-tests/testData/ig/com/siyeh/igtest/dataflow/scope";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }
}