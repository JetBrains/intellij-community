// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class MethodMayBeStaticInspectionTest extends LightJavaInspectionTestCase {

  public void testMethodMayBeStatic() {
    doTest();
  }
  
  public void testJava16() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_16, this::doTest);
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final MethodMayBeStaticInspection inspection = new MethodMayBeStaticInspection();
    inspection.m_ignoreEmptyMethods = false;
    inspection.m_ignoreDefaultMethods = false;
    return inspection;
  }
}