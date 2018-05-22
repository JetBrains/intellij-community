// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;


public class GenericsInnerClassHighlightingTest extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/genericsInnerClassHighlighting";

  public void testGenericToRawAssignment() {
    doTest();
  }

  @Override
  protected Sdk getProjectJDK() {
    return getTestName(false).contains("Jdk14") ? IdeaTestUtil.getMockJdk14() : super.getProjectJDK();
  }

  private void doTest() {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", false, false);
  }
}
