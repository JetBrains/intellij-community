// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.testFramework;

import com.intellij.testFramework.junit5.AnalysisHostRuntime;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks that a subclass gets {@link AnalysisHostTest#hosts()} of its base class.
 * The base class runs only on the language server, so the IDE host disables this class.
 */
public class AnalysisHostInheritedHostsTest extends LspOnlyAnalysisHostTestBase {
}

@AnalysisHostTest(hosts = AnalysisHostTest.Host.LSP)
abstract class LspOnlyAnalysisHostTestBase {
  @Test
  public void runsOnlyOnTheLspHost() {
    assertEquals(AnalysisHostTest.Host.LSP, AnalysisHostRuntime.current().getHost());
  }
}
