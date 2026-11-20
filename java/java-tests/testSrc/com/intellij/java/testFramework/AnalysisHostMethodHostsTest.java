// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.testFramework;

import com.intellij.testFramework.junit5.AnalysisHostRuntime;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks that {@link AnalysisHostTest#hosts()} on a method disables the method on a host that it does not list.
 * Each host runs one method and disables the other.
 */
@AnalysisHostTest
public class AnalysisHostMethodHostsTest {
  @Test
  @AnalysisHostTest(hosts = AnalysisHostTest.Host.IDE)
  public void runsOnlyOnTheIdeHost() {
    assertEquals(AnalysisHostTest.Host.IDE, AnalysisHostRuntime.current().getHost());
  }

  @Test
  @AnalysisHostTest(hosts = AnalysisHostTest.Host.LSP)
  public void runsOnlyOnTheLspHost() {
    assertEquals(AnalysisHostTest.Host.LSP, AnalysisHostRuntime.current().getHost());
  }
}
