// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;

public class LightAdvRawStringLiteralsHighlightingTest extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advRawStringLiteral";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setLanguageLevel(LanguageLevel.JDK_11_PREVIEW);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_11, getModule(), getTestRootDisposable());
  }

  private void doTest() {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", false, false);
  }

  public void testStringAssignability() {
    doTest();
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk9();
  }
}