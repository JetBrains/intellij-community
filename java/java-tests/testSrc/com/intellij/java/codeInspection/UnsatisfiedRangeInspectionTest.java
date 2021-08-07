// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.dataFlow.UnsatisfiedRangeInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class UnsatisfiedRangeInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testUnsatisfiedRange() {
    myFixture.addClass("package org.jetbrains.annotations;\n" +
                       "import java.lang.annotation.*;\n" +
                       "@Target(ElementType.TYPE_USE)\n" +
                       "public @interface Range {\n" +
                       "  long from();\n" +
                       "  long to();\n" +
                       "}");
    myFixture.enableInspections(new UnsatisfiedRangeInspection());
    myFixture.configureByFile(getTestName(false)+".java");
    myFixture.testHighlighting(true, false, false);
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11_ANNOTATED;
  }



  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/unsatisfiedRange/";
  }
}