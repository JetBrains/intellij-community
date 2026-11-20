// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.testFramework;

import com.intellij.testFramework.fixtures.JavaHostFixtures;
import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.AnalysisHostRuntime;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks {@link AnalysisHostTest} on the IDE host.
 */
// The test checks the IDE host. The tag would also select it on the language server, where its first check fails.
@AnalysisHostTest(hosts = AnalysisHostTest.Host.IDE)
public class AnalysisHostTestTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<Path> tempPath = tempPathFixture();

  @Test
  public void runsOnTheIdeHost() {
    assertEquals(AnalysisHostTest.Host.IDE, AnalysisHostRuntime.current().getHost());
    assertEquals(AnalysisHostTest.Host.IDE, JavaHostFixtures.current().getHost());
  }

  @Test
  @AnalysisHostTest(hosts = AnalysisHostTest.Host.IDE)
  public void initializesTheFixturesOnceWhenTheMethodRepeatsTheAnnotation() {
    projectFixture.get();
    assertTrue(Files.isDirectory(tempPath.get()));
  }
}
