// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.classlayout;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.fixture.TestFixture;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

public class FinalPrivateMethodInspection9Test extends FinalPrivateMethodInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_9);

  @Override
  protected TestFixture<LightJavaProject> getProjectFixture() {
    return projectFixture;
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + '9';
  }
}