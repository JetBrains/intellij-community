// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.java;

import com.intellij.codeInspection.sourceToSink.SourceToSinkFlowInspection;
import com.intellij.jvm.analysis.JvmAnalysisTestsUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class SourceToSinkFlowInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass(
      "  package org.checkerframework.checker.tainting.qual;\n" +
      "      import java.lang.annotation.ElementType;\n" +
      "      import java.lang.annotation.Target;\n" +
      "      @Target({ElementType.LOCAL_VARIABLE, ElementType.FIELD, ElementType.METHOD})\n" +
      "      public @interface Tainted {\n" +
      "      }");
    myFixture.addClass(
      "      package org.checkerframework.checker.tainting.qual;\n" +
      "      import java.lang.annotation.ElementType;\n" +
      "      import java.lang.annotation.Target;\n" +
      "      @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})\n" +
      "      public @interface Untainted {\n" +
      "      }");
    myFixture.enableInspections(new SourceToSinkFlowInspection());
  }

  @Override
  protected String getTestDataPath() {
    return JvmAnalysisTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/sourceToSinkFlow";
  }

  public void testSimple() throws Exception {
    myFixture.testHighlighting("Simple.java");
  }
}
