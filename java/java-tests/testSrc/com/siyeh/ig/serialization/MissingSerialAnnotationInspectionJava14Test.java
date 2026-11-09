// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.serialization;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class MissingSerialAnnotationInspectionJava14Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new MissingSerialAnnotationInspection());

  @Test
  public void testSerializableClassInJava14Positive() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testSerializableClassInJava14Negative() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testExternalizableClassInJava14Positive() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testExternalizableClassInJava14Negative() {
    inspectionFixture.get().doTest();
  }
}
