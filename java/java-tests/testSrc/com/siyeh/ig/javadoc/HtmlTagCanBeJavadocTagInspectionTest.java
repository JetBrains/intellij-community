// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.javadoc;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class HtmlTagCanBeJavadocTagInspectionTest extends LightJavaInspectionTestCase {

  public void testHtmlTagCanBeJavadocTag() {
    doTest();
    checkQuickFixAll();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new HtmlTagCanBeJavadocTagInspection();
  }
}