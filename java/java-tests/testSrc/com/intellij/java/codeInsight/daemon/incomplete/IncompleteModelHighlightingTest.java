// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.incomplete;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.IncompleteDependenciesService;

public final class IncompleteModelHighlightingTest extends LightDaemonAnalyzerTestCase {
  static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/incompleteHighlighting";

  private void doTest() {
    var ignored = WriteAction.compute(() -> getProject().getService(IncompleteDependenciesService.class).enterIncompleteState());
    try {
      doTest(BASE_PATH + "/" + getTestName(false) + ".java", true, true);
    }
    finally {
      WriteAction.run(ignored::close);
    }
  }
  
  public void testSimple() { doTest(); }

  public void testDefaultLoaderFactory() { doTest(); }
  
  public void testServer() { doTest(); }
}
