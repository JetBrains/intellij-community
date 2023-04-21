// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.java.sourceToSink;

import com.intellij.codeInspection.tests.sourceToSink.SourceToSinkFlowInspectionTestBase;
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil;

public class JavaSourceToSinkFlowInspectionTest extends SourceToSinkFlowInspectionTestBase {

  @Override
  protected String getBasePath() {
    return JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/sourceToSinkFlow";
  }

  public void testSimple() {
    prepareCheckFramework();
    myFixture.testHighlighting("Simple.java");
  }

  public void testLocalInference() {
    prepareCheckFramework();
    myFixture.testHighlighting("LocalInference.java");
  }

  public void testJsrSimple() {
    prepareJsr();
    myFixture.testHighlighting("JsrSimple.java");
  }
}
