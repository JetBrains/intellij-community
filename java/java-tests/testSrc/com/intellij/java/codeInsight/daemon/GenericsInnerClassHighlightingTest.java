// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;


@TestApplication
public class GenericsInnerClassHighlightingTest {
  private static final LightProjectDescriptor DESCRIPTOR = lightJavaTestDescriptor();
  private static final LightProjectDescriptor JDK14_DESCRIPTOR =
    lightJavaTestDescriptor(LanguageLevel.HIGHEST, () -> IdeaTestUtil.getMockJdk14());
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture =
    lightDaemonAnalyzerFixture(projectFixture, testName -> testName.contains("Jdk14") ? JDK14_DESCRIPTOR : DESCRIPTOR);

  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/genericsInnerClassHighlighting";

  @Test
  public void testGenericToRawAssignment() {
    doTest();
  }

  private void doTest() {
    daemonFixture.get().doTest(BASE_PATH + "/" + daemonFixture.get().getFixture().getTestName(false) + ".java", false, false);
  }
}
