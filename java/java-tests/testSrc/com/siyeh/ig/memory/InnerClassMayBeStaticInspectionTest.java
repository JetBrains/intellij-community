// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.memory;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class InnerClassMayBeStaticInspectionTest extends LightJavaInspectionTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    addEnvironmentClass("package org.junit.jupiter.api;" +
                        "public @interface Nested {}");
  }

  public void testInnerClassMayBeStatic() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_15, () -> {
      doTest();
      checkQuickFixAll();
    });
  }

  public void testInnerStaticsJDK16() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_16, () -> {
      doTest();
      checkQuickFixAll();
    });
  }

  public void testImplicitClassNoFix() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      doTest();
      checkQuickFixAll();
    });
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new InnerClassMayBeStaticInspection();
  }
}