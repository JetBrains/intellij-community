// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInspection.miscGenerics.RedundantTypeArgsInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_21;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class RedundantTypeArgsInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(JAVA_21);
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture, () -> List.of(new RedundantTypeArgsInspection()));

  private void doTest() {
    daemonFixture.get().doTest("/inspection/redundantTypeArgs/" + daemonFixture.get().getFixture().getTestName(false) + ".java", true, false);
  }

  @Test
  public void testReturnPrimitiveTypes() { // javac non-boxing: IDEA-53984
    doTest();
  }

  @Test
  public void testConditionalExpression() {
    doTest();
  }

  @Test
  public void testBoundInference() {
    doTest();
  }

  @Test
  public void testNestedCalls() {
    doTest();
  }
  
  @Test
  public void testTooManyArguments() {
    doTest();
  }

  @Test
  public void testNonGeneric() {
    IdeaTestUtil.withLevel(daemonFixture.get().getFixture().getModule(), LanguageLevel.JDK_1_8, () -> doTest());
  }
}