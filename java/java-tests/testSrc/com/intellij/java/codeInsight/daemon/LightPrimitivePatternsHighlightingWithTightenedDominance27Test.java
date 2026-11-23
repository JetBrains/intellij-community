// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.fixture.TestFixture;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

//JEP 532 for java 27 is the same as JEP 520 for java 26 (without any changes)
public class LightPrimitivePatternsHighlightingWithTightenedDominance27Test extends LightPrimitivePatternsHighlightingWithTightenedDominanceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_27);

  @Override
  protected TestFixture<LightJavaProject> getProjectFixture() {
    return projectFixture;
  }
}