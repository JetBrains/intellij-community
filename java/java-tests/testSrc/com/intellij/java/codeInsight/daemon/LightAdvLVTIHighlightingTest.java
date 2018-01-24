// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.AnonymousCanBeLambdaInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;

public class LightAdvLVTIHighlightingTest extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advLVTI";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setLanguageLevel(LanguageLevel.JDK_10);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_10, getModule(), getTestRootDisposable());
  }

  private void doTest() {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", false, false);
  }

  public void testSimpleAvailability() { doTest(); }
  public void testDisabledInspections() {
    enableInspectionTool(new AnonymousCanBeLambdaInspection());
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", true, false);
  }
  public void testVarClassNameConflicts() { doTest(); }
  public void testStandaloneInVarContext() { doTest(); }
  public void testUpwardProjection() { doTest(); }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk9();
  }
}
