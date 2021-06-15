// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.sourceToSinkFlow;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.sourceToSink.SourceToSinkFlowInspection;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

public class SourceToSinkFlowInspectionTest extends DaemonAnalyzerTestCase {
  
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new SourceToSinkFlowInspection()};
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected Sdk getTestProjectJdk() {
    setLanguageLevel(LanguageLevel.JDK_1_8);
    return IdeaTestUtil.getMockJdk18();
  }

  public void testSimple() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    doTest("/codeInsight/daemonCodeAnalyzer/sourceToSinkFlow/" + getTestName(false) + ".java", true, false);
  }
}
