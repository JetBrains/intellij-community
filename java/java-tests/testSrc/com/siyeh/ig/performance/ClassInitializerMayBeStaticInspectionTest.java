// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ClassInitializerMayBeStaticInspectionTest extends LightJavaInspectionTestCase {

  public void testEmptyInitializer() {
    doTest();
  }

  public void testAnonymousClass() {
    doTest();
  }
  
  public void testNoStaticInInnerClass() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_15, this::doTest);
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ClassInitializerMayBeStaticInspection();
  }
}