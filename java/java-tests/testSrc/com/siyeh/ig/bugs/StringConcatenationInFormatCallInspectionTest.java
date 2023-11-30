// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringConcatenationInFormatCallInspectionTest extends LightJavaInspectionTestCase {

  public void testStringConcatenationInFormatCall() {
    doTest();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new StringConcatenationInFormatCallInspection();
  }
}