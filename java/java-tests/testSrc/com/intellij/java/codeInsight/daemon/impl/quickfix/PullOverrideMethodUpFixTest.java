// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerOptions;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class PullOverrideMethodUpFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(
    projectFixture, List::of,
    LightDaemonAnalyzerOptions.DEFAULT.withBasePath("/java/java-tests/testData/codeInsight/daemonCodeAnalyzer/quickFix/pullUp"));

  @Test
  public void test1() {
    daemonFixture.get().quickFixFixture().doTest("1.java");
  }

  @Test
  public void test2() {
    daemonFixture.get().quickFixFixture().doTest("2.java");
  }

  @Test
  public void test3() {
    daemonFixture.get().quickFixFixture().doTest("3.java");
  }

  @Test
  public void test4() {
    daemonFixture.get().quickFixFixture().doTest("4.java");
  }

  @Test
  public void test6() {
    daemonFixture.get().quickFixFixture().doTest("6.java");
  }

  @Test
  public void testRefactoringIntentionsAvailable() {
    doTestActionAvailable(5, "Pull members up");
    doTestActionAvailable(5, "Extract interface");
    doTestActionAvailable(5, "Extract superclass");
  }

  private void doTestActionAvailable(final int suffix, final String actionText) {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    final String testFullPath = "/before" + suffix + ".java";
    daemon.configureByFile(testFullPath);
    daemon.doHighlighting();
    final IntentionAction action = daemon.findActionWithText(actionText);
    assertNotNull(action);
  }
}
